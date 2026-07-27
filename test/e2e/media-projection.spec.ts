import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {describe, it, before, beforeEach, after, type TestContext} from 'node:test';

import {ADB} from 'appium-adb';

import {SettingsApp} from '../../lib/client.js';
import {getSettingsApkPath} from '../../lib/utils.js';

describe('Media Projection', function () {
  let adb: ADB;
  let settingsApp: SettingsApp;
  let recorder: ReturnType<SettingsApp['makeMediaProjectionRecorder']>;
  let shouldSkip: boolean;

  before(async function () {
    // Initialize ADB
    adb = await ADB.createADB();

    // Check API level - media projection only works on API 29+
    const apiLevel = await adb.getApiLevel();
    if (apiLevel < 29) {
      shouldSkip = true; // Skip entire suite if API level is too low
      return;
    }

    // Initialize SettingsApp
    settingsApp = new SettingsApp({adb});

    // Ensure the app is installed
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

    // Ensure the app is running
    await settingsApp.requireRunning();

    // Create a single recorder instance
    recorder = settingsApp.makeMediaProjectionRecorder();
  });

  beforeEach(async function () {
    if (shouldSkip) {
      return;
    }

    // Ensure recorder is stopped before each test
    try {
      if (await recorder.isRunning()) {
        await recorder.stop();
      }
    } catch {
      // Ignore cleanup errors
    }
  });

  after(async function () {
    if (shouldSkip) {
      return;
    }

    // Clean up: stop any running recording
    try {
      if (await recorder.isRunning()) {
        await recorder.stop();
      }
    } catch {
      // Ignore cleanup errors
    }
  });

  describe('Media Projection Recorder', function () {
    it('should start and stop recording successfully', async function (ctx: TestContext) {
      if (shouldSkip) {
        ctx.skip();
        return;
      }

      // Initially, recording should not be running
      assert.strictEqual(await recorder.isRunning(), false);

      // Adjust permissions for API 29+
      await settingsApp.adjustMediaProjectionServicePermissions();

      // Start recording
      const started = await recorder.start({
        filename: 'test-recording.mp4',
        maxDurationSec: 60,
        priority: 'normal',
      });
      assert.strictEqual(started, true);

      // Verify recording is running
      assert.strictEqual(await recorder.isRunning(), true);

      // Wait a bit to ensure recording is active
      await new Promise<void>((resolve) => setTimeout(resolve, 2000));

      // Stop recording
      const stopped = await recorder.stop();
      assert.strictEqual(stopped, true);

      // Verify recording is stopped
      assert.strictEqual(await recorder.isRunning(), false);
    });

    it('should handle multiple start calls gracefully', async function (ctx: TestContext) {
      if (shouldSkip) {
        ctx.skip();
        return;
      }

      // Adjust permissions
      await settingsApp.adjustMediaProjectionServicePermissions();

      // Start recording
      const started1 = await recorder.start({
        filename: 'test-recording-2.mp4',
      });
      assert.strictEqual(started1, true);

      // Try to start again - should return false since already running
      const started2 = await recorder.start({
        filename: 'test-recording-3.mp4',
      });
      assert.strictEqual(started2, false);

      // Clean up
      await recorder.stop();
    });

    it('should pull recording file after stopping', async function (ctx: TestContext) {
      if (shouldSkip) {
        ctx.skip();
        return;
      }

      // Adjust permissions
      await settingsApp.adjustMediaProjectionServicePermissions();

      // Start recording
      await recorder.start({
        filename: 'test-recording-pull.mp4',
      });

      // Wait a bit to ensure some content is recorded
      await new Promise<void>((resolve) => setTimeout(resolve, 3000));

      // Stop recording
      await recorder.stop();

      // Pull the recording file
      const recordingPath = await recorder.pullRecent();
      try {
        if (recordingPath) {
          // Verify file exists
          const stats = await fs.stat(recordingPath);
          assert.strictEqual(stats.isFile(), true);
          assert.ok(stats.size > 0);
        }
      } finally {
        // Clean up the pulled file
        if (recordingPath) {
          await fs.unlink(recordingPath);
        }
      }
    });

    it('should handle cleanup of old recordings', async function (ctx: TestContext) {
      if (shouldSkip) {
        ctx.skip();
        return;
      }

      // Adjust permissions
      await settingsApp.adjustMediaProjectionServicePermissions();

      // Cleanup should not throw
      await assert.doesNotReject(() => recorder.cleanup());

      // Start and stop a recording
      await recorder.start({
        filename: 'test-cleanup.mp4',
      });
      await new Promise<void>((resolve) => setTimeout(resolve, 2000));
      await recorder.stop();

      // Cleanup again should not throw
      await assert.doesNotReject(() => recorder.cleanup());
    });
  });
});
