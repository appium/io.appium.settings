import {waitForCondition} from 'asyncbox';
import B from 'bluebird';
import _ from 'lodash';
import fs from 'fs/promises';
import path from 'node:path';
import {
  SETTINGS_HELPER_ID,
  RECORDING_ACTION_START,
  RECORDING_ACTION_STOP,
  RECORDING_ACTIVITY_NAME,
  RECORDING_SERVICE_NAME,
} from '../constants';
import type { ADB } from 'appium-adb';
import type { SettingsApp } from '../client';
import type { StartMediaProjectionRecordingOpts } from './types';

/**
 * Creates a new instance of the MediaProjection-based recorder.
 * The recorder only works since Android API 29+
 *
 * @returns The recorder instance
 */
export function makeMediaProjectionRecorder(this: SettingsApp): MediaProjectionRecorder {
  return new MediaProjectionRecorder(this.adb);
}

/**
 * Adjusts the necessary permissions for the Media Projection-based recording service.
 * This method only applies to devices running Android API 29 or higher.
 *
 * @returns True if permissions were adjusted, false if device API level is below 29
 */
export async function adjustMediaProjectionServicePermissions(this: SettingsApp): Promise<boolean> {
  if (await this.adb.getApiLevel() >= 29) {
    await this.adb.shell(['appops', 'set', SETTINGS_HELPER_ID, 'PROJECT_MEDIA', 'allow']);
    return true;
  }
  return false;
}

const RECORDING_STARTUP_TIMEOUT_MS = 3 * 1000;
const RECORDING_STOP_TIMEOUT_MS = 3 * 1000;
const RECORDINGS_ROOT = `/storage/emulated/0/Android/data/${SETTINGS_HELPER_ID}/files`;

/**
 * Media projection recorder for capturing device screen recordings.
 * This class provides methods to start, stop, and manage screen recordings
 * using Android's MediaProjection API (API 29+).
 */
export class MediaProjectionRecorder {
  private readonly adb: ADB;

  /**
   * Creates a new MediaProjectionRecorder instance.
   *
   * @param adb - ADB instance for device communication
   */
  constructor(adb: ADB) {
    this.adb = adb;
  }

  /**
   * Checks if the recording is currently running.
   */
  async isRunning(): Promise<boolean> {
    const stdout = await this.adb.shell([
      'dumpsys',
      'activity',
      'services',
      RECORDING_SERVICE_NAME,
    ]);
    return stdout.includes(RECORDING_SERVICE_NAME);
  }

  /**
   * Starts the media projection recording.
   * If a recording is already running, this method will return false without starting a new one.
   *
   * @param opts Recording options including filename, resolution, duration, and priority
   * @returns True if recording was started successfully, false if already running
   * @throws {Error} If recording fails to start within the timeout period
   */
  async start(opts: StartMediaProjectionRecordingOpts = {}): Promise<boolean> {
    if (await this.isRunning()) {
      return false;
    }

    await this.cleanup();
    const {filename, maxDurationSec, priority, resolution} = opts;
    const args: string[] = ['am', 'start', '-n', RECORDING_ACTIVITY_NAME, '-a', RECORDING_ACTION_START];
    if (filename) {
      args.push('--es', 'filename', filename);
    }
    if (maxDurationSec) {
      args.push('--es', 'max_duration_sec', `${maxDurationSec}`);
    }
    if (priority) {
      args.push('--es', 'priority', priority);
    }
    if (resolution) {
      args.push('--es', 'resolution', resolution);
    }
    await this.adb.shell(args);
    await new B<void>((resolve, reject) => {
      setTimeout(async () => {
        if (!(await this.isRunning())) {
          return reject(
            new Error(
              `The media projection recording is not running after ${RECORDING_STARTUP_TIMEOUT_MS}ms. ` +
                `Please check the logcat output for more details.`
            )
          );
        }
        resolve();
      }, RECORDING_STARTUP_TIMEOUT_MS);
    });
    return true;
  }

  /**
   * Cleans up old recording files.
   */
  async cleanup(): Promise<void> {
    await this.adb.shell([`rm -f ${RECORDINGS_ROOT}/*`]);
  }

  /**
   * Pulls the most recent recording file from the device.
   *
   * @returns Path to the pulled file, or null if no recordings exist
   */
  async pullRecent(): Promise<string | null> {
    const recordings = await this.adb.ls(RECORDINGS_ROOT, ['-tr']);
    if (_.isEmpty(recordings)) {
      return null;
    }

    const tmpRoot = await fs.mkdtemp('recording');
    const dstPath = path.join(tmpRoot, recordings[0]);
    // increase timeout to 5 minutes because it might take a while to pull a large video file
    await this.adb.pull(`${RECORDINGS_ROOT}/${recordings[0]}`, dstPath, {timeout: 300000});
    return dstPath;
  }

  /**
   * Stops the current recording.
   *
   * @returns True if recording was stopped successfully, false if no recording was running
   * @throws {Error} If the recording fails to stop within the timeout period
   */
  async stop(): Promise<boolean> {
    if (!(await this.isRunning())) {
      return false;
    }

    await this.adb.shell([
      'am',
      'start',
      '-n',
      RECORDING_ACTIVITY_NAME,
      '-a',
      RECORDING_ACTION_STOP,
    ]);
    try {
      await waitForCondition(async () => !(await this.isRunning()), {
        waitMs: RECORDING_STOP_TIMEOUT_MS,
        intervalMs: 500,
      });
    } catch {
      throw new Error(
        `The attempt to stop the current media projection recording timed out after ` +
          `${RECORDING_STOP_TIMEOUT_MS}ms`
      );
    }
    return true;
  }
}
