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

import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.util.Log;


public class LocationBuilder {

    private static final String TAG = "MOCKED LOCATION BUILDER";
    private static final String LONGITUDE_PARAMETER_KEY = "longitude";
    private static final String LATITUDE_PARAMETER_KEY = "latitude";
    private static final String ALTITUDE_PARAMETER_KEY = "altitude";
    private static final String SPEED_PARAMETER_KEY = "speed";
    private static final String BEARING_PARAMETER_KEY = "bearing";

    @Nullable
    private static Double extractParam(Intent intent, String paramKey) {
        Double value = null;

        try {
            if (intent.hasExtra(paramKey)) {
                value = Double.parseDouble(intent.getStringExtra(paramKey));
                Log.i(TAG, String.format("Received parameter: %s, value: %s", paramKey, value));
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, String.format("%s should be a valid number. '%s' is given instead",
                    paramKey, intent.getStringExtra(paramKey)));
        }
        return value;
    }

    public static Location buildFromIntent(Intent intent, String providerName) {
        Double longitude = extractParam(intent, LONGITUDE_PARAMETER_KEY);
        Double latitude = extractParam(intent, LATITUDE_PARAMETER_KEY);
        Double altitude = extractParam(intent, ALTITUDE_PARAMETER_KEY);
        Double speed = extractParam(intent, SPEED_PARAMETER_KEY);
        Double bearing = extractParam(intent, BEARING_PARAMETER_KEY);
        Location location = new Location(providerName);
        location.setAccuracy(Criteria.ACCURACY_FINE);
        if (longitude != null) {
            location.setLongitude(longitude);
        }
        if (latitude != null) {
            location.setLatitude(latitude);
        }
        if (altitude != null) {
            location.setAltitude(altitude);
        }
        if (speed != null) {
            location.setSpeed(speed.floatValue());
        }
        if (bearing != null) {
            location.setBearing(bearing.floatValue());
        }

        location.setTime(System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            location.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }
        return location;
    }
}
