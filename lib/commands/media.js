import { LOG_PREFIX } from '../logger.js';
import { MEDIA_SCAN_ACTION, MEDIA_SCAN_RECEIVER } from '../constants.js';

/**
 * Performs recursive media scan at the given destination.
 * All successfully scanned items are being added to the device's
 * media library.
 *
 * @this {import('../client').SettingsApp}
 * @param {string} destination File/folder path on the remote device.
 * @throws {Error} If there was an unexpected error by scanning.
 */
export async function scanMedia (destination) {
  this.log.debug(LOG_PREFIX, `Scanning '${destination}' for media files`);
  await this.checkBroadcast([
    '-n', MEDIA_SCAN_RECEIVER,
    '-a', MEDIA_SCAN_ACTION,
    '--es', 'path', destination
  ], 'scan media');
}
