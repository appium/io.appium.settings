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
