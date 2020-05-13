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

package io.appium.settings.handlers;

import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

public class LocaleSettingHandler extends AbstractSettingHandler {
    private static final String TAG = "APPIUM SETTINGS(LOCALE)";
    private static final String CHANGE_CONFIGURATION = "android.permission.CHANGE_CONFIGURATION";

    public LocaleSettingHandler(Context context) {
        super(context, CHANGE_CONFIGURATION);
    }

    public void setLocale(Locale locale) {
        try {
            if(hasPermissions()) {
                setLocaleWith(locale);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to set locale", e);
        }
    }

    private void setLocaleWith(Locale locale) throws
            ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException, NoSuchFieldException {
        Class<?> activityManagerNativeClass = Class.forName("android.app.ActivityManagerNative");

        Method methodGetDefault = activityManagerNativeClass.getMethod("getDefault");
        methodGetDefault.setAccessible(true);
        Object amn = methodGetDefault.invoke(activityManagerNativeClass);

        // Build.VERSION_CODES.O
        if (Build.VERSION.SDK_INT >= 26) {
            // getConfiguration moved from ActivityManagerNative to ActivityManagerProxy
            activityManagerNativeClass = Class.forName(amn.getClass().getName());
        }

        Method methodGetConfiguration = activityManagerNativeClass.getMethod("getConfiguration");
        methodGetConfiguration.setAccessible(true);
        Configuration config = (Configuration) methodGetConfiguration.invoke(amn);

        Class<?> configClass = config.getClass();
        Field f = configClass.getField("userSetLocale");
        f.setBoolean(config, true);

        config.locale = locale;
        config.setLayoutDirection(locale);

        Method methodUpdateConfiguration = activityManagerNativeClass.getMethod("updateConfiguration", Configuration.class);
        methodUpdateConfiguration.setAccessible(true);
        methodUpdateConfiguration.invoke(amn, config);
    }

    @Override
    protected boolean setState(boolean state) {
        return false;
    }

    @Override
    protected String getSettingDescription() {
        return "locale";
    }
}
