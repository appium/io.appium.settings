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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import io.appium.settings.helpers.PlayServicesHelpers;

import static android.content.Context.LOCATION_SERVICE;

public class LocationTracker implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener, LocationListener {
    private static final String TAG = LocationTracker.class.getSimpleName();
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10; // 10 meters
    private static final long LOCATION_UPDATES_INTERVAL = 1000 * 60; // 1 minute
    private static final long FAST_INTERVAL = 5000; // 5 seconds

    private volatile LocationManager mLocationManager;
    private volatile GoogleApiClient mGoogleApiClient;
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
    public void onLocationChanged(Location location) {
        if (location != null) {
            Log.d(TAG, "Got an updated location");
            mLocation = location;
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

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (mGoogleApiClient == null) {
            return;
        }
        if (!mGoogleApiClient.isConnected()) {
            // This should never happen, but it's happening on some phones
            Log.e(TAG, "Google API is still connecting being inside onConnected callback");
            mGoogleApiClient = null;
            return;
        }
        LocationRequest locationRequest = new LocationRequest();
        locationRequest.setPriority(LocationRequest.PRIORITY_LOW_POWER);
        locationRequest.setInterval(LOCATION_UPDATES_INTERVAL);
        locationRequest.setFastestInterval(FAST_INTERVAL);
        try {
            //noinspection deprecation
            LocationServices.FusedLocationApi
                    .requestLocationUpdates(mGoogleApiClient, locationRequest, this);
            Log.d(TAG, "Google Play Services location provider is connected");
            return;
        } catch (SecurityException e) {
            Log.e(TAG, "Appium Settings has no access to location permission", e);
        } catch (Exception e) {
            Log.e(TAG, "Cannot connect to Google location service", e);
        }
        stopLocationUpdatesWithPlayServices();
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        if (mGoogleApiClient == null) {
            return;
        }

        Log.e(TAG, String.format("Google Play Services location provider has failed to connect (code %s)",
                connectionResult.toString()));
        stopLocationUpdatesWithPlayServices();
    }

    synchronized void start(Context context) {
        if (isStarted) {
            return;
        }
        isStarted = true;

        boolean isPlayServicesAvailable = PlayServicesHelpers.isAvailable(context);
        if (isPlayServicesAvailable) {
            initializePlayServices(context);
        } else {
            initializeLocationManager(context);
        }
    }

    private void initializePlayServices(Context context) {
        if (isPlayServicesConnected()) {
            return;
        }

        Log.d(TAG, "Configuring location provider for Google Play Services");
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();
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
        if (mGoogleApiClient == null) {
            return;
        }

        Log.d(TAG, "Stopping Google Play Services location provider");
        if (mGoogleApiClient.isConnected()) {
            //noinspection deprecation
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
        mGoogleApiClient = null;
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
                        LOCATION_UPDATES_INTERVAL,
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
                    LOCATION_UPDATES_INTERVAL,
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

    private boolean isPlayServicesConnected() {
        return mGoogleApiClient != null && mGoogleApiClient.isConnected();
    }

    private boolean isLocationManagerConnected() {
        return mLocationManager != null && mLocationProvider != null;
    }

    @Nullable
    public synchronized Location getLocation(Context context) {
        if (mLocation != null) {
            return mLocation;
        }

        // Make sure the service has been started
        start(context);

        if (isPlayServicesConnected()) {
            try {
                mLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                if (mLocation == null) {
                    // If GPS didn't work, try location manager
                    if (!isLocationManagerConnected()) {
                        initializeLocationManager(context);
                    }
                } else {
                    // Make sure the fallback is removed after play services connection succeeds
                    if (isLocationManagerConnected()) {
                        stopLocationUpdatesWithoutPlayServices();
                    }
                    return mLocation;
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Appium Settings has no access to location permission", e);
            }
        }
        if (isLocationManagerConnected() || mGoogleApiClient != null) {
            // Try fallback to the default provider if Google API is available but is still not connected
            if (mGoogleApiClient != null && !isLocationManagerConnected()) {
                Object locationManager = context.getSystemService(LOCATION_SERVICE);
                if (locationManager != null) {
                    mLocationManager = (LocationManager) locationManager;
                    startLocationUpdatesWithoutPlayServices();
                }
            }
            if (mLocationManager != null && mLocationProvider != null) {
                try {
                    mLocation = mLocationManager.getLastKnownLocation(mLocationProvider);
                    return mLocation;
                } catch (SecurityException e) {
                    Log.e(TAG, String.format("Appium Settings has no access to %s location permission",
                            mLocationProvider), e);
                }
            }
        }
        return null;
    }
}
