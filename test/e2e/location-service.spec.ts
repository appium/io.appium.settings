import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import {describe, it, before, beforeEach, after} from 'node:test';

import {ADB} from 'appium-adb';
import {waitForCondition} from 'asyncbox';

import {SettingsApp} from '../../lib/client.js';
import type {Location} from '../../lib/commands/types.js';
import {LOCATION_SERVICE} from '../../lib/constants.js';
import {getSettingsApkPath} from '../../lib/utils.js';

const SERVICE_STARTUP_TIMEOUT_MS = 10000;
// LocationService pushes updates every 2s (see app/src/main/java/io/appium/settings/LocationService.java),
// so the cache needs at least that long to reflect a freshly requested location.
const LOCATION_UPDATE_TIMEOUT_MS = 15000;

async function isLocationServiceRunning(adb: ADB): Promise<boolean> {
  const stdout = await adb.shell(['dumpsys', 'activity', 'services', LOCATION_SERVICE]);
  return stdout.includes(LOCATION_SERVICE) && stdout.includes('isForeground=true');
}

async function stopLocationService(adb: ADB): Promise<void> {
  // 'am stopservice' exits non-zero both when the service was actually stopped
  // and when it was not running in the first place, so its exit code is not a
  // reliable signal here. Callers that care about the outcome should poll
  // isLocationServiceRunning() instead.
  try {
    await adb.shell(['am', 'stopservice', LOCATION_SERVICE]);
  } catch {
    // Ignored, see above
  }
}

function toFloat(value: string | number | null | undefined): number {
  return typeof value === 'string' ? parseFloat(value) : Number(value ?? NaN);
}

// getGeoLocation() throws until the service has pushed at least one location update,
// so a transient failure here just means "not ready yet" rather than a real error.
async function locationMatches(settingsApp: SettingsApp, expected: Location, checkAltitude = true): Promise<boolean> {
  let actual: Location;
  try {
    actual = await settingsApp.getGeoLocation();
  } catch {
    return false;
  }
  const latMatches = Math.abs(toFloat(actual.latitude) - toFloat(expected.latitude)) < 0.0001;
  const lonMatches = Math.abs(toFloat(actual.longitude) - toFloat(expected.longitude)) < 0.0001;
  if (!checkAltitude) {
    return latMatches && lonMatches;
  }
  return latMatches && lonMatches && Math.abs(toFloat(actual.altitude) - toFloat(expected.altitude)) < 0.1;
}

describe('Location Service', function () {
  let adb: ADB;
  let settingsApp: SettingsApp;

  before(async function () {
    adb = await ADB.createADB();
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
    await adb.shell(['appops', 'set', 'io.appium.settings', 'android:mock_location', 'allow']);

    await settingsApp.requireRunning({timeout: 10000});
  });

  beforeEach(async function () {
    await stopLocationService(adb);
  });

  after(async function () {
    await stopLocationService(adb);
  });

  it('should start the foreground service once a location is set', async function () {
    await settingsApp.setGeoLocation({longitude: -122.4194, latitude: 37.7749});

    await waitForCondition(async () => isLocationServiceRunning(adb), {
      waitMs: SERVICE_STARTUP_TIMEOUT_MS,
      intervalMs: 300,
    });
  });

  it('should periodically push the requested location until it is retrievable', async function () {
    const location: Location = {longitude: -122.4194, latitude: 37.7749, altitude: 10.0};
    await settingsApp.setGeoLocation(location);

    await waitForCondition(() => locationMatches(settingsApp, location), {
      waitMs: LOCATION_UPDATE_TIMEOUT_MS,
      intervalMs: 500,
    });
  });

  it('should switch to a new location without restarting the service', async function () {
    await settingsApp.setGeoLocation({longitude: -122.4194, latitude: 37.7749});
    await waitForCondition(async () => isLocationServiceRunning(adb), {
      waitMs: SERVICE_STARTUP_TIMEOUT_MS,
      intervalMs: 300,
    });

    const nextLocation: Location = {longitude: -74.006, latitude: 40.7128};
    await settingsApp.setGeoLocation(nextLocation);

    await waitForCondition(() => locationMatches(settingsApp, nextLocation, false), {
      waitMs: LOCATION_UPDATE_TIMEOUT_MS,
      intervalMs: 500,
    });
    assert.strictEqual(await isLocationServiceRunning(adb), true);
  });

  it('should stop the service when stopped via adb', async function () {
    await settingsApp.setGeoLocation({longitude: -122.4194, latitude: 37.7749});
    await waitForCondition(async () => isLocationServiceRunning(adb), {
      waitMs: SERVICE_STARTUP_TIMEOUT_MS,
      intervalMs: 300,
    });

    await stopLocationService(adb);

    await waitForCondition(async () => !(await isLocationServiceRunning(adb)), {
      waitMs: SERVICE_STARTUP_TIMEOUT_MS,
      intervalMs: 300,
    });
  });
});
