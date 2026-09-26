import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {describe, it, before, beforeEach, after, type TestContext} from 'node:test';

import {ADB} from 'appium-adb';

import {SettingsApp} from '../../lib/client.js';
import type {AccessUnit} from '../../lib/commands/types.js';
import {getSettingsApkPath} from '../../lib/utils.js';

describe('Video Streaming', function () {
  let adb: ADB;
  let settingsApp: SettingsApp;
  let session: ReturnType<SettingsApp['makeVideoStreamSession']>;
  let shouldSkip: boolean;

  before(async function () {
    adb = await ADB.createADB();

    const apiLevel = await adb.getApiLevel();
    if (apiLevel < 29) {
      shouldSkip = true;
      return;
    }

    settingsApp = new SettingsApp({adb});

    const apkPath = getSettingsApkPath();
    if (
      !(await fs
        .access(apkPath)
        .then(() => true)
        .catch(() => false))
    ) {
      throw new Error(`APK not found at ${apkPath}. Please run 'npm run build' first.`);
    }
    await adb.install(apkPath, {
      replace: true,
      grantPermissions: true,
    });

    await settingsApp.requireRunning();
    await settingsApp.adjustMediaProjectionServicePermissions();
  });

  beforeEach(async function () {
    if (shouldSkip) {
      return;
    }
    session = settingsApp.makeVideoStreamSession();
  });

  after(async function () {
    if (shouldSkip || !session) {
      return;
    }
    try {
      if (await session.isRunning()) {
        await session.stop();
      }
    } catch {
      // Ignore cleanup errors
    }
  });

  it('should stream H.264 access units starting with a config unit and a later keyframe', async function (ctx: TestContext) {
    if (shouldSkip) {
      ctx.skip();
      return;
    }

    const started = await session.start({codec: 'h264', fps: 15, bitrate: 1000000});
    assert.strictEqual(started, true);
    assert.strictEqual(await session.isRunning(), true);

    const collected: AccessUnit[] = [];
    const deadline = Date.now() + 15000;
    for await (const unit of session.accessUnits()) {
      collected.push(unit);
      if (collected.length >= 10 || Date.now() > deadline) {
        break;
      }
    }

    assert.ok(collected.length > 0, 'expected at least one video access unit');
    assert.strictEqual(collected[0].track, 'video');
    assert.strictEqual(collected[0].isConfig, true);
    // Annex-B start code: 00 00 00 01 or 00 00 01
    const csd = collected[0].data;
    const hasLongStartCode = csd[0] === 0 && csd[1] === 0 && csd[2] === 0 && csd[3] === 1;
    const hasShortStartCode = csd[0] === 0 && csd[1] === 0 && csd[2] === 1;
    assert.ok(hasLongStartCode || hasShortStartCode, 'expected an Annex-B start code in the config unit');

    assert.ok(
      collected.some((unit) => unit.isKeyFrame),
      'expected at least one keyframe among the collected access units',
    );

    const stopped = await session.stop();
    assert.strictEqual(stopped, true);
  });

  it('should interleave an ADTS-framed audio track when audio is enabled', async function (ctx: TestContext) {
    if (shouldSkip) {
      ctx.skip();
      return;
    }

    const started = await session.start({audio: true});
    assert.strictEqual(started, true);

    let sawVideo = false;
    let sawAudio = false;
    const deadline = Date.now() + 15000;
    for await (const unit of session.accessUnits()) {
      if (unit.track === 'video') {
        sawVideo = true;
      } else if (unit.track === 'audio') {
        sawAudio = true;
        // ADTS sync word: 0xFF Fx
        assert.strictEqual(unit.data[0], 0xff);
        assert.strictEqual(unit.data[1] & 0xf0, 0xf0);
      }
      if ((sawVideo && sawAudio) || Date.now() > deadline) {
        break;
      }
    }

    assert.ok(sawVideo, 'expected at least one video access unit');
    assert.ok(sawAudio, 'expected at least one audio access unit');

    await session.stop();
  });
});
