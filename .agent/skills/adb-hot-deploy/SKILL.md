---
description: Fast deploy via Gradle & ADB without opening Android Studio
---
# ADB Hot Deploy Workflow

This skill is designed for testing Android project changes when the emulator is running standalone. By keeping Android Studio closed, you save significant system resources (RAM/CPU). This workflow ensures the correct Java environment is set, compiles the APK with Gradle, installs it over ADB, and launches the app instantly.

## Requirements
- Android Emulator must be running (e.g., via `emulator.exe -avd Pixel_8`).
- You must be operating in the Android project root directory where `gradlew` is located.

## Automate Deployment

When the user asks to "test the app", "deploy the app", or "run it on the emulator" while avoiding Android Studio:

// turbo-all
1. First, always increment the `versionName` (e.g. 0.5.1 to 0.5.2) and `versionCode` in `app/build.gradle.kts` before deploying, as requested by the user.

2. Stop the app to clear memory caches, compile the APK, force-install, and explicitly launch the Dashboard Activity:
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; & "C:\Users\jinab\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell am force-stop com.example.mqttpanelcraft; ./gradlew assembleDebug; if ($LASTEXITCODE -eq 0) { & "C:\Users\jinab\AppData\Local\Android\Sdk\platform-tools\adb.exe" install -r app\build\outputs\apk\debug\app-debug.apk; & "C:\Users\jinab\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell am start -n com.example.mqttpanelcraft/.DashboardActivity }
```
