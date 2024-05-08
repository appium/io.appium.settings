import {
  BLUETOOTH_SETTING_ACTION,
  BLUETOOTH_SETTING_RECEIVER,
  BLUETOOTH_UNPAIR_ACTION,
  BLUETOOTH_UNPAIR_RECEIVER,
} from '../constants.js';

/**
 * Change the state of bluetooth on the device under test.
 *
 * @this {import('../client').SettingsApp}
 * @param {boolean} on - True to enable and false to disable it.
 */
export async function setBluetoothState (on) {
  if (await this.adb.getApiLevel() < 30) {
    await this.checkBroadcast([
      '-a', BLUETOOTH_SETTING_ACTION,
      '-n', BLUETOOTH_SETTING_RECEIVER,
      '--es', 'setstatus', on ? 'enable' : 'disable'
    ], `${on ? 'enable' : 'disable'} bluetooth`);
  } else {
    await this.adb.setBluetoothOn(on);
  }
}

/**
 * Unpairs all previosly paired bluetooth devices if any exist
 *
 * @this {import('../client').SettingsApp}
 */
export async function unpairAllBluetoothDevices() {
  await this.checkBroadcast([
    '-a', BLUETOOTH_UNPAIR_ACTION,
    '-n', BLUETOOTH_UNPAIR_RECEIVER,
  ], 'unpair all bluetooth devices');
}
