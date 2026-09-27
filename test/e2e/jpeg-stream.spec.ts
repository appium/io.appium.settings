import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import fs from 'node:fs/promises';
import {describe, it, before, beforeEach, afterEach, type TestContext} from 'node:test';

import {ADB} from 'appium-adb';
import {waitForCondition} from 'asyncbox';

import {SettingsApp} from '../../lib/client.js';
import {JPEG_STREAM_ACTION_START, JPEG_STREAM_ACTION_STOP, STREAMING_ACTIVITY_NAME} from '../../lib/constants.js';
import {getSettingsApkPath} from '../../lib/utils/index.js';

// Scans JPEG markers for a Start Of Frame segment (baseline SOF0 or progressive SOF2 -
// both encode height/width the same way, right after a 1-byte sample precision field), so a
// frame's own dimensions can be checked without a full JPEG decode.
function parseJpegDimensions(data: Buffer): {width: number; height: number} {
  let offset = 2; // Skip the SOI marker (FF D8).
  while (offset + 4 <= data.length) {
    if (data[offset] !== 0xff) {
      throw new Error(`Expected a JPEG marker byte at offset ${offset}, got 0x${data[offset].toString(16)}`);
    }
    const marker = data[offset + 1];
    if (marker === 0xd8 || marker === 0xd9) {
      offset += 2;
      continue;
    }
    const segmentLength = data.readUInt16BE(offset + 2);
    const isSofMarker = marker >= 0xc0 && marker <= 0xcf && marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc;
    if (isSofMarker) {
      return {height: data.readUInt16BE(offset + 5), width: data.readUInt16BE(offset + 7)};
    }
    offset += 2 + segmentLength;
  }
  throw new Error('No SOF marker found in JPEG data');
}

// `fixed-to-user-rotation` (API 30+) overrides the foreground app's own orientation request
// (e.g. a launcher's portrait lock); best-effort since it doesn't exist below that - `lock`
// is the actual trigger, so its own failure means this platform can't force a rotation at all.
async function tryLockRotation(adb: ADB, rotation: number): Promise<boolean> {
  await adb.shell(['cmd', 'window', 'fixed-to-user-rotation', 'enabled']).catch(() => {});
  return adb
    .shell(['cmd', 'window', 'user-rotation', 'lock', `${rotation}`])
    .then(() => true)
    .catch(() => false);
}

async function restoreRotation(adb: ADB): Promise<void> {
  await adb.shell(['cmd', 'window', 'user-rotation', 'lock', '0']).catch(() => {});
  await adb.shell(['cmd', 'window', 'fixed-to-user-rotation', 'default']).catch(() => {});
}

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

    // waitForCondition's own success is the assertion - a redundant re-check right after can
    // flake, since `dumpsys activity services` can briefly flicker right after a service stop.
    await waitForCondition(async () => !(await session.isRunning()), {waitMs: 10000, intervalMs: 300});
  });

  it('should stop the on-device service on its own if no client connects within the accept timeout', async function (ctx: TestContext) {
    if (shouldSkip) {
      ctx.skip();
      return;
    }

    // Starts the service directly (bypassing session.start()) so no adb forward/client
    // ever connects - the on-device accept-timeout watchdog must stop the session, the
    // service and its media projection on its own.
    const socketName = `io.appium.settings.jpegstream.${randomUUID()}`;
    await adb.shell([
      'am',
      'start',
      '-n',
      STREAMING_ACTIVITY_NAME,
      '-a',
      JPEG_STREAM_ACTION_START,
      '--es',
      'socket_name',
      socketName,
    ]);
    try {
      await waitForCondition(async () => await session.isRunning(), {waitMs: 3000, intervalMs: 300});
      // waitForCondition's own success is the assertion (see the test above for why).
      await waitForCondition(async () => !(await session.isRunning()), {waitMs: 15000, intervalMs: 500});
    } finally {
      await adb.shell(['am', 'start', '-n', STREAMING_ACTIVITY_NAME, '-a', JPEG_STREAM_ACTION_STOP]).catch(() => {});
    }
  });

  it('should stop the on-device service if stopped before any client connects', async function (ctx: TestContext) {
    if (shouldSkip) {
      ctx.skip();
      return;
    }

    const socketName = `io.appium.settings.jpegstream.${randomUUID()}`;
    await adb.shell([
      'am',
      'start',
      '-n',
      STREAMING_ACTIVITY_NAME,
      '-a',
      JPEG_STREAM_ACTION_START,
      '--es',
      'socket_name',
      socketName,
    ]);
    await waitForCondition(async () => await session.isRunning(), {waitMs: 3000, intervalMs: 300});

    // Stops while accept() is still blocked (no client ever connected) - this must not
    // leave the session thread, listening socket or media projection alive.
    await adb.shell(['am', 'start', '-n', STREAMING_ACTIVITY_NAME, '-a', JPEG_STREAM_ACTION_STOP]);
    // waitForCondition's own success is the assertion (see the first test above for why).
    await waitForCondition(async () => !(await session.isRunning()), {waitMs: 5000, intervalMs: 300});
  });

  it('should adapt frame dimensions to a device rotation without interrupting the stream', async function (ctx: TestContext) {
    if (shouldSkip) {
      ctx.skip();
      return;
    }
    if (!(await tryLockRotation(adb, 0))) {
      // Platform can't force a rotation via adb shell (e.g. no fixed-to-user-rotation and
      // something in the foreground holds its own orientation lock) - nothing to test here.
      ctx.skip();
      return;
    }

    const started = await session.start({fps: 10});
    assert.strictEqual(started, true);

    try {
      let lastSequence = -1;
      let firstDimensions: {width: number; height: number} | undefined;
      for await (const frame of session.frames()) {
        lastSequence = frame.sequence;
        firstDimensions = parseJpegDimensions(frame.data);
        break;
      }
      assert.ok(firstDimensions, 'expected at least one frame before rotating');

      // 1 = ROTATION_90, guaranteed to flip portrait<->landscape from ROTATION_0 above.
      await adb.shell(['cmd', 'window', 'user-rotation', 'lock', '1']);

      let rotatedDimensions: {width: number; height: number} | undefined;
      const afterRotationDeadline = Date.now() + 15000;
      for await (const frame of session.frames()) {
        assert.ok(frame.sequence > lastSequence, 'expected sequence numbers to keep increasing across the rotation');
        lastSequence = frame.sequence;
        const dimensions = parseJpegDimensions(frame.data);
        if (dimensions.width !== firstDimensions!.width || dimensions.height !== firstDimensions!.height) {
          rotatedDimensions = dimensions;
          break;
        }
        if (Date.now() > afterRotationDeadline) {
          break;
        }
      }

      assert.ok(rotatedDimensions, 'expected a later frame with different dimensions after rotating');
      assert.strictEqual(rotatedDimensions!.width, firstDimensions!.height);
      assert.strictEqual(rotatedDimensions!.height, firstDimensions!.width);
    } finally {
      await restoreRotation(adb);
    }

    await session.stop();
  });
});
