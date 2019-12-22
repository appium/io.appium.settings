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

import android.content.Intent;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.support.annotation.Nullable;
import android.util.Log;
import io.appium.settings.notifications.StoredNotification;
import io.appium.settings.notifications.StoredNotifications;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

public class NLService extends NotificationListenerService {
    private static final String TAG = NLService.class.getSimpleName();
    private static final int MAX_BUFFER_SIZE = 100;

    private final List<StoredNotification> notificationsBuffer = new LinkedList<>();

    @Override
    public void onCreate() {
        super.onCreate();

        synchronized (notificationsBuffer) {
            StoredNotifications.getInstance().bindNotificationsBuffer(notificationsBuffer);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public void onListenerDisconnected() {
        Log.i(TAG, "The notification listener has been disconnected");
        synchronized (notificationsBuffer) {
            notificationsBuffer.clear();
        }

        super.onListenerDisconnected();
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.i(TAG, "The notification listener is connected");

        synchronized (notificationsBuffer) {
            StatusBarNotification[] activeNotifications = getActiveNotifications();
            StatusBarNotification[] notificationsSlice = Arrays.copyOfRange(activeNotifications,
                    0, Math.min(MAX_BUFFER_SIZE, activeNotifications.length));
            notificationsBuffer.clear();
            for (StatusBarNotification sbn : notificationsSlice) {
                notificationsBuffer.add(new StoredNotification(sbn));
            }
            Log.d(TAG, String.format("Successfully synchronized %s active notifications", notificationsBuffer.size()));
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        synchronized (notificationsBuffer) {
            if (notificationsBuffer.size() >= MAX_BUFFER_SIZE) {
                Log.d(TAG, String.format("The notifications buffer size has reached its maximum size of %s items. " +
                        "Shrinking it in order to satisfy the constraints.", notificationsBuffer.size()));
                StoredNotification itemToRemove = null;
                ListIterator<StoredNotification> iter = notificationsBuffer.listIterator(notificationsBuffer.size());
                while (iter.hasPrevious()) {
                    StoredNotification currentItem = iter.previous();
                    if (itemToRemove == null) {
                        // Remove the last item in the list if nothing else matches
                        itemToRemove = currentItem;
                    }
                    if (currentItem.isRemoved()) {
                        itemToRemove = currentItem;
                        // Quit the loop as soon as we found the oldest item marked as removed
                        break;
                    }
                }
                if (itemToRemove != null) {
                    notificationsBuffer.remove(itemToRemove);
                }
            }
            try {
                if (notificationsBuffer.isEmpty()) {
                    notificationsBuffer.add(new StoredNotification((sbn)));
                } else {
                    notificationsBuffer.add(0, new StoredNotification(sbn));
                }
                Log.d(TAG, String.format("Successfully stored the newly arrived notification identified by %s",
                        sbn.getId()));
            } catch (Exception e) {
                Log.e(TAG, "Cannot store the newly arrived notification", e);
            }
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        synchronized (notificationsBuffer) {
            for (StoredNotification storedNotification : notificationsBuffer) {
                if (storedNotification.getNotification().getId() == sbn.getId()) {
                    storedNotification.setRemoved(true);
                    Log.d(TAG, String.format("Successfully marked the removed notification identified by %s",
                            sbn.getId()));
                    break;
                }
            }
        }
    }
}
