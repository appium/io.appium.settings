package io.appium.settings.handlers;

import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.util.Log;
import io.appium.settings.Service;
import java.lang.reflect.Field;
import java.lang.reflect.Method;


public class DataService extends Service {
  private static final String TAG = "APPIUM SETTINGS (DATA)";

  public DataService(Context context) {
    super(context);
  }

  public boolean enable() {
    Log.d(TAG, "Enabling data");

    return setDataConnection(true);
  }

  public boolean disable() {
    Log.d(TAG, "Disabling data");

    return setDataConnection(false);
  }

  private boolean setDataConnection(boolean on) {
    try {
      if (Build.VERSION.SDK_INT == Build.VERSION_CODES.FROYO) {
        Method dataConnSwitchmethod;
        Class<?> telephonyManagerClass;
        Object ITelephonyStub;
        Class<?> ITelephonyClass;

        TelephonyManager telephonyManager = (TelephonyManager) mContext
            .getSystemService(Context.TELEPHONY_SERVICE);

        telephonyManagerClass = Class.forName(telephonyManager.getClass().getName());
        Method getITelephonyMethod = telephonyManagerClass.getDeclaredMethod("getITelephony");
        getITelephonyMethod.setAccessible(true);
        ITelephonyStub = getITelephonyMethod.invoke(telephonyManager);
        ITelephonyClass = Class.forName(ITelephonyStub.getClass().getName());

        if (on) {
          dataConnSwitchmethod = ITelephonyClass
              .getDeclaredMethod("enableDataConnectivity");
        } else {
          dataConnSwitchmethod = ITelephonyClass
              .getDeclaredMethod("disableDataConnectivity");
        }
        dataConnSwitchmethod.setAccessible(true);
        dataConnSwitchmethod.invoke(ITelephonyStub);
      } else {
        //log.i("App running on Ginger bread+");
        final ConnectivityManager conman = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        final Class<?> conmanClass = Class.forName(conman.getClass().getName());
        final Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
        iConnectivityManagerField.setAccessible(true);
        final Object iConnectivityManager = iConnectivityManagerField.get(conman);
        final Class<?> iConnectivityManagerClass =  Class.forName(iConnectivityManager.getClass().getName());
        final Method setMobileDataEnabledMethod = iConnectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
        setMobileDataEnabledMethod.setAccessible(true);
        setMobileDataEnabledMethod.invoke(iConnectivityManager, on);
      }

      return true;
    } catch(Exception e) {
      Log.e(TAG,"error turning on/off data: " + e.getMessage());
      return false;
    }
  }
}
