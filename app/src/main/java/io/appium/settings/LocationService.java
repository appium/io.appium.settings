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
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.IBinder;
import android.util.Log;

public class LocationService extends Service {
    private static final String TAG = "MOCKED LOCATION SERVICE";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        for (String p : new String[]{
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_FINE_LOCATION"}) {
            if (getApplicationContext().checkCallingOrSelfPermission(p)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, String.format("Cannot mock location due to missing permission '%s'", p));
                return START_NOT_STICKY;
            }
        }
        double longitude;
        try {
            longitude = Double.valueOf(intent.getStringExtra("longitude"));
        } catch (NumberFormatException e) {
            Log.e(TAG, String.format("longitude should be a valid number. '%s' is given instead",
                    intent.getStringExtra("longitude")));
            return START_NOT_STICKY;
        }
        double latitude;
        try {
            latitude = Double.valueOf(intent.getStringExtra("latitude"));
        } catch (NumberFormatException e) {
            Log.e(TAG, String.format("latitude should be a valid number. '%s' is given instead",
                    intent.getStringExtra("latitude")));
            return START_NOT_STICKY;
        }
        Log.i(TAG,
                String.format("Setting the location from service with longitude: %.5f, latitude: %.5f",
                        longitude, latitude));
        MockLocationProvider mock = new MockLocationProvider(LocationManager.GPS_PROVIDER, this);
        // Set mock location
        mock.pushLocation(latitude, longitude);
        return START_NOT_STICKY;
    }
}
