package io.appium.settings.handlers;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.util.Log;
import io.appium.settings.Service;


public class AirplaneModeService extends Service {
  private static final String TAG = "APPIUM SETTINGS (AIRPLANE MODE)";

  public AirplaneModeService(Context context) {
    super(context);
  }

  public boolean enable() {
    Log.d(TAG, "Enabling airplane mode");
    Settings.System.putInt(mContext.getContentResolver(),
    Settings.System.AIRPLANE_MODE_ON, 1);
    Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
    intent.putExtra("state", 1);
    mContext.sendBroadcast(intent);

    return true;
  }

  public boolean disable() {
    Log.d(TAG, "Disabling airplane mode");
    return false;
  }
}
