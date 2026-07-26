import releaseConfig from '@appium/semantic-release-config';

export default releaseConfig({
  githubAssets: ['apks/settings_apk-debug.apk'],
});
