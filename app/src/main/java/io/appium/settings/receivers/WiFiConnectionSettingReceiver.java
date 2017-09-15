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

import android.content.Context;

import io.appium.settings.handlers.WiFiConnectionSettingHandler;

/**
* am broadcast -a io.appium.settings.wifi --es setstatus [enable|disable]
*/
public class WiFiConnectionSettingReceiver extends AbstractSettingReceiver {

    @Override
    protected WiFiConnectionSettingHandler getHandler(Context context) {
        return new WiFiConnectionSettingHandler(context);
    }
}
