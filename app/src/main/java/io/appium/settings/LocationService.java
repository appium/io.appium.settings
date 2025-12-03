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

import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.location.provider.ProviderProperties;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.util.Log;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.util.LinkedList;
import java.util.List;

import androidx.annotation.Nullable;
import io.appium.settings.helpers.NotificationHelpers;
import io.appium.settings.helpers.PlayServicesHelpers;
import io.appium.settings.location.FusedLocationProvider;
import io.appium.settings.location.LocationBuilder;
import io.appium.settings.location.LocationManagerProvider;
import io.appium.settings.location.MockLocationProvider;

public class LocationService extends Service {
    private static final String TAG = "MOCKED LOCATION SERVICE";
    private static final long UPDATE_INTERVAL_MS = 5000L;

    private final List<MockLocationProvider> mockLocationProviders = new LinkedList<>();
    private HandlerThread locationUpdateThread;
    private Handler locationUpdateHandler;
    private Runnable locationUpdateRunnable;
    private Notification notification;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // Pre-create notification to avoid any delay when calling startForeground
        notification = NotificationHelpers.getNotification(this);
        // Start foreground immediately to avoid RemoteServiceException on older Android versions
        // This must be called synchronously and before any other operations
        startForeground(NotificationHelpers.APPIUM_NOTIFICATION_IDENTIFIER, notification);
        initializeLocationProviders();
        enableLocationProviders();
        initializeLocationUpdateThread();
    }

    private void initializeLocationUpdateThread() {
        locationUpdateThread = new HandlerThread("LocationUpdateThread", Process.THREAD_PRIORITY_BACKGROUND);
        locationUpdateThread.start();
        locationUpdateHandler = new Handler(locationUpdateThread.getLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Start foreground immediately - must be called before any other operations
        // This is critical on older Android versions (API 32 and below)
        // Use pre-created notification if available, otherwise create it
        if (notification == null) {
            notification = NotificationHelpers.getNotification(this);
        }
        try {
            startForeground(NotificationHelpers.APPIUM_NOTIFICATION_IDENTIFIER, notification);
        } catch (IllegalStateException e) {
            // Service might already be in foreground, which is fine
            Log.d(TAG, "Service already in foreground");
        }

        for (String p : new String[]{"android.permission.ACCESS_FINE_LOCATION"}) {
            if (getApplicationContext().checkCallingOrSelfPermission(p)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, String.format("Cannot mock location due to missing permission '%s'", p));
                return START_NOT_STICKY;
            }
        }

        handleIntent(intent);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Shutting down MockLocationService");
        // Stop updates first to prevent new messages from being posted
        stopLocationUpdates();
        disableLocationProviders();
        // Clean up the thread - this will wait for any currently executing runnable to finish
        cleanupLocationUpdateThread();
        super.onDestroy();
    }

    private void stopLocationUpdates() {
        if (locationUpdateHandler != null && locationUpdateRunnable != null) {
            locationUpdateHandler.removeCallbacks(locationUpdateRunnable);
        }
        locationUpdateRunnable = null;
    }

    private void cleanupLocationUpdateThread() {
        if (locationUpdateThread != null) {
            // Remove any pending callbacks before quitting
            if (locationUpdateHandler != null && locationUpdateRunnable != null) {
                locationUpdateHandler.removeCallbacks(locationUpdateRunnable);
            }
            // Quit the looper - this will stop processing new messages
            locationUpdateThread.quitSafely();
            try {
                // Wait for the thread to finish processing current messages
                locationUpdateThread.join(1000);
            } catch (InterruptedException e) {
                Log.w(TAG, "Interrupted while waiting for location update thread to finish", e);
                Thread.currentThread().interrupt();
            }
            locationUpdateThread = null;
            locationUpdateHandler = null;
        }
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        Log.i(TAG, "INTENT " + intent.getExtras());

        scheduleLocationUpdate(intent);
    }

    private void enableLocationProviders() {
        for (MockLocationProvider mockLocationProvider : mockLocationProviders) {
            try {
                mockLocationProvider.enable();
            } catch (Exception e) {
                Log.e(TAG, String.format("Couldn't enable location provider: '%s'",
                        mockLocationProvider.getProviderName()));
            }
        }
    }

    private void disableLocationProviders() {
        for (MockLocationProvider mockLocationProvider : mockLocationProviders) {
            try {
                mockLocationProvider.disable();
            } catch (Exception e) {
                Log.e(TAG, String.format("Could not disable location provider: '%s'",
                        mockLocationProvider.getProviderName()));
            }
        }
    }

    private void initializeLocationProviders() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        mockLocationProviders.clear();
        mockLocationProviders.addAll(createMockProviders(locationManager));
        if (PlayServicesHelpers.isAvailable(this)) {
            Log.d(TAG, "Adding FusedLocationProvider");
            mockLocationProviders.add(createFusedLocationProvider());
        }
        Log.d(TAG, String.format("Created mock providers: %s", mockLocationProviders.toString()));
    }

    private void scheduleLocationUpdate(final Intent intent) {
        Log.i(TAG, "Scheduling mock location updates");

        // Stop any existing updates
        stopLocationUpdates();

        // Create a new runnable that will update location periodically
        locationUpdateRunnable = new Runnable() {
            @Override
            public void run() {
                // Check if handler and runnable are still valid (service might have been destroyed)
                if (locationUpdateHandler == null || locationUpdateRunnable == null ||
                    locationUpdateRunnable != this) {
                    return;
                }

                // Check if the handler thread is still alive
                if (locationUpdateThread == null || !locationUpdateThread.isAlive()) {
                    return;
                }

                // Build location from intent for each provider and update
                for (MockLocationProvider mockLocationProvider : mockLocationProviders) {
                    Location location = LocationBuilder.buildFromIntent(intent, mockLocationProvider.getProviderName());
                    Log.d(TAG, String.format("Setting location of '%s' to '%s'",
                            mockLocationProvider.getProviderName(), location));
                    try {
                        mockLocationProvider.setLocation(location);
                    } catch (Exception e) {
                        Log.e(TAG, String.format("Could not set location for '%s'",
                                mockLocationProvider.getProviderName()), e);
                    }
                }

                // Schedule next update only if handler is still valid
                if (locationUpdateHandler != null && locationUpdateRunnable != null &&
                    locationUpdateRunnable == this && locationUpdateThread != null &&
                    locationUpdateThread.isAlive()) {
                    locationUpdateHandler.postDelayed(this, UPDATE_INTERVAL_MS);
                }
            }
        };

        // Start updates immediately, then continue with interval
        if (locationUpdateHandler != null) {
            locationUpdateHandler.post(locationUpdateRunnable);
        }
    }

    private List<MockLocationProvider> createMockProviders(LocationManager locationManager) {
        List<String> providers = locationManager.getAllProviders();
        List<MockLocationProvider> mockProviders = new LinkedList<>();
        for (String providerName : providers) {
            // The passive provider is not required to be mocked.
            if (providerName.equals(LocationManager.PASSIVE_PROVIDER)) {
                continue;
            }
            MockLocationProvider mockProvider = createLocationManagerMockProvider(locationManager, providerName);
            if (mockProvider == null) {
                Log.e(TAG, String.format("Could not create mock provider for '%s'", providerName));
                continue;
            }
            mockProviders.add(mockProvider);
        }
        return mockProviders;
    }

    /**
     * Creates a mock location provider based on the given provider name.
     *
     * @param locationManager the location manager
     * @param providerName    the name of the provider
     * @return a MockLocationProvider if the provider exists, otherwise null
     */
    @Nullable
    private MockLocationProvider createLocationManagerMockProvider(LocationManager locationManager, String providerName) {
        if (providerName == null) {
            return null;
        }
        // API level check for existence of provider properties
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { // API level 31 and above
            ProviderProperties providerProperties = locationManager.getProviderProperties(providerName);
            if (providerProperties == null) {
                return null;
            }
            return new LocationManagerProvider(
                    locationManager,
                    providerName,
                    providerProperties.hasNetworkRequirement(),
                    providerProperties.hasSatelliteRequirement(),
                    providerProperties.hasCellRequirement(),
                    providerProperties.hasMonetaryCost(),
                    providerProperties.hasAltitudeSupport(),
                    providerProperties.hasSpeedSupport(),
                    providerProperties.hasBearingSupport(),
                    providerProperties.getPowerUsage(),
                    providerProperties.getAccuracy()
            );
        }
        LocationProvider provider = locationManager.getProvider(providerName);
        if (provider == null) {
            return null;
        }
        return new LocationManagerProvider(
                locationManager,
                provider.getName(),
                provider.requiresNetwork(),
                provider.requiresSatellite(),
                provider.requiresCell(),
                provider.hasMonetaryCost(),
                provider.supportsAltitude(),
                provider.supportsSpeed(),
                provider.supportsBearing(),
                provider.getPowerRequirement(),
                provider.getAccuracy()
        );
    }

    private FusedLocationProvider createFusedLocationProvider() {
        FusedLocationProviderClient locationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        return new FusedLocationProvider(locationProviderClient, this);
    }
}
