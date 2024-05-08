import { LOG_PREFIX } from '../logger';
import {
  NOTIFICATIONS_RETRIEVAL_ACTION,
  SETTING_NOTIFICATIONS_LISTENER_SERVICE,
} from '../constants';

/**
 * Retrieves Android notifications via Appium Settings helper.
 * Appium Settings app itself must be *manually* granted to access notifications
 * under device Settings in order to make this feature working.
 * Appium Settings helper keeps all the active notifications plus
 * notifications that appeared while it was running in the internal buffer,
 * but no more than 100 items altogether. Newly appeared notifications
 * are always added to the head of the notifications array.
 * The `isRemoved` flag is set to `true` for notifications that have been removed.
 *
 * See https://developer.android.com/reference/android/service/notification/StatusBarNotification
 * and https://developer.android.com/reference/android/app/Notification.html
 * for more information on available notification properties and their values.
 *
 * @this {import('../client').SettingsApp}
 * @returns {Promise<Record<string, any>>} The example output is:
 * ```json
 * {
 *   "statusBarNotifications":[
 *     {
 *       "isGroup":false,
 *       "packageName":"io.appium.settings",
 *       "isClearable":false,
 *       "isOngoing":true,
 *       "id":1,
 *       "tag":null,
 *       "notification":{
 *         "title":null,
 *         "bigTitle":"Appium Settings",
 *         "text":null,
 *         "bigText":"Keep this service running, so Appium for Android can properly interact with several system APIs",
 *         "tickerText":null,
 *         "subText":null,
 *         "infoText":null,
 *         "template":"android.app.Notification$BigTextStyle"
 *       },
 *       "userHandle":0,
 *       "groupKey":"0|io.appium.settings|1|null|10133",
 *       "overrideGroupKey":null,
 *       "postTime":1576853518850,
 *       "key":"0|io.appium.settings|1|null|10133",
 *       "isRemoved":false
 *     }
 *   ]
 * }
 * ```
 * @throws {Error} If there was an error while getting the notifications list
 */
export async function getNotifications () {
  this.log.debug(LOG_PREFIX, 'Retrieving notifications');
  const output = await this.checkBroadcast([
    '-a', NOTIFICATIONS_RETRIEVAL_ACTION,
  ], 'retrieve notifications');
  return this._parseJsonData(output, 'notifications');
};

/**
 * Adjusts the necessary permissions for the
 * Notifications retreval service for Android API level 29+
 *
 * @this {import('../client').SettingsApp}
 * @returns {Promise<boolean>} If permissions adjustment has been actually made
 */
export async function adjustNotificationsPermissions() {
  if (await this.adb.getApiLevel() >= 29) {
    await this.adb.shell([
      'cmd',
      'notification',
      'allow_listener',
      SETTING_NOTIFICATIONS_LISTENER_SERVICE,
    ]);
    return true;
  }
  return false;
}
