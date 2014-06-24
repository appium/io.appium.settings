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
import android.location.LocationManager;
import android.provider.Settings;
import android.util.Log;
import io.appium.settings.Service;


public class GPSService extends Service {
  private static final String TAG = "APPIUM SETTINGS (GPS)";
  private String beforeEnable;

  public GPSService(Context context) {
    super(context);
  }

  public boolean enable() {
    Log.d(TAG, "Enabling GPS");
    beforeEnable = Settings.Secure.getString(mContext.getContentResolver(),
                                             Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
    String newSet = String.format("%s,%s",
                                  beforeEnable,
                                  LocationManager.GPS_PROVIDER);
    try {
        return Settings.Secure.putString(mContext.getContentResolver(),
                                         Settings.Secure.LOCATION_PROVIDERS_ALLOWED,
                                         newSet);
    } catch(Exception e) {
      Log.e(TAG, "Unable to enable Data: " + e.getMessage());
      return false;
    }
  }

  public boolean disable() {
    Log.d(TAG, "Disabling GPS");
    if (null == beforeEnable) {
      String str = Settings.Secure.getString (mContext.getContentResolver(),
                                              Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
      if (null == str) {
        str = "";
      } else {
        String[] list = str.split (",");
        str = "";
        int j = 0;
        for (int i = 0; i < list.length; i++) {
          if (!list[i].equals (LocationManager.GPS_PROVIDER)) {
            if (j > 0) {
              str += ",";
            }
            str += list[i];
            j++;
          }
        }
        beforeEnable = str;
      }
    }
    try {
      return Settings.Secure.putString (mContext.getContentResolver(),
                                        Settings.Secure.LOCATION_PROVIDERS_ALLOWED,
                                        beforeEnable);
    } catch(Exception e) {
      Log.e(TAG, "Unable to disable Data: " + e.getMessage());
      return false;
    }
  }
}
