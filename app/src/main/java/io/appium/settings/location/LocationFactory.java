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

import android.location.Location;
import android.os.Build;
import android.os.SystemClock;

public class LocationFactory {

    private double latitude;
    private double longitude;
    private double altitude;


    public synchronized Location createLocation(String providerName, float accuracy) {
        Location l = new Location(providerName);
        l.setAccuracy(accuracy);

        l.setLatitude(latitude);
        l.setLongitude(longitude);
        l.setAltitude(altitude);
        l.setSpeed(0);
        l.setBearing(0);

        l.setTime(System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }
        return l;
    }

    public synchronized void setLocation(double latitude, double longitude, double altitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.altitude = altitude;
    }
}
