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
import android.os.IBinder;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.Arrays;

public class AnimationSettingHandler extends AbstractSettingHandler {
    private static final String TAG = "APPIUM SETTINGS (ANIMATION)";
    private static final String ANIMATION_PERMISSION = "android.permission.SET_ANIMATION_SCALE";

    public AnimationSettingHandler(Context context) {
        super(context, ANIMATION_PERMISSION);
    }

    @Override
    protected boolean setState(boolean state) {
        try {
            Class<?> serviceManagerClass = Class.forName("android.os.ServiceManager");
            Method getServiceMethod = serviceManagerClass.getDeclaredMethod("getService", String.class);
            IBinder windowManagerBinder = (IBinder) getServiceMethod.invoke(null, "window");

            Class<?> windowManagerClass = Class.forName("android.view.IWindowManager");
            Method setAnimationScales = windowManagerClass.getDeclaredMethod("setAnimationScales",
                    float[].class);
            Method getAnimationScales = windowManagerClass.getDeclaredMethod("getAnimationScales");

            Class<?> windowManagerStubClass = Class.forName("android.view.IWindowManager$Stub");
            Method asInterfaceMethod = windowManagerStubClass.getDeclaredMethod("asInterface", IBinder.class);
            Object windowManagerObj = asInterfaceMethod.invoke(null, windowManagerBinder);

            float[] currentScales = (float[]) getAnimationScales.invoke(windowManagerObj);
            Arrays.fill(currentScales, (state == true) ? 1.0f : 0.0f);
            setAnimationScales.invoke(windowManagerObj, currentScales);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "Cannot set animation scale.", e);
        }
        return false;
    }

    @Override
    protected String getSettingDescription() {
        return "animation";
    }
}
