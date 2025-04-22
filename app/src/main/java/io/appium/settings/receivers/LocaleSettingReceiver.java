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
import android.util.Log;

import org.apache.commons.lang3.LocaleUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.appium.settings.handlers.LocaleSettingHandler;

public class LocaleSettingReceiver extends BroadcastReceiver implements HasAction {
    private static final String TAG = LocaleSettingReceiver.class.getSimpleName();

    private static final String LANG = "lang";
    private static final String COUNTRY = "country";
    private static final String SKIP_LOCALE_CHECK = "skip_locale_check";
    private static final String SCRIPT = "script";

    private static final String ACTION = "io.appium.settings.locale";

    // am broadcast -a io.appium.settings.locale --es lang ja --es country JP
    @Override
    public void onReceive(Context context, Intent intent) {
        String language = intent.getStringExtra(LANG);
        String country = intent.getStringExtra(COUNTRY);
        String skipLocaleCheck = intent.getStringExtra(SKIP_LOCALE_CHECK);
        if (language == null || country == null) {
            Log.w(TAG, "It is required to provide both language and country, for example: " +
                    "am broadcast -a io.appium.settings.locale --es lang ja --es country JP");
            Log.i(TAG, "Set en-US by default.");
            language = "en";
            country= "US";
        }
        // Expect https://developer.android.com/reference/java/util/Locale.html#Locale(java.lang.String,%20java.lang.String) format.
        Locale locale = new Locale(language, country);
        Log.i(TAG, String.format("Obtained locale: %s", locale));
        String script = intent.getStringExtra(SCRIPT);
        if (script != null) {
            locale = new Locale.Builder().setLocale(locale).setScript(script).build();
        }
        if (skipLocaleCheck != null) {
            Log.i(TAG, "'skip_locale_check' value is provided, will not check locale availability");
        }
        else if (!LocaleUtils.isAvailableLocale(locale)) {
            List<Locale> approximateMatchesLc = matchLocales(language, country);
            if (!approximateMatchesLc.isEmpty() && isBlank(script)) {
                Log.i(TAG, String.format(
                        "The locale %s is not known. Selecting the closest known one %s instead",
                        locale, approximateMatchesLc.get(0))
                );
                locale = approximateMatchesLc.get(0);
            } else {
                List<Locale> approximateMatchesL = matchLocales(language);
                if (approximateMatchesL.isEmpty()) {
                    Log.e(TAG, String.format(
                            "The locale %s is not known. Only the following locales are available: %s",
                            locale, LocaleUtils.availableLocaleList())
                    );
                } else {
                    Log.e(TAG, String.format(
                            "The locale %s is not known. " +
                                    "The following locales are available for the %s language: %s" +
                                    "The following locales are available altogether: %s",
                            locale, language, approximateMatchesL, LocaleUtils.availableLocaleList())
                    );
                }
                setResultCode(Activity.RESULT_CANCELED);
                setResultData(String.format("The locale %s is not known", locale));
                return;
            }
        }

        try {
            new LocaleSettingHandler(context).setLocale(locale);
            Log.i(TAG, String.format("Set locale: %s", locale));
            setResultCode(Activity.RESULT_OK);
            setResultData(locale.toString());
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.e(TAG, String.format("Cannot set the system locale to %s", locale), e);
            setResultCode(Activity.RESULT_CANCELED);
            setResultData(String.format("%s: %s", e.getClass().getName(), e.getMessage()));
        }
    }

    @Override
    public String getAction() {
        return ACTION;
    }

    private static List<Locale> matchLocales(String language) {
        List<Locale> matches = new ArrayList<>();
        for (Locale locale: LocaleUtils.availableLocaleList()) {
            if (locale.getLanguage().equalsIgnoreCase(language)) {
                matches.add(locale);
            }
        }
        return matches;
    }

    private static List<Locale> matchLocales(String language, String country) {
        List<Locale> matches = new ArrayList<>();
        for (Locale locale: LocaleUtils.availableLocaleList()) {
            if (locale.getLanguage().equalsIgnoreCase(language)
                    && locale.getCountry().equalsIgnoreCase(country)) {
                matches.add(locale);
            }
        }
        return matches;
    }
}
