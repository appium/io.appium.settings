import { LOG_PREFIX } from '../logger';
import { SMS_LIST_RECEIVER, SMS_LIST_RETRIEVAL_ACTION } from '../constants';
import type { SettingsApp } from '../client';
import type { SmsListOptions, SmsListResult } from './types';

/**
 * Retrieves the list of the most recent SMS
 * properties list via Appium Settings helper.
 * Messages are sorted by date in descending order.
 *
 * The example output is:
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
 *
 * @param opts Options for retrieving SMS list
 * @returns The SMS list result
 * @throws {Error} If there was an error while getting the SMS list
 */
export async function getSmsList(this: SettingsApp, opts: SmsListOptions = {}): Promise<SmsListResult> {
  this.log.debug(LOG_PREFIX, 'Retrieving the recent SMS messages');
  const args: string[] = [
    '-n', SMS_LIST_RECEIVER,
    '-a', SMS_LIST_RETRIEVAL_ACTION,
  ];
  if (opts.max) {
    args.push('--es', 'max', `${opts.max}`);
  }
  const output = await this.checkBroadcast(args, 'list SMS');
  return this._parseJsonData(output, 'SMS list');
}
