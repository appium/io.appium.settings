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

import android.annotation.SuppressLint;
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

    private LocationManager mLocationManager;
    private GoogleApiClient mGoogleApiClient;
    private volatile Location mLocation;
    private Context mContext;
    private String locationProvider;

    @SuppressLint("StaticFieldLeak")
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
        startLocationUpdatesWithPlayServices();
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }

    void start(Context mContext) {
        if (mContext == this.mContext) {
            return;
        }

        this.mContext = mContext;
        if (PlayServicesHelpers.isAvailable(mContext)) {
            Log.i(TAG, "Configuring location provider for Google Play Services");
            stopLocationUpdatesWithPlayServices();
            mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            mGoogleApiClient.connect();
        } else {
            Log.i(TAG, "Configuring the default Android location provider");
            stopLocationUpdatesWithoutPlayServices();
            mLocationManager = (LocationManager) mContext.getSystemService(LOCATION_SERVICE);
            if (mLocationManager == null) {
                Log.e(TAG, "Cannot retrieve the location manager");
                return;
            }
            startLocationUpdatesWithoutPlayServices();
        }
    }

    private void startLocationUpdatesWithPlayServices() {
        if (mGoogleApiClient == null) {
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
        } catch (SecurityException e) {
            Log.e(TAG, "Appium Settings has no access to location permission");
        }
    }

    private void stopLocationUpdatesWithPlayServices() {
        if (mGoogleApiClient == null || !mGoogleApiClient.isConnected()) {
            return;
        }
        //noinspection deprecation
        LocationServices.FusedLocationApi
                .removeLocationUpdates(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
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
            Log.e(TAG, "Both Coarse and GPS providers are disabled");
            return;
        }

        if (isGPSEnabled) {
            try {
                mLocationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        LOCATION_UPDATES_INTERVAL,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                locationProvider = LocationManager.GPS_PROVIDER;
                Log.d(TAG, "GPS enabled. Getting FINE location");
                return;
            } catch (SecurityException e) {
                Log.e(TAG, "Appium Settings has no access to FINE location permission");
            }
        }
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    LOCATION_UPDATES_INTERVAL,
                    MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
            locationProvider = LocationManager.NETWORK_PROVIDER;
            Log.d(TAG, "Network enabled. Getting COARSE location");
        } catch (SecurityException e) {
            Log.e(TAG, "Appium Settings has no access to COARSE location permission");
        }
    }

    private void stopLocationUpdatesWithoutPlayServices() {
        if (mLocationManager == null) {
            return;
        }
        mLocationManager.removeUpdates(this);
        mLocationManager = null;
    }
    
    @Nullable
    public Location getLocation() {
        if (mLocation == null) {
            if (mGoogleApiClient != null) {
                if (mGoogleApiClient.isConnected()) {
                    try {
                        //noinspection deprecation
                        mLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
                    } catch (SecurityException e) {
                        Log.e(TAG, "Appium Settings has no access to location permission");
                    }
                }
            } else if (mLocationManager != null && locationProvider != null) {
                try {
                    mLocation = mLocationManager.getLastKnownLocation(locationProvider);
                } catch (SecurityException e) {
                    Log.e(TAG, String.format("Appium Settings has no access to %s location permission",
                            locationProvider));
                }
            }
        }
        return mLocation;
    }
}
