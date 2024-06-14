import { log, LOG_PREFIX } from './logger';
import _ from 'lodash';
import { waitForCondition } from 'asyncbox';
import { SETTINGS_HELPER_ID, SETTINGS_HELPER_MAIN_ACTIVITY } from './constants.js';
import { setAnimationState } from './commands/animation';
import { setBluetoothState, unpairAllBluetoothDevices } from './commands/bluetooth';
import { getClipboard } from './commands/clipboard';
import { setGeoLocation, getGeoLocation, refreshGeoLocationCache } from './commands/geolocation';
import { setDeviceLocale, listSupportedLocales } from './commands/locale';
import { scanMedia } from './commands/media';
import { setDataState, setWifiState } from './commands/network';
import { getNotifications, adjustNotificationsPermissions } from './commands/notifications';
import { getSmsList } from './commands/sms';
import { performEditorAction, typeUnicode } from './commands/typing';
import {
  makeMediaProjectionRecorder,
  adjustMediaProjectionServicePermissions,
} from './commands/media-projection';

/**
 * @typedef {Object} SettingsAppOpts
 * @property {import('appium-adb').ADB} adb
 */


export class SettingsApp {
  /** @type {import('appium-adb').ADB} */
  adb;

  /** @type {import('@appium/logger').Logger} */
  log;

  /**
   * @param {SettingsAppOpts} opts
   */
  constructor (opts) {
    this.adb = opts.adb;
    this.log = log;
  }

  /**
   * @typedef {Object} SettingsAppStartupOptions
   * @property {number} [timeout=5000] The maximum number of milliseconds
   * to wait until the app has started
   * @property {boolean} [shouldRestoreCurrentApp=false] Whether to restore
   * the activity which was the current one before Settings startup
   */

  /**
   * Ensures that Appium Settings helper application is running
   * and starts it if necessary
   *
   * @param {SettingsAppStartupOptions} [opts={}]
   * @throws {Error} If Appium Settings has failed to start
   * @returns {Promise<SettingsApp>} self instance for chaining
   */
  async requireRunning (opts = {}) {
    if (await this.isRunningInForeground()) {
      return this;
    }

    this.log.debug(LOG_PREFIX, 'Starting Appium Settings app');
    const {
      timeout = 5000,
      shouldRestoreCurrentApp = false,
    } = opts;
    let appPackage;
    if (shouldRestoreCurrentApp) {
      try {
        ({appPackage} = await this.adb.getFocusedPackageAndActivity());
      } catch (e) {
        this.log.warn(LOG_PREFIX, `The current application can not be restored: ${e.message}`);
      }
    }
    await this.adb.startApp({
      pkg: SETTINGS_HELPER_ID,
      activity: SETTINGS_HELPER_MAIN_ACTIVITY,
      action: 'android.intent.action.MAIN',
      category: 'android.intent.category.LAUNCHER',
      stopApp: false,
      waitForLaunch: false,
    });
    try {
      await waitForCondition(async () => await this.isRunningInForeground(), {
        waitMs: timeout,
        intervalMs: 300,
      });
      if (shouldRestoreCurrentApp && appPackage) {
        try {
          await this.adb.activateApp(appPackage);
        } catch (e) {
          log.warn(`The current application can not be restored: ${e.message}`);
        }
      }
      return this;
    } catch (err) {
      throw new Error(`Appium Settings app is not running after ${timeout}ms`);
    }
  }

  /**
   * If the io.appium.settings package has running foreground service.
   * It returns the io.appium.settings's process existence for api level 25 and lower
   * becase the concept of foreground services has only been introduced since API 26
   *
   * @throws {Error} If the method gets an error in the adb shell execution.
   * @returns {Promise<boolean>} Return true if the device Settings app has a servicve running in foreground.
   */
  async isRunningInForeground () {
    if (await this.adb.getApiLevel() < 26) {
      // The foreground service check is available since api level 26
      return await this.adb.processExists(SETTINGS_HELPER_ID);
    }

    // 'dumpsys activity services <package>' had slightly better performance
    // than 'dumpsys activity services' and parsing the foreground apps.
    const output = await this.adb.shell(['dumpsys', 'activity', 'services', SETTINGS_HELPER_ID]);
    return output.includes('isForeground=true');
  }

  /**
   * Performs broadcast and verifies the result of it
   *
   * @param {string[]} args Arguments passed to the `am broadcast` comand
   * @param {string} action The exception message in case of broadcast failure
   * @param {boolean} [requireRunningApp=true] Whether to run a check for a running Appium Settings app
   * @returns {Promise<string>}
   */
  async checkBroadcast(args, action, requireRunningApp = true) {
    if (requireRunningApp) {
      await this.requireRunning({shouldRestoreCurrentApp: true});
    }

    const output = await this.adb.shell([
      'am', 'broadcast',
      ...args,
    ]);
    if (!output.includes('result=-1')) {
      this.log.debug(LOG_PREFIX, output);
      throw new Error(`Cannot execute the '${action}' action. Check the logcat output for more details.`);
    }
    return output;
  }

  /**
   * Parses the output in JSON format retrieved from
   * the corresponding Appium Settings broadcast calls
   *
   * @param {string} output The actual command output
   * @param {string} entityName The name of the entity which is
   * going to be parsed
   * @returns {Object} The parsed JSON object
   * @throws {Error} If the output cannot be parsed
   * as a valid JSON
   */
  _parseJsonData (output, entityName) {
    if (!/\bresult=-1\b/.test(output) || !/\bdata="/.test(output)) {
      this.log.debug(LOG_PREFIX, output);
      throw new Error(
        `Cannot retrieve ${entityName} from the device. ` +
        'Check the server log for more details'
      );
    }
    const match = /\bdata="(.+)",?/.exec(output);
    if (!match) {
      this.log.debug(LOG_PREFIX, output);
      throw new Error(
        `Cannot parse ${entityName} from the command output. ` +
        'Check the server log for more details'
      );
    }
    const jsonStr = _.trim(match[1]);
    try {
      return JSON.parse(jsonStr);
    } catch (e) {
      log.debug(jsonStr);
      throw new Error(
        `Cannot parse ${entityName} from the resulting data string. ` +
        'Check the server log for more details'
      );
    }
  }

  setAnimationState = setAnimationState;

  setBluetoothState = setBluetoothState;
  unpairAllBluetoothDevices = unpairAllBluetoothDevices;

  getClipboard = getClipboard;

  setGeoLocation = setGeoLocation;
  getGeoLocation = getGeoLocation;
  refreshGeoLocationCache = refreshGeoLocationCache;

  listSupportedLocales = listSupportedLocales;
  setDeviceLocale = setDeviceLocale;

  scanMedia = scanMedia;

  setDataState = setDataState;
  setWifiState = setWifiState;

  getNotifications = getNotifications;
  adjustNotificationsPermissions = adjustNotificationsPermissions;
  getSmsList = getSmsList;

  performEditorAction = performEditorAction;
  typeUnicode = typeUnicode;

  makeMediaProjectionRecorder = makeMediaProjectionRecorder;
  adjustMediaProjectionServicePermissions = adjustMediaProjectionServicePermissions;
}
