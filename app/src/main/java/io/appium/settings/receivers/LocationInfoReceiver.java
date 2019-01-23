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
import android.location.Location;
import android.util.Log;

import java.util.Locale;

import io.appium.settings.LocationTracker;

public class LocationInfoReceiver extends BroadcastReceiver
    implements HasAction {
    private static final String TAG = LocationInfoReceiver.class.getSimpleName();

    private static final String ACTION = "io.appium.settings.location";

    /**
     * Responds to broadcast requests like
     * am broadcast -a io.appium.settings.location
     * with location properties separated by a single space
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Getting current location");
        final Location location = LocationTracker.getInstance().getLocation(context);
        if (location != null) {
            setResultCode(Activity.RESULT_OK);
            // Decimal separator is a dot
            setResultData(String.format(Locale.US, "%.5f %.5f %.5f",
                    location.getLatitude(), location.getLongitude(), location.getAltitude()));
        } else {
            setResultCode(Activity.RESULT_CANCELED);
            setResultData("");
        }
    }

    @Override
    public String getAction() {
        return ACTION;
    }
}
