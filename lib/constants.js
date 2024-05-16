export const SETTINGS_HELPER_ID = 'io.appium.settings';
export const SETTINGS_HELPER_MAIN_ACTIVITY = '.Settings';

const RECEIVERS_PKG = `${SETTINGS_HELPER_ID}/.receivers`;

export const CLIPBOARD_RECEIVER = `${RECEIVERS_PKG}.ClipboardReceiver`;
export const CLIPBOARD_RETRIEVAL_ACTION = `${SETTINGS_HELPER_ID}.clipboard.get`;

export const APPIUM_IME = `${SETTINGS_HELPER_ID}/.AppiumIME`;
export const UNICODE_IME = `${SETTINGS_HELPER_ID}/.UnicodeIME`;
export const EMPTY_IME = `${SETTINGS_HELPER_ID}/.EmptyIME`;

export const WIFI_CONNECTION_SETTING_RECEIVER = `${RECEIVERS_PKG}.WiFiConnectionSettingReceiver`;
export const WIFI_CONNECTION_SETTING_ACTION = `${SETTINGS_HELPER_ID}.wifi`;
export const DATA_CONNECTION_SETTING_RECEIVER = `${RECEIVERS_PKG}.DataConnectionSettingReceiver`;
export const DATA_CONNECTION_SETTING_ACTION = `${SETTINGS_HELPER_ID}.data_connection`;
export const BLUETOOTH_SETTING_RECEIVER = `${RECEIVERS_PKG}.BluetoothConnectionSettingReceiver`;
export const BLUETOOTH_SETTING_ACTION = `${SETTINGS_HELPER_ID}.bluetooth`;
export const BLUETOOTH_UNPAIR_RECEIVER = `${RECEIVERS_PKG}.UnpairBluetoothDevicesReceiver`;
export const BLUETOOTH_UNPAIR_ACTION = `${SETTINGS_HELPER_ID}.unpair_bluetooth`;

export const ANIMATION_SETTING_RECEIVER = `${RECEIVERS_PKG}.AnimationSettingReceiver`;
export const ANIMATION_SETTING_ACTION = `${SETTINGS_HELPER_ID}.animation`;

export const LOCALE_SETTING_RECEIVER = `${RECEIVERS_PKG}.LocaleSettingReceiver`;
export const LOCALE_SETTING_ACTION = `${SETTINGS_HELPER_ID}.locale`;

export const LOCALES_LIST_SETTING_RECEIVER = `${RECEIVERS_PKG}.LocalesReader`;
export const LOCALES_LIST_SETTING_ACTION = `${SETTINGS_HELPER_ID}.list_locales`;

export const LOCATION_SERVICE = `${SETTINGS_HELPER_ID}/.LocationService`;
export const LOCATION_RECEIVER = `${RECEIVERS_PKG}.LocationInfoReceiver`;
export const LOCATION_RETRIEVAL_ACTION = `${SETTINGS_HELPER_ID}.location`;

export const SMS_LIST_RECEIVER = `${RECEIVERS_PKG}.SmsReader`;
export const SMS_LIST_RETRIEVAL_ACTION = `${SETTINGS_HELPER_ID}.sms.read`;

export const MEDIA_SCAN_RECEIVER = `${RECEIVERS_PKG}.MediaScannerReceiver`;
export const MEDIA_SCAN_ACTION = `${SETTINGS_HELPER_ID}.scan_media`;

export const SETTING_NOTIFICATIONS_LISTENER_SERVICE = `${SETTINGS_HELPER_ID}/.NLService`;
export const NOTIFICATIONS_RETRIEVAL_ACTION = `${SETTINGS_HELPER_ID}.notifications`;

export const RECORDING_SERVICE_NAME = `${SETTINGS_HELPER_ID}/.recorder.RecorderService`;
export const RECORDING_ACTIVITY_NAME = `${SETTINGS_HELPER_ID}/io.appium.settings.Settings`;
export const RECORDING_ACTION_START = `${SETTINGS_HELPER_ID}.recording.ACTION_START`;
export const RECORDING_ACTION_STOP = `${SETTINGS_HELPER_ID}.recording.ACTION_STOP`;
