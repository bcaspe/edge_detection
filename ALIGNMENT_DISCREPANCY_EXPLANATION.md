# Understanding Android Studio vs Script Alignment Discrepancy

## The Issue

You're seeing different results:
- **Android Studio APK Analyzer**: Shows `libflutter.so` as 16KB aligned ✅
- **check_16k_alignment.sh script**: Shows `libflutter.so` as 4KB aligned ❌

## Why This Happens

### Two Different Types of Alignment

1. **File Alignment (APK/ZIP level)** - What Android Studio shows
   - This is how the `.so` file is stored inside the APK (ZIP archive)
   - Android Studio's APK Analyzer checks the file alignment in the ZIP structure
   - This can be 16KB even if the ELF alignment is 4KB

2. **ELF Segment Alignment (Binary level)** - What the script checks
   - This is the actual alignment of memory segments in the ELF binary
   - This is what Android checks at **runtime** when loading the library
   - This is what matters for 16KB page size compatibility

### What Actually Matters

**The ELF segment alignment is what matters for 16KB page size support!**

When Android loads a native library on a device with 16KB page size:
- It checks the ELF segment alignment in the binary itself
- If segments are only aligned to 4KB (0x1000), the library may fail to load or crash
- File alignment in the APK doesn't affect runtime behavior

## How to Verify the Real Alignment

### Method 1: Check ELF Alignment Directly

```bash
# Extract libflutter.so from APK
unzip -p your-app.apk lib/arm64-v8a/libflutter.so > /tmp/libflutter.so

# Check ELF segment alignment (what matters!)
/opt/homebrew/Cellar/binutils/2.45.1/bin/readelf -lW /tmp/libflutter.so | grep -A 1 "LOAD" | grep "Align"
```

Look for the `Align` field. It should show:
- `0x4000` (16384 bytes = 16KB) ✅ - Correct
- `0x1000` (4096 bytes = 4KB) ❌ - Will fail on 16KB devices

### Method 2: Use the Updated Script

The script has been updated to show the actual ELF alignment value, not just a pass/fail. Run it again:

```bash
./check_16k_alignment.sh your-app.apk
```

It will now show the actual alignment values like:
```
✅ libflutter.so - 16KB aligned (ELF alignment: 0x4000 = 16384 bytes)
❌ libflutter.so - Only 4KB aligned (ELF alignment: 0x1000 = 4096 bytes) - NEEDS FIX
```

## Why Android Studio Shows 16KB

Android Studio's APK Analyzer might be:
1. Checking file alignment in the ZIP (which can be different)
2. Using a different method to determine alignment
3. Showing cached or incorrect information
4. Checking a different property (like file size alignment)

**Important**: Trust the ELF alignment check, not the APK Analyzer alignment for 16KB page size compliance.

## What to Do

### If ELF Alignment is 4KB (0x1000)

1. **Upgrade Flutter SDK**:
   ```bash
   flutter upgrade
   flutter clean
   flutter build apk --release
   ```

2. **Check Flutter Version**:
   ```bash
   flutter --version
   ```
   You need Flutter 3.24.0+ for 16KB support in libflutter.so

3. **Verify Engine Version**:
   ```bash
   flutter doctor -v
   ```
   Check the engine version - it should support 16KB

4. **If Still Not Working**:
   - Try Flutter beta/master channel
   - Report issue to Flutter team
   - Consider using a custom Flutter engine build (advanced)

### If ELF Alignment is 16KB (0x4000)

You're good! The script was checking correctly, and Android Studio might have been showing something else.

## Summary

- **Trust the script's ELF alignment check** - it checks what Android actually uses at runtime
- **Android Studio APK Analyzer** may show file alignment, which is different
- **ELF segment alignment** is what determines 16KB page size compatibility
- **The updated script** now shows actual alignment values for clarity

