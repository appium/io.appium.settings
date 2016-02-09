package io.appium.settings;

import android.app.Service;
import android.content.Intent;
import android.location.LocationManager;
import android.os.IBinder;
import android.util.Log;

public class LocationService extends Service {

    MockLocationProvider mock;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String longitude = intent.getStringExtra("longitude");
        String latitude = intent.getStringExtra("latitude");
        Log.i("LocationService", "setting the location from service with longitude: "+longitude+" latitude: "+latitude);
        mock = new MockLocationProvider(LocationManager.GPS_PROVIDER, this);
        // Set mock location
        mock.pushLocation(Double.valueOf(latitude), Double.valueOf(longitude));
        return START_NOT_STICKY;
    }
}
