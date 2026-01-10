import _ from 'lodash';
import { APPIUM_IME, UNICODE_IME } from '../constants';
import { imap } from './utf7';
import { LOG_PREFIX } from '../logger';
import type { SettingsApp } from '../client';

/**
 * Performs the given editor action on the focused input field.
 * This method requires Appium Settings helper to be installed on the device.
 * No exception is thrown if there was a failure while performing the action.
 * You must investigate the logcat output if something did not work as expected.
 *
 * @param action - Either action code or name. The following action names are supported:
 *                 `normal, unspecified, none, go, search, send, next, done, previous`
 */
export async function performEditorAction(this: SettingsApp, action: string | number): Promise<void> {
  this.log.debug(LOG_PREFIX, `Performing editor action: ${action}`);
  await this.adb.runInImeContext(APPIUM_IME,
    async () => await this.adb.shell(['input', 'text', `/${action}/`]));
}

/**
 * Types the given Unicode string.
 * It is expected that the focus is already put
 * to the destination input field before this method is called.
 *
 * @param text The string to type
 * @returns `true` if the input text has been successfully sent to adb
 */
export async function typeUnicode(this: SettingsApp, text: string | null | undefined): Promise<boolean> {
  if (_.isNil(text)) {
    return false;
  }

  const textStr = `${text}`;
  this.log.debug(LOG_PREFIX, `Typing ${textStr.length} character${textStr.length === 1 ? '' : 's'}`);
  if (!textStr) {
    return false;
  }
  await this.adb.runInImeContext(
    UNICODE_IME, async () => await this.adb.inputText(imap.encode(textStr))
  );
  return true;
}
