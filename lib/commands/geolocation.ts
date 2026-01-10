import _ from 'lodash';
import {
  LOCATION_SERVICE,
  LOCATION_RECEIVER,
  LOCATION_RETRIEVAL_ACTION,
} from '../constants';
import { SubProcess } from 'teen_process';
import B from 'bluebird';
import { LOG_PREFIX } from '../logger';
import type { SettingsApp } from '../client';
import type { Location } from './types';

const DEFAULT_SATELLITES_COUNT = 12;
const DEFAULT_ALTITUDE = 0.0;
const LOCATION_TRACKER_TAG = 'LocationTracker';
const GPS_CACHE_REFRESHED_LOGS = [
  'The current location has been successfully retrieved from Play Services',
  'The current location has been successfully retrieved from Location Manager'
];

const GPS_COORDINATES_PATTERN = /data="(-?[\d.]+)\s+(-?[\d.]+)\s+(-?[\d.]+)"/;

/**
 * Emulate geolocation coordinates on the device under test.
 * The `altitude` value is ignored while mocking the position.
 *
 * @param location - Location object containing coordinates and optional metadata
 * @param isEmulator - Set it to true if the device under test is an emulator rather than a real device
 * @throws {Error} If required location values are missing or invalid
 */
export async function setGeoLocation(this: SettingsApp, location: Location, isEmulator = false): Promise<void> {
  const formatLocationValue = (valueName: keyof Location, isRequired = true): string | null => {
    if (_.isNil(location[valueName])) {
      if (isRequired) {
        throw new Error(`${valueName} must be provided`);
      }
      return null;
    }
    const floatValue = parseFloat(String(location[valueName]));
    if (!isNaN(floatValue)) {
      return `${_.ceil(floatValue, 5)}`;
    }
    if (isRequired) {
      throw new Error(`${valueName} is expected to be a valid float number. ` +
        `'${location[valueName]}' is given instead`);
    }
    return null;
  };
  const longitude = formatLocationValue('longitude') as string;
  const latitude = formatLocationValue('latitude') as string;
  const altitude = formatLocationValue('altitude', false);
  const speed = formatLocationValue('speed', false);
  const bearing = formatLocationValue('bearing', false);
  const accuracy = formatLocationValue('accuracy', false);
  if (isEmulator) {
    const args: string[] = [longitude, latitude];
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
    const args: string[] = [
      'am', 'start-foreground-service',
      '-e', 'longitude', longitude,
      '-e', 'latitude', latitude,
    ];
    if (!_.isNil(altitude)) {
      args.push('-e', 'altitude', altitude);
    }
    if (!_.isNil(speed)) {
      if (_.toNumber(speed) < 0) {
        throw new Error(`${speed} is expected to be 0.0 or greater.`);
      }
      args.push('-e', 'speed', speed);
    }
    if (!_.isNil(bearing)) {
      if (!_.inRange(_.toNumber(bearing), 0, 360)) {
        throw new Error(`${accuracy} is expected to be in [0, 360) range.`);
      }
      args.push('-e', 'bearing', bearing);
    }
    if (!_.isNil(accuracy)) {
      if (_.toNumber(accuracy) < 0) {
        throw new Error(`${accuracy} is expected to be 0.0 or greater.`);
      }
      args.push('-e', 'accuracy', accuracy);
    }
    args.push(LOCATION_SERVICE);
    await this.adb.shell(args);
  }
}

/**
 * Get the current cached GPS location from the device under test.
 *
 * @returns The current location
 * @throws {Error} If the current location cannot be retrieved
 */
export async function getGeoLocation(this: SettingsApp): Promise<Location> {
  const output = await this.checkBroadcast([
    '-n', LOCATION_RECEIVER,
    '-a', LOCATION_RETRIEVAL_ACTION,
  ], 'retrieve geolocation', true);

  const match = GPS_COORDINATES_PATTERN.exec(output);
  if (!match) {
    throw new Error(`Cannot parse the actual location values from the command output: ${output}`);
  }
  const location: Location = {
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
 * @param timeoutMs The maximum number of milliseconds to block until GPS cache is refreshed.
 *                  Providing zero or a negative value to it skips waiting completely.
 * @throws {Error} If the GPS cache cannot be refreshed.
 */
export async function refreshGeoLocationCache(this: SettingsApp, timeoutMs = 20000): Promise<void> {
  await this.requireRunning({shouldRestoreCurrentApp: true});

  let logcatMonitor: SubProcess | undefined;
  let monitoringPromise: Promise<void> | undefined;

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
    const monitor = logcatMonitor;
    monitoringPromise = new B<void>((resolve, reject) => {
      setTimeout(() => reject(new Error(timeoutErrorMsg)), timeoutMs);

      monitor.on('exit', () => reject(new Error(timeoutErrorMsg)));
      (['lines-stderr', 'lines-stdout'] as const).map((evt) => monitor.on(evt, (lines: string[]) => {
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
