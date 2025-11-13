import { SettingsApp } from '../../lib/client';
import { ADB } from 'appium-adb';
import { getSettingsApkPath } from '../../lib/utils';
import fs from 'fs/promises';
import { expect, use } from 'chai';
import chaiAsPromised from 'chai-as-promised';

use(chaiAsPromised);

describe('Media Projection', function () {
  let adb: ADB;
  let settingsApp: SettingsApp;
  let recorder: ReturnType<SettingsApp['makeMediaProjectionRecorder']>;

  before(async function () {
    // Initialize ADB
    adb = await ADB.createADB();

    // Check API level - media projection only works on API 29+
    const apiLevel = await adb.getApiLevel();
    if (apiLevel < 29) {
      this.skip(); // Skip entire suite if API level is too low
    }

    // Initialize SettingsApp
    settingsApp = new SettingsApp({ adb });

    // Ensure the app is installed
    const apkPath = getSettingsApkPath();
    if (!(await fs.access(apkPath).then(() => true).catch(() => false))) {
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
    it('should start and stop recording successfully', async function () {
      // Initially, recording should not be running
      expect(await recorder.isRunning()).to.be.false;

      // Adjust permissions for API 29+
      await settingsApp.adjustMediaProjectionServicePermissions();

      // Start recording
      const started = await recorder.start({
        filename: 'test-recording.mp4',
        maxDurationSec: 60,
        priority: 'normal',
      });
      expect(started).to.be.true;

      // Verify recording is running
      expect(await recorder.isRunning()).to.be.true;

      // Wait a bit to ensure recording is active
      await new Promise<void>((resolve) => setTimeout(resolve, 2000));

      // Stop recording
      const stopped = await recorder.stop();
      expect(stopped).to.be.true;

      // Verify recording is stopped
      expect(await recorder.isRunning()).to.be.false;
    });

    it('should handle multiple start calls gracefully', async function () {
      // Adjust permissions
      await settingsApp.adjustMediaProjectionServicePermissions();

      // Start recording
      const started1 = await recorder.start({
        filename: 'test-recording-2.mp4',
      });
      expect(started1).to.be.true;

      // Try to start again - should return false since already running
      const started2 = await recorder.start({
        filename: 'test-recording-3.mp4',
      });
      expect(started2).to.be.false;

      // Clean up
      await recorder.stop();
    });

    it('should pull recording file after stopping', async function () {
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
          expect(stats.isFile()).to.be.true;
          expect(stats.size).to.be.greaterThan(0);
        }
      } finally {
        // Clean up the pulled file
        if (recordingPath) {
          await fs.unlink(recordingPath);
        }
      }
    });

    it('should handle cleanup of old recordings', async function () {
      // Adjust permissions
      await settingsApp.adjustMediaProjectionServicePermissions();

      // Cleanup should not throw
      await expect(recorder.cleanup()).to.be.fulfilled;

      // Start and stop a recording
      await recorder.start({
        filename: 'test-cleanup.mp4',
      });
      await new Promise<void>((resolve) => setTimeout(resolve, 2000));
      await recorder.stop();

      // Cleanup again should not throw
      await expect(recorder.cleanup()).to.be.fulfilled;
    });
  });
});

