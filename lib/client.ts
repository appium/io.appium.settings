import { log, LOG_PREFIX } from './logger';
import { waitForCondition } from 'asyncbox';
import { SETTINGS_HELPER_ID, SETTINGS_HELPER_MAIN_ACTIVITY } from './constants';
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
import type { ADB } from 'appium-adb';
import type { Logger } from '@appium/logger';

export interface SettingsAppOpts {
  adb: ADB;
}

export interface SettingsAppStartupOptions {
  /** The maximum number of milliseconds to wait until the app has started */
  timeout?: number;
  /** Whether to restore the activity which was the current one before Settings startup */
  shouldRestoreCurrentApp?: boolean;
  /** Whether to forcefully restart the Settings app if it is already running */
  forceRestart?: boolean;
}

export class SettingsApp {
  readonly adb: ADB;
  readonly log: Logger;

  constructor(opts: SettingsAppOpts) {
    this.adb = opts.adb;
    this.log = log;
  }

  /**
   * Ensures that Appium Settings helper application is running
   * and starts it if necessary
   *
   * @param opts Startup options
   * @throws {Error} If Appium Settings has failed to start
   * @returns Self instance for chaining
   */
  async requireRunning(opts: SettingsAppStartupOptions = {}): Promise<SettingsApp> {
    const {
      timeout = 5000,
      shouldRestoreCurrentApp = false,
      forceRestart = false,
    } = opts;

    if (forceRestart) {
      await this.adb.forceStop(SETTINGS_HELPER_ID);
    } else if (await this.isRunningInForeground()) {
      return this;
    }

    this.log.debug(LOG_PREFIX, 'Starting Appium Settings app');
    let appPackage: string | undefined;
    if (shouldRestoreCurrentApp) {
      try {
        const result = await this.adb.getFocusedPackageAndActivity();
        appPackage = result.appPackage ?? undefined;
      } catch (e: any) {
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
        } catch (e: any) {
          log.warn(`The current application can not be restored: ${e.message}`);
        }
      }
      return this;
    } catch {
      throw new Error(`Appium Settings app is not running after ${timeout}ms`);
    }
  }

  /**
   * If the io.appium.settings package has running foreground service.
   *
   * @throws {Error} If the method gets an error in the adb shell execution
   * @returns Return true if the device Settings app has a service running in foreground
   */
  async isRunningInForeground(): Promise<boolean> {
    // 'dumpsys activity services <package>' had slightly better performance
    // than 'dumpsys activity services' and parsing the foreground apps.
    const output = await this.adb.shell(['dumpsys', 'activity', 'services', SETTINGS_HELPER_ID]);
    return output.includes('isForeground=true');
  }

  /**
   * Performs broadcast and verifies the result of it
   *
   * @param args Arguments passed to the `am broadcast` command
   * @param action The exception message in case of broadcast failure
   * @param requireRunningApp Whether to run a check for a running Appium Settings app
   * @returns The broadcast output
   * @throws {Error} If the broadcast fails
   */
  async checkBroadcast(args: string[], action: string, requireRunningApp = true): Promise<string> {
    if (requireRunningApp) {
      await this.requireRunning({shouldRestoreCurrentApp: true});
    }

    const output = await this.adb.shell([
      'am', 'broadcast',
      ...args,
    ]);
    if (!output.includes('result=-1')) {
      this.log.debug(LOG_PREFIX, output);
      const error = new Error(`Cannot execute the '${action}' action. Check the logcat output for more details.`) as Error & { output?: string };
      error.output = output;
      throw error;
    }
    return output;
  }

  /**
   * Parses the output in JSON format retrieved from
   * the corresponding Appium Settings broadcast calls
   *
   * @param output The actual command output
   * @param entityName The name of the entity which is going to be parsed
   * @returns The parsed JSON object
   * @throws {Error} If the output cannot be parsed as a valid JSON
   */
  _parseJsonData(output: string, entityName: string): any {
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
    const jsonStr = match[1].trim();
    try {
      return JSON.parse(jsonStr);
    } catch {
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
