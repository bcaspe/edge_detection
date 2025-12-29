# Ensuring libflutter.so is 16KB Page Size Compatible

## Overview

`libflutter.so` is the Flutter engine library that comes bundled with your Flutter SDK. Unlike plugin native libraries, you don't build it yourself - it's provided by the Flutter SDK version you're using.

## Requirements

### 1. Flutter SDK Version

**Flutter 3.24.0 or later** includes 16KB page size support for `libflutter.so`. Earlier versions may not be properly aligned.

**Check your Flutter version:**
```bash
flutter --version
```

**If you need to upgrade:**
```bash
flutter upgrade
```

### 2. Build Configuration

The build configuration we've set up ensures proper handling:

✅ **Already Configured:**
- Android Gradle Plugin 8.9.1 (supports 16KB)
- NDK 28.2.13676358 (supports 16KB)
- compileSdkVersion 36 (Android 15)
- Modern packaging configuration

### 3. Flutter Gradle Plugin

The Flutter Gradle Plugin (included with Flutter SDK) automatically handles `libflutter.so` alignment when:
- Using AGP 8.5.1+ (✓ we're using 8.9.1)
- Building with Flutter 3.24+
- Using NDK r28+ (✓ configured)

## Verification Steps

### Step 1: Verify Flutter Version

```bash
cd /Users/briancaspe/projects/flutter_projects/edge_detection/example
flutter --version
```

Look for version **3.24.0 or higher**. If lower, upgrade:
```bash
flutter upgrade
```

### Step 2: Build the APK

```bash
cd example
flutter clean
flutter pub get
flutter build apk --release
```

### Step 3: Check libflutter.so Alignment

**Option A: Using Android Studio**
1. Open Android Studio
2. Go to **Build > Analyze APK**
3. Select: `example/build/app/outputs/flutter-apk/app-release.apk`
4. Navigate to `lib/arm64-v8a/libflutter.so`
5. Check the alignment - should show **16384 (16KB)**

**Option B: Using Command Line**

```bash
# Extract and check libflutter.so
cd /tmp
unzip -q /Users/briancaspe/projects/flutter_projects/edge_detection/example/build/app/outputs/flutter-apk/app-release.apk lib/arm64-v8a/libflutter.so -d flutter_check
readelf -l flutter_check/lib/arm64-v8a/libflutter.so | grep -A 1 "LOAD" | grep "Align"
```

Look for alignment values of `0x4000` (16384 bytes = 16KB) or higher.

**Option C: Use the verification script**

```bash
./verify_16kb_alignment.sh example/build/app/outputs/flutter-apk/app-release.apk
```

## If libflutter.so is NOT 16KB Aligned

### Solution 1: Upgrade Flutter SDK

```bash
flutter upgrade
flutter clean
flutter pub get
flutter build apk --release
```

### Solution 2: Use Flutter Channel with 16KB Support

If stable doesn't have it yet, try beta or master:

```bash
flutter channel beta  # or master
flutter upgrade
flutter clean
flutter pub get
flutter build apk --release
```

### Solution 3: Verify Flutter Engine Build

The Flutter engine is pre-built by Google. If your Flutter version is 3.24+ and it's still not aligned, it might be a Flutter SDK issue. Check:

```bash
# Check Flutter engine version
flutter doctor -v
```

Look for the engine version. Flutter 3.24+ should have engine version that supports 16KB.

## Current Configuration Status

✅ **Build Tools:**
- AGP 8.9.1 (supports 16KB)
- NDK 28.2.13676358 (supports 16KB)
- Gradle 8.14.3
- compileSdkVersion 36

✅ **Packaging:**
- Modern packaging enabled
- Proper native library handling

⚠️ **Action Required:**
- Verify Flutter SDK version is 3.24.0+
- Rebuild APK after Flutter upgrade if needed

## Testing on 16KB Device

After building, test on a 16KB device/emulator:

```bash
# Create 16KB emulator (Android 15+)
# Then install and test
adb install example/build/app/outputs/flutter-apk/app-release.apk
adb shell getconf PAGE_SIZE  # Should output: 16384
```

## Notes

- `libflutter.so` is provided by Flutter SDK, not built by your project
- Flutter 3.24+ includes 16KB support in the engine
- The Flutter Gradle Plugin handles alignment automatically
- Your plugin's native libraries (OpenCV, etc.) are already configured for 16KB
- The build configuration ensures all libraries are properly aligned during APK creation

## Troubleshooting

**Issue: libflutter.so shows 4KB alignment**

**Solution:**
1. Upgrade Flutter to 3.24+
2. Clean build: `flutter clean && flutter build apk --release`
3. Verify again

**Issue: Flutter version is 3.24+ but still not aligned**

**Solution:**
1. Check if you're using a custom Flutter engine build
2. Try switching Flutter channels: `flutter channel stable`
3. Report issue to Flutter team if it persists

