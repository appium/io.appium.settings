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

import static org.apache.commons.lang3.StringUtils.isBlank;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import org.apache.commons.lang3.LocaleUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public class LocalesReader extends BroadcastReceiver implements HasAction {
    private static final String TAG = LocalesReader.class.getSimpleName();
    private static final String ACTION = "io.appium.settings.list_locales";

    private JSONObject listLocales() throws JSONException {
        JSONArray items = new JSONArray();
        for (Locale locale : LocaleUtils.availableLocaleList()) {
            if (isBlank(locale.getLanguage()) || isBlank(locale.getCountry())) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("language", locale.getLanguage());
            item.put("country", locale.getCountry());
            if (!isBlank(locale.getScript())) {
                item.put("script", locale.getScript());
            }
            items.put(item);
        }
        JSONObject result = new JSONObject();
        result.put("items", items);
        return result;
    }

    /**
     * Responds to broadcast requests like
     * am broadcast -a io.appium.settings.sms.list_locales
     * with the list of supported locales messages formatted as base64-encoded JSON
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Getting the list of supported device locales");
        try {
            String b64Output = Base64.encodeToString(
                    listLocales().toString().getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP
            );
            setResultCode(Activity.RESULT_OK);
            setResultData(b64Output);
        } catch (Exception e) {
            String result = "Appium Settings helper is unable to list supported device locales";
            Log.e(TAG, result, e);
            setResultCode(Activity.RESULT_CANCELED);
            setResultData(result);
        }
    }

    @Override
    public String getAction() {
        return ACTION;
    }
}
