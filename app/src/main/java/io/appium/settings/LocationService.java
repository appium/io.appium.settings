package io.appium.settings;

import android.app.Service;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
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
        if ((getApplication().getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            Log.e(TAG, "Location mocking only works in debug mode");
            return START_NOT_STICKY;
        }
        for (String p : new String[]{
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_MOCK_LOCATION"}) {
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
