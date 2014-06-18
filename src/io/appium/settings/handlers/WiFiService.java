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

    boolean ret = setWiFi(true);

    WifiManager mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    boolean b = mWifiManager.isWifiEnabled();
    Log.d(TAG, "WIF? " + b);

    return ret;
  }

  public boolean disable() {
    Log.d(TAG, "Disabling wifi");

    return setWiFi(false);
  }

  private boolean setWiFi(boolean state) {
    WifiManager mWifiManager = (WifiManager) mContext.getSystemService(Context.WIFI_SERVICE);
    return mWifiManager.setWifiEnabled(state);
  }
}
