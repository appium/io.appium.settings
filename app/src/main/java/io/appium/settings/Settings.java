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

package io.appium.settings;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import io.appium.settings.receivers.AnimationSettingReceiver;
import io.appium.settings.receivers.DataConnectionSettingReceiver;
import io.appium.settings.receivers.LocaleSettingReceiver;
import io.appium.settings.receivers.LocationInfoReceiver;

public class Settings extends Activity {
    private static final String TAG = "APPIUM SETTINGS";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.d(TAG, "Entering Appium settings");

        registerLocaleSettingReceiver();
        registerAnimationSettingReceiver();
        registerDataConnectionSettingReceiver();
        registerLocationInfoReceiver();

        // Close yourself!
        Log.d(TAG, "Closing settings app");
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                Settings.this.finish();
            }
        }, 1000);
    }

    private void registerAnimationSettingReceiver() {
        BroadcastReceiver animationSettingReceiver = new AnimationSettingReceiver();
        IntentFilter filter = new IntentFilter(AnimationSettingReceiver.ACTION);
        getApplicationContext().registerReceiver(animationSettingReceiver, filter);
    }

    private void registerDataConnectionSettingReceiver() {
        BroadcastReceiver dataConnectionSettingReceiver = new DataConnectionSettingReceiver();
        IntentFilter filter = new IntentFilter(DataConnectionSettingReceiver.ACTION);
        getApplicationContext().registerReceiver(dataConnectionSettingReceiver, filter);
    }

    private void registerLocaleSettingReceiver() {
        BroadcastReceiver localeSettingReceiver = new LocaleSettingReceiver();
        IntentFilter filter = new IntentFilter(LocaleSettingReceiver.ACTION);
        getApplicationContext().registerReceiver(localeSettingReceiver, filter);
    }

    private void registerLocationInfoReceiver() {
        BroadcastReceiver locationInfoReceiver = new LocationInfoReceiver();
        IntentFilter filter = new IntentFilter(LocationInfoReceiver.ACTION);
        getApplicationContext().registerReceiver(locationInfoReceiver, filter);
    }
}
