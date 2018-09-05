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
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

@SuppressLint("Registered")
public class GPSTracker extends Service implements LocationListener {
    private static final String TAG = GPSTracker.class.getSimpleName();
    private final Context mContext;

    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10; // 10 meters
    private static final long MIN_TIME_BW_UPDATES = 1000 * 60; // 1 minute

    public GPSTracker(Context context) {
        this.mContext = context;
    }

    @SuppressLint("MissingPermission")
    public Location getLocation() {
        try {
            LocationManager locationManager = (LocationManager) mContext
                    .getSystemService(LOCATION_SERVICE);
            if (locationManager == null) {
                Log.e(TAG, "Cannot retrieve the location manager");
                return null;
            }

            boolean isGPSEnabled = locationManager
                    .isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean isNetworkEnabled = locationManager
                    .isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if (!isGPSEnabled && !isNetworkEnabled) {
                Log.e(TAG, "Both Coarse and GPS providers are disabled");
                return null;
            }

            if (isGPSEnabled) {
                try {
                    locationManager.requestLocationUpdates(
                            LocationManager.GPS_PROVIDER,
                            MIN_TIME_BW_UPDATES,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                    Log.d(TAG, "GPS enabled. Getting FINE location");
                    return locationManager
                            .getLastKnownLocation(LocationManager.GPS_PROVIDER);
                } catch (SecurityException e) {
                    Log.e(TAG, "Appium Settings has no access to FINE location permission");
                }
            }
            try {
                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        MIN_TIME_BW_UPDATES,
                        MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                Log.d(TAG, "Network enabled. Getting COARSE location");
                return locationManager
                        .getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            } catch (SecurityException e) {
                Log.e(TAG, "Appium Settings has no access to COARSE location permission");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public void onLocationChanged(Location location) {
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
    public IBinder onBind(Intent arg0) {
        return null;
    }
}
