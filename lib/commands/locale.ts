import _ from 'lodash';
import { LOG_PREFIX } from '../logger';
import {
  LOCALE_SETTING_ACTION,
  LOCALE_SETTING_RECEIVER,
  LOCALES_LIST_SETTING_ACTION,
  LOCALES_LIST_SETTING_RECEIVER,
} from '../constants';
import type { SettingsApp } from '../client';
import type { SupportedLocale } from './types';

/**
 * Set the locale name of the device under test.
 * Don't need to reboot the device after changing the locale.
 * This method sets an arbitrary locale following:
 *   https://developer.android.com/reference/java/util/Locale.html
 *   https://developer.android.com/reference/java/util/Locale.html#Locale(java.lang.String,%20java.lang.String)
 *
 * @param language - Language. The language field is case insensitive, but Locale always canonicalizes to lower case.
 *                  format: [a-zA-Z]{2,8}. e.g. en, ja : https://developer.android.com/reference/java/util/Locale.html
 * @param country - Country. The country (region) field is case insensitive, but Locale always canonicalizes to upper case.
 *                 format: [a-zA-Z]{2} | [0-9]{3}. e.g. US, JP : https://developer.android.com/reference/java/util/Locale.html
 * @param script - Script. The script field is case insensitive but Locale always canonicalizes to title case.
 *                format: [a-zA-Z]{4}. e.g. Hans in zh-Hans-CN : https://developer.android.com/reference/java/util/Locale.html
 * @throws {Error} If language or country name is not provided
 */
export async function setDeviceLocale(this: SettingsApp, language: string, country: string, script: string | null = null): Promise<void> {
  if (_.isEmpty(language)) {
    throw new Error('Language name must be provided');
  }
  if (_.isEmpty(country)) {
    throw new Error('Country name must be provided');
  }

  const lcLanguage = language.toLowerCase();
  const ucCountry = country.toUpperCase();
  const curLocale = await this.adb.getDeviceLocale();

  // zh-Hans-CN : zh-CN
  const localeCode = script ? `${lcLanguage}-${script}-${ucCountry}` : `${lcLanguage}-${ucCountry}`;
  this.log.debug(LOG_PREFIX, `Current locale: '${curLocale}'; requested locale: '${localeCode}'`);
  if (localeCode.toLowerCase() !== curLocale.toLowerCase()) {
    await setDeviceLocaleInternal.bind(this)(lcLanguage, ucCountry, script);
  }
}

/**
 * Retrieves the list of supported device locales
 *
 * @returns List of supported locales
 * @throws {Error} If the list cannot be retrieved
 */
export async function listSupportedLocales(this: SettingsApp): Promise<SupportedLocale[]> {
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

/**
 * Change the locale on the device under test. Don't need to reboot the device after changing the locale.
 * This method sets an arbitrary locale following:
 *   https://developer.android.com/reference/java/util/Locale.html
 *   https://developer.android.com/reference/java/util/Locale.html#Locale(java.lang.String,%20java.lang.String)
 *
 * @param language - Language. e.g. en, ja
 * @param country - Country. e.g. US, JP
 * @param script - Script. e.g. Hans in `zh-Hans-CN`
 */
async function setDeviceLocaleInternal(this: SettingsApp, language: string, country: string, script: string | null = null): Promise<void> {
  const params: string[] = [
    '-a', LOCALE_SETTING_ACTION,
    '-n', LOCALE_SETTING_RECEIVER,
    '--es', 'lang', language.toLowerCase(),
    '--es', 'country', country.toUpperCase()
  ];
  if (script) {
    params.push('--es', 'script', script);
  }

  for (let retry = 0; retry < 2; retry++) {
    try {
      await this.checkBroadcast(params, 'set device locale');
    } catch (err: any) {
      if (retry === 0 && _.has(err, 'output') && err.output.includes('NoSuchMethodException')) {
        // The above exception may be thrown if hidden API policies have not been picked up by
        // Settings app yet. Restart might fix this issue.
        await this.requireRunning({shouldRestoreCurrentApp: true, forceRestart: true});
        continue;
      }
      throw err;
    }
  }
}
