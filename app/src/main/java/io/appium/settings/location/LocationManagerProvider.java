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
import android.location.LocationManager;

public class LocationManagerProvider implements MockLocationProvider {

    private final LocationManager locationManager;

    private final String name;
    private final boolean requiresNetwork;
    private final boolean requiresSatellite;
    private final boolean requiresCell;
    private final boolean hasMonetaryCost;
    private final boolean supportsAltitude;
    private final boolean supportsSpeed;
    private final boolean supportsBearing;
    private final int powerRequirement;
    private final int accuracy;

    public LocationManagerProvider(LocationManager locationManager, String name, boolean requiresNetwork, boolean requiresSatellite, boolean requiresCell, boolean hasMonetaryCost, boolean supportsAltitude, boolean supportsSpeed, boolean supportsBearing, int powerRequirement, int accuracy) {
        this.locationManager = locationManager;
        this.name = name;
        this.requiresNetwork = requiresNetwork;
        this.requiresSatellite = requiresSatellite;
        this.requiresCell = requiresCell;
        this.hasMonetaryCost = hasMonetaryCost;
        this.supportsAltitude = supportsAltitude;
        this.supportsSpeed = supportsSpeed;
        this.supportsBearing = supportsBearing;
        this.powerRequirement = powerRequirement;
        this.accuracy = accuracy;
    }

    @Override
    public void setLocation(Location location) {
        locationManager.setTestProviderLocation(name, location);
    }

    @Override
    public void enable() {
        locationManager.addTestProvider(name,
                requiresNetwork,
                requiresSatellite,
                requiresCell,
                hasMonetaryCost,
                supportsAltitude,
                supportsSpeed,
                supportsBearing,
                powerRequirement,
                accuracy);
        locationManager.setTestProviderEnabled(name, true);
    }

    @Override
    public void disable() {
        locationManager.setTestProviderEnabled(name, false);
        locationManager.removeTestProvider(name);
    }

    @Override
    public String getProviderName() {
        return name;
    }

    @Override
    public String toString() {
        return "LocationManagerProvider{" +
                "name='" + name + '\'' +
                ", requiresNetwork=" + requiresNetwork +
                ", requiresSatellite=" + requiresSatellite +
                ", requiresCell=" + requiresCell +
                ", hasMonetaryCost=" + hasMonetaryCost +
                ", supportsAltitude=" + supportsAltitude +
                ", supportsSpeed=" + supportsSpeed +
                ", supportsBearing=" + supportsBearing +
                ", powerRequirement=" + powerRequirement +
                ", accuracy=" + accuracy +
                '}';
    }
}
