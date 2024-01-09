import { LOCALE_SETTING_ACTION, LOCALE_SETTING_RECEIVER } from '../constants.js';

/**
 * Change the locale on the device under test. Don't need to reboot the device after changing the locale.
 * This method sets an arbitrary locale following:
 *   https://developer.android.com/reference/java/util/Locale.html
 *   https://developer.android.com/reference/java/util/Locale.html#Locale(java.lang.String,%20java.lang.String)
 *
 * @this {import('../client').SettingsApp}
 * @param {string} language - Language. e.g. en, ja
 * @param {string} country - Country. e.g. US, JP
 * @param {string?} [script=null] - Script. e.g. Hans in `zh-Hans-CN`
 */
export async function setDeviceSysLocale (language, country, script = null) {
  const params = [
    'am', 'broadcast',
    '-a', LOCALE_SETTING_ACTION,
    '-n', LOCALE_SETTING_RECEIVER,
    '--es', 'lang', language.toLowerCase(),
    '--es', 'country', country.toUpperCase()
  ];

  if (script) {
    params.push('--es', 'script', script);
  }

  await this.adb.shell(params);
};
