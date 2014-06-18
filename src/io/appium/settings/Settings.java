package io.appium.settings;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
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
    setContentView(R.layout.main);

    Log.d(TAG, "Entering Appium settings");

    Bundle extras = this.getIntent().getExtras();
    Iterator iter = extras.keySet().iterator();
    int i = 0;
    while (iter.hasNext()) {
      String name = (String)iter.next();
      Service service = ServicesFactory.getService(this, name);
      if (service != null) {
        String value = extras.getString(name);
        updateView(i, name, value);
        boolean status = (value.equalsIgnoreCase("on")) ? service.enable() : service.disable();
        Log.d(TAG, "Status: " + status);
      } else {
        Log.e(TAG, "Unknown service '" + name + "'");
      }
      i++;
    }

    // Close yourself!
    Log.d(TAG, "Closing settings app");
    Handler handler = new Handler();
    handler.postDelayed(new Runnable() {
      public void run() {
        Settings.this.finish();
      }
    }, 2000);
  }

  private void updateView(int index, String name, String value) {
    int viewId = this.getResources().getIdentifier("notice_" + index, "id", this.getPackageName());
    final TextView notice = (TextView) findViewById(viewId);
    Log.d(TAG, name + "_" + value);
    int stringId = this.getResources().getIdentifier(name + "_" + value, "string", this.getPackageName());
    notice.setText(getResources().getString(stringId));
  }
}
