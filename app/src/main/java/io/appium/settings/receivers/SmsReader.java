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
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import static io.appium.settings.helpers.Utils.formatJsonNull;

public class SmsReader extends BroadcastReceiver implements HasAction {
    private static final String TAG = SmsReader.class.getSimpleName();
    private static final Uri INCOMING_SMS = Uri.parse("content://sms/inbox");
    private static final String ACTION = "io.appium.settings.sms.read";
    private static final int MAX_ITEMS = 100;
    private static final String MAX_ITEMS_SETTING_NAME = "max";
    private static final String[][] SMS_INFO_MAPPING = new String[][]{
            {"_id", "id"},
            {"address", "address"},
            {"person", "person"},
            {"date", "date"},
            {"read", "read"},
            {"status", "status"},
            {"type", "type"},
            {"subject", "subject"},
            {"body", "body"},
            {"service_center", "serviceCenter"}
    };

    private JSONObject listSms(Context context, int maxCount) throws JSONException {
        Cursor cursor = context.getContentResolver().query(INCOMING_SMS,
                null, null, null, "date desc");
        //noinspection TryFinallyCanBeTryWithResources
        try {
            JSONArray items = new JSONArray();
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject item = new JSONObject();
                    for (String[] entry : SMS_INFO_MAPPING) {
                        int columnIndex = cursor.getColumnIndex(entry[0]);
                        if (columnIndex >= 0) {
                            item.put(entry[1], formatJsonNull(cursor.getString(columnIndex)));
                        }
                    }
                    items.put(item);
                } while (cursor.moveToNext() && items.length() < maxCount);
            }
            JSONObject result = new JSONObject();
            result.put("items", items);
            result.put("total", cursor == null ? 0 : cursor.getCount());
            return result;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * Responds to broadcast requests like
     * am broadcast -a io.appium.settings.sms.read --es max 10
     * with the list of the recent SMS messages formatted as JSON
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        int maxItems = MAX_ITEMS;
        if (intent.hasExtra(MAX_ITEMS_SETTING_NAME)) {
            try {
                maxItems = Integer.parseInt(intent.getStringExtra(MAX_ITEMS_SETTING_NAME));
            } catch (NumberFormatException e) {
                e.printStackTrace();
            }
        }
        Log.d(TAG, String.format("Getting the recent %s SMS messages", maxItems));
        String output;
        try {
            output = listSms(context, maxItems).toString();
        } catch (Exception e) {
            e.printStackTrace();
            output = "Appium Settings helper is unable to list SMS messages. " +
                    "Check the logcat output for more details.";
            Log.e(TAG, output);
        }
        setResultCode(Activity.RESULT_OK);
        setResultData(output);
    }

    @Override
    public String getAction() {
        return ACTION;
    }
}
