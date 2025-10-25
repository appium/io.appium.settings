import { SettingsApp } from '../../lib/client';
import { withMocks } from '@appium/test-support';
import { ADB } from 'appium-adb';

const adb = new ADB({});

describe('client', withMocks({adb}, function (mocks) {
  const client = new SettingsApp({adb});
  let chai;

  before(async function () {
    chai = await import('chai');
    const chaiAsPromised = await import('chai-as-promised');

    chai.should();
    chai.use(chaiAsPromised.default);
  });

  afterEach(function () {
    mocks.verify();
  });

  describe('isRunningInForeground', function () {
    it('should return true if the output includes isForeground=true', async function () {
      // this case is when 'io.appium.settings/.NLService' was started AND
      // the settings app is running as a foreground service.
      // This case could happen when only 'shell cmd notification allow_listener io.appium.settings/.NLService' is
      // called but the process hasn't been started from io.appium.settings/.ForegroundService,
      // or the app process was stopped by "Force Stop" via the system settings app.
      const getActivityServiceOutput = `
          ACTIVITY MANAGER SERVICES (dumpsys activity services)
            User 0 active services:
            * ServiceRecord{f0ad90b u0 io.appium.settings/.NLService}
              intent={act=android.service.notification.NotificationListenerService cmp=io.appium.settings/.NLService}
              packageName=io.appium.settings
              processName=io.appium.settings
              permission=android.permission.BIND_NOTIFICATION_LISTENER_SERVICE
              baseDir=/data/app/~~fHuRc6u9ehtAcXvuXy-fiw==/io.appium.settings-wJRwd1HrrbVG5ZINWuHi5Q==/base.apk
              dataDir=/data/user/0/io.appium.settings
              app=ProcessRecord{1d61746 18302:io.appium.settings/u0a320}
              whitelistManager=true
              allowWhileInUsePermissionInFgs=true
              startForegroundCount=0
              recentCallingPackage=android
              createTime=-6m21s859ms startingBgTimeout=--
              lastActivity=-6m21s783ms restartTime=-6m21s783ms createdFromFg=true
              Bindings:
              * IntentBindRecord{a5d675f CREATE}:
                intent={act=android.service.notification.NotificationListenerService cmp=io.appium.settings/.NLService}
                binder=android.os.BinderProxy@1be25ac
                requested=true received=true hasBound=true doRebind=false
                * Client AppBindRecord{c78a275 ProcessRecord{3f853b1 1847:system/1000}}
                  Per-process Connections:
                    ConnectionRecord{fbf1188 u0 CR FGS !PRCP io.appium.settings/.NLService:@339692b}
              All Connections:
                ConnectionRecord{fbf1188 u0 CR FGS !PRCP io.appium.settings/.NLService:@339692b}

            * ServiceRecord{e7a180b u0 io.appium.settings/.ForegroundService}
              intent={act=start cmp=io.appium.settings/.ForegroundService}
              packageName=io.appium.settings
              processName=io.appium.settings
              permission=android.permission.FOREGROUND_SERVICE
              baseDir=/data/app/~~fHuRc6u9ehtAcXvuXy-fiw==/io.appium.settings-wJRwd1HrrbVG5ZINWuHi5Q==/base.apk
              dataDir=/data/user/0/io.appium.settings
              app=ProcessRecord{1d61746 18302:io.appium.settings/u0a320}
              allowWhileInUsePermissionInFgs=true
              startForegroundCount=1
              recentCallingPackage=io.appium.settings
              isForeground=true foregroundId=1 foregroundNoti=Notification(channel=main_channel shortcut=null contentView=null vibrate=null sound=null defaults=0x0 flags=0x62 color=0x00000000 vis=PRIVATE)
              createTime=-5m1s703ms startingBgTimeout=--
              lastActivity=-5m1s702ms restartTime=-5m1s702ms createdFromFg=true
              startRequested=true delayedStop=false stopIfKilled=false callStart=true lastStartId=1

            Connection bindings to services:
            * ConnectionRecord{fbf1188 u0 CR FGS !PRCP io.appium.settings/.NLService:@339692b}
              binding=AppBindRecord{c78a275 io.appium.settings/.NLService:system}
              conn=android.app.LoadedApk$ServiceDispatcher$InnerConnection@339692b flags=0x5000101`;
      mocks.adb.expects('getApiLevel').once().returns(26);
      mocks.adb.expects('processExists').never();
      mocks.adb.expects('shell').once().withArgs(['dumpsys', 'activity', 'services', 'io.appium.settings']).returns(getActivityServiceOutput);
      await client.isRunningInForeground().should.eventually.true;
    });
    it('should return false if the output does not include isForeground=true', async function () {
      // this case is when 'io.appium.settings/.NLService' was started but
      // the settings app hasn't been started as a foreground service yet.
      const getActivityServiceOutput = `
        ACTIVITY MANAGER SERVICES (dumpsys activity services)
          User 0 active services:
          * ServiceRecord{41dde04 u0 io.appium.settings/.NLService}
            intent={act=android.service.notification.NotificationListenerService cmp=io.appium.settings/.NLService}
            packageName=io.appium.settings
            processName=io.appium.settings
            permission=android.permission.BIND_NOTIFICATION_LISTENER_SERVICE
            baseDir=/data/app/~~fHuRc6u9ehtAcXvuXy-fiw==/io.appium.settings-wJRwd1HrrbVG5ZINWuHi5Q==/base.apk
            dataDir=/data/user/0/io.appium.settings
            app=ProcessRecord{d3b2ed1 18588:io.appium.settings/u0a320}
            whitelistManager=true
            allowWhileInUsePermissionInFgs=true
            startForegroundCount=0
            recentCallingPackage=android
            createTime=-2s362ms startingBgTimeout=--
            lastActivity=-2s283ms restartTime=-2s283ms createdFromFg=true
            Bindings:
            * IntentBindRecord{26ce8cd CREATE}:
              intent={act=android.service.notification.NotificationListenerService cmp=io.appium.settings/.NLService}
              binder=android.os.BinderProxy@2dbc582
              requested=true received=true hasBound=true doRebind=false
              * Client AppBindRecord{24ce493 ProcessRecord{3f853b1 1847:system/1000}}
                Per-process Connections:
                  ConnectionRecord{8f3e709 u0 CR FGS !PRCP io.appium.settings/.NLService:@d481010}
                  ConnectionRecord{bd3f9f8 u0 CR FGS !PRCP io.appium.settings/.NLService:@1c7ed5b}
            All Connections:
              ConnectionRecord{bd3f9f8 u0 CR FGS !PRCP io.appium.settings/.NLService:@1c7ed5b}
              ConnectionRecord{8f3e709 u0 CR FGS !PRCP io.appium.settings/.NLService:@d481010}

          Connection bindings to services:
          * ConnectionRecord{bd3f9f8 u0 CR FGS !PRCP io.appium.settings/.NLService:@1c7ed5b}
            binding=AppBindRecord{24ce493 io.appium.settings/.NLService:system}
            conn=android.app.LoadedApk$ServiceDispatcher$InnerConnection@1c7ed5b flags=0x5000101
          * ConnectionRecord{8f3e709 u0 CR FGS !PRCP io.appium.settings/.NLService:@d481010}
            binding=AppBindRecord{24ce493 io.appium.settings/.NLService:system}
            conn=android.app.LoadedApk$ServiceDispatcher$InnerConnection@d481010 flags=0x5000101`;

      mocks.adb.expects('getApiLevel').once().returns(26);
      mocks.adb.expects('processExists').never();
      mocks.adb.expects('shell').once().withArgs(['dumpsys', 'activity', 'services', 'io.appium.settings']).returns(getActivityServiceOutput);
      await client.isRunningInForeground().should.eventually.false;
    });
  });
}));
