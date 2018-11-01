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

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.ConnectivityManager;
import android.os.Build;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

public class DataConnectionSettingHandler extends AbstractSettingHandler {
    private static final String TAG = "APPIUM SETTINGS (DATA)";
    private static final String NETWORK_PERMISSION = "android.permission.CHANGE_NETWORK_STATE";

    public DataConnectionSettingHandler(Context context) {
        super(context, NETWORK_PERMISSION);
    }

    private static boolean executeCommandViaSu(String command) {
        final String[] suPaths = new String[]{"su", "/system/xbin/su", "/system/bin/su"};
        for (String su : suPaths) {
            try {
                Runtime.getRuntime().exec(new String[]{su, "-c", command});
                return true;
            } catch (IOException e) {
                // ignore
            }
        }
        Log.e(TAG, String.format("'su' binary is not available at %s. Is the phone rooted?",
                Arrays.toString(suPaths)));
        return false;
    }

    private static String getTransactionCode(Context context) throws Exception {
        final TelephonyManager mTelephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        final Class<?> mTelephonyClass = Class.forName(mTelephonyManager.getClass().getName());
        final Method mTelephonyMethod = mTelephonyClass.getDeclaredMethod("getITelephony");
        mTelephonyMethod.setAccessible(true);
        final Object mTelephonyStub = mTelephonyMethod.invoke(mTelephonyManager);
        final Class<?> mTelephonyStubClass = Class.forName(mTelephonyStub.getClass().getName());
        final Class<?> mClass = mTelephonyStubClass.getDeclaringClass();
        final Field field = mClass.getDeclaredField("TRANSACTION_setDataEnabled");
        field.setAccessible(true);
        return String.valueOf(field.getInt(null));
    }

    private static void setMobileNetworkFromLollipop(Context context, boolean isEnabled)
            throws Exception {
        final int state = isEnabled ? 1 : 0;
        final String transactionCode = getTransactionCode(context);
        if (transactionCode.length() == 0) {
            throw new IllegalStateException("The transaction code should not be empty");
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            final SubscriptionManager mSubscriptionManager = (SubscriptionManager) context
                    .getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE);
            for (int i = 0; i < mSubscriptionManager.getActiveSubscriptionInfoCountMax(); i++) {
                if (transactionCode.length() > 0) {
                    @SuppressLint("MissingPermission") final int subscriptionId = mSubscriptionManager
                            .getActiveSubscriptionInfoList().get(i).getSubscriptionId();
                    final String command = String.format("service call phone %s i32 %s i32 %s",
                            transactionCode, subscriptionId, state);
                    executeCommandViaSu(command);
                }
            }
        } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
            if (transactionCode.length() > 0) {
                final String command = String.format("service call phone %s i32 %s",
                        transactionCode, state);
                executeCommandViaSu(command);
            }
        }
    }

    private static void setMobileNetworkFromGingerbreadToLollipop(
            Context mContext, boolean isEnabled) throws Exception {
        final ConnectivityManager conman =
                (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        final Class<?> conmanClass = Class.forName(conman.getClass().getName());
        final Field iConnectivityManagerField = conmanClass.getDeclaredField("mService");
        iConnectivityManagerField.setAccessible(true);
        final Object iConnectivityManager = iConnectivityManagerField.get(conman);
        final Class<?> iConnectivityManagerClass =
                Class.forName(iConnectivityManager.getClass().getName());
        final Method setMobileDataEnabledMethod =
                iConnectivityManagerClass.getDeclaredMethod("setMobileDataEnabled", Boolean.TYPE);
        setMobileDataEnabledMethod.setAccessible(true);
        setMobileDataEnabledMethod.invoke(iConnectivityManager, isEnabled);
    }

    @Override
    protected boolean setState(boolean on) {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                setMobileNetworkFromGingerbreadToLollipop(mContext, on);
            } else {
                // http://stackoverflow.com/questions/26539445/the-setmobiledataenabled-method-is-no-longer-callable-as-of-android-l-and-later
                setMobileNetworkFromLollipop(mContext, on);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, String.format(
                    "Error turning %s mobile data: %s", on ? "on" : "off", e.getMessage()));
            return false;
        }
    }

    @Override
    protected String getSettingDescription() {
        return "mobile data";
    }
}
