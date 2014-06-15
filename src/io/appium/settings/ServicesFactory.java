package io.appium.settings;

import android.content.Context;
import io.appium.settings.handlers.*;
import io.appium.settings.Service;


public class ServicesFactory {
  public static Service getService(Context context, String name) {
    if (name.equalsIgnoreCase("wifi")) {
      return new WiFiService(context);
    } else if (name.equalsIgnoreCase("data")) {
      return new DataService(context);
    } else if (name.equalsIgnoreCase("gps")) {
      return new GPSService(context);
    } else if (name.equalsIgnoreCase("airplane_mode")) {
      return new AirplaneModeService(context);
    } else {
      return null;
    }
  }
}
