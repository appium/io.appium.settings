# Settings

Toggle settings in Android device or emulator.

A small and simple Android application that deals with the system settings. Then the application shuts down.


## Requirements

* [Android SDK](http://developer.android.com)
* [Java JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
* [Gradle](https://gradle.org/)

You may also consider using [Android Studio](https://developer.android.com/studio/index.html) to debug the code easily.

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
```

To uninstall:

```shell
$ adb uninstall io.appium.settings
```


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

To turn on `data`:

```shell
$ adb shell am broadcast -a io.appium.settings.data_connection --es setstatus enable
```

To turn off `data`:

```shell
$ adb shell am broadcast -a io.appium.settings.data_connection --es setstatus disable
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

To turn off `animation`:

```shell
$ adb shell am broadcast -a io.appium.settings.animation --es setstatus disable
```

Set particular locale:

```shell
$ adb shell am broadcast -a io.appium.settings.locale --es lang ja --es country JP
$ adb shell getprop persist.sys.locale # ja-JP
$ adb shell am broadcast -a io.appium.settings.locale --es lang zh --es country CN --es script Hans
$ adb shell getprop persist.sys.locale # zh-Hans-CN for API level 21+
```

You can set the [Locale](https://developer.android.com/reference/java/util/Locale.html) format, especially this feature support [Locale(String language, String country)](https://developer.android.com/reference/java/util/Locale.html#Locale(java.lang.String,%20java.lang.String)) so far.


## Retrieval of system settings

You can retrieve the current geo location by executing:

```shell
$ adb shell am broadcast -a io.appium.settings.location -n io.appium.settings/.receivers.LocationInfoReceiver
```

The first value in the returned `data` string is the current latitude, the second is the longitude and the last one is the altitude. An empty string is returned if the data cannot be retrieved (more details on the failure cause can be found in the logcat output).

## Setting Mock Locations

Start sending scheduled updates (every 2s) for mock location with the specified values by executing:
(API versions 26+):
```shell
$ adb shell am start-foreground-service --user 0 -n io.appium.settings/.LocationService --es longitude {longitude-value} --es latitude {latitude-value} [--es altitude {altitude-value}]
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
