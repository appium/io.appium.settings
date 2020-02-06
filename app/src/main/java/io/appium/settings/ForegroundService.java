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

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import io.appium.settings.helpers.NotificationHelpers;

public class ForegroundService extends Service {
    private static final String TAG = "APPIUM SERVICE";
    public static final String ACTION_START = "start";
    public static final String ACTION_STOP = "stop";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_START:
                    startForegroundService();
                    break;
                case ACTION_STOP:
                    stopForegroundService();
                    break;
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void startForegroundService() {
        startForeground(NotificationHelpers.APPIUM_NOTIFICATION_IDENTIFIER,
                NotificationHelpers.getNotification(this));
    }

    private void stopForegroundService() {
        stopForeground(true);
        stopSelf();
    }

    public static Intent getForegroundServiceIntent(Context context) {
        Log.d(TAG, "Initializing the foreground service");
        Intent intent = new Intent(context, ForegroundService.class);
        intent.setAction(ForegroundService.ACTION_START);
        return intent;
    }
}
