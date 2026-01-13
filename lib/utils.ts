import path from 'node:path';
import fs from 'node:fs';

const MODULE_NAME = 'io.appium.settings';

/**
 * Tries to synchronously detect the absolute path to the folder
 * where the given `moduleName` is located.
 *
 * @returns Full path to the module root
 */
function getModuleRootSync(): string {
  let currentDir = __dirname;
  let isAtFsRoot = false;
  while (!isAtFsRoot) {
    const manifestPath = path.join(currentDir, 'package.json');
    try {
      if (fs.existsSync(manifestPath) &&
          JSON.parse(fs.readFileSync(manifestPath, 'utf8')).name === MODULE_NAME) {
        return currentDir;
      }
    } catch {}
    currentDir = path.dirname(currentDir);
    isAtFsRoot = currentDir.length <= path.dirname(currentDir).length;
  }
  throw new Error(`Cannot find the root of the ${MODULE_NAME} Node.js module`);
}

/**
 * Get the full path to the Settings APK
 *
 * @returns Full path to the APK file
 */
export function getSettingsApkPath(): string {
  return path.resolve(getModuleRootSync(), 'apks', 'settings_apk-debug.apk');
}
