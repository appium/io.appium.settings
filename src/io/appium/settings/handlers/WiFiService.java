/**
 * Copyright 2012-2014 Appium Committers
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 **/

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

    return setWiFi(true);
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
