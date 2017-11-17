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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Locale;

import io.appium.settings.handlers.LocaleSettingHandler;

public class LocaleSettingReceiver extends BroadcastReceiver {
    private static final String TAG = LocaleSettingReceiver.class.getSimpleName();

    private static final String LANG = "lang";
    private static final String COUNTRY = "country";

    // am broadcast -a io.appium.settings.locale --es lang ja --es country JP
    @Override
    public void onReceive(Context context, Intent intent) {
        if(!hasExtraLocale(intent)) {
            Log.e(TAG, "Don't forget to set lang and country like: am broadcast -a io.appium.settings.locale --es lang ja --es country JP");
            Log.e(TAG, "Set en-US by default.");

            intent.putExtra(LANG, "en");
            intent.putExtra(COUNTRY, "US");
        }

        String language = intent.getStringExtra(LANG);
        String country = intent.getStringExtra(COUNTRY);

        // Expect https://developer.android.com/reference/java/util/Locale.html#Locale(java.lang.String,%20java.lang.String) format.
        // If we'd like to extend the other format like `Locale(String language, String country, String variant)`,
        // you can implement it around here.
        Locale locale = new Locale(language, country);

        LocaleSettingHandler localeSettingHandler = new LocaleSettingHandler(context);

        localeSettingHandler.setLocale(locale);
    }

    private boolean hasExtraLocale(Intent intent) {
        return intent.hasExtra(LANG) && intent.hasExtra(COUNTRY);
    }
}
