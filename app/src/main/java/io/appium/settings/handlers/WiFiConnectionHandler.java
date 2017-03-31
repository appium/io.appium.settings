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

package io.appium.settings.handlers;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;

public class WiFiConnectionHandler extends ConnectionHandler {
    private static final String TAG = "APPIUM SETTINGS (WIFI)";

    public WiFiConnectionHandler(Context context) {
        super(context, "android.permission.CHANGE_WIFI_STATE");
    }

    public boolean enable() {
        Log.d(TAG, "Enabling wifi");

        if (!hasPermissions()) {
            Log.e(TAG, "The necessary permissions are not set. Cannot enable Wi-Fi");
            return false;
        }
        return setWiFi(true);
    }

    public boolean disable() {
        Log.d(TAG, "Disabling wifi");

        if (!hasPermissions()) {
            Log.e(TAG, "The necessary permissions are not set. Cannot disable Wi-Fi");
            return false;
        }
        return setWiFi(false);
    }

    private boolean setWiFi(boolean state) {
        WifiManager mWifiManager =
                (WifiManager) mContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        return mWifiManager.setWifiEnabled(state);
    }
}
