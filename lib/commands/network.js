import {
  WIFI_CONNECTION_SETTING_ACTION,
  WIFI_CONNECTION_SETTING_RECEIVER,
  DATA_CONNECTION_SETTING_ACTION,
  DATA_CONNECTION_SETTING_RECEIVER,
} from '../constants.js';

/**
 * Change the state of WiFi on the device under test.
 *
 * @this {import('../client').SettingsApp}
 * @param {boolean} on - True to enable and false to disable it.
 * @param {boolean} [isEmulator=false] - Set it to true if the device under test
 *                                       is an emulator rather than a real device.
 */
export async function setWifiState (on, isEmulator = false) {
  if (isEmulator) {
    // The svc command does not require to be root since API 26
    await this.adb.shell(['svc', 'wifi', on ? 'enable' : 'disable'], {
      privileged: await this.adb.getApiLevel() < 26,
    });
    return;
  }

  if (await this.adb.getApiLevel() < 30) {
    // Android below API 30 does not have a dedicated adb command
    // to manipulate wifi connection state, so try to do it via Settings app
    // as a workaround
    await this.checkBroadcast([
      '-a', WIFI_CONNECTION_SETTING_ACTION,
      '-n', WIFI_CONNECTION_SETTING_RECEIVER,
      '--es', 'setstatus', on ? 'enable' : 'disable'
    ], `${on ? 'enable' : 'disable'} WiFi`);
    return;
  }

  await this.adb.shell(['cmd', '-w', 'wifi', 'set-wifi-enabled', on ? 'enabled' : 'disabled']);
}

/**
 * Change the state of Data transfer on the device under test.
 *
 * @this {import('../client').SettingsApp}
 * @param {boolean} on - True to enable and false to disable it.
 * @param {boolean} [isEmulator=false] - Set it to true if the device under test
 *                                       is an emulator rather than a real device.
 */
export async function setDataState (on, isEmulator = false) {
  if (isEmulator) {
    // The svc command does not require to be root since API 26
    await this.adb.shell(['svc', 'data', on ? 'enable' : 'disable'], {
      privileged: await this.adb.getApiLevel() < 26,
    });
    return;
  }

  if (await this.adb.getApiLevel() < 30) {
    await this.checkBroadcast([
      '-a', DATA_CONNECTION_SETTING_ACTION,
      '-n', DATA_CONNECTION_SETTING_RECEIVER,
      '--es', 'setstatus', on ? 'enable' : 'disable'
    ], `${on ? 'enable' : 'disable'} data`);
    return;
  }

  await this.adb.shell(['cmd', 'phone', 'data', on ? 'enable' : 'disable']);
}
