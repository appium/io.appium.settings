# Settings

Toggle settings in Android device or emulator.

A small and simple Android application that deals with the system settings. Then the application shuts down.

## Requirements

* [Android SDK](developer.android.com)
* [Java JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
* [Apache ant](http://ant.apache.org/)

## Building

```shell
$ cd into/this/repo
$ ant debug
```

You can also run `ant debug install` to build and immediately deploy the app to a connected Android device or emulator.

## Installing

You can install the apk through the [Android Debug Bridge](http://developer.android.com/tools/help/adb.html).

To install:

```shell
$ cd into/this/repo
$ adb install bin/settings_apk-debug.apk
```

To uninstall:

```shell
$ adb uninstall io.appium.settings
```

## Running

Once installed on a device, you can change the `wifi` and `data` settings through the following commands:

To turn on `wifi`:

```shell
$ adb shell am start -n io.appium.settings/.Settings -e wifi on
```

To turn off `wifi`:

```shell
$ adb shell am start -n io.appium.settings/.Settings -e wifi off
```

To turn on `data`:

```shell
$ adb shell am start -n io.appium.settings/.Settings -e data on
```

To turn off `data`:

```shell
$ adb shell am start -n io.appium.settings/.Settings -e data off
```

The two can be changed at the same time, as well:

```shell
$ adb shell am start -n io.appium.settings/.Settings -e wifi on -e data off
```

Voila!


## Caveats

There are certain system services which cannot be accessed through an application. Two ones central here are `airplane_mode` and `gps`.

## License

Apache License 2.0
