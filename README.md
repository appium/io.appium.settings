# Settings

[![NPM version](http://img.shields.io/npm/v/io.appium.settings.svg)](https://npmjs.org/package/io.appium.settings)
[![Downloads](http://img.shields.io/npm/dm/io.appium.settings.svg)](https://npmjs.org/package/io.appium.settings)

[![Release](https://github.com/appium/io.appium.settings/actions/workflows/publish.js.yml/badge.svg)](https://github.com/appium/io.appium.settings/actions/workflows/publish.js.yml)

Toggle settings in Android device or emulator.

A small and simple Android application that deals with the system settings. Then the application shuts down.

## Requirements

* [Android SDK](http://developer.android.com)
* [Java JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
* [Gradle](https://gradle.org/)

You may also consider using the latest [Android Studio](https://developer.android.com/studio/index.html) to debug the code easily.

### Enable access to non-SDK interfaces

[Restrictions on non-SDK interfaces](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces#test-for-non-sdk) could affect some functionalities.
Changing of the system locale in API Level 34 or later demonstrates the above restriction. Please [enable access to non-SDK interfaces](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces#how_can_i_enable_access_to_non-sdk_interfaces) via `adb`. [This issue comment](https://github.com/appium/io.appium.settings/issues/180#issuecomment-2156279885) is adb logcat output when the non-SDK interfaces are enabled, or disabled (system default). Please try out a device reboot if the same logcat continues even after setting the hidden api policy.

## Building

```shell
$ ./gradlew clean assembleDebug
```

You can also run `gradlew installDebug` to build and immediately deploy the app to a connected Android device or emulator.


## Installing

You can install the apk through the [Android Debug Bridge](http://developer.android.com/tools/help/adb.html).

To install:

```shell
$ cd app/build/outputs/apk
$ adb install settings_apk-debug.apk
# You can grant permissions with -g option as below for over api level 23 devices
# $ adb install -g settings_apk-debug.apk
```

To uninstall:

```shell
$ adb uninstall io.appium.settings
```

## Using the JavaScript wrapper

This module exports the [SettingsApp](./lib/client.js) class, which allows you to automate the below interactions with JavaScript.
The wrapper expects you to also have the [appium-adb](https://github.com/appium/appium-adb) module listed
in your dependencies. The actual version of the appium-adb module must satisfy the same semver requirement this module
has in its [devDependencies](./package.json).
Here is the usage example:

```js
import ADB from 'appium-adb'
import { SettingsApp } from 'io.appium.settings';

async function main() {
  // It is expected 'io.appium.settings' is already installed on the device
  // and the necessary permissions are granted to it.
  // Check https://github.com/appium/appium-android-driver/blob/master/lib/helpers/android.ts
  // if you are looking on how to automate this process.
  const app = new SettingsApp({
    adb: await ADB.createADB()
  });
  const recorder = app.makeMediaProjectionRecorder();
  const filename = 'video.mp4';
  const didStart = await recorder.start({filename});
  if (didStart) {
    log.info(`A new media projection recording '${filename}' has been successfully started`);
  } else {
    log.info('A new media projection recording was unable to start. Is it already running?');
  }
}

main();
```

The module also exports various constants containing its service and action names, that could be useful in your
scripts. Check [constants.js](./lib/constants.js) for more details.

## Changing of system settings

Once installed on a device, you can change the `wifi`, `data`, `animation` and `locale` settings through the following commands:

To turn on `wifi`:

```shell
$ adb shell am broadcast -a io.appium.settings.wifi --es setstatus enable
```

To turn off `wifi`:

```shell
$ adb shell am broadcast -a io.appium.settings.wifi --es setstatus disable
```

To turn on `bluetooth`:

```shell
$ adb shell am broadcast -a io.appium.settings.bluetooth --es setstatus enable
```

To turn off `bluetooth`:

```shell
$ adb shell am broadcast -a io.appium.settings.bluetooth --es setstatus disable
```

To unpair known `bluetooth devices`:

```shell
$ adb shell am broadcast -a io.appium.settings.unpair_bluetooth
```

To turn on `animation`:

```shell
$ adb shell am broadcast -a io.appium.settings.animation --es setstatus enable
```

(Note that [Restrictions on non-SDK interfaces](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces) affects this animation command.)

To turn off `animation`:

```shell
$ adb shell am broadcast -a io.appium.settings.animation --es setstatus disable
```

(Note that [Restrictions on non-SDK interfaces](https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces) affects this animation command.)

Set particular locale:

```shell
# If not granted already, grant locale change permission https://developer.android.com/reference/android/Manifest.permission#CHANGE_CONFIGURATION
$ adb shell pm grant io.appium.settings android.permission.CHANGE_CONFIGURATION
$ adb shell am broadcast -a io.appium.settings.locale -n io.appium.settings/.receivers.LocaleSettingReceiver --es lang ja --es country JP
$ adb shell getprop persist.sys.locale # ja-JP
$ adb shell am broadcast -a io.appium.settings.locale -n io.appium.settings/.receivers.LocaleSettingReceiver --es lang zh --es country CN --es script Hans
$ adb shell getprop persist.sys.locale # zh-Hans-CN for API level 21+
# When 'skip_locale_check' parameter is set appium settings application doesn't check that locale you are trying to set is a valid locale string, by default it validates the locale and throws error when invalid
$ adb shell am broadcast -a io.appium.settings.locale -n io.appium.settings/.receivers.LocaleSettingReceiver --es lang xx --es country US --es skip_locale_check 1
```

You can set the [Locale](https://developer.android.com/reference/java/util/Locale.html) format, especially this feature support [Locale(String language, String country)](https://developer.android.com/reference/java/util/Locale.html#Locale(java.lang.String,%20java.lang.String)) so far.

`-n io.appium.settings/.receivers.LocaleSettingReceiver` is not necessary in some devices.

List all supported locales as base64-encoded JSON:

```shell
$ adb shell am broadcast -a io.appium.settings.list_locales
```

## Retrieval of system settings

You can retrieve the current geo location by executing:

```shell
$ adb shell am broadcast -a io.appium.settings.location -n io.appium.settings/.receivers.LocationInfoReceiver --ez forceUpdate false
```

The first value in the returned `data` string is the current latitude, the second is the longitude and the last one is the altitude. An empty string is returned if the data cannot be retrieved (more details on the failure cause can be found in the logcat output).

Since version 3.6.0 it is also possible to provide `forceUpdate` boolean argument. If it is set to
`true` then GPS cache refresh request is going to be send asynchronously every time when the
current location is requested. By default the cached location value is returned instead.

_Note_

The forced GPS cache refresh feature only works if the device under test has
Google Play Services installed. In case the vanilla LocationManager is used the device API level must be at
version 30 (Android R) or higher. If none of the conditions above is satisfied then enabling of the `forceUpdate`
option would have no effect.


## Setting Mock Locations

Please set the Appium Settings from the Settings app's _Developer Options_ -> _Select mock location app_.
Or `adb shell appops set io.appium.settings android:mock_location allow` let you do the same via adb command.
`adb shell appops set io.appium.settings android:mock_location deny` is to turn it off.


Start sending scheduled updates (every 2s) for mock location with the specified values by executing:
(API versions 26+):
```shell
$ adb shell am start-foreground-service --user 0 -n io.appium.settings/.LocationService --es longitude {longitude-value} --es latitude {latitude-value} [--es altitude {altitude-value}] [--es speed {speed-value}] [--es bearing {bearing-value}] [--es accuracy {accuracy-value}]
```
(Older versions):
```shell
$ adb shell am startservice --user 0 -n io.appium.settings/.LocationService --es longitude {longitude-value} --es latitude {latitude-value} [--es altitude {altitude-value}]
```
Running the command again stops sending the previously specified location and starts sending updates for the
new mock location.

Additionally the service allows to provide the following optional parameters to the mocked
location:

- `speed`: the speed, in meters/second over ground. A float value greater than zero is acceptable.
- `bearing`: the bearing, in degrees. Bearing is the horizontal direction of travel of this device, and is not related to the device orientation. The input will be wrapped into the range (0.0, 360.0]

Stop sending new mocklocations and clean up everything (remove the mock location providers) by executing:
```shell
$ adb shell am stopservice io.appium.settings/.LocationService
```


## IME actions generation

You can simulate IME actions generation with this application. First, it is necessary to enable and activate the corresponding service:

```bash
adb shell ime enable io.appium.settings/.AppiumIME
adb shell ime set io.appium.settings/.AppiumIME
```

After the service is active simply focus any edit field, which contains an IME handler, and send `/action_name_or_integer_code/` text into this field: `adb shell input text '/action_name_or_integer_code/'` (enclosing slashes are required). The following action names are supported (case-insensitive): `normal, unspecified, none, go, search, send, next, done, previous`. If the given action name is unknown then it is going to be printed into the text field as is without executing any action.


## Unicode IME

This input method allows to enter unicode values into text fields using `adb shell input text` terminal command. The idea is to encode the given unicode string into UTF-7 and then let the corresponding IME to decode and transform the actual input. This helper is also useful for automating applications running under Android API19 and older where `sendText` method of `UiObject` did not support Unicode properly. The actual implementation is based on the [Uiautomator Unicode Input Helper](https://github.com/sumio/uiautomator-unicode-input-helper) by TOYAMA Sumio.

Use the following commands to enable the Unicode IME:

```bash
adb shell ime enable io.appium.settings/.UnicodeIME
adb shell ime set io.appium.settings/.UnicodeIME
```


## Clipboard

This action allows to retrieve the text content of the current clipboard
as base64-encoded string.
An empty string is returned if the clipboard cannot be retrieved
or the clipboard is empty.
Remember, that since Android Q the clipboard content can only be retrieved if
the requester application is set as the default IME in the system:

```bash
adb shell ime enable io.appium.settings/.AppiumIME
adb shell ime set io.appium.settings/.AppiumIME
adb shell am broadcast -a io.appium.settings.clipboard.get
adb shell ime set com.google.android.inputmethod.latin/com.android.inputmethod.latin.LatinIME
```


## Notifications

Since version 2.16.0 Appium Settings supports retrieval of system notifications.
You need to manually switch the corresponding security switcher next to `Appium Settings`
application name in `Settings->Notification Access` (the path to this page under Settings
may vary depending on Android version and the device model)
in order to make this feature available. The next step would be to send the following broadcast command:
```bash
$ adb shell am broadcast -a io.appium.settings.notifications
```
The notifications listener service is running in the background and collects
all the active and newly created notifications into the internal buffer with maximum
size of `100`. The collected data (e.g. the properties and texts of each notification)
is returned as JSON-formatted string. An error description string is returned instead if the
notifications list cannot be retrieved.
The example of the resulting data:
```json
{
  "statusBarNotifications": [
    {
      "isGroup":false,
      "packageName":"io.appium.settings",
      "isClearable":false,
      "isOngoing":true,
      "id":1,
      "tag":null,
      "notification":{
        "title":null,
        "bigTitle":"Appium Settings",
        "text":null,
        "bigText":"Keep this service running, so Appium for Android can properly interact with several system APIs",
        "tickerText":null,
        "subText":null,
        "infoText":null,
        "template":"android.app.Notification$BigTextStyle"
      },
      "userHandle":0,
      "groupKey":"0|io.appium.settings|1|null|10133",
      "overrideGroupKey":null,
      "postTime":1576853518850,
      "key":"0|io.appium.settings|1|null|10133",
      "isRemoved":false
    }
  ]
}
```
See https://developer.android.com/reference/android/service/notification/StatusBarNotification
and https://developer.android.com/reference/android/app/Notification.html
for more information on available notification properties and their values.


## SMS

Since version 3.1 Appium Settings supports retrieval of SMS messages.
Make sure the corresponding permission has been granted to the app
in order to make this feature available. The next step would be to send
the following broadcast command:
```bash
$ adb shell am broadcast -a io.appium.settings.sms.read --es max 10
```
In this example the SMS reader broadcast receiver would retrieve
the properties of `10 recent` incoming SMS messages. By default the limit
is set to `100`. The collected data (e.g. the properties and texts of each SMS)
is returned as JSON-formatted string. An error description string is returned instead if the
SMS list cannot be retrieved.
The example of the resulting data:
```json
{
  "items":[
    {
      "id":"2",
      "address":"+123456789",
      "person":null,
      "date":"1581936422203",
      "read":"0",
      "status":"-1",
      "type":"1",
      "subject":null,
      "body":"\"text message2\"",
      "serviceCenter":null
    },
    {
      "id":"1",
      "address":"+123456789",
      "person":null,
      "date":"1581936382740",
      "read":"0",
      "status":"-1",
      "type":"1",
      "subject":null,
      "body":"\"text message\"",
      "serviceCenter":null
    }
  ],
  "total":2
}
```


## Media Scanning

Since version 3.5 Appium Settings supports broadcast messages handling
that performs media scanning in response to `io.appium.settings.scan_media`
intent. This was done due to `android.intent.action.MEDIA_SCANNER_SCAN_FILE` deprecation
since Android API version 30. To scan the given file or folder for media data simply run:

```bash
$ adb shell am broadcast -a io.appium.settings.scan_media --es path /sdcard/media
```

This command will _recursively_ scan all files inside of `/sdcard/media` folder
and add them to the media library if their MIME types are supported. If the
file/folder in _path_ does not exist/is not readable or is not provided then an
error will be returned and the corresponding log message would be written into logs.

## Internal Audio & Video Recording

Required steps to activate recording:

```bash
adb shell pm grant io.appium.settings android.permission.RECORD_AUDIO
adb shell appops set io.appium.settings PROJECT_MEDIA allow
```

Start Recording:
```bash
adb shell am start -n "io.appium.settings/io.appium.settings.Settings" -a io.appium.settings.recording.ACTION_START --es filename abc.mp4 --es priority high --es max_duration_sec 900 --es resolution 1920x1080
```

### Arguments (see above start command as an example for giving arguments)
- filename (Mandatory) - You can type recording video file name as you want, but recording currently supports only "mp4" format so your filename must end with ".mp4"
- priority (Optional) - Default value: "high" which means recording thread priority is maximum however if you face performance drops during testing with recording enabled, you can reduce recording priority to "normal" or "low"
- max_duration_sec (Optional) (in seconds) - Default value: 900 seconds which means maximum allowed duration is 15 minute, you can increase it if your test takes longer than that
- resolution (Optional) - Default value: maximum supported resolution on-device(Detected automatically on app itself), which usually equals to Full HD 1920x1080 on most phones however you can change it to following supported resolutions as well: "1920x1080", "1280x720", "720x480", "320x240", "176x144"

Stop Recording:
```bash
adb shell am start -n "io.appium.settings/io.appium.settings.Settings" -a io.appium.settings.recording.ACTION_STOP
```

Obtain Recording Output File:
```bash
adb pull /storage/emulated/0/Android/data/io.appium.settings/files/abc.mp4 abc.mp4
```

## Live Screen Streaming (JPEG / H.264 / HEVC)

In addition to file-based recording above, Appium Settings can stream the screen live as
either a sequence of JPEG frames or an H.264/HEVC video (with an optional interleaved AAC
audio track), instead of writing a file. This is a **raw local-socket protocol**, not an
HTTP/MJPEG server: the app opens a local abstract socket on-device, and the consumer is
expected to `adb forward` it to a host TCP port and parse the (simple, length-prefixed)
frame protocol itself. The [SettingsApp](./lib/client.js) wrapper (`makeJpegStreamSession()` /
`makeVideoStreamSession()`) already does this for you and is the recommended way to consume
either stream; talking to the socket directly is only needed for other languages/tools.

Required steps to activate streaming (same as recording; `RECORD_AUDIO` is only needed if you
enable the video stream's `audio` option):

```bash
adb shell pm grant io.appium.settings android.permission.RECORD_AUDIO
adb shell appops set io.appium.settings PROJECT_MEDIA allow
```

### JPEG streaming

Start:
```bash
adb shell am start -n "io.appium.settings/io.appium.settings.Settings" -a io.appium.settings.streaming.jpeg.ACTION_START --es socket_name my-jpeg-stream --es fps 30 --es quality 80 --es scale 100
```

- `socket_name` (Mandatory) - Name of the local abstract socket the app should listen on; forward it with `adb forward tcp:<port> localabstract:<socket_name>`
- `fps` (Optional) - Default value: 60
- `quality` (Optional) - JPEG quality, 1-100. Default value: 80
- `scale` (Optional) - Percentage (1-100) to scale the captured frame to before encoding. Default value: 100

Stop:
```bash
adb shell am start -n "io.appium.settings/io.appium.settings.Settings" -a io.appium.settings.streaming.jpeg.ACTION_STOP
```

### Video streaming

Start:
```bash
adb shell am start -n "io.appium.settings/io.appium.settings.Settings" -a io.appium.settings.streaming.video.ACTION_START --es socket_name my-video-stream --es codec h264 --es fps 30 --es bitrate 4000000 --es resolution 1920x1080 --es audio false
```

- `socket_name` (Mandatory) - Same meaning as for JPEG streaming above
- `codec` (Optional) - `h264` or `hevc`. Default value: `h264`
- `fps` (Optional) - Default value: 30
- `bitrate` (Optional) - In bits per second. Default value: 4000000 (4 Mbps)
- `resolution` (Optional) - Same syntax/allowed values as the recording `resolution` argument above
- `audio` (Optional) - `true` to interleave an AAC audio track captured via `AudioPlaybackCaptureConfiguration`. Default value: `false`

Stop:
```bash
adb shell am start -n "io.appium.settings/io.appium.settings.Settings" -a io.appium.settings.streaming.video.ACTION_STOP
```

### Node usage example

```js
import ADB from 'appium-adb'
import { SettingsApp } from 'io.appium.settings';

async function main() {
  const app = new SettingsApp({adb: await ADB.createADB()});
  await app.adjustMediaProjectionServicePermissions();

  const session = app.makeVideoStreamSession();
  await session.start({codec: 'h264', fps: 30, audio: true});
  for await (const unit of session.accessUnits()) {
    console.log(unit.track, unit.data.length, unit.isKeyFrame);
  }
}

main();
```

By default the Node wrapper picks an OS-assigned ephemeral port for the `adb forward` bridge
between the device's local socket and the host. Both `makeJpegStreamSession().start()` and
`makeVideoStreamSession().start()` also accept:

- `localPort` (Optional) - Use this exact local TCP port instead. Throws if it's already in use.
- `localPortRange` (Optional) - A `[min, max]` tuple; the first free port in this inclusive range is used. Throws if none are free.

Only one of `localPort`/`localPortRange` may be provided at a time. This is useful if you need a
predictable port (e.g. for a firewall rule or a proxy in front of the stream):

```js
await session.start({codec: 'h264', localPort: 8000});
// or
await session.start({codec: 'h264', localPortRange: [8000, 8010]});
```

_Note_

JPEG streaming throughput is CPU-bound (each frame is compressed on-device via
`Bitmap.compress`), so on lower-end devices or emulators you may need to reduce `fps`,
`quality` and/or `scale` from their defaults to keep up with real-time capture.


## Notes:

* You have to specify the receiver class if the app has never been executed before:
```shell
$ adb shell am broadcast -a io.appium.settings.wifi -n io.appium.settings/.receivers.WiFiConnectionSettingReceiver --es setstatus disable
```
* To change animation setting, the app should be granted `SET_ANIMATION_SCALE` permission:
```shell
$ adb shell pm grant io.appium.settings android.permission.SET_ANIMATION_SCALE
```
* To change locale setting, the app should be granted `CHANGE_CONFIGURATION` permission:
```shell
$ adb shell pm grant io.appium.settings android.permission.CHANGE_CONFIGURATION
```
* To get location, the app should be granted `ACCESS_FINE_LOCATION` permission at least:
```shell
$ adb shell pm grant io.appium.settings android.permission.ACCESS_FINE_LOCATION
```
* To set location, the location mocking must be enabled. On Android 5 this requires enabling option
`Allow mock locations` in Developer Settings. In later versions following command can be used:
```shell
$ adb shell appops set io.appium.settings android:mock_location allow
```

* On Android 6.0+ you must enable the corresponding permissions for the app first. This can be
done in application settings, Permissions entry.

* Switching mobile data on/off requires the phone to be rooted on Android 5.0+
('su' binary is expected to be available on internal phone file system).
Read [this](http://stackoverflow.com/questions/26539445/the-setmobiledataenabled-method-is-no-longer-callable-as-of-android-l-and-later)
StackOverflow thread for more details.

Voila!


## Caveats

There are certain system services which cannot be accessed through an application. Two ones central here are `airplane_mode` and `gps`.


## License

Apache License 2.0
