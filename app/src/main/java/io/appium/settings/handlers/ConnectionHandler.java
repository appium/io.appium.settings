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

abstract class ConnectionHandler {
    protected Context mContext;
    private String[] permissions;

    ConnectionHandler(Context context, String... permissions) {
        this.mContext = context;
        this.permissions = permissions;
    }

    public abstract boolean enable();

    public abstract boolean disable();

    protected boolean hasPermissions() {
        for (String p : permissions) {
            if (mContext.checkCallingOrSelfPermission(p) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }
}
