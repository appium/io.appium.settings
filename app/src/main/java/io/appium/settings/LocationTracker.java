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
import android.location.LocationProvider;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import io.appium.settings.helpers.PlayServicesHelpers;

import static android.content.Context.LOCATION_SERVICE;

public class LocationTracker implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = LocationTracker.class.getSimpleName();
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10; // 10 meters
    private static final long LOCATION_UPDATES_INTERVAL = 1000 * 60; // 1 minute
    private static final long FAST_INTERVAL = 5000; // 5 seconds

    private static final long UPDATE_INTERVAL_IN_MILLISECONDS = 1000;
    private static final long FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS =
            UPDATE_INTERVAL_IN_MILLISECONDS / 2;

    private volatile LocationManager mLocationManager;
    private volatile GoogleApiClient mGoogleApiClient;
    private volatile Location mLocation;
    private volatile boolean isStarted = false;

    private String mGpsLocationProviderName;
    private String mNetworkLocationProviderName;

    private synchronized void updateLocation(Location location){
        if (location != null) {
            Log.d(TAG, "Got an updated location");
            mLocation = location;
        }
    }
    private final com.google.android.gms.location.LocationListener googleApiLocationListener = new com.google.android.gms.location.LocationListener(){
        @Override
        public void onLocationChanged(Location location) {
            updateLocation(location);
        }
    };
    private final LocationListener gpsLocationListener =new LocationListener(){

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            switch (status) {
                case LocationProvider.AVAILABLE:
                    Log.d(TAG,"GPS available again\n");
                    break;
                case LocationProvider.OUT_OF_SERVICE:
                    Log.d(TAG,"GPS out of service\n");
                    break;
                case LocationProvider.TEMPORARILY_UNAVAILABLE:
                    Log.d(TAG,"GPS temporarily unavailable\n");
                    break;
            }
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.d(TAG, "GPS Provider Enabled\n");
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.d(TAG,"GPS Provider Disabled\n");
        }

        @Override
        public void onLocationChanged(Location location) {
            updateLocation(location);
            Log.d(TAG,"New GPS location: "
                    + String.format("%9.6f", location.getLatitude()) + ", "
                    + String.format("%9.6f", location.getLongitude()) + "\n");
        }
    };
    private final LocationListener networkLocationListener = new LocationListener(){
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras){
            switch (status) {
                case LocationProvider.AVAILABLE:
                    Log.d(TAG,"Network location available again\n");
                    break;
                case LocationProvider.OUT_OF_SERVICE:
                    Log.d(TAG,"Network location out of service\n");
                    break;
                case LocationProvider.TEMPORARILY_UNAVAILABLE:
                    Log.d(TAG,"Network location temporarily unavailable\n");
                    break;
            }
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.d(TAG,"Network Provider Enabled\n");
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.d(TAG,"Network Provider Disabled\n");
        }

        @Override
        public void onLocationChanged(Location location) {
            updateLocation(location);
            Log.d(TAG,"New network location: "
                    + String.format("%9.6f", location.getLatitude()) + ", "
                    + String.format("%9.6f", location.getLongitude()) + "\n");
        }
    };

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
                    .requestLocationUpdates(mGoogleApiClient, locationRequest, googleApiLocationListener);
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

    synchronized void start(Context context, FusedLocationProviderClient fusedLocationProviderClient) {
        mFusedLocationClient = fusedLocationProviderClient;
        start(context);
    }


    private FusedLocationProviderClient mFusedLocationClient;
    private LocationRequest mLocationRequest;
    private LocationCallback mLocationCallback;

    private void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setFastestInterval(FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
    }

    private void initializeRequestLocation(){
        if (mFusedLocationClient == null){
            Log.d(TAG, "Failed to initialize request location");
            return;
        }
        createLocationRequest();
        mLocationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                // This is the result!!!
                Log.d(TAG, "Frequent request location get update from FusedLocationClient");
                mLocation = locationResult.getLastLocation();
            }
        };
        try {
            mFusedLocationClient.requestLocationUpdates(mLocationRequest,
                    mLocationCallback, Looper.getMainLooper());
        } catch (SecurityException unlikely) {
            Log.e(TAG, "Lost location permission. Could not request updates. " + unlikely);
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
        Log.d(TAG, "Try to request update");
        initializeRequestLocation();

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
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, googleApiLocationListener);
            mGoogleApiClient.disconnect();
        }
        mGoogleApiClient = null;
        stopLocationRequestInFusedLocationClient();
    }

    private void stopLocationRequestInFusedLocationClient(){
        if (mLocationCallback!=null) {
            mFusedLocationClient.removeLocationUpdates(mLocationCallback);
        }
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
                        MIN_DISTANCE_CHANGE_FOR_UPDATES, gpsLocationListener);
                mGpsLocationProviderName = LocationManager.GPS_PROVIDER;
                Log.d(TAG, "GPS location provider is enabled. Getting FINE location");
            } catch (SecurityException e) {
                Log.e(TAG, "Appium Settings has no access to FINE location permission", e);
            }
        }
        if (isNetworkEnabled) {
            try {
                mLocationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        LOCATION_UPDATES_INTERVAL,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES, networkLocationListener);
                mNetworkLocationProviderName = LocationManager.NETWORK_PROVIDER;
                Log.d(TAG, "NETWORK location provider is enabled. Getting COARSE location");
            } catch (SecurityException e) {
                Log.e(TAG, "Appium Settings has no access to COARSE location permission", e);
            }
        }
    }

    private void stopLocationUpdatesWithoutPlayServices() {
        if (mLocationManager == null) {
            return;
        }

        Log.d(TAG, "Stopping Android location provider");
        mLocationManager.removeUpdates(gpsLocationListener);
        mLocationManager.removeUpdates(networkLocationListener);
        mLocationManager = null;
    }

    private boolean isPlayServicesConnected() {
        return mGoogleApiClient != null && mGoogleApiClient.isConnected();
    }

    private boolean isLocationManagerConnected() {
        return mLocationManager != null && (mGpsLocationProviderName != null || mNetworkLocationProviderName != null);
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
            if (isLocationManagerConnected()) {
                mLocation = fetchLastKnownLocationFromProviders();
                return mLocation;
            }
        }
        return null;
    }

    @SuppressWarnings("MissingPermission")
    private Location fetchLastKnownLocationFromProviders() {
        Location lastKnownLocation = null;
        try {
            lastKnownLocation = mLocationManager.getLastKnownLocation(mGpsLocationProviderName);
            if (lastKnownLocation != null) {
                return lastKnownLocation;
            }
        } catch (SecurityException e){
            Log.e(TAG, String.format("Appium Settings has no access to %s location permission",
                    mGpsLocationProviderName), e);
        }

        try {
            lastKnownLocation = mLocationManager.getLastKnownLocation(mNetworkLocationProviderName);
        } catch (SecurityException e){
            Log.e(TAG, String.format("Appium Settings has no access to %s location permission",
                    mNetworkLocationProviderName), e);
        }
        return lastKnownLocation;
    }
}
