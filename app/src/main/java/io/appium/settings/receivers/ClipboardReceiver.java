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
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.util.Base64;
import android.util.Log;

import java.io.UnsupportedEncodingException;

public class ClipboardReceiver extends BroadcastReceiver implements HasAction {
    private static final String TAG = ClipboardReceiver.class.getSimpleName();

    private static final String ACTION = "io.appium.settings.clipboard.get";

    private static String charSequenceToString(CharSequence input) {
        return input == null ? "" : input.toString();
    }

    private String getClipboardText(Context context) {
        final ClipboardManager cm = (ClipboardManager) context
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null) {
            Log.e(TAG, "Cannot get an instance of ClipboardManager");
            return null;
        }
        if (!cm.hasPrimaryClip()) {
            return "";
        }
        final ClipData cd = cm.getPrimaryClip();
        if (cd == null || cd.getItemCount() == 0) {
            return "";
        }
        return charSequenceToString(cd.getItemAt(0).coerceToText(context));
    }

    /**
     * Responds to broadcast requests like
     * am broadcast -a io.appium.settings.clipboard.get
     * with the base64-encoded current clipboard text content
     * or an empty string if no content has been received
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Getting current clipboard content");
        final String clipboardContent = getClipboardText(context);
        if (clipboardContent == null) {
            setResultCode(Activity.RESULT_CANCELED);
            setResultData("");
            return;
        }

        try {
            // TODO: Use StandardCharsets.UTF_8 after the minimum supported API version
            // TODO: is bumped above 18
            //noinspection CharsetObjectCanBeUsed
            String clipboardContentBase64 = Base64.encodeToString(
                    clipboardContent.getBytes("UTF-8"), Base64.DEFAULT);
            setResultCode(Activity.RESULT_OK);
            setResultData(clipboardContentBase64);
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            setResultCode(Activity.RESULT_CANCELED);
            setResultData("");
        }
    }

    @Override
    public String getAction() {
        return ACTION;
    }
}
