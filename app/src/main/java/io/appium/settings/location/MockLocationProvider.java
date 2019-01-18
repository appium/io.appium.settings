package io.appium.settings.location;

import android.location.Location;

public interface MockLocationProvider {

    void setLocation(Location location);

    void enable();

    void disable();

    String getProviderName();

}
