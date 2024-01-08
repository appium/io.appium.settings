const path = require('node:path');
const { SettingsApp } = require('./build/lib/client');
const constants = require('./build/lib/constants');

module.exports = {
  path: path.resolve(__dirname, 'apks', 'settings_apk-debug.apk'),
  SettingsApp,
  ...constants,
};
