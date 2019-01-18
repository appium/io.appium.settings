package io.appium.settings.location;

import android.location.Location;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;

public class FusedLocationProvider implements MockLocationProvider {

    private final static String PROVIDER_NAME = "fused";

    private final GoogleApiClient googleApiClient;
    private final FusedLocationProviderClient fusedLocationProviderClient;

    public FusedLocationProvider(GoogleApiClient googleApiClient, FusedLocationProviderClient fusedLocationProviderClient) {
        this.googleApiClient = googleApiClient;
        this.fusedLocationProviderClient = fusedLocationProviderClient;
    }

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public void setLocation(Location location) {
        fusedLocationProviderClient.setMockLocation(location);
    }

    @Override
    public void enable() {
        googleApiClient.connect();
        fusedLocationProviderClient.setMockMode(true);
    }

    @Override
    public void disable() {
        fusedLocationProviderClient.setMockMode(false);
        googleApiClient.disconnect();
    }

    @Override
    public String toString() {
        return "FusedLocationProvider{" +
                "name='" + PROVIDER_NAME + '\'' +
                '}';
    }
}
