# Settings

Toggle settings in Android device or emulator.

A small and simple Android application that deals with the system settings. Then the application shuts down.

## Requirements

* [Android SDK](developer.android.com)
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

## Running

Once installed on a device, you can change the `wifi` and `data` settings through the following commands:

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

On Android 6.0+ you must enable the corresponding permissions for the app first. This can be
done in application settings, Permissions entry.

Switching mobile data on/off requires the phone to be rooted on Android 5.0+
('su' binary is expected to be available on internal phone file system).
Read [this](http://stackoverflow.com/questions/26539445/the-setmobiledataenabled-method-is-no-longer-callable-as-of-android-l-and-later)
StackOveflow thread for more details.

Voila!

## Caveats

There are certain system services which cannot be accessed through an application. Two ones central here are `airplane_mode` and `gps`.

## License

Apache License 2.0
