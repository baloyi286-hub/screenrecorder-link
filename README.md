# ScreenRecorder TV

Standalone Android TV screen recorder written in Kotlin.

## Features
- TV remote friendly launcher screen
- Android MediaProjection screen-capture permission flow
- Foreground-service recording
- MP4 video recording
- 720p / 1080p quality selection
- 30 / 60 FPS selection
- Recordings saved in Movies/ScreenRecorderTV
- Persistent notification with Stop action
- Recording timer and status

## Android limitations
Android requires user consent before a normal application can capture the screen.

Apps or video surfaces protected using DRM or FLAG_SECURE may appear black in recordings. This application does not attempt to bypass Android secure-content protections.

## Build
Open in Android Studio and run the app configuration on Android TV / Google TV.

Command line:

```bash
./gradlew assembleDebug
```

APK:

```
app/build/outputs/apk/debug/app-debug.apk
```

Minimum Android version: Android 8.0 (API 26).

Internal audio capture is planned separately because support depends on Android version and whether the source app permits playback capture.
