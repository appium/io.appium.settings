import { log, LOG_PREFIX } from './logger';
import _ from 'lodash';
import { waitForCondition } from 'asyncbox';
import { SETTINGS_HELPER_ID, SETTINGS_HELPER_MAIN_ACTIVITY } from './constants.js';
import { setAnimationState } from './commands/animation';
import { getClipboard } from './commands/clipboard';
import { setGeoLocation, getGeoLocation, refreshGeoLocationCache } from './commands/geolocation';
import { setDeviceSysLocale } from './commands/locale';
import { scanMedia } from './commands/media';
import { setDataState, setWifiState } from './commands/network';
import { getNotifications } from './commands/notifications';
import { getSmsList } from './commands/sms';
import { performEditorAction, typeUnicode } from './commands/typing';

/**
 * @typedef {Object} SettingsAppOpts
 * @property {import('appium-adb').ADB} adb
 */


export class SettingsApp {
  /** @type {import('appium-adb').ADB} */
  adb;

  /** @type {import('npmlog').Logger} */
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
    if (await this.adb.processExists(SETTINGS_HELPER_ID)) {
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
      await waitForCondition(async () => await this.adb.processExists(SETTINGS_HELPER_ID), {
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

  getClipboard = getClipboard;

  setGeoLocation = setGeoLocation;
  getGeoLocation = getGeoLocation;
  refreshGeoLocationCache = refreshGeoLocationCache;

  setDeviceSysLocale = setDeviceSysLocale;

  scanMedia = scanMedia;

  setDataState = setDataState;
  setWifiState = setWifiState;

  getNotifications = getNotifications;
  getSmsList = getSmsList;

  performEditorAction = performEditorAction;
  typeUnicode = typeUnicode;
}
