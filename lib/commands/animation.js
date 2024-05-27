import { ANIMATION_SETTING_ACTION, ANIMATION_SETTING_RECEIVER } from '../constants.js';

/**
 * Change the state of animation on the device under test via adb settings command (for API level 26+) or settings app (for API level 25 and lower).
 * Animation on the device is controlled by the following global properties:
 * [ANIMATOR_DURATION_SCALE]{@link https://developer.android.com/reference/android/provider/Settings.Global.html#ANIMATOR_DURATION_SCALE},
 * [TRANSITION_ANIMATION_SCALE]{@link https://developer.android.com/reference/android/provider/Settings.Global.html#TRANSITION_ANIMATION_SCALE},
 * [WINDOW_ANIMATION_SCALE]{@link https://developer.android.com/reference/android/provider/Settings.Global.html#WINDOW_ANIMATION_SCALE}.
 * This method sets all this properties to 0.0 to disable (1.0 to enable) animation.
 *
 * Turning off animation might be useful to improve stability
 * and reduce tests execution time.
 *
 * @this {import('../client').SettingsApp}
 * @param {boolean} on - True to enable and false to disable it.
 */
export async function setAnimationState (on) {
  if (await this.adb.getApiLevel() >= 26) {
    await this.adb.setAnimationScale(Number(on));
    return;
  };
  // TODO: Please delete AnimationSettingHandler.java implementation also to avoid non-sdk https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces
  // error in settings app for Android 9 and over when we drop api level 25 and lower.
  const statusToSet = on ? 'enable' : 'disable';
  await this.checkBroadcast([
    '-a', ANIMATION_SETTING_ACTION,
    '-n', ANIMATION_SETTING_RECEIVER,
    '--es', 'setstatus', statusToSet
  ], `${statusToSet} animation`);
}
