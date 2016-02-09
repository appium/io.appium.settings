package io.appium.settings;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.os.SystemClock;

public class MockLocationProvider {
    String providerName;
    Context ctx;

    public MockLocationProvider(String name, Context ctx) {
        this.providerName = name;
        this.ctx = ctx;
    }

    public void pushLocation(double lat, double lon) {
        LocationManager lm = (LocationManager) ctx.getSystemService(
                Context.LOCATION_SERVICE);
        lm.addTestProvider(providerName, false, false, false, false, false,
                false, true, 0, 5);
        lm.setTestProviderEnabled(providerName, true);

        Location mockLocation = new Location(providerName);
        mockLocation.setLatitude(lat);
        mockLocation.setLongitude(lon);
        mockLocation.setAltitude(0);
        mockLocation.setTime(System.currentTimeMillis());
        mockLocation.setAccuracy(1);
        mockLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());

        lm.setTestProviderLocation(providerName, mockLocation);
    }
}
