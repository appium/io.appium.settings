package io.appium.settings;

import io.appium.settings.handlers.*;

import io.appium.settings.Service;

import android.content.Context;


public class ServicesFactory {
  public static Service getService(Context context, String name) {
    if (name.equalsIgnoreCase("wifi")) {
      return new WiFiService(context);
    } else if (name.equalsIgnoreCase("data")) {
      return new DataService(context);
    } else if (name.equalsIgnoreCase("gps")) {
      return new GPSService(context);
    } else {
      return null;
    }
  }
}
