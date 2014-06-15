package io.appium.settings.handlers;

import io.appium.settings.Service;

import android.content.Context;

import android.provider.Settings;

import android.util.Log;

import android.location.LocationManager;


public class GPSService extends Service {
  private static final String TAG = "APPIUM SETTINGS (GPS)";
  private String beforeEnable;

  public GPSService(Context context) {
    super(context);
  }

  public boolean enable() {
    beforeEnable = Settings.Secure.getString(context.getContentResolver(),
                                             Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
    String newSet = String.format("%s,%s",
                                  beforeEnable,
                                  LocationManager.GPS_PROVIDER);
    try {
        return Settings.Secure.putString(context.getContentResolver(),
                                         Settings.Secure.LOCATION_PROVIDERS_ALLOWED,
                                         newSet);
    } catch(Exception e) {
      Log.e(TAG, "Unable to enable Data: " + e.getMessage());
      return false;
    }
  }

  public boolean disable() {
    if (null == beforeEnable) {
      String str = Settings.Secure.getString (context.getContentResolver(),
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
        return Settings.Secure.putString (context.getContentResolver(),
                                          Settings.Secure.LOCATION_PROVIDERS_ALLOWED,
                                          beforeEnable);
    } catch(Exception e) {
      Log.e(TAG, "Unable to disable Data: " + e.getMessage());
      return false;
    }
  }
//   private String beforeEnable;

//   private void turnGpsOn (Context context) {
//     beforeEnable = Settings.Secure.getString(context.getContentResolver(),
//                                              Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
//     String newSet = String.format("%s,%s",
//                                   beforeEnable,
//                                   LocationManager.GPS_PROVIDER);
//     try {
//         Settings.Secure.putString(context.getContentResolver(),
//                                   Settings.Secure.LOCATION_PROVIDERS_ALLOWED,
//                                   newSet);
//     } catch(Exception e) {
//       //
//     }
// }


// private void turnGpsOff (Context context) {
//     if (null == beforeEnable) {
//         String str = Settings.Secure.getString (context.getContentResolver(),
//                                                 Settings.Secure.LOCATION_PROVIDERS_ALLOWED);
//         if (null == str) {
//             str = "";
//         } else {
//             String[] list = str.split (",");
//             str = "";
//             int j = 0;
//             for (int i = 0; i < list.length; i++) {
//                 if (!list[i].equals (LocationManager.GPS_PROVIDER)) {
//                     if (j > 0) {
//                         str += ",";
//                     }
//                     str += list[i];
//                     j++;
//                 }
//             }
//             beforeEnable = str;
//         }
//     }
//     try {
//         Settings.Secure.putString (context.getContentResolver(),
//                                    Settings.Secure.LOCATION_PROVIDERS_ALLOWED,
//                                    beforeEnable);
//     } catch(Exception e) {}
// }
}
