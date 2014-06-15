package io.appium.settings;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import io.appium.settings.Service;
import io.appium.settings.ServicesFactory;
import java.util.Iterator;


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

    // Close yourself!
    Handler handler = new Handler();
    handler.postDelayed(new Runnable() {
      public void run() {
        Settings.this.finish();
      }
    }, 200);
  }
}
