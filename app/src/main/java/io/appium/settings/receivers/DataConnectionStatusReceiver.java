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
import android.util.Log;

import java.util.Arrays;
import java.util.List;

import io.appium.settings.handlers.DataConnectionHandler;

public class DataConnectionStatusReceiver extends BroadcastReceiver {
    private static final String TAG = DataConnectionStatusReceiver.class.getSimpleName();

    private static final String COMMAND = "setstatus";
    private static final String COMMAND_ENABLE = "enable";
    private static final String COMMAND_DISABLE = "disable";

    // am broadcast -a io.appium.settings.data_connection --es setstatus [enable|disable]
    @Override
    public void onReceive(Context context, Intent intent) {
        String command = intent.getStringExtra(COMMAND);
        List<String> supportedCommands = Arrays.asList(COMMAND_ENABLE, COMMAND_DISABLE);
        if (!supportedCommands.contains(command)) {
            Log.e(TAG, String.format("Cannot identify the command [%s]", command));
            setResultCode(Activity.RESULT_CANCELED);
            return;
        }
        boolean isSuccessful;
        if (command.equals(COMMAND_ENABLE)) {
            isSuccessful = new DataConnectionHandler(context).enable();
        } else {
            isSuccessful = new DataConnectionHandler(context).disable();
        }
        setResultCode(isSuccessful ? Activity.RESULT_OK : Activity.RESULT_CANCELED);
    }
}
