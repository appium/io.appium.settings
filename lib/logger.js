import appiumLogger from '@appium/logger';


export const LOG_PREFIX = 'SettingsApp';

function getLogger () {
  const logger = global._global_npmlog || appiumLogger;
  if (!logger.debug) {
    logger.addLevel('debug', 1000, { fg: 'blue', bg: 'black' }, 'dbug');
  }
  return logger;
}

export const log = getLogger();
