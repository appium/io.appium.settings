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
import android.content.pm.PackageManager;
import android.util.Log;

public abstract class AbstractSettingHandler {
    private static final String TAG = AbstractSettingHandler.class.getSimpleName();
    protected Context mContext;
    private String[] permissions;

    AbstractSettingHandler(Context context, String... permissions) {
        this.mContext = context;
        this.permissions = permissions;
    }

    public boolean enable() {
        Log.d(TAG, "Enabling " + getSettingDescription());
        if (!hasPermissions()) {
            return false;
        }
        return setState(true);
    }

    public boolean disable() {
        Log.d(TAG, "Disabling " + getSettingDescription());
        if (!hasPermissions()) {
            return false;
        }
        return setState(false);
    }

    protected boolean hasPermissions() {
        for (String p : permissions) {
            if (mContext.checkCallingOrSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                String logMessage = String.format("The permission %s is not set. Cannot change state of %s.",
                    p, getSettingDescription());
                Log.e(TAG, logMessage);
                return false;
            }
        }
        return true;
    }

    protected abstract boolean setState(boolean state);

    protected abstract String getSettingDescription();
}
