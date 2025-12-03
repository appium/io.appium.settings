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

    // Ensure the app is running
    await settingsApp.requireRunning();
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
    it('should update location when new coordinates are provided', async function () {
      // Set initial location
      const location1 = {
        longitude: -122.4194,
        latitude: 37.7749,
      };
      await settingsApp.setGeoLocation(location1);
      await waitForCondition(async () => await isLocationServiceRunning(adb), {
        waitMs: SERVICE_STARTUP_TIMEOUT_MS,
        intervalMs: 300,
      });

      // Update to a different location
      const location2 = {
        longitude: -74.0060,
        latitude: 40.7128,
      };
      await settingsApp.setGeoLocation(location2);
      // Wait for the service to still be running after update - will throw if condition is not met
      await waitForCondition(async () => await isLocationServiceRunning(adb), {
        waitMs: SERVICE_STARTUP_TIMEOUT_MS,
        intervalMs: 300,
      });
    });

    it('should retrieve the current location', async function () {
      // Set a location
      const location = {
        longitude: -122.4194,
        latitude: 37.7749,
        altitude: 10.0,
      };

      await settingsApp.setGeoLocation(location);
      await waitForCondition(async () => await isLocationServiceRunning(adb), {
        waitMs: SERVICE_STARTUP_TIMEOUT_MS,
        intervalMs: 300,
      });

      // Wait for the location to be set and retrievable - will throw if condition is not met
      await waitForCondition(async () => {
        try {
          const retrievedLocation = await settingsApp.getGeoLocation();
          const parsed = parseRetrievedLocation(retrievedLocation);

          // Check if values are close to expected (allowing for precision differences)
          const latMatch = Math.abs(parsed.latitude - location.latitude) < 0.0001;
          const lonMatch = Math.abs(parsed.longitude - location.longitude) < 0.0001;
          const altMatch = Math.abs(parsed.altitude - location.altitude) < 0.1;

          return latMatch && lonMatch && altMatch;
        } catch {
          return false;
        }
      }, {
        waitMs: SERVICE_STARTUP_TIMEOUT_MS,
        intervalMs: 300,
      });
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
      // Wait for the service to start - will throw if condition is not met
      await waitForCondition(async () => await isLocationServiceRunning(adb), {
        waitMs: SERVICE_STARTUP_TIMEOUT_MS,
        intervalMs: 300,
      });
    });

    it('should stop the location service when stopped via adb', async function () {
      // Start the service
      const location = {
        longitude: -122.4194,
        latitude: 37.7749,
      };
      await settingsApp.setGeoLocation(location);
      // Wait for the service to start - will throw if condition is not met
      await waitForCondition(async () => await isLocationServiceRunning(adb), {
        waitMs: SERVICE_STARTUP_TIMEOUT_MS,
        intervalMs: 300,
      });

      // Stop the service
      await stopLocationService(adb);
      // Wait for the service to stop - will throw if condition is not met
      await waitForCondition(async () => !(await isLocationServiceRunning(adb)), {
        waitMs: SERVICE_STARTUP_TIMEOUT_MS,
        intervalMs: 300,
      });
    });
  });

});

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



