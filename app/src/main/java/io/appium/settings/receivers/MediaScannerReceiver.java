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
import android.media.MediaScannerConnection;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MediaScannerReceiver extends BroadcastReceiver
        implements HasAction {
    private static final String TAG = MediaScannerReceiver.class.getSimpleName();
    private static final String ACTION = "io.appium.settings.scan_media";
    private static final String PATH = "path";

    private List<String> fetchFiles(File root) {
        if (root.isFile()) {
            return root.canRead()
                    ? Collections.singletonList(root.toString())
                    : Collections.emptyList();
        }
        File[] items = root.listFiles();
        if (items == null) {
            return Collections.emptyList();
        }
        List<String> filePaths = new ArrayList<>();
        for (File item : items) {
            filePaths.addAll(fetchFiles(item));
        }
        return filePaths;
    }

    /**
     * Responds to broadcast requests like
     * am broadcast -a io.appium.settings.scan_media -e path /sdcard/yolo
     * by scanning all files/folders under the given path
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Scanning the requested media");
        if (!intent.hasExtra(PATH)) {
            Log.e(TAG, "No path has been provided");
            setResultCode(Activity.RESULT_CANCELED);
            setResultData("");
            return;
        }
        File item = new File(intent.getStringExtra(PATH));
        if (!item.exists()) {
            Log.e(TAG, String.format("The item at '%s' does not exist", item.toString()));
            setResultCode(Activity.RESULT_CANCELED);
            setResultData("");
            return;
        }
        List<String> filePaths = fetchFiles(item);
        if (filePaths.isEmpty()) {
            Log.i(TAG, String.format("Found no files to scan at '%s'", item.toString()));
        } else {
            MediaScannerConnection.scanFile(context, filePaths.toArray(new String[0]), null, null);
            Log.i(TAG, String.format("Successfully scanned %s file(s) at '%s'",
                    filePaths.size(), item.toString()));
        }
        setResultCode(Activity.RESULT_OK);
        setResultData("");
    }

    @Override
    public String getAction() {
        return ACTION;
    }
}
