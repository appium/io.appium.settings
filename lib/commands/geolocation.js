import _ from 'lodash';
import {
  LOCATION_SERVICE,
  LOCATION_RECEIVER,
  LOCATION_RETRIEVAL_ACTION,
} from '../constants.js';
import { SubProcess } from 'teen_process';
import B from 'bluebird';
import { LOG_PREFIX } from '../logger.js';

const DEFAULT_SATELLITES_COUNT = 12;
const DEFAULT_ALTITUDE = 0.0;
const LOCATION_TRACKER_TAG = 'LocationTracker';
const GPS_CACHE_REFRESHED_LOGS = [
  'The current location has been successfully retrieved from Play Services',
  'The current location has been successfully retrieved from Location Manager'
];

const GPS_COORDINATES_PATTERN = /data="(-?[\d.]+)\s+(-?[\d.]+)\s+(-?[\d.]+)"/;

/**
 * @typedef {Object} Location
 * @property {number|string} longitude - Valid longitude value.
 * @property {number|string} latitude - Valid latitude value.
 * @property {?number|string} [altitude] - Valid altitude value.
 * @property {?number|string} [satellites=12] - Number of satellites being tracked (1-12).
 * This value is ignored on real devices.
 * @property {?number|string} [speed] - Valid speed value.
 * Should be greater than 0.0 meters/second for real devices or 0.0 knots
 * for emulators.
 */

/**
 * Emulate geolocation coordinates on the device under test.
 *
 * @this {import('../client').SettingsApp}
 * @param {Location} location - Location object. The `altitude` value is ignored
 * while mocking the position.
 * @param {boolean} [isEmulator=false] - Set it to true if the device under test
 *                                       is an emulator rather than a real device.
 */
export async function setGeoLocation (location, isEmulator = false) {
  const formatLocationValue = (valueName, isRequired = true) => {
    if (_.isNil(location[valueName])) {
      if (isRequired) {
        throw new Error(`${valueName} must be provided`);
      }
      return null;
    }
    const floatValue = parseFloat(location[valueName]);
    if (!isNaN(floatValue)) {
      return `${_.ceil(floatValue, 5)}`;
    }
    if (isRequired) {
      throw new Error(`${valueName} is expected to be a valid float number. ` +
        `'${location[valueName]}' is given instead`);
    }
    return null;
  };
  const longitude = /** @type {string} */ (formatLocationValue('longitude'));
  const latitude = /** @type {string} */ (formatLocationValue('latitude'));
  const altitude = formatLocationValue('altitude', false);
  const speed = formatLocationValue('speed', false);
  if (isEmulator) {
    /** @type {string[]} */
    const args = [longitude, latitude];
    if (!_.isNil(altitude)) {
      args.push(altitude);
    }
    const satellites = parseInt(`${location.satellites}`, 10);
    if (!Number.isNaN(satellites) && satellites > 0 && satellites <= 12) {
      if (args.length < 3) {
        args.push(`${DEFAULT_ALTITUDE}`);
      }
      args.push(`${satellites}`);
    }
    if (!_.isNil(speed)) {
      if (args.length < 3) {
        args.push(`${DEFAULT_ALTITUDE}`);
      }
      if (args.length < 4) {
        args.push(`${DEFAULT_SATELLITES_COUNT}`);
      }
      args.push(speed);
    }
    await this.adb.resetTelnetAuthToken();
    await this.adb.adbExec(['emu', 'geo', 'fix', ...args]);
    // A workaround for https://code.google.com/p/android/issues/detail?id=206180
    await this.adb.adbExec(['emu', 'geo', 'fix', ...(args.map((arg) => arg.replace('.', ',')))]);
  } else {
    const args = [
      'am', (await this.adb.getApiLevel() >= 26) ? 'start-foreground-service' : 'startservice',
      '-e', 'longitude', longitude,
      '-e', 'latitude', latitude,
    ];
    if (!_.isNil(altitude)) {
      args.push('-e', 'altitude', altitude);
    }
    if (!_.isNil(speed)) {
      args.push('-e', 'speed', speed);
    }
    args.push(LOCATION_SERVICE);
    await this.adb.shell(args);
  }
}

/**
 * Get the current cached GPS location from the device under test.
 *
 * @this {import('../client').SettingsApp}
 * @returns {Promise<Location>} The current location
 * @throws {Error} If the current location cannot be retrieved
 */
export async function getGeoLocation () {
  const output = await this.checkBroadcast([
    '-n', LOCATION_RECEIVER,
    '-a', LOCATION_RETRIEVAL_ACTION,
  ], 'retrieve geolocation', true);

  const match = GPS_COORDINATES_PATTERN.exec(output);
  if (!match) {
    throw new Error(`Cannot parse the actual location values from the command output: ${output}`);
  }
  const location = {
    latitude: match[1],
    longitude: match[2],
    altitude: match[3],
  };
  this.log.debug(LOG_PREFIX, `Got geo coordinates: ${JSON.stringify(location)}`);
  return location;
}

/**
 * Sends an async request to refresh the GPS cache.
 * This feature only works if the device under test has
 * Google Play Services installed. In case the vanilla
 * LocationManager is used the device API level must be at
 * version 30 (Android R) or higher.
 *
 * @this {import('../client').SettingsApp}
 * @param {number} timeoutMs The maximum number of milliseconds
 * to block until GPS cache is refreshed. Providing zero or a negative
 * value to it skips waiting completely.
 *
 * @throws {Error} If the GPS cache cannot be refreshed.
 */
export async function refreshGeoLocationCache (timeoutMs = 20000) {
  await this.requireRunning({shouldRestoreCurrentApp: true});

  let logcatMonitor;
  let monitoringPromise;

  if (timeoutMs > 0) {
    const cmd = [
      ...this.adb.executable.defaultArgs,
      'logcat', '-s', LOCATION_TRACKER_TAG,
    ];
    logcatMonitor = new SubProcess(this.adb.executable.path, cmd);
    const timeoutErrorMsg = `The GPS cache has not been refreshed within ${timeoutMs}ms timeout. ` +
      `Please make sure the device under test has Appium Settings app installed and running. ` +
      `Also, it is required that the device has Google Play Services installed or is running ` +
      `Android 10+ otherwise.`;
    monitoringPromise = new B((resolve, reject) => {
      setTimeout(() => reject(new Error(timeoutErrorMsg)), timeoutMs);

      logcatMonitor.on('exit', () => reject(new Error(timeoutErrorMsg)));
      ['lines-stderr', 'lines-stdout'].map((evt) => logcatMonitor.on(evt, (lines) => {
        if (lines.some((line) => GPS_CACHE_REFRESHED_LOGS.some((x) => line.includes(x)))) {
          resolve();
        }
      }));
    });
    await logcatMonitor.start(0);
  }

  await this.checkBroadcast([
    '-n', LOCATION_RECEIVER,
    '-a', LOCATION_RETRIEVAL_ACTION,
    '--ez', 'forceUpdate', 'true',
  ], 'refresh GPS cache', false);

  if (logcatMonitor && monitoringPromise) {
    const startMs = performance.now();
    this.log.debug(LOG_PREFIX, `Waiting up to ${timeoutMs}ms for the GPS cache to be refreshed`);
    try {
      await monitoringPromise;
      this.log.info(LOG_PREFIX, `The GPS cache has been successfully refreshed after ` +
        `${(performance.now() - startMs).toFixed(0)}ms`);
    } finally {
      if (logcatMonitor.isRunning) {
        await logcatMonitor.stop();
      }
    }
  } else {
    this.log.info(LOG_PREFIX, 'The request to refresh the GPS cache has been sent. Skipping waiting for its result.');
  }
}
