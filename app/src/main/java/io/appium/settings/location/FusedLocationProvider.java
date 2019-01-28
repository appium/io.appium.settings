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

package io.appium.settings.location;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.location.Location;
import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.support.v4.content.ContextCompat.checkSelfPermission;

public class FusedLocationProvider implements MockLocationProvider {

    private final static String TAG = FusedLocationProvider.class.getSimpleName();

    private final static String PROVIDER_NAME = "fused";

    private final GoogleApiClient googleApiClient;
    private final FusedLocationProviderClient fusedLocationProviderClient;
    private final Context context;

    public FusedLocationProvider(GoogleApiClient googleApiClient, FusedLocationProviderClient fusedLocationProviderClient, Context context) {
        this.googleApiClient = googleApiClient;
        this.fusedLocationProviderClient = fusedLocationProviderClient;
        this.context = context;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @SuppressLint("MissingPermission")
    @Override
    public void setLocation(Location location) {
        if (!hasPermissions()) {
            return;
        }
        if (!googleApiClient.isConnected()) {
            Log.d(TAG, "GoogleApiClient is not connected");
            return;
        }
        fusedLocationProviderClient.setMockLocation(location);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void enable() {
        if (!hasPermissions()) {
            return;
        }
        googleApiClient.connect();
        fusedLocationProviderClient.setMockMode(true);
    }

    @SuppressLint("MissingPermission")
    @Override
    public void disable() {
        if (!hasPermissions()) {
            return;
        }
        fusedLocationProviderClient.setMockMode(false);
        googleApiClient.disconnect();
    }

    @Override
    public String toString() {
        return "FusedLocationProvider{" +
                "name='" + PROVIDER_NAME + '\'' +
                '}';
    }

    private boolean hasPermissions() {
        if (checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) != PERMISSION_GRANTED) {
            Log.e(TAG, String.format("Missing permission: '%s'", Manifest.permission.ACCESS_FINE_LOCATION));
            return false;
        }
        return true;
    }
}
