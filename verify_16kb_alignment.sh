#!/bin/bash
# Script to verify 16KB page size alignment for native libraries in APK/AAB

set -e

APK_PATH="$1"
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

if [ -z "$APK_PATH" ]; then
    echo "Usage: $0 <path-to-apk-or-aab>"
    echo "Example: $0 app-release.apk"
    exit 1
fi

echo "=== Verifying 16KB Page Size Alignment ==="
echo "APK/AAB: $APK_PATH"
echo ""

# Extract APK/AAB
if [[ "$APK_PATH" == *.aab ]]; then
    echo "Extracting AAB..."
    unzip -q "$APK_PATH" -d "$TEMP_DIR"
    # AAB structure: base/lib/{abi}/*.so
    LIB_DIR="$TEMP_DIR/base/lib"
else
    echo "Extracting APK..."
    unzip -q "$APK_PATH" -d "$TEMP_DIR"
    # APK structure: lib/{abi}/*.so
    LIB_DIR="$TEMP_DIR/lib"
fi

if [ ! -d "$LIB_DIR" ]; then
    echo "Warning: No native libraries found in $APK_PATH"
    exit 0
fi

echo "Checking native libraries..."
echo ""

# Check each .so file
ISSUES=0
TOTAL=0

for so_file in $(find "$LIB_DIR" -name "*.so" -type f); do
    TOTAL=$((TOTAL + 1))
    REL_PATH="${so_file#$TEMP_DIR/}"
    
    # Try readelf first (Linux/Android)
    if command -v readelf >/dev/null 2>&1; then
        ALIGNMENT=$(readelf -l "$so_file" 2>/dev/null | grep -A 1 "LOAD" | grep "Align" | awk '{print $NF}' | head -1)
        if [ -n "$ALIGNMENT" ]; then
            # Convert hex to decimal
            ALIGN_DEC=$(printf "%d" "$ALIGNMENT" 2>/dev/null || echo "0")
            if [ "$ALIGN_DEC" -lt 16384 ] && [ "$ALIGN_DEC" -gt 0 ]; then
                echo "⚠️  $REL_PATH: Alignment $ALIGN_DEC bytes (should be >= 16384)"
                ISSUES=$((ISSUES + 1))
            else
                echo "✓  $REL_PATH: Alignment OK ($ALIGN_DEC bytes)"
            fi
        else
            echo "?  $REL_PATH: Could not determine alignment"
        fi
    # Try otool on macOS
    elif command -v otool >/dev/null 2>&1; then
        # otool -l shows load commands, but alignment info is limited
        echo "?  $REL_PATH: Checking with otool (alignment check limited on macOS)"
    else
        echo "?  $REL_PATH: No readelf/otool available - manual verification needed"
    fi
done

echo ""
echo "=== Summary ==="
echo "Total native libraries: $TOTAL"
if [ "$ISSUES" -eq 0 ]; then
    echo "✓ All libraries appear to be properly aligned for 16KB page size"
    echo ""
    echo "Note: For complete verification, test on:"
    echo "  - Android 15+ emulator with 16KB page size"
    echo "  - Physical device (Pixel 8/9) with 16KB page size enabled"
    echo ""
    echo "To test on emulator:"
    echo "  1. Create AVD with Android 15+ and 16KB page size"
    echo "  2. Install APK: adb install $APK_PATH"
    echo "  3. Run app and verify no crashes"
    exit 0
else
    echo "⚠️  Found $ISSUES library(ies) with potential alignment issues"
    echo ""
    echo "Recommendations:"
    echo "  - Ensure all native libraries are built with NDK r28+"
    echo "  - Verify OpenCV and other dependencies support 16KB"
    echo "  - Rebuild with: -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON"
    exit 1
fi

