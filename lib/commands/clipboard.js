import _ from 'lodash';
import { LOG_PREFIX } from '../logger';
import {
  CLIPBOARD_RECEIVER,
  CLIPBOARD_RETRIEVAL_ACTION,
  APPIUM_IME
} from '../constants';

/**
 * Retrieves the text content of the device's clipboard.
 * The method works for Android below and above 29.
 * It temorarily enforces the IME setting in order to workaround
 * security limitations if needed.
 * This method only works if Appium Settings v. 2.15+ is installed
 * on the device under test
 *
 * @this {import('../client').SettingsApp}
 * @returns {Promise<string>} The actual content of the main clipboard as
 * base64-encoded string or an empty string if the clipboard is empty
 * @throws {Error} If there was a problem while getting the
 * clipboard contant
 */
export async function getClipboard () {
  this.log.debug(LOG_PREFIX, 'Getting the clipboard content');
  await this.requireRunning({shouldRestoreCurrentApp: true});
  const retrieveClipboard = async () => await this.checkBroadcast([
    '-n', CLIPBOARD_RECEIVER,
    '-a', CLIPBOARD_RETRIEVAL_ACTION,
  ], 'retrieve clipboard', false);
  let output;
  try {
    output = (await this.adb.getApiLevel() >= 29)
      ? (await this.adb.runInImeContext(APPIUM_IME, retrieveClipboard))
      : (await retrieveClipboard());
  } catch (err) {
    throw new Error(`Cannot retrieve the current clipboard content from the device. ` +
      `Make sure the Appium Settings application is up to date. ` +
      `Original error: ${err.message}`);
  }

  const match = /data="([^"]*)"/.exec(output);
  if (!match) {
    throw new Error(`Cannot parse the actual cliboard content from the command output: ${output}`);
  }
  return _.trim(match[1]);
}
