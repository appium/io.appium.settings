package io.appium.settings;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.Window;

import static android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP;
import static android.os.PowerManager.FULL_WAKE_LOCK;
import static android.os.PowerManager.PARTIAL_WAKE_LOCK;
import static android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK;
import static android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;
import static android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED;
import static android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;

public class Unlock extends Activity {
    private static final String TAG = Unlock.class.getSimpleName();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Set window flags to unlock screen. This works on most devices by itself.
        Window window = this.getWindow();
        window.addFlags(FLAG_SHOW_WHEN_LOCKED);
        window.addFlags(FLAG_TURN_SCREEN_ON);
        window.addFlags(FLAG_DISMISS_KEYGUARD);

        unlockUsingWakeLock();

        unlockUsingKeyguard();
    }

    @SuppressLint("WakelockTimeout")
    private void unlockUsingWakeLock() {
        // some devices needs waking up screen first before disable keyguard
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (powerManager == null) {
            Log.w(TAG, "Cannot retrieve the power manager instance");
            return;
        }
        final PowerManager.WakeLock[] wakeLocks = {
                powerManager.newWakeLock(SCREEN_BRIGHT_WAKE_LOCK | FULL_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP, getLocalClassName()),
                powerManager.newWakeLock(PARTIAL_WAKE_LOCK, getLocalClassName())
        };
        for (PowerManager.WakeLock wakeLock : wakeLocks) {
            wakeLock.acquire();
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }

    private void unlockUsingKeyguard() {
        // On most other devices, using the KeyguardManager + the permission in
        // AndroidManifest.xml will do the trick
        KeyguardManager mKeyguardManager = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (mKeyguardManager == null) {
            Log.w(TAG, "Cannot retrieve the keyguard manager instance");
            return;
        }
        if (mKeyguardManager.inKeyguardRestrictedInputMode()) {
            mKeyguardManager.newKeyguardLock(getLocalClassName()).disableKeyguard();
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        moveTaskToBack(true);
    }
}
