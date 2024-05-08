import _ from 'lodash';
import { APPIUM_IME, UNICODE_IME } from '../constants.js';
import { imap } from './utf7';
import { LOG_PREFIX } from '../logger.js';

/**
 * Performs the given editor action on the focused input field.
 * This method requires Appium Settings helper to be installed on the device.
 * No exception is thrown if there was a failure while performing the action.
 * You must investigate the logcat output if something did not work as expected.
 *
 * @this {import('../client').SettingsApp}
 * @param {string|number} action - Either action code or name. The following action
 *                                 names are supported: `normal, unspecified, none,
 *                                 go, search, send, next, done, previous`
 */
export async function performEditorAction (action) {
  this.log.debug(LOG_PREFIX, `Performing editor action: ${action}`);
  await this.adb.runInImeContext(APPIUM_IME,
    async () => await this.adb.shell(['input', 'text', `/${action}/`]));
}

/**
 * Types the given Unicode string.
 * It is expected that the focus is already put
 * to the destination input field before this method is called.
 *
 * @this {import('../client').SettingsApp}
 * @param {string} text The string to type
 * @returns {Promise<boolean>} `true` if the input text has been successfully sent to adb
 */
export async function typeUnicode (text) {
  if (_.isNil(text)) {
    return false;
  }

  text = `${text}`;
  this.log.debug(LOG_PREFIX, `Typing ${text.length} character${text.length === 1 ? '' : 's'}`);
  if (!text) {
    return false;
  }
  await this.adb.runInImeContext(
    UNICODE_IME, async () => await this.adb.inputText(imap.encode(text))
  );
  return true;
}
