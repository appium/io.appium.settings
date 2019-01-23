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
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import io.appium.settings.receivers.AnimationSettingReceiver;
import io.appium.settings.receivers.DataConnectionSettingReceiver;
import io.appium.settings.receivers.HasAction;
import io.appium.settings.receivers.LocaleSettingReceiver;
import io.appium.settings.receivers.LocationInfoReceiver;
import io.appium.settings.receivers.WiFiConnectionSettingReceiver;

public class Settings extends Activity {
    private static final String TAG = "APPIUM SETTINGS";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.d(TAG, "Entering the app");

        LocationTracker.getInstance().start(this, 0, true);

        final List<Class<? extends BroadcastReceiver>> receiverClasses = new ArrayList<>();
        receiverClasses.add(WiFiConnectionSettingReceiver.class);
        receiverClasses.add(AnimationSettingReceiver.class);
        receiverClasses.add(DataConnectionSettingReceiver.class);
        receiverClasses.add(LocaleSettingReceiver.class);
        receiverClasses.add(LocationInfoReceiver.class);
        registerSettingsReceivers(receiverClasses);

        // https://developer.android.com/about/versions/oreo/background-location-limits
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Log.d(TAG, "Initializing the foreground service");
            Intent intent = new Intent(Settings.this, ForegroundService.class);
            intent.setAction(ForegroundService.ACTION_START);
            startService(intent);
        }

        Log.d(TAG, "Closing the app");
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            public void run() {
                Settings.this.finish();
            }
        }, 1000);
    }

    private void registerSettingsReceivers(List<Class<? extends BroadcastReceiver>> receiverClasses) {
        for (Class<? extends BroadcastReceiver> receiverClass: receiverClasses) {
            try {
                final BroadcastReceiver receiver = receiverClass.newInstance();
                IntentFilter filter = new IntentFilter(((HasAction) receiver).getAction());
                getApplicationContext().registerReceiver(receiver, filter);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            }
        }
    }
}
