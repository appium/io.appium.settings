import {install} from 'source-map-support';
install();

import { getSettingsApkPath } from './utils';

export const path = getSettingsApkPath();
export * from './constants';
export { SettingsApp } from './client';
