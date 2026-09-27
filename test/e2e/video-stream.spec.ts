import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {describe, it, before, beforeEach, afterEach, type TestContext} from 'node:test';

import {ADB} from 'appium-adb';

import {SettingsApp} from '../../lib/client.js';
import type {AccessUnit} from '../../lib/commands/types.js';
import {getSettingsApkPath} from '../../lib/utils/index.js';

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

  // Runs after every test (pass or fail), not just at suite end, so a failed assertion
  // mid-test can't leave a stream (and its socket/adb forward) running into the next test.
  afterEach(async function () {
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
    let firstVideoTimestampMicros: number | undefined;
    let firstAudioTimestampMicros: number | undefined;
    const deadline = Date.now() + 15000;
    for await (const unit of session.accessUnits()) {
      // The CONFIG unit's timestamp already came from getPresentationTimeUs() before the
      // fix, so it can't detect a regression there - only a real (non-config) video frame,
      // which carries the encoder's own presentationTimeUs, exercises toSessionRelativeUs().
      if (unit.track === 'video' && !unit.isConfig) {
        sawVideo = true;
        firstVideoTimestampMicros ??= unit.timestampMicros;
      } else if (unit.track === 'audio') {
        sawAudio = true;
        firstAudioTimestampMicros ??= unit.timestampMicros;
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

    // Both tracks are documented as session-relative timestamps; if video were left on its
    // encoder's absolute clock instead, this delta would be off by the session's uptime
    // (many seconds to hours), not by mere capture-startup jitter between the two tracks.
    assert.ok(firstVideoTimestampMicros !== undefined && firstAudioTimestampMicros !== undefined);
    const timestampDeltaMicros = Math.abs(firstVideoTimestampMicros - firstAudioTimestampMicros);
    assert.ok(
      timestampDeltaMicros < 10_000_000,
      `expected video/audio timestamps to share a session-relative origin, got a ${timestampDeltaMicros}us delta`,
    );

    await session.stop();
  });

  it('should reconfigure the encoder with a fresh CONFIG unit after a device rotation', async function (ctx: TestContext) {
    if (shouldSkip) {
      ctx.skip();
      return;
    }

    const started = await session.start({codec: 'h264', fps: 15, bitrate: 1000000});
    assert.strictEqual(started, true);

    try {
      // fixed-to-user-rotation overrides the foreground app's own orientation request (e.g.
      // the launcher's portrait lock), which otherwise makes a plain `settings put system
      // user_rotation` silently no-op with nothing visibly requesting a rotation change.
      await adb.shell(['cmd', 'window', 'fixed-to-user-rotation', 'enabled']);
      await adb.shell(['cmd', 'window', 'user-rotation', 'lock', '0']);

      // Video and audio sequence numbers are independent per-track counters, so only the
      // video track's own sequence is checked for monotonicity across the rotation.
      let lastVideoSequence = -1;
      let firstConfig: AccessUnit | undefined;
      const beforeRotationDeadline = Date.now() + 15000;
      for await (const unit of session.accessUnits()) {
        if (unit.track !== 'video') {
          continue;
        }
        assert.ok(unit.sequence > lastVideoSequence, 'expected strictly increasing video sequence numbers');
        lastVideoSequence = unit.sequence;
        if (unit.isConfig) {
          firstConfig = unit;
          break;
        }
        if (Date.now() > beforeRotationDeadline) {
          break;
        }
      }
      assert.ok(firstConfig, 'expected an initial CONFIG unit');

      // 1 = ROTATION_90, guaranteed to flip portrait<->landscape from ROTATION_0 above.
      await adb.shell(['cmd', 'window', 'user-rotation', 'lock', '1']);

      let secondConfig: AccessUnit | undefined;
      let sawKeyframeAfterConfig = false;
      const afterRotationDeadline = Date.now() + 15000;
      for await (const unit of session.accessUnits()) {
        if (unit.track !== 'video') {
          continue;
        }
        assert.ok(
          unit.sequence > lastVideoSequence,
          'expected video sequence numbers to keep increasing (not reset) across the rotation',
        );
        lastVideoSequence = unit.sequence;
        if (!secondConfig) {
          if (unit.isConfig) {
            secondConfig = unit;
          }
        } else if (unit.isKeyFrame) {
          sawKeyframeAfterConfig = true;
          break;
        }
        if (Date.now() > afterRotationDeadline) {
          break;
        }
      }

      assert.ok(secondConfig, 'expected a second CONFIG unit after rotating');
      assert.notDeepStrictEqual(
        secondConfig!.data,
        firstConfig!.data,
        'expected the post-rotation CONFIG (SPS/PPS) bytes to differ from the original',
      );
      assert.ok(sawKeyframeAfterConfig, 'expected a keyframe to follow the post-rotation CONFIG unit');
    } finally {
      await adb.shell(['cmd', 'window', 'user-rotation', 'lock', '0']).catch(() => {});
      await adb.shell(['cmd', 'window', 'fixed-to-user-rotation', 'default']).catch(() => {});
    }

    await session.stop();
  });
});
