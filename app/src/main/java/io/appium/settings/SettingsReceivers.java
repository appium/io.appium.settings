package io.appium.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.appium.settings.receivers.AnimationSettingReceiver;
import io.appium.settings.receivers.BluetoothConnectionSettingReceiver;
import io.appium.settings.receivers.ClipboardReceiver;
import io.appium.settings.receivers.HasAction;
import io.appium.settings.receivers.LocaleSettingReceiver;
import io.appium.settings.receivers.LocalesReader;
import io.appium.settings.receivers.LocationInfoReceiver;
import io.appium.settings.receivers.MediaScannerReceiver;
import io.appium.settings.receivers.NotificationsReceiver;
import io.appium.settings.receivers.SmsReader;
import io.appium.settings.receivers.UnpairBluetoothDevicesReceiver;
import io.appium.settings.receivers.WiFiConnectionSettingReceiver;

public class SettingsReceivers {
    private static final String TAG = "APPIUM SERVICE";

    static List<BroadcastReceiver> register(Context applicationContext) {
        List<Class<? extends BroadcastReceiver>> receiverClasses = Arrays.asList(
                WiFiConnectionSettingReceiver.class,
                AnimationSettingReceiver.class,
                LocaleSettingReceiver.class,
                LocalesReader.class,
                LocationInfoReceiver.class,
                ClipboardReceiver.class,
                BluetoothConnectionSettingReceiver.class,
                UnpairBluetoothDevicesReceiver.class,
                NotificationsReceiver.class,
                SmsReader.class,
                MediaScannerReceiver.class
        );

        List<BroadcastReceiver> settingsReceivers = new ArrayList<>();
        for (Class<? extends BroadcastReceiver> receiverClass: receiverClasses) {
            try {
                final BroadcastReceiver receiver = receiverClass.newInstance();
                IntentFilter filter = new IntentFilter(((HasAction) receiver).getAction());
                applicationContext.registerReceiver(receiver, filter);
                Log.d(TAG, "Register " + receiver);
                settingsReceivers.add(receiver);
            } catch (IllegalAccessException | InstantiationException e) {
                Log.e(TAG, "Failed to register the receiver: " + receiverClass, e);
            }
        }
        return settingsReceivers;
    }

    static void unregister(Context applicationContext, List<BroadcastReceiver> settingsReceivers) {
        if (settingsReceivers.isEmpty()) {
            Log.d(TAG, "Nothing has registered");
            return;
        }

        for (BroadcastReceiver receiver: settingsReceivers) {
            Log.d(TAG, "Unregister " + receiver);
            try {
                applicationContext.unregisterReceiver(receiver);
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Got an error in unregisterReceiver: " + receiver, e);
            }
        }
    }
}
