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
    }, 1000);
  }

  private void updateView(int index, String name, String value) {
    int viewId = this.getResources().getIdentifier("notice_" + index, "id", this.getPackageName());
    final TextView notice = (TextView) findViewById(viewId);
    Log.d(TAG, name + "_" + value);
    int stringId = this.getResources().getIdentifier(name + "_" + value, "string", this.getPackageName());
    notice.setText(getResources().getString(stringId));
  }
}
