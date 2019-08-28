/*
  Copyright 2012-present Appium Committers
  <p>
  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at
  <p>
  http://www.apache.org/licenses/LICENSE-2.0
  <p>
  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
 */

package io.appium.settings;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import io.appium.settings.helpers.PlayServicesHelpers;
import io.appium.settings.location.FusedLocationProvider;
import io.appium.settings.location.LocationFactory;
import io.appium.settings.location.LocationManagerProvider;
import io.appium.settings.location.MockLocationProvider;

public class LocationService extends Service {
    private static final String TAG = "MOCKED LOCATION SERVICE";

    private static final String LONGITUDE_PARAMETER_KEY = "longitude";
    private static final String LATITUDE_PARAMETER_KEY = "latitude";
    private static final String ALTITUDE_PARAMETER_KEY = "altitude";

    private static final long UPDATE_INTERVAL_MS = 2000L;

    private final List<MockLocationProvider> mockLocationProviders = new LinkedList<>();
    private final LocationFactory locationFactory = new LocationFactory();
    private final Timer locationUpdatesTimer = new Timer();
    private TimerTask locationUpdateTask;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initializeLocationProviders();
        enableLocationProviders();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        for (String p : new String[]{"android.permission.ACCESS_FINE_LOCATION"}) {
            if (getApplicationContext().checkCallingOrSelfPermission(p)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, String.format("Cannot mock location due to missing permission '%s'", p));
                return START_NOT_STICKY;
            }
        }

        // https://stackoverflow.com/a/45047542
        // https://developer.android.com/about/versions/oreo/android-8.0-changes.html
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.i(TAG, "Starting location service");
            startService(ForegroundService.getForegroundServiceIntent(LocationService.this));
        }

        handleIntent(intent);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Shutting down MockLocationService");
        locationUpdatesTimer.cancel();
        disableLocationProviders();
        super.onDestroy();
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        // update the locationFactory also if the service is already running to mock
        updateMockLocationFactory(intent);
        scheduleLocationUpdate();
    }

    private void enableLocationProviders() {
        for (MockLocationProvider mockLocationProvider : mockLocationProviders) {
            try {
                mockLocationProvider.enable();
            } catch (Exception e) {
                Log.e(TAG, String.format("Couldn't enable location provider: '%s'",
                        mockLocationProvider.getProviderName()));
            }
        }
    }

    private void disableLocationProviders() {
        for (MockLocationProvider mockLocationProvider : mockLocationProviders) {
            try {
                mockLocationProvider.disable();
            } catch (Exception e) {
                Log.e(TAG, String.format("Could not disable location provider: '%s'",
                        mockLocationProvider.getProviderName()));
            }
        }
    }

    private void initializeLocationProviders() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        mockLocationProviders.clear();
        mockLocationProviders.addAll(createMockProviders(locationManager));
        if (PlayServicesHelpers.isAvailable(this)) {
            Log.d(TAG, "Adding FusedLocationProvider");
            mockLocationProviders.add(createFusedLocationProvider());
        }
        Log.d(TAG, String.format("Created mock providers: %s", mockLocationProviders.toString()));
    }

    private void scheduleLocationUpdate() {
        Log.i(TAG, "Scheduling mock location updates");

        // If we run 'startservice' again we should schedule an update right away to avoid a delay
        if (locationUpdateTask != null) {
            locationUpdateTask.cancel();
        }

        locationUpdateTask = new TimerTask() {
            @Override
            public void run() {
                for (MockLocationProvider mockLocationProvider : mockLocationProviders) {
                    Location location = locationFactory.createLocation(mockLocationProvider.getProviderName(), Criteria.ACCURACY_FINE);
                    Log.d(TAG, String.format("Setting location of '%s' to '%s'", mockLocationProvider.getProviderName(), location));
                    try {
                        mockLocationProvider.setLocation(location);
                    } catch (Exception e) {
                        Log.e(TAG, String.format("Could not set location for '%s'",
                                mockLocationProvider.getProviderName()), e);
                    }
                }
            }
        };

        locationUpdatesTimer.schedule(locationUpdateTask, 0, UPDATE_INTERVAL_MS);
    }

    private void updateMockLocationFactory(Intent intent) {
        double longitude;
        try {
            longitude = Double.valueOf(intent.getStringExtra("longitude"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    String.format("longitude should be a valid number. '%s' is given instead",
                            intent.getStringExtra(LONGITUDE_PARAMETER_KEY)));
        }
        double latitude;
        try {
            latitude = Double.valueOf(intent.getStringExtra("latitude"));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    String.format("latitude should be a valid number. '%s' is given instead",
                            intent.getStringExtra(LATITUDE_PARAMETER_KEY)));
        }
        double altitude = 0.0;
        try {
            if (intent.hasExtra(ALTITUDE_PARAMETER_KEY)) {
                altitude = Double.valueOf(intent.getStringExtra(ALTITUDE_PARAMETER_KEY));
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, String.format("altitude should be a valid number. '%s' is given instead",
                    intent.getStringExtra(ALTITUDE_PARAMETER_KEY)));
        }

        locationFactory.setLocation(latitude, longitude, altitude);
    }

    private List<MockLocationProvider> createMockProviders(LocationManager locationManager) {
        List<String> providers = locationManager.getAllProviders();
        List<MockLocationProvider> mockProviders = new LinkedList<>();
        for (String providerName : providers) {
            // The passive provider is not required to be mocked.
            if (providerName.equals(LocationManager.PASSIVE_PROVIDER)) {
                continue;
            }
            MockLocationProvider mockProvider = createLocationManagerMockProvider(locationManager, providerName);
            if (mockProvider == null) {
                Log.e(TAG, String.format("Could not create mock provider for '%s'", providerName));
                continue;
            }
            mockProviders.add(mockProvider);
        }
        return mockProviders;
    }

    private MockLocationProvider createLocationManagerMockProvider(LocationManager locationManager, String providerName) {
        LocationProvider provider = locationManager.getProvider(providerName);
        return createLocationManagerMockProvider(locationManager, provider);
    }

    private MockLocationProvider createLocationManagerMockProvider(LocationManager locationManager, LocationProvider locationProvider) {
        return new LocationManagerProvider(locationManager,
                locationProvider.getName(),
                locationProvider.requiresNetwork(),
                locationProvider.requiresSatellite(),
                locationProvider.requiresCell(),
                locationProvider.hasMonetaryCost(),
                locationProvider.supportsAltitude(),
                locationProvider.supportsSpeed(),
                locationProvider.supportsBearing(),
                locationProvider.getPowerRequirement(),
                locationProvider.getAccuracy());
    }

    private FusedLocationProvider createFusedLocationProvider() {
        GoogleApiClient googleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .build();
        FusedLocationProviderClient locationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        return new FusedLocationProvider(googleApiClient, locationProviderClient, this);
    }
}
