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

package io.appium.settings;

import android.annotation.SuppressLint;
import android.inputmethodservice.InputMethodService;
import android.text.method.MetaKeyKeyListener;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;

import java.util.HashMap;
import java.util.Map;

public class AppiumIME extends InputMethodService {
    private static final String TAG = AppiumIME.class.getSimpleName();
    private static final int MAX_ACTION_NAME_LENGTH = 20;
    private static final int ACTION_FLAG_KEY = "/".codePointAt(0);

    private static final Map<String, Integer> ACTION_CODES_MAP = new HashMap<>();
    static {
        ACTION_CODES_MAP.put("normal", 0);
        ACTION_CODES_MAP.put("unspecified", 0);
        ACTION_CODES_MAP.put("none", 1);
        ACTION_CODES_MAP.put("go", 2);
        ACTION_CODES_MAP.put("search", 3);
        ACTION_CODES_MAP.put("send", 4);
        ACTION_CODES_MAP.put("next", 5);
        ACTION_CODES_MAP.put("done", 6);
        ACTION_CODES_MAP.put("previous", 7);
    }

    private boolean isEnteringActionName = false;
    private StringBuilder actionName = new StringBuilder();
    private long metaState = 0;

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        Log.i(TAG, "onStartInput");
        super.onStartInput(attribute, restarting);

        if (!restarting) {
            metaState = 0;
            isEnteringActionName = false;
        }
        actionName = new StringBuilder();
    }

    @Override
    public void onFinishInput() {
        Log.i(TAG, "onFinishInput");
        super.onFinishInput();
        actionName = new StringBuilder();
    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public boolean onEvaluateInputViewShown() {
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.i(TAG, String.format("onKeyDown (keyCode='%s', event.keyCode='%s', metaState='%s')",
                keyCode, event.getKeyCode(), event.getMetaState()));
        final int c = getUnicodeChar(keyCode, event);

        if (c == 0) {
            return super.onKeyDown(keyCode, event);
        }

        if (isEnteringActionName) {
            if (c == ACTION_FLAG_KEY || actionName.toString().length() >= MAX_ACTION_NAME_LENGTH) {
                unshift();
            } else {
                appendChar(c);
            }
            return true;
        }

        if (c == ACTION_FLAG_KEY) {
            shift();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        Log.i(TAG, String.format("onKeyUp (keyCode='%s', event.keyCode='%s', metaState='%s')",
                keyCode, event.getKeyCode(), event.getMetaState()));
        metaState = MetaKeyKeyListener.handleKeyUp(metaState, keyCode, event);
        return super.onKeyUp(keyCode, event);
    }

    private void shift() {
        isEnteringActionName = true;
        actionName = new StringBuilder();
    }

    private void unshift() {
        isEnteringActionName = false;
        Integer editorAction;
        try {
            editorAction = Integer.parseInt(actionName.toString());
        } catch (Exception e) {
            editorAction = ACTION_CODES_MAP.get(actionName.toString().toLowerCase());
        }
        if (editorAction == null) {
            Log.i(TAG, String.format("There is no known action code for '%s'. " +
                    "Available action names: %s", actionName.toString(), ACTION_CODES_MAP.keySet()));
            getCurrentInputConnection().commitText(actionName.toString(), 1);
        } else {
            Log.i(TAG, String.format("Matched '%s' to editor action code %s", actionName.toString(),
                    editorAction));
            if (!getCurrentInputConnection().performEditorAction(editorAction)) {
                Log.w(TAG, String.format("Cannot perform editor action %s on the focused element",
                        editorAction));
            }
        }
        actionName = new StringBuilder();
    }

    private int getUnicodeChar(int keyCode, KeyEvent event) {
        metaState = MetaKeyKeyListener.handleKeyDown(metaState, keyCode, event);
        int c = event.getUnicodeChar(event.getMetaState());
        metaState = MetaKeyKeyListener.adjustMetaAfterKeypress(metaState);
        return c;
    }

    private void appendChar(int c) {
        actionName.append((char) c);
    }
}
