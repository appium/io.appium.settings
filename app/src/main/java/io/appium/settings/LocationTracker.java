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
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.CancellationTokenSource;

import io.appium.settings.helpers.PlayServicesHelpers;

import static android.content.Context.LOCATION_SERVICE;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

public class LocationTracker implements LocationListener {
    private static final String TAG = LocationTracker.class.getSimpleName();
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 1; // 1 meter
    private static final long LOCATION_UPDATES_INTERVAL_MS = 1000 * 60; // 1 minute
    private static final long FAST_INTERVAL_MS = 5000;

    private volatile LocationManager mLocationManager;
    private volatile FusedLocationProviderClient mFusedLocationProviderClient;
    private final LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(@NonNull LocationResult locationResult) {
            Log.d(TAG, "Got a location update from Play Services");
            mLocation = locationResult.getLastLocation();
        }
    };
    private volatile Location mLocation;
    private final AtomicBoolean isStarted = new AtomicBoolean(false);
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

    public boolean isRunning() {
        return isStarted.get();
    }

    private void setIsRunning(boolean value) {
        isStarted.set(value);
    }

    @Override
    public void onLocationChanged(@Nullable Location location) {
        if (location == null) {
            return;
        }

        Log.d(TAG, "Got a location update from Location Manager");
        mLocation = location;
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    synchronized void start(Context context) {
        if (isRunning()) {
            return;
        }
        setIsRunning(true);

        if (PlayServicesHelpers.isAvailable(context)) {
            initializePlayServices(context);
        } else {
            initializeLocationManager(context);
        }
    }

    synchronized void stop() {
        if (!isRunning()) {
            return;
        }

        try {
            stopLocationUpdatesWithPlayServices();
            stopLocationUpdatesWithoutPlayServices();
        } finally {
            setIsRunning(false);
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
        try {
            mFusedLocationProviderClient.requestLocationUpdates(
                    locationRequest, locationCallback, Looper.getMainLooper());
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
        if (mLocation != null) {
            Log.d(TAG, "The cached location has been successfully retrieved");
            return mLocation;
        }
        Log.d(TAG, "The cached location has not been retrieved");
        return null;
    }

    public synchronized void forceLocationUpdate(Context context) {
        if (!isRunning()) {
            Log.e(TAG, "The location tracker is not running");
            return;
        }

        if (isFusedLocationProviderInitialized()) {
            try {
                mFusedLocationProviderClient.getCurrentLocation(
                        LocationRequest.PRIORITY_HIGH_ACCURACY, new CancellationTokenSource().getToken())
                        .addOnCompleteListener(t -> {
                            if (t.isSuccessful()) {
                                mLocation = t.getResult();
                                Log.d(TAG, "The current location has been successfully retrieved from " +
                                        "Play Services");
                            } else {
                                Log.w(TAG, "Failed to retrieve the current location from Play Services",
                                        t.getException());
                            }
                        });
            } catch (SecurityException e) {
                Log.e(TAG, "Appium Settings has no access to location permission", e);
            }
        } else if (isLocationManagerConnected() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                mLocationManager.getCurrentLocation(mLocationProvider, null,
                        context.getMainExecutor(), location -> {
                            mLocation = location;
                            Log.d(TAG, "The current location has been successfully retrieved " +
                                    "from Location Manager");
                        });
            } catch (SecurityException e) {
                Log.e(TAG, "Appium Settings has no access to location permission", e);
            }
        }
    }

    @Nullable
    public synchronized Location getLocation(Context context) {
        if (!isRunning()) {
            Log.e(TAG, "The location tracker is not running");
            return null;
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
        return isLocationManagerConnected() ? getCachedLocation() : null;
    }
}
