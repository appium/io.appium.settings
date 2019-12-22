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

package io.appium.settings.receivers;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import io.appium.settings.notifications.StoredNotifications;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class NotificationsReceiver extends BroadcastReceiver
        implements HasAction {
    private static final String TAG = NotificationsReceiver.class.getSimpleName();
    private static final String ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners";
    private static final String ACTION = "io.appium.settings.notifications";

    private boolean isNotificationServiceEnabled(Context context) {
        String pkgName = context.getPackageName();
        final String enabledListeners = Settings.Secure.getString(context.getContentResolver(),
                ENABLED_NOTIFICATION_LISTENERS);
        if (TextUtils.isEmpty(enabledListeners)) {
            return false;
        }
        for (String name : enabledListeners.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(name);
            if (cn != null && TextUtils.equals(pkgName, cn.getPackageName())) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private JSONObject getResponse() {
        try {
            JSONArray notifications = StoredNotifications.getInstance().getNotifications();
            JSONObject result = new JSONObject();
            result.put("statusBarNotifications", notifications);
            return result;
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Responds to broadcast requests like
     * am broadcast -a io.appium.settings.notifications
     * with the list of buffered notifications formatted as JSON
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Getting system notifications");
        String result;
        if (isNotificationServiceEnabled(context)) {
            JSONObject response = getResponse();
            if (response == null) {
                result = "Cannot parse the resulting notifications list. Check the device log for more details.";
                Log.e(TAG, result);
            } else {
                result = response.toString();
            }
        } else {
            result = "Appium Settings helper has no access to the system notifications. " +
                    "The access must be granted manually via 'Notification access' page in device Settings.";
            Log.e(TAG, result);
        }
        setResultCode(Activity.RESULT_OK);
        setResultData(result);
    }

    @Override
    public String getAction() {
        return ACTION;
    }
}
