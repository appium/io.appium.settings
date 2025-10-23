# SDK Version Update Changelog

This document describes the changes made to update the minimum and target SDK versions.

## Changes Made

### SDK Version Updates
- **minSdkVersion**: Bumped from 21 (Android 5.0) to 26 (Android 8.0)
- **targetSdkVersion**: Bumped from 32 (Android 12L) to 35 (Android 15)
- **compileSdk**: Updated from 32 to 35

### Manifest Changes
Added required foreground service type permissions for Android 14+ compliance:
- `FOREGROUND_SERVICE_LOCATION` - Required for location tracking services
- `FOREGROUND_SERVICE_MEDIA_PROJECTION` - Required for screen recording service

Updated service declarations to specify foreground service types:
- `LocationService`: Added `foregroundServiceType="location"`
- `ForegroundService`: Added `foregroundServiceType="location"`
- `RecorderService`: Already had `foregroundServiceType="mediaProjection"`

## Android API Changes Summary

### Android 13 (API 33)
**Key Changes:**
- **Notification Permission (Breaking)**: Apps must request runtime permission `POST_NOTIFICATIONS` to show notifications
- **Granular Media Permissions**: Replaces `READ_EXTERNAL_STORAGE` with specific permissions:
  - `READ_MEDIA_IMAGES`
  - `READ_MEDIA_VIDEO`
  - `READ_MEDIA_AUDIO`
- **Per-app Language Preferences**: Users can set language preferences per app
- **Themed App Icons**: Support for themed icons that adapt to user's wallpaper
- **Clipboard Privacy**: Clipboard access restricted to foreground apps or default IME
- **Exact Alarms**: New permission `SCHEDULE_EXACT_ALARM` required for exact alarms
- **Nearby Wi-Fi Devices**: New permission required for discovering nearby Wi-Fi devices

**Impact on io.appium.settings:**
- The app uses notifications for foreground services. Starting with API 33, these require runtime permission (already handled by system for foreground service notifications)
- Clipboard functionality is already IME-based, so it should continue working

### Android 14 (API 34)
**Key Changes:**
- **Foreground Service Types (Breaking)**: All foreground services must declare specific types
  - Available types: camera, connectedDevice, dataSync, health, location, mediaPlayback, mediaProjection, microphone, phoneCall, remoteMessaging, shortService, specialUse, systemExempted
  - Requires corresponding permissions (e.g., `FOREGROUND_SERVICE_LOCATION`)
- **Non-SDK Interface Restrictions**: Further restrictions on accessing hidden/internal Android APIs
- **Implicit Intent Restrictions**: Implicit intents must specify package or component explicitly
- **Dynamic Code Loading**: Enhanced security for loading code from app-writable locations
- **Screenshot Detection API**: Apps can register callbacks for screenshot events
- **Regional Preferences API**: Enhanced support for region-specific preferences

**Impact on io.appium.settings:**
- **Action Required**: Foreground services now declare appropriate types:
  - `LocationService` uses `location` type for mock location functionality
  - `ForegroundService` uses `location` type as it tracks location
  - `RecorderService` already uses `mediaProjection` type for screen recording
- May need to review any reflection or non-SDK API usage in the codebase

### Android 15 (API 35)
**Key Changes:**
- **16 KB Page Size Support (Breaking)**: Apps must support devices with 16 KB memory page sizes (vs traditional 4 KB)
  - Affects buffer sizes, memory alignment, and NDK usage
  - Most apps are compatible, but should be tested
- **Private Space**: Users can create isolated environments for sensitive apps
- **Enhanced Media Projection Security**: Stricter controls on screen recording
- **Background Activity Restrictions**: Even stricter restrictions on starting activities from background
- **Package Stopped State**: More restrictions on apps in stopped state (after force-stop or fresh boot)
- **OpenJDK 17 APIs**: Android Runtime updated to OpenJDK 17 APIs
- **Predictive Back Gesture**: Enhanced support for predictive back animations
- **Satellite Connectivity**: Support for satellite-based messaging (hardware-dependent)

**Impact on io.appium.settings:**
- 16 KB page size support: Likely compatible as the app doesn't use NDK or custom memory management
- Enhanced media projection security aligns with existing permissions model
- Should be tested on devices/emulators with 16 KB page size if available

## Compatibility Notes

### Minimum SDK 26 (Android 8.0) Support
By setting minSdkVersion to 26, the app no longer supports:
- Android 5.0 Lollipop (API 21)
- Android 5.1 Lollipop (API 22)
- Android 6.0 Marshmallow (API 23)
- Android 7.0 Nougat (API 24)
- Android 7.1 Nougat (API 25)

**Rationale:**
- Android 8.0+ accounts for 95%+ of active Android devices (as of 2024)
- Reduces maintenance burden for legacy Android versions
- Enables use of modern Android APIs without extensive compatibility code
- Aligns with Google Play's minimum API level recommendations

### Testing Recommendations

1. **Foreground Services**: Verify all foreground services start correctly on API 34+ devices
2. **Notifications**: Test notification display for foreground services
3. **Location Services**: Verify mock location functionality works on API 26-35
4. **Media Projection**: Test screen recording on API 34-35 with enhanced security
5. **Permissions**: Verify all runtime permissions are properly requested and handled
6. **Background Execution**: Test that background operations work within API 35 restrictions

### Known Behavioral Changes

1. **Notification Channels**: All notifications must use notification channels (API 26+)
   - Already implemented in the app
   
2. **Background Execution Limits**: Starting with API 26, background execution is more restricted
   - Foreground services are the recommended approach (already used)

3. **Installation from Unknown Sources**: Changed to per-app permission in API 26+
   - Not directly applicable to this app

4. **Implicit Broadcast Restrictions**: Many implicit broadcasts are restricted
   - App uses explicit broadcast receivers, should not be affected

## Migration Guide for Downstream Users

If you're using io.appium.settings in your automation framework:

1. **Minimum Android Version**: Target devices must run Android 8.0 (API 26) or higher
2. **Permissions**: Ensure all necessary permissions are granted before using app features:
   ```bash
   adb shell pm grant io.appium.settings android.permission.ACCESS_FINE_LOCATION
   adb shell pm grant io.appium.settings android.permission.CHANGE_CONFIGURATION
   adb shell pm grant io.appium.settings android.permission.SET_ANIMATION_SCALE
   adb shell pm grant io.appium.settings android.permission.RECORD_AUDIO
   adb shell appops set io.appium.settings android:mock_location allow
   adb shell appops set io.appium.settings PROJECT_MEDIA allow
   ```
3. **Testing**: Test your automation scripts on Android 14 and 15 devices to ensure compatibility

## References

- [Android 13 Behavior Changes](https://developer.android.com/about/versions/13/behavior-changes-all)
- [Android 14 Behavior Changes](https://developer.android.com/about/versions/14/behavior-changes-all)
- [Android 15 Behavior Changes](https://developer.android.com/about/versions/15/behavior-changes-all)
- [Foreground Service Types](https://developer.android.com/develop/background-work/services/fg-service-types)
- [Android API Levels](https://apilevels.com/)
