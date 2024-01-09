import npmlog from 'npmlog';


export const LOG_PREFIX = 'SettingsApp';

function getLogger () {
  const logger = global._global_npmlog || npmlog;
  if (!logger.debug) {
    logger.addLevel('debug', 1000, { fg: 'blue', bg: 'black' }, 'dbug');
  }
  return logger;
}

export const log = getLogger();
