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
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.Set;

public class UnpairBluetoothDevicesReceiver extends BroadcastReceiver implements HasAction {
    private static final String TAG = UnpairBluetoothDevicesReceiver.class.getSimpleName();

    private static final String ACTION = "io.appium.settings.unpair_bluetooth";
    private boolean isFailed = false;

    /**
     * am broadcast -a io.appium.settings.unpair_bluetooth
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Unpairing bluetooth devices");
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            String message = "No Bluetooth adapter found";
            Log.e(TAG, message);
            setResultCode(Activity.RESULT_CANCELED);
            setResultData(message);
            return;
        }
        Set<BluetoothDevice> bondedBluetoothDevices = bluetoothAdapter.getBondedDevices();
        if (bondedBluetoothDevices.size() > 0) {
            unpairBluetoothDevices(bondedBluetoothDevices);
            setResultCode(!isFailed ? Activity.RESULT_OK : Activity.RESULT_CANCELED);
        } else {
            String message = "No paired devices found";
            Log.d(TAG, message);
            setResultCode(Activity.RESULT_OK);
            setResultData(message);

        }
    }

    private void unpairBluetoothDevices(Set<BluetoothDevice> pairedDevices) {
        for (BluetoothDevice device : pairedDevices) {
            try {
                device.getClass().getMethod("removeBond", null).invoke(device, null);
            } catch (Exception e) {
                isFailed = true;
                Log.e("UnpairBluetooth", String.format("Unpairing bluetooth device %s failed. %s", device, e.getMessage()));
            }
        }
    }

    @Override
    public String getAction() {
        return ACTION;
    }
}
