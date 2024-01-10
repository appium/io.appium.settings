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

const RECORDING_STARTUP_TIMEOUT_MS = 3 * 1000;
const RECORDING_STOP_TIMEOUT_MS = 3 * 1000;
const RECORDINGS_ROOT = `/storage/emulated/0/Android/data/${SETTINGS_HELPER_ID}/files`;

/**
 * @typedef {Object} StartMediaProjectionRecordingOpts
 * @property {string} [resolution] Maximum supported resolution on-device (Detected automatically by the app
 * itself), which usually equals to Full HD 1920x1080 on most phones however
 * you can change it to following supported resolutions as well: "1920x1080",
 * "1280x720", "720x480", "320x240", "176x144".
 * @property {number} [maxDurationSec=900] Maximum allowed duration is 15 minutes; you can increase it if your test
 * takes longer than that.
 * @property {'high' | 'normal' | 'low'} [priority='high'] Recording thread priority.
 * If you face performance drops during testing with recording enabled, you
 * can reduce recording priority
 * @property {string} [filename] You can type recording video file name as you want, but recording currently
 * supports only "mp4" format so your filename must end with ".mp4". An
 * invalid file name will fail to start the recording.
 */

class MediaProjectionRecorder {
  /**
   * @param {ADB} adb
   */
  constructor(adb) {
    this.adb = adb;
  }

  /**
   *
   * @returns {Promise<boolean>}
   */
  async isRunning() {
    const stdout = await this.adb.shell([
      'dumpsys',
      'activity',
      'services',
      RECORDING_SERVICE_NAME,
    ]);
    return stdout.includes(RECORDING_SERVICE_NAME);
  }

  /**
   *
   * @param {StartMediaProjectionRecordingOpts} opts
   * @returns {Promise<boolean>}
   */
  async start(opts = {}) {
    if (await this.isRunning()) {
      return false;
    }

    await this.cleanup();
    const {filename, maxDurationSec, priority, resolution} = opts;
    const args = ['am', 'start', '-n', RECORDING_ACTIVITY_NAME, '-a', RECORDING_ACTION_START];
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
    await new B((resolve, reject) => {
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
   * @returns {Promise<void>}
   */
  async cleanup() {
    await this.adb.shell([`rm -f ${RECORDINGS_ROOT}/*`]);
  }

  /**
   *
   * @returns {Promise<string?>}
   */
  async pullRecent() {
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
   * @returns {Promise<boolean>}
   */
  async stop() {
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
    } catch (e) {
      throw new Error(
        `The attempt to stop the current media projection recording timed out after ` +
          `${RECORDING_STOP_TIMEOUT_MS}ms`
      );
    }
    return true;
  }
}

/**
 * Creates a new instance of the MediaProjection-based recorder
 * The recorder only works since Android API 29+
 *
 * @this {import('../client').SettingsApp}
 * @returns {MediaProjectionRecorder} The recorder instance
 */
export function makeMediaProjectionRecorder() {
  return new MediaProjectionRecorder(this.adb);
}

/**
 * Adjusts the necessary permissions for the
 * Media Projection-based recording service
 *
 * @this {import('../client').SettingsApp}
 * @returns {Promise<boolean>} If the permssions adjustment has actually been made
 */
export async function adjustMediaProjectionServicePermissions() {
  if (await this.adb.getApiLevel() >= 29) {
    await this.adb.shell(['appops', 'set', SETTINGS_HELPER_ID, 'PROJECT_MEDIA', 'allow']);
    return true;
  }
  return false;
}

/**
 * @typedef {import('appium-adb').ADB} ADB
 */
