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
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import io.appium.settings.receivers.AnimationSettingReceiver;
import io.appium.settings.receivers.BluetoothConnectionSettingReceiver;
import io.appium.settings.receivers.ClipboardReceiver;
import io.appium.settings.receivers.DataConnectionSettingReceiver;
import io.appium.settings.receivers.HasAction;
import io.appium.settings.receivers.LocaleSettingReceiver;
import io.appium.settings.receivers.LocationInfoReceiver;
import io.appium.settings.receivers.MediaScannerReceiver;
import io.appium.settings.receivers.NotificationsReceiver;
import io.appium.settings.receivers.SmsReader;
import io.appium.settings.receivers.UnpairBluetoothDevicesReceiver;
import io.appium.settings.receivers.WiFiConnectionSettingReceiver;

public class Settings extends Activity {
    private static final String TAG = "APPIUM SETTINGS";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        Log.d(TAG, "Entering the app");

        registerSettingsReceivers(Arrays.asList(
                WiFiConnectionSettingReceiver.class,
                AnimationSettingReceiver.class,
                DataConnectionSettingReceiver.class,
                LocaleSettingReceiver.class,
                LocationInfoReceiver.class,
                ClipboardReceiver.class,
                BluetoothConnectionSettingReceiver.class,
                UnpairBluetoothDevicesReceiver.class,
                NotificationsReceiver.class,
                SmsReader.class,
                MediaScannerReceiver.class
        ));

        // https://developer.android.com/about/versions/oreo/background-location-limits
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(ForegroundService.getForegroundServiceIntent(Settings.this));
        } else {
            LocationTracker.getInstance().start(this);
        }

        Log.d(TAG, "Closing the app");
        Handler handler = new Handler();
        handler.postDelayed(Settings.this::finish, 1000);
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
