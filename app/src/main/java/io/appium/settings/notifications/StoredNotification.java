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

package io.appium.settings.notifications;

import android.os.Build;
import android.os.Bundle;
import android.service.notification.StatusBarNotification;
import org.json.JSONException;
import org.json.JSONObject;

import static io.appium.settings.helpers.Utils.formatJsonNull;
import static io.appium.settings.helpers.Utils.toNullableString;

public class StoredNotification {
    private final StatusBarNotification sbn;
    private boolean isRemoved = false;

    public StoredNotification(StatusBarNotification sbn) {
        this.sbn = sbn;
    }

    public StatusBarNotification getNotification() {
        return sbn;
    }

    private void storeCharSequenceProperty(JSONObject dst, String name, String propertyName,
                                           Bundle extras) throws JSONException {
        CharSequence value = extras.getCharSequence(propertyName);
        dst.put(name, formatJsonNull(toNullableString(value)));
    }

    public JSONObject toJson() throws JSONException {
        JSONObject result = new JSONObject();
        result.put("packageName", formatJsonNull(sbn.getPackageName()));
        result.put("isClearable", sbn.isClearable());
        result.put("isOngoing", sbn.isOngoing());
        result.put("id", sbn.getId());
        result.put("tag", formatJsonNull(sbn.getTag()));
        result.put("postTime", sbn.getPostTime());
        result.put("isRemoved", isRemoved());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            JSONObject notification = new JSONObject();
            Bundle extras = sbn.getNotification().extras;
            storeCharSequenceProperty(notification, "title", "android.title", extras);
            storeCharSequenceProperty(notification, "bigTitle", "android.title.big", extras);
            storeCharSequenceProperty(notification, "text", "android.text", extras);
            storeCharSequenceProperty(notification, "bigText", "android.bigText", extras);
            storeCharSequenceProperty(notification, "tickerText", "android.tickerText", extras);
            storeCharSequenceProperty(notification, "subText", "android.subText", extras);
            storeCharSequenceProperty(notification, "infoText", "android.infoText", extras);
            storeCharSequenceProperty(notification, "template", "android.template", extras);
            result.put("notification", notification);
        } else {
            result.put("notification", JSONObject.NULL);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            result.put("isGroup", sbn.isGroup());
        } else {
            result.put("isGroup", false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            result.put("userHandle", sbn.getUser().hashCode());
            result.put("groupKey", formatJsonNull(sbn.getGroupKey()));
        } else {
            //noinspection deprecation
            result.put("userHandle", sbn.getUserId());
            result.put("groupKey", JSONObject.NULL);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            result.put("overrideGroupKey", formatJsonNull(sbn.getOverrideGroupKey()));
        } else {
            result.put("overrideGroupKey", JSONObject.NULL);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            result.put("key", formatJsonNull(sbn.getKey()));
        } else {
            result.put("key", JSONObject.NULL);
        }
        return result;
    }

    public boolean isRemoved() {
        return isRemoved;
    }

    public void setRemoved(boolean removed) {
        this.isRemoved = removed;
    }
}
