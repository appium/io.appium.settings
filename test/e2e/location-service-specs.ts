import { SettingsApp } from '../../lib/client';
import { ADB } from 'appium-adb';
import { getSettingsApkPath } from '../../lib/utils';
import { waitForCondition } from 'asyncbox';
import fs from 'fs/promises';

const SERVICE_STARTUP_TIMEOUT_MS = 6000;

describe('Location Service', function () {
  let adb: ADB;
  let settingsApp: SettingsApp;

  before(async function () {
    // Initialize ADB
    adb = await ADB.createADB();

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

    // Set the mock location app permission (required for Android 6.0+)
    await adb.shell(['appops', 'set', 'io.appium.settings', 'android:mock_location', 'allow']);

    // Ensure the app is running
    await settingsApp.requireRunning({
      timeout: 10000,
    });
  });

  beforeEach(async function () {
    // Stop any running location service before each test
    try {
      await stopLocationService(adb);
    } catch {
      // Ignore cleanup errors
    }
  });

  after(async function () {
    // Clean up: stop any running location service
    try {
      await stopLocationService(adb);
    } catch {
      // Ignore cleanup errors
    }
  });

  describe('LocationService Basic Operations', function () {
    let isEmulator: boolean;

    before(async function () {
      // Detect if running on an emulator using the same method as android driver
      // Check device properties that indicate an emulator
      const productModel = await adb.shell(['getprop', 'ro.product.model']);
      const hardware = await adb.shell(['getprop', 'ro.hardware']);
      const kernelQemu = await adb.shell(['getprop', 'ro.kernel.qemu']);
      const buildProduct = await adb.shell(['getprop', 'ro.build.product']);

      isEmulator = productModel.toLowerCase().includes('sdk') ||
                   productModel.toLowerCase().includes('emulator') ||
                   hardware.toLowerCase().includes('goldfish') ||
                   hardware.toLowerCase().includes('ranchu') ||
                   kernelQemu === '1' ||
                   buildProduct.toLowerCase().includes('sdk') ||
                   buildProduct.toLowerCase().includes('emulator');
    });
    it('should update location when new coordinates are provided', async function () {
      // Set initial location
      const location1 = {
        longitude: -122.4194,
        latitude: 37.7749,
      };
      await settingsApp.setGeoLocation(location1);
      await waitForServiceToStart(adb);

      // Update to a different location
      const location2 = {
        longitude: -74.0060,
        latitude: 40.7128,
      };
      await settingsApp.setGeoLocation(location2);
      // Wait for the service to still be running after update - will throw if condition is not met
      await waitForServiceToStart(adb);
    });

    it('should retrieve the current location', async function () {
      // Set a location
      const location = {
        longitude: -122.4194,
        latitude: 37.7749,
        altitude: 10.0,
      };

      await settingsApp.setGeoLocation(location);
      await waitForServiceToStart(adb);

      // Wait for the location to be set and retrievable
      // LocationService updates every 5 seconds, so we need to wait at least that long
      // plus some buffer time for the location to propagate
      await waitForLocationToMatch(location, settingsApp, SERVICE_STARTUP_TIMEOUT_MS * 2);
    });

    it('should handle location with all optional parameters', async function () {
      // Set a location with all parameters
      const location = {
        longitude: -122.4194,
        latitude: 37.7749,
        altitude: 10.0,
        speed: 5.5,
        bearing: 90.0,
        accuracy: 10.0,
      };

      await settingsApp.setGeoLocation(location);
      await waitForServiceToStart(adb);
    });

    it('should stop the location service when stopped via adb', async function () {
      // Start the service
      const location = {
        longitude: -122.4194,
        latitude: 37.7749,
      };
      await settingsApp.setGeoLocation(location);
      await waitForServiceToStart(adb);

      // Stop the service
      await stopLocationService(adb);
      // Wait for the service to stop - will throw if condition is not met
      await waitForCondition(async () => !(await isLocationServiceRunning(adb)), {
        waitMs: SERVICE_STARTUP_TIMEOUT_MS,
        intervalMs: 300,
      });
    });

    it('should set location on emulator using emu geo fix', async function () {
      // Skip test if not running on an emulator
      if (!isEmulator) {
        this.skip();
      }

      // Set a location using emulator-specific method
      // Note: emulators don't support altitude, so we only set longitude and latitude
      const location = {
        longitude: -122.4194,
        latitude: 37.7749,
      };

      await settingsApp.setGeoLocation(location, true);

      // Wait for the location to be set and retrievable
      // Emulator location updates are immediate, but we still need to wait a bit
      // Only check longitude and latitude since altitude is not supported on emulators
      await waitForLocationToMatch(location, settingsApp, SERVICE_STARTUP_TIMEOUT_MS, false);
    });
  });

});

/**
 * Wait for the LocationService to start running
 */
async function waitForServiceToStart(adb: ADB): Promise<void> {
  await waitForCondition(async () => await isLocationServiceRunning(adb), {
    waitMs: SERVICE_STARTUP_TIMEOUT_MS,
    intervalMs: 300,
  });
}

/**
 * Wait for the retrieved location to match the expected location
 * @param checkAltitude - If false, only checks longitude and latitude (for emulators that don't support altitude)
 */
async function waitForLocationToMatch(
  expectedLocation: {longitude: number; latitude: number; altitude?: number},
  settingsApp: SettingsApp,
  timeoutMs: number,
  checkAltitude: boolean = true
): Promise<void> {
  await waitForCondition(async () => {
    try {
      const retrievedLocation = await settingsApp.getGeoLocation();
      const parsed = parseRetrievedLocation(retrievedLocation);

      // Check if values are close to expected (allowing for precision differences)
      const latMatch = Math.abs(parsed.latitude - expectedLocation.latitude) < 0.0001;
      const lonMatch = Math.abs(parsed.longitude - expectedLocation.longitude) < 0.0001;

      if (!checkAltitude) {
        return latMatch && lonMatch;
      }

      const altMatch = Math.abs(parsed.altitude - (expectedLocation.altitude || 0)) < 0.1;
      return latMatch && lonMatch && altMatch;
    } catch {
      return false;
    }
  }, {
    waitMs: timeoutMs,
    intervalMs: 300,
  });
}

/**
 * Parse retrieved location values from the getGeoLocation response
 */
function parseRetrievedLocation(retrievedLocation: {latitude: string | number; longitude: string | number; altitude?: string | number | null}): {latitude: number; longitude: number; altitude: number} {
  let latitude: number;
  if (typeof retrievedLocation.latitude === 'string') {
    latitude = parseFloat(retrievedLocation.latitude);
  } else {
    latitude = Number(retrievedLocation.latitude);
  }

  let longitude: number;
  if (typeof retrievedLocation.longitude === 'string') {
    longitude = parseFloat(retrievedLocation.longitude);
  } else {
    longitude = Number(retrievedLocation.longitude);
  }

  let altitude: number;
  if (retrievedLocation.altitude) {
    if (typeof retrievedLocation.altitude === 'string') {
      altitude = parseFloat(retrievedLocation.altitude);
    } else {
      altitude = Number(retrievedLocation.altitude);
    }
  } else {
    altitude = 0;
  }

  return { latitude, longitude, altitude };
}

/**
 * Check if the LocationService is currently running
 */
async function isLocationServiceRunning(adb: ADB): Promise<boolean> {
  try {
    const output = await adb.shell(['dumpsys', 'activity', 'services', 'io.appium.settings']);
    // Check for LocationService in the output
    return output.includes('LocationService') && output.includes('isForeground=true');
  } catch {
    return false;
  }
}

/**
 * Stop the LocationService
 */
async function stopLocationService(adb: ADB): Promise<void> {
  try {
    await adb.shell(['am', 'stopservice', 'io.appium.settings/.LocationService']);
  } catch {
    // Ignore errors if service is not running
  }
}
