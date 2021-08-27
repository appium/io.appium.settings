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


public class LocationBuilder {

    final private String providerName;

    private double latitude = 0.0f;
    private double longitude = 0.0f;
    private double altitude = 0.0f;
    private float bearing = 0.0f;
    private float speed = 0.0f;
    private float accuracy = 0.0f;

    public LocationBuilder (String providerName) {
        this.providerName = providerName;
    }

    public LocationBuilder setAccuracy(float accuracy) {
        this.accuracy = accuracy;
        return this;
    }

    public LocationBuilder setLatitude(double latitude) {
        this.latitude = latitude;
        return this;
    }

    public LocationBuilder setLongitude(double longitude) {
        this.longitude = longitude;
        return this;
    }

    public LocationBuilder setAltitude(double altitude) {
        this.altitude = altitude;
        return this;
    }

    public LocationBuilder setSpeed(float speed) {
        this.speed = speed;
        return this;
    }

    public LocationBuilder setBearing(float bearing) {
        this.bearing = bearing;
        return this;
    }

    public Location build() {
        Location l = new Location(this.providerName);
        l.setAccuracy(this.accuracy);
        l.setLatitude(this.latitude);
        l.setLongitude(this.longitude);
        l.setAltitude(this.altitude);
        l.setBearing(this.bearing);
        l.setSpeed(this.speed);
        l.setTime(System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            l.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }
        return l;
    }
}
