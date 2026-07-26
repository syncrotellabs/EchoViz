# EchoViz

EchoViz is a native Android accessibility prototype for low-vision reading. It provides a movable floating Echo logo button that opens quick controls for larger text, Reading Mode, MagLens, and app shutdown.

It started as a practical app for my wife: Android's built-in options help, but they can feel clumsy, and she needed a better way to read and navigate on her phone.

The app is built for a real-world use case: making phone reading more practical for someone with severe vision loss while keeping the controls simple enough to use repeatedly.

## Current Features

- Movable floating Echo logo button over other apps.
- Bubble can be dragged vertically or horizontally and snaps to the left or right screen edge.
- Bubble position is remembered between service restarts.
- Floating menu opens inward from whichever side the bubble is on.
- Quick text controls: `-`, `100%`, and `+`.
- Text scaling is relative to the user's captured baseline, so EchoViz `100%` means "the phone text size when setup was completed."
- Reading Mode for large, calm display of shared text or text exposed through Android Accessibility.
- Reading Mode includes a `Back to page` button that returns to the underlying app/task.
- MagLens uses Android magnification at 2x.
- MagLens includes a translucent center pan handle and an on-lens close button.
- Floating overlays hide on the launcher/home screen.
- `Exit EchoViz` confirmation can shut down the overlay without disabling the Accessibility permission.
- Launching EchoViz again restores the overlay service state.

## Important Android Behavior

EchoViz uses Android Accessibility APIs and system text-size settings. That means a few behaviors are intentionally system-level:

- The `+`, `100%`, and `-` buttons adjust Android's system font scale relative to EchoViz's saved baseline.
- Android does not allow one accessibility overlay to directly restyle text inside every third-party app or webpage independently.
- Some apps expose rich text to Accessibility; others expose very little. Reading Mode quality depends on what the foreground app makes available.
- MagLens uses Android's built-in magnification controller, so it magnifies the screen rather than rewriting app layouts.

## Privacy

EchoViz is designed to avoid storing or transmitting screen content.

- No analytics are included.
- No backend service is included.
- No screen content is uploaded.
- Shared or accessible text may be displayed locally in Reading Mode.
- The app stores only local preferences such as font baseline, requested text scale, runtime active state, and floating bubble position.

## Permissions

EchoViz currently requests:

- `android.permission.BIND_ACCESSIBILITY_SERVICE`
  - Required for the floating accessibility overlay, visible text extraction, and magnification control.
- `android.permission.WRITE_SETTINGS`
  - Required to adjust Android's system font scale.
- `android.permission.INTERNET`
  - Present in the manifest, though the current prototype does not use a network backend.

Android requires the user to manually enable Accessibility and system setting modification. EchoViz's setup screen guides the user to both places.

## Project Structure

```text
EchoViz/
  app/
    src/main/
      AndroidManifest.xml
      java/com/example/echoviz/
        EchoVizAccessibilityService.java
        EchoVizRuntime.java
        FontScaleBaseline.java
        MainActivity.java
        ReaderActivity.java
      res/
        drawable/
        drawable-nodpi/
        values/
        xml/
  build.gradle
  settings.gradle
  gradle.properties
  gradlew
  gradlew.bat
```

## Development Setup

### Requirements

- Android Studio or Android SDK command-line tools.
- JDK 17.
- Android SDK Platform 36.
- Android SDK Build Tools compatible with Android Gradle Plugin 8.10.1.

The Gradle wrapper is included, so a separate Gradle install is not required.

### Build

From the repo root:

```powershell
.\gradlew.bat assembleDebug
```

The debug APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Install On A Connected Phone

Enable USB debugging on the Android phone, connect it to the computer, then run:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

After installing:

1. Open EchoViz.
2. Enable `EchoViz Floating Controls` in Android Accessibility settings.
3. Allow system setting modification for text-size controls.
4. Use `Use Current as 100%` if the current phone font size should become the EchoViz baseline.

## Emulator Smoke Test

Build the APK:

```powershell
.\gradlew.bat assembleDebug
```

Install it on a running emulator:

```powershell
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Enable the accessibility service for local testing:

```powershell
adb shell settings put secure enabled_accessibility_services com.example.echoviz/com.example.echoviz.EchoVizAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

Launch EchoViz:

```powershell
adb shell am start -n com.example.echoviz/.MainActivity
```

## Play Store Notes

Before Play Store release, EchoViz will need:

- Production package/application id instead of `com.example.echoviz`.
- Release signing key and Android App Bundle build.
- Privacy policy.
- Store listing assets.
- Accessibility permission disclosure written in plain language.
- Data safety form aligned with the app's actual behavior.
- Testing across Android versions and manufacturer skins.
- Careful review of whether `INTERNET` is still needed.
- Accessibility UX pass with the intended users.

Accessibility apps are reviewed closely by Google Play, so the app description and in-app setup should clearly explain why Accessibility is required and what EchoViz does with visible text.

## Build Status

Current local verification:

- `.\gradlew.bat assembleDebug` passes.
- Floating bubble appears over non-home apps.
- Bubble drags to both sides and saves position.
- Menu opens inward from the right side.

## License

No license has been selected yet.
