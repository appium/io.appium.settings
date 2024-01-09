import { LOG_PREFIX } from '../logger.js';
import { SMS_LIST_RECEIVER, SMS_LIST_RETRIEVAL_ACTION } from '../constants.js';

/**
 * @typedef {Object} SmsListOptions
 * @property {number} [max=100] - The maximum count of recent messages
 * to retrieve
 */

/**
 * @typedef SmsListResult
 * @property {SmsListResultItem[]} items
 * @property {number} total
 */

/**
 * @privateRemarks XXX: WAG
 * @typedef SmsListResultItem
 * @property {string} id
 * @property {string} address
 * @property {string|null} person
 * @property {string} date
 * @property {string} read
 * @property {string} status
 * @property {string} type
 * @property {string|null} subject
 * @property {string} body
 * @property {string|null} serviceCenter
 */

/**
 * Retrieves the list of the most recent SMS
 * properties list via Appium Settings helper.
 * Messages are sorted by date in descending order.
 *
 * @this {import('../client').SettingsApp}
 * @param {SmsListOptions} opts
 * @returns {Promise<SmsListResult>} The example output is:
 * ```json
 * {
 *   "items":[
 *     {
 *       "id":"2",
 *       "address":"+123456789",
 *       "person":null,
 *       "date":"1581936422203",
 *       "read":"0",
 *       "status":"-1",
 *       "type":"1",
 *       "subject":null,
 *       "body":"\"text message2\"",
 *       "serviceCenter":null
 *     },
 *     {
 *       "id":"1",
 *       "address":"+123456789",
 *       "person":null,
 *       "date":"1581936382740",
 *       "read":"0",
 *       "status":"-1",
 *       "type":"1",
 *       "subject":null,
 *       "body":"\"text message\"",
 *       "serviceCenter":null
 *     }
 *   ],
 *   "total":2
 * }
 * ```
 * @throws {Error} If there was an error while getting the SMS list
 */
export async function getSmsList (opts = {}) {
  this.log.debug(LOG_PREFIX, 'Retrieving the recent SMS messages');
  await this.requireRunning({shouldRestoreCurrentApp: true});
  const args = [
    'am', 'broadcast',
    '-n', SMS_LIST_RECEIVER,
    '-a', SMS_LIST_RETRIEVAL_ACTION,
  ];
  if (opts.max) {
    args.push('--es', 'max', `${opts.max}`);
  }
  let output;
  try {
    output = await this.adb.shell(args);
  } catch (err) {
    throw new Error(`Cannot retrieve SMS list from the device. ` +
      `Make sure the Appium Settings application is installed and is up to date. ` +
      `Original error: ${err.message}`);
  }
  return this._parseJsonData(output, 'SMS list');
};
