import _ from 'lodash';
import { LOG_PREFIX } from '../logger.js';
import B from 'bluebird';
import {
  LOCALE_SETTING_ACTION,
  LOCALE_SETTING_RECEIVER,
  LOCALES_LIST_SETTING_ACTION,
  LOCALES_LIST_SETTING_RECEIVER,
} from '../constants.js';

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
async function setDeviceLocaleInternal (language, country, script = null) {
  const params = [
    '-a', LOCALE_SETTING_ACTION,
    '-n', LOCALE_SETTING_RECEIVER,
    '--es', 'lang', language.toLowerCase(),
    '--es', 'country', country.toUpperCase()
  ];
  if (script) {
    params.push('--es', 'script', script);
  }
  await this.checkBroadcast(params, 'set device locale');
}

/**
 * Set the locale name of the device under test.
 *
 * @this {import('../client').SettingsApp}
 * @param {string} language - Language. The language field is case insensitive, but Locale always canonicalizes to lower case.
 * format: [a-zA-Z]{2,8}. e.g. en, ja : https://developer.android.com/reference/java/util/Locale.html
 * @param {string} country - Country. The country (region) field is case insensitive, but Locale always canonicalizes to upper case.
 * format: [a-zA-Z]{2} | [0-9]{3}. e.g. US, JP : https://developer.android.com/reference/java/util/Locale.html
 * @param {string?} [script=null] - Script. The script field is case insensitive but Locale always canonicalizes to title case.
 * format: [a-zA-Z]{4}. e.g. Hans in zh-Hans-CN : https://developer.android.com/reference/java/util/Locale.html
 */
export async function setDeviceLocale (language, country, script = null) {
  if (_.isEmpty(language)) {
    throw new Error('Language name must be provided');
  }
  if (_.isEmpty(country)) {
    throw new Error('Country name must be provided');
  }

  const lcLanguage = language.toLowerCase();
  const ucCountry = country.toUpperCase();
  if (await this.adb.getApiLevel() < 23) {
    const [curLanguageRaw, curCountryRaw] = await B.all([
      this.adb.getDeviceLanguage(),
      this.adb.getDeviceCountry(),
    ]);
    const curLanguage = curLanguageRaw.toLowerCase();
    const curCountry = curCountryRaw.toUpperCase();
    this.log.debug(LOG_PREFIX, `Current language: '${curLanguage}'; requested language: '${lcLanguage}'`);
    this.log.debug(LOG_PREFIX, `Current country: '${curCountry}'; requested country: '${ucCountry}'`);
    if (lcLanguage !== curLanguage || ucCountry !== curCountry) {
      await setDeviceLocaleInternal.bind(this)(lcLanguage, ucCountry);
    }
  } else {
    const curLocale = await this.adb.getDeviceLocale();

    // zh-Hans-CN : zh-CN
    const localeCode = script ? `${lcLanguage}-${script}-${ucCountry}` : `${lcLanguage}-${ucCountry}`;
    this.log.debug(LOG_PREFIX, `Current locale: '${curLocale}'; requested locale: '${localeCode}'`);
    if (localeCode.toLowerCase() !== curLocale.toLowerCase()) {
      await setDeviceLocaleInternal.bind(this)(lcLanguage, ucCountry, script);
    }
  }
}

/**
 * @typedef {Object} SupportedLocale
 * @property {string} language
 * @property {string} country
 * @property {string} [script]
 */

/**
 * Retrieves the list of supported device locales
 *
 * @this {import('../client').SettingsApp}
 * @returns {Promise<SupportedLocale[]>}
 */
export async function listSupportedLocales() {
  const params = [
    '-a', LOCALES_LIST_SETTING_ACTION,
    '-n', LOCALES_LIST_SETTING_RECEIVER,
  ];
  const output = await this.checkBroadcast(params, 'list supported locales');
  const match = /result=-1, data="([^"]+)/.exec(output);
  if (!match) {
    throw new Error(
      'Cannot retrieve the list of supported device locales. Check the logcat output for more details'
    );
  }
  return JSON.parse(Buffer.from(match[1], 'base64').toString()).items;
}
