import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {describe, it, before, beforeEach, afterEach, type TestContext} from 'node:test';

import {ADB} from 'appium-adb';
import {waitForCondition} from 'asyncbox';

import {SettingsApp} from '../../lib/client.js';
import {getSettingsApkPath} from '../../lib/utils/index.js';

describe('JPEG Streaming', function () {
  let adb: ADB;
  let settingsApp: SettingsApp;
  let session: ReturnType<SettingsApp['makeJpegStreamSession']>;
  let shouldSkip: boolean;

  before(async function () {
    adb = await ADB.createADB();

    // Live streaming, like media projection recording, only works on API 29+
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
    session = settingsApp.makeJpegStreamSession();
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

  it('should stream JPEG frames with strictly increasing sequence numbers', async function (ctx: TestContext) {
    if (shouldSkip) {
      ctx.skip();
      return;
    }

    assert.strictEqual(await session.isRunning(), false);

    const started = await session.start({fps: 10, quality: 60, scale: 50});
    assert.strictEqual(started, true);
    assert.strictEqual(await session.isRunning(), true);

    const collected: {sequence: number; data: Buffer}[] = [];
    const timeoutMs = 15000;
    const deadline = Date.now() + timeoutMs;
    for await (const frame of session.frames()) {
      collected.push(frame);
      if (collected.length >= 5 || Date.now() > deadline) {
        break;
      }
    }

    assert.ok(collected.length > 0, 'expected at least one JPEG frame to be received');
    for (const frame of collected) {
      assert.strictEqual(frame.data[0], 0xff);
      assert.strictEqual(frame.data[1], 0xd8);
      assert.strictEqual(frame.data[frame.data.length - 2], 0xff);
      assert.strictEqual(frame.data[frame.data.length - 1], 0xd9);
    }
    for (let i = 1; i < collected.length; i++) {
      assert.ok(collected[i].sequence > collected[i - 1].sequence);
    }

    const stopped = await session.stop();
    assert.strictEqual(stopped, true);
    assert.strictEqual(await session.isRunning(), false);
  });

  it('should return false when starting twice', async function (ctx: TestContext) {
    if (shouldSkip) {
      ctx.skip();
      return;
    }

    const started1 = await session.start({fps: 5});
    assert.strictEqual(started1, true);

    const otherSession = settingsApp.makeJpegStreamSession();
    const started2 = await otherSession.start({fps: 5});
    assert.strictEqual(started2, false);

    await session.stop();
  });

  it('should stop the on-device service on its own after the client disconnects', async function (ctx: TestContext) {
    if (shouldSkip) {
      ctx.skip();
      return;
    }

    const started = await session.start({fps: 10});
    assert.strictEqual(started, true);

    for await (const _frame of session.frames()) {
      break;
    }

    // Disconnect the client transport directly (bypassing stop()/ACTION_STOP) to simulate
    // a client crash/disconnect. This reaches into a private field deliberately, since
    // there is no public API for "disconnect without stopping" - the on-device service is
    // expected to notice on its own and stop itself/its foreground notification.
    const transport = (session as unknown as {transport: {close(): Promise<void>}}).transport;
    assert.ok(transport, 'expected an internal transport after start()');
    await transport.close();

    await waitForCondition(async () => !(await session.isRunning()), {waitMs: 10000, intervalMs: 300});
    assert.strictEqual(await session.isRunning(), false);
  });
});
