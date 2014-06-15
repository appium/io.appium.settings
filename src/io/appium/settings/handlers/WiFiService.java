package io.appium.settings.handlers;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;
import io.appium.settings.Service;


public class WiFiService extends Service {
  private static final String TAG = "APPIUM SETTINGS (WIFI)";

  public WiFiService(Context context) {
    super(context);
  }

  public boolean enable() {
    Log.d(TAG, "Enabling wifi");

    WifiManager mWifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
    return mWifiManager.setWifiEnabled(true);
  }

  public boolean disable() {
    Log.d(TAG, "Disabling wifi");

    WifiManager mWifiManager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
    return mWifiManager.setWifiEnabled(false);
  }
}
