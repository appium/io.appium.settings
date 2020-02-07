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

package io.appium.settings.helpers;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.v4.app.NotificationCompat;

import io.appium.settings.R;

import static android.content.Context.NOTIFICATION_SERVICE;

public class NotificationHelpers {
    public static final int APPIUM_NOTIFICATION_IDENTIFIER = 1;
    private static final String CHANNEL_ID = "main_channel";
    private static final String CHANNEL_NAME = "Appium Settings";
    private static final String CHANNEL_DESCRIPTION = "Keep this service running, " +
            "so Appium for Android can properly interact with several system APIs";

    @RequiresApi(Build.VERSION_CODES.O)
    private static void createChannel(Context context) {
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(NOTIFICATION_SERVICE);
        if (mNotificationManager == null) {
            return;
        }
        NotificationChannel mChannel = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
        mChannel.setDescription(CHANNEL_DESCRIPTION);
        mChannel.setShowBadge(true);
        mChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        mNotificationManager.createNotificationChannel(mChannel);
    }

    public static Notification getNotification(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createChannel(context);
        }
        NotificationCompat.BigTextStyle bigTextStyle = new NotificationCompat.BigTextStyle();
        bigTextStyle.setBigContentTitle(CHANNEL_NAME);
        bigTextStyle.bigText(CHANNEL_DESCRIPTION);
        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setStyle(bigTextStyle)
                .setWhen(System.currentTimeMillis())
                .setSmallIcon(R.drawable.ic_launcher)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.drawable.ic_launcher))
                .build();
    }
}
