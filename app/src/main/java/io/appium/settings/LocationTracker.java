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

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Task;

import io.appium.settings.helpers.LocationMode;
import io.appium.settings.helpers.PlayServicesHelpers;

import static android.content.Context.LOCATION_SERVICE;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class LocationTracker implements
        com.google.android.gms.location.LocationListener, LocationListener {
    private static final String TAG = LocationTracker.class.getSimpleName();
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10; // 10 meters
    private static final long LOCATION_UPDATES_INTERVAL_MS = 1000 * 60; // 1 minute
    private static final long FAST_INTERVAL_MS = 5000;
    private static final int MAX_LOCATION_RETRIEVAL_DELAY_SEC = 5;

    private volatile LocationManager mLocationManager;
    private volatile FusedLocationProviderClient mFusedLocationProviderClient;
    private final HandlerThread locationHandler = new HandlerThread("LocationHandler");
    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            try {
                Log.d(TAG, "Got a location update from Play Services");
                mLocation = locationResult.getLastLocation();
            } finally {
                try {
                    cachedLocationRetrievalGuard.unlock();
                } catch (RuntimeException e) {
                    // ignore
                }
            }
        }
    };
    private final Lock cachedLocationRetrievalGuard = new ReentrantLock(false);
    private final Lock currentLocationRetrievalGuard = new ReentrantLock(false);
    private volatile Location mLocation;
    private volatile boolean isStarted = false;
    private String mLocationProvider;

    private static LocationTracker instance = null;

    private LocationTracker() {
    }

    public synchronized static LocationTracker getInstance() {
        if (instance == null) {
            instance = new LocationTracker();
        }
        return instance;
    }

    @Override
    public void onLocationChanged(@Nullable Location location) {
        try {
            if (location == null) {
                return;
            }

            Log.d(TAG, "Got a location update from Location Manager");
            mLocation = location;
        } finally {
            try {
                cachedLocationRetrievalGuard.unlock();
            } catch (RuntimeException e) {
                // ignore
            }
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    synchronized void start(Context context) {
        if (isStarted) {
            return;
        }
        isStarted = true;

        if (PlayServicesHelpers.isAvailable(context)) {
            initializePlayServices(context);
        } else {
            initializeLocationManager(context);
        }
    }

    private void initializePlayServices(Context context) {
        if (isFusedLocationProviderInitialized()) {
            return;
        }

        Log.d(TAG, "Configuring location provider for Google Play Services");
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context);
        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_LOW_POWER)
                .setInterval(LOCATION_UPDATES_INTERVAL_MS)
                .setFastestInterval(FAST_INTERVAL_MS);
        if (!locationHandler.isAlive()) {
            locationHandler.start();
        }
        try {
            mFusedLocationProviderClient.requestLocationUpdates(
                    locationRequest, locationCallback, locationHandler.getLooper());
            Log.d(TAG, "Google Play Services location provider is connected");
            return;
        } catch (SecurityException e) {
            Log.e(TAG, "Appium Settings has no access to location permission", e);
        } catch (Exception e) {
            Log.e(TAG, "Cannot connect to Google location service", e);
        }
        stopLocationUpdatesWithPlayServices();
    }

    private void initializeLocationManager(Context context) {
        if (isLocationManagerConnected()) {
            return;
        }

        Log.d(TAG, "Configuring the default Android location provider");
        Object locationManager = context.getSystemService(LOCATION_SERVICE);
        if (locationManager == null) {
            Log.e(TAG, "Cannot retrieve the location manager");
            return;
        }
        mLocationManager = (LocationManager) locationManager;
        startLocationUpdatesWithoutPlayServices();
    }

    private void stopLocationUpdatesWithPlayServices() {
        if (!isFusedLocationProviderInitialized()) {
            return;
        }

        Log.d(TAG, "Stopping Google Play Services location provider");
        mFusedLocationProviderClient.removeLocationUpdates(locationCallback);
        if (mFusedLocationProviderClient.asGoogleApiClient().isConnected()) {
            mFusedLocationProviderClient.asGoogleApiClient().disconnect();
        }
        mFusedLocationProviderClient = null;
    }

    private void startLocationUpdatesWithoutPlayServices() {
        if (mLocationManager == null) {
            return;
        }

        boolean isGPSEnabled = mLocationManager
                .isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetworkEnabled = mLocationManager
                .isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!isGPSEnabled && !isNetworkEnabled) {
            Log.e(TAG, "Both COARSE and GPS location providers are disabled");
            return;
        }

        if (isGPSEnabled) {
            try {
                mLocationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        LOCATION_UPDATES_INTERVAL_MS,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                mLocationProvider = LocationManager.GPS_PROVIDER;
                Log.d(TAG, "GPS location provider is enabled. Getting FINE location");
                return;
            } catch (SecurityException e) {
                Log.e(TAG, "Appium Settings has no access to FINE location permission", e);
            }
        }
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    LOCATION_UPDATES_INTERVAL_MS,
                    MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
            mLocationProvider = LocationManager.NETWORK_PROVIDER;
            Log.d(TAG, "NETWORK location provider is enabled. Getting COARSE location");
        } catch (SecurityException e) {
            Log.e(TAG, "Appium Settings has no access to COARSE location permission", e);
        }
    }

    private void stopLocationUpdatesWithoutPlayServices() {
        if (mLocationManager == null) {
            return;
        }

        Log.d(TAG, "Stopping Android location provider");
        mLocationManager.removeUpdates(this);
        mLocationManager = null;
    }

    private boolean isFusedLocationProviderInitialized() {
        return mFusedLocationProviderClient != null;
    }

    private boolean isLocationManagerConnected() {
        return mLocationManager != null && mLocationProvider != null;
    }

    @Nullable
    private Location getCachedLocation() {
        try {
            if (mLocation != null) {
                Log.d(TAG, "The cached location has been successfully retrieved");
                return mLocation;
            }

            Log.d(TAG, String.format("Waiting up to %s seconds to retrieve the cached location",
                    MAX_LOCATION_RETRIEVAL_DELAY_SEC));
            long msStarted = SystemClock.uptimeMillis();
            if (cachedLocationRetrievalGuard.tryLock(MAX_LOCATION_RETRIEVAL_DELAY_SEC, TimeUnit.SECONDS)
                    && mLocation != null) {
                Log.d(TAG, String.format("The location has been successfully retrieved in %sms",
                        SystemClock.uptimeMillis() - msStarted));
                return mLocation;
            }
        } catch (InterruptedException e) {
            // ignore
        }
        Log.d(TAG, String.format("The location has not been retrieved within %s seconds timeout",
                MAX_LOCATION_RETRIEVAL_DELAY_SEC));
        return null;
    }

    @Nullable
    private Location getCurrentLocation() {
        if (isFusedLocationProviderInitialized()) {
            try {
                Task<Location> task = mFusedLocationProviderClient.getCurrentLocation(
                        LocationRequest.PRIORITY_HIGH_ACCURACY, new CancellationTokenSource().getToken());
                task.addOnSuccessListener(location -> {
                    try {
                        mLocation = location;
                        Log.d(TAG, "The current location has been successfully retrieved");
                    } finally {
                        try {
                            currentLocationRetrievalGuard.unlock();
                        } catch (RuntimeException e) {
                            // ignore
                        }
                    }
                });
            } catch (SecurityException e) {
                Log.e(TAG, "Appium Settings has no access to location permission", e);
            }
            try {
                if (currentLocationRetrievalGuard.tryLock(MAX_LOCATION_RETRIEVAL_DELAY_SEC,
                        TimeUnit.SECONDS)) {
                    return mLocation;
                }
            } catch (InterruptedException e) {
                // ignore
            }
        }
        return null;
    }

    @Nullable
    public synchronized Location getLocation(Context context, LocationMode mode) {
        // Make sure the service has been started
        start(context);

        if (mode == LocationMode.CURRENT) {
            Location location = getCurrentLocation();
            if (location != null) {
                return location;
            }
            Log.d(TAG, "The current location cannot be retrieved. Falling back " +
                    "to the cached one");
        }

        if (isFusedLocationProviderInitialized()) {
            Location location = getCachedLocation();
            if (location != null) {
                // If Play services worked, make sure location manager is disabled
                stopLocationUpdatesWithoutPlayServices();
                return location;
            }
        }

        // If Play services didn't work, try location manager
        try {
            if (!isLocationManagerConnected()) {
                initializeLocationManager(context);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Appium Settings has no access to location permission", e);
        }
        if (isLocationManagerConnected()) {
            return getCachedLocation();
        }
        return null;
    }
}
