import {
  WIFI_CONNECTION_SETTING_ACTION,
  WIFI_CONNECTION_SETTING_RECEIVER,
} from '../constants';
import type { SettingsApp } from '../client';

/**
 * Change the state of WiFi on the device under test.
 *
 * @param on - True to enable and false to disable it
 * @param isEmulator - Set it to true if the device under test is an emulator rather than a real device
 */
export async function setWifiState(this: SettingsApp, on: boolean, isEmulator = false): Promise<void> {
  if (isEmulator) {
    // The svc command does not require to be root since API 26
    await this.adb.shell(['svc', 'wifi', on ? 'enable' : 'disable']);
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
 * @param on - True to enable and false to disable it
 * @param isEmulator - Set it to true if the device under test is an emulator rather than a real device
 * @throws {Error} If the data state cannot be changed
 */
export async function setDataState(this: SettingsApp, on: boolean, isEmulator = false): Promise<void> {
  if (isEmulator) {
    // The svc command does not require to be root since API 26
    await this.adb.shell(['svc', 'data', on ? 'enable' : 'disable']);
  } else {
    try {
      await this.adb.shell(['cmd', 'phone', 'data', on ? 'enable' : 'disable']);
    } catch (e: any) {
      throw new Error(`Cannot change the data state. Original error: ${e.stderr || e.message}`);
    }
  }
}
