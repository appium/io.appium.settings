package io.appium.settings;

// import android.app.*;
// import android.content.*;
// import android.os.*;
// import android.view.*;
import android.util.Log;

import android.os.Bundle;

// import android.net.ConnectivityManager;
// import android.net.wifi.WifiManager;
// import android.telephony.TelephonyManager;

// import java.lang.reflect.Field;
// import java.lang.reflect.Method;

import java.util.Iterator;

import android.app.Activity;
import android.os.Handler;

import io.appium.settings.Service;
import io.appium.settings.ServicesFactory;

public class Settings extends Activity
{
  private static final String TAG = "APPIUM SETTINGS";

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);

    Log.d(TAG, "Entering Appium settings");

    Bundle extras = this.getIntent().getExtras();
    Iterator iter = extras.keySet().iterator();
    while (iter.hasNext()) {
      String name = (String)iter.next();
      Service service = ServicesFactory.getService(this, name);
      boolean status = (extras.getString(name).equalsIgnoreCase("on")) ? service.enable() : service.disable();
      Log.d(TAG, "Status: " + status);
    }

    // if (extras != null) {
    //   if (extras.containsKey("wifi")) {
    //     String action = extras.getString("wifi");
    //     setWifi(action);
    //   }
    //   if (extras.containsKey("data")) {
    //     String action = extras.getString("data");
    //     setData(action);
    //   }
    // }

    // Close yourself!
    Handler handler = new Handler();
    handler.postDelayed(new Runnable() {
      public void run() {
        Settings.this.finish();
      }
    }, 200);
  }

  // private void setWifi(String action) {
  //   Log.d(TAG, "Setting wifi: " + action);

  //   WifiManager mWifiManager = (WifiManager)getSystemService(Context.WIFI_SERVICE);
  //   mWifiManager.setWifiEnabled(action.equalsIgnoreCase("on"));
  // }

  // private void setData(String action) {
  //   Log.d(TAG, "Setting data: " + action);

  //   turnOnDataConnection(action.equalsIgnoreCase("on"), this);
  // }

  // boolean turnOnDataConnection(boolean ON,Context context) {
  //   try {
  //     if (Build.VERSION.SDK_INT == Build.VERSION_CODES.FROYO) {
  //       Method dataConnSwitchmethod;
  //       Class<?> telephonyManagerClass;
  //       Object ITelephonyStub;
  //       Class<?> ITelephonyClass;

  //       TelephonyManager telephonyManager = (TelephonyManager) context
  //           .getSystemService(Context.TELEPHONY_SERVICE);

  //       telephonyManagerClass = Class.forName(telephonyManager.getClass().getName());
  //       Method getITelephonyMethod = telephonyManagerClass.getDeclaredMethod("getITelephony");
  //       getITelephonyMethod.setAccessible(true);
  //       ITelephonyStub = getITelephonyMethod.invoke(telephonyManager);
  //       ITelephonyClass = Class.forName(ITelephonyStub.getClass().getName());

  //       if (ON) {
  //         dataConnSwitchmethod = ITelephonyClass
  //             .getDeclaredMethod("enableDataConnectivity");
  //       } else {
  //         dataConnSwitchmethod = ITelephonyClass
  //             .getDeclaredMethod("disableDataConnectivity");
  //       }
  //       dataConnSwitchmethod.setAccessible(true);
  //       dataConnSwitchmethod.invoke(ITelephonyStub);
  //     } else {
  //       //log.i("App running on Ginger bread+");
  //       final ConnectivityManager conman = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
  //       final Class<?> conmanClass = Class.forName(conman.getClass().getName());
  //       final Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
  //       iConnectivityManagerField.setAccessible(true);
  //       final Object iConnectivityManager = iConnectivityManagerField.get(conman);
  //       final Class<?> iConnectivityManagerClass =  Class.forName(iConnectivityManager.getClass().getName());
  //       final Method setMobileDataEnabledMethod = iConnectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
  //       setMobileDataEnabledMethod.setAccessible(true);
  //       setMobileDataEnabledMethod.invoke(iConnectivityManager, ON);
  //     }

  //     return true;
  //   } catch(Exception e) {
  //     Log.e(TAG,"error turning on/off data: " + e.getMessage());
  //     return false;
  //   }
  // }
}
