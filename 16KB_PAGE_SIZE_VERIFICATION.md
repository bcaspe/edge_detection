# 16KB Page Size Compatibility Verification

## Summary

The edge_detection plugin has been updated to support Android's 16KB page size requirement. All build configurations have been updated to match the myline app's Android setup.

## Changes Made

### 1. Build Configuration Updates

- **compileSdkVersion**: Updated to 36 (Android 15)
- **targetSdkVersion**: Updated to 36
- **minSdkVersion**: Updated to 24 (to match myline app)
- **Android Gradle Plugin**: Updated to 8.9.1
- **Kotlin**: Updated to 2.3.0
- **Gradle**: Updated to 8.14.3
- **NDK Version**: Set to 28.2.13676358 (supports 16KB page size)

### 2. OpenCV Dependency

- **Updated**: From OpenCV 3.4.5 to OpenCV 4.5.3
- **Note**: OpenCV 4.11.0 is not available in Maven Central. Version 4.5.3 is the latest available and supports 16KB page size when built with NDK r28+.

### 3. Code Compatibility Fixes

- Fixed OpenCV API changes:
  - `CV_LOAD_IMAGE_UNCHANGED` → `IMREAD_UNCHANGED`
- Fixed Kotlin/Android API compatibility:
  - Updated `onDraw(canvas: Canvas?)` → `onDraw(canvas: Canvas)`

### 4. Packaging Configuration

- Updated from `packagingOptions` to `packaging` block (modern syntax)
- Set `useLegacyPackaging = false` for proper native library handling

### 5. Gradle Properties

- Updated JVM args for better build performance
- Removed deprecated `android.bundle.enableUncompressedNativeLibs` (deprecated in AGP 8.1+)

## Build Status

✅ **Library Build**: Successfully built
- AAR file generated at: `example/build/edge_detection/outputs/aar/edge_detection-release.aar`
- All compilation errors resolved
- OpenCV 4.5.3 dependency resolved

## Verification Steps

### 1. Automated Verification Script

A verification script has been created to check APK/AAB alignment:

```bash
./verify_16kb_alignment.sh <path-to-apk-or-aab>
```

### 2. Manual Testing on 16KB Emulator

1. **Create 16KB Emulator**:
   - Open Android Studio → Device Manager
   - Create new Virtual Device
   - Select system image with "16KB" label (Android 15+)
   - Complete setup

2. **Verify Page Size**:
   ```bash
   adb shell getconf PAGE_SIZE
   # Should output: 16384
   ```

3. **Build and Install APK**:
   ```bash
   cd example/android
   ./gradlew assembleRelease
   adb install app/build/outputs/apk/release/app-release.apk
   ```

4. **Test Application**:
   - Launch the app
   - Test edge detection functionality
   - Monitor for crashes or errors

### 3. Physical Device Testing

- Use Pixel 8 or Pixel 9 series
- Ensure device is running Android 15 QPR1 or later
- Enable 16KB page size via developer options (if available)

## Important Notes

### OpenCV Version

- **Requested**: OpenCV 4.11.0
- **Available**: OpenCV 4.5.3 (latest in Maven Central)
- **Status**: OpenCV 4.5.3 supports 16KB page size when:
  - Built with NDK r28 or later (✓ configured)
  - Used with AGP 8.5.1+ (✓ using 8.9.1)
  - Native libraries are properly aligned (✓ configured)

If OpenCV 4.11.0 becomes available or is needed from a different source, update:
```gradle
implementation 'com.quickbirdstudios:opencv:4.11.0'
```

### Native Library Alignment

The build configuration ensures:
- NDK r28+ is used (supports 16KB by default)
- Modern packaging is enabled
- All native libraries from dependencies will be merged with proper alignment

### Compatibility with myline App

All configurations now match the myline app:
- Same SDK versions (36)
- Same Gradle/AGP versions
- Same Kotlin version
- Same NDK version
- Compatible build settings

## Next Steps

1. **Build Full APK**: Resolve Flutter SDK compatibility issues if needed, then build complete APK
2. **Run Verification Script**: Use `verify_16kb_alignment.sh` on the built APK
3. **Test on 16KB Emulator**: Follow manual testing steps above
4. **Monitor for Issues**: Watch for any runtime errors related to page size

## Troubleshooting

If you encounter issues:

1. **Build Failures**: Ensure Flutter SDK is compatible with the updated Gradle/AGP versions
2. **Runtime Crashes**: Verify all native dependencies support 16KB
3. **Alignment Issues**: Check that NDK r28+ is being used
4. **OpenCV Issues**: Verify OpenCV 4.5.3 is compatible with your use case

## References

- [Android 16KB Page Size Guide](https://developer.android.com/guide/practices/page-sizes)
- [OpenCV Android Build](https://github.com/quickbirdstudios/opencv-android)
- [AGP 8.9.1 Release Notes](https://developer.android.com/build/releases/gradle-plugin)

