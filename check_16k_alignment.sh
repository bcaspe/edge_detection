#!/bin/bash

# Script to check 16KB page size alignment of native libraries in an APK
# Usage: ./check_16kb_alignment.sh <path-to-apk>

# Colors for output
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Check if APK path is provided
if [ -z "$1" ]; then
    echo "Usage: $0 <path-to-apk>"
    echo "Example: $0 build/app/outputs/flutter-apk/app-prod-release.apk"
    exit 1
fi

APK_PATH="$1"

# Check if APK file exists
if [ ! -f "$APK_PATH" ]; then
    echo "Error: APK file not found: $APK_PATH"
    exit 1
fi

# Check if readelf is available
READELF="/opt/homebrew/Cellar/binutils/2.45.1/bin/readelf"
if [ ! -f "$READELF" ]; then
    # Try to find readelf in PATH
    if command -v readelf &> /dev/null; then
        READELF="readelf"
    elif command -v greadelf &> /dev/null; then
        READELF="greadelf"
    else
        echo "Error: readelf not found. Please install binutils: brew install binutils"
        exit 1
    fi
fi

# Extract directory name from APK path
EXTRACT_DIR="extracted_apk_$(basename "$APK_PATH" .apk)"

echo "Checking 16KB alignment for: $APK_PATH"
echo "File size: $(du -h "$APK_PATH" | cut -f1)"
echo "=========================================="
echo ""

# Extract the APK (use -o to overwrite without prompting)
echo "Extracting APK (this may take a moment for large files)..."
rm -rf "$EXTRACT_DIR"

# Use -o flag to overwrite files without prompting
if ! unzip -q -o "$APK_PATH" -d "$EXTRACT_DIR" 2>&1; then
    echo "Error: Failed to extract APK"
    exit 1
fi

# Verify extraction succeeded
if [ ! -d "$EXTRACT_DIR" ]; then
    echo "Error: Extraction directory was not created"
    exit 1
fi

# Check 64-bit libraries
TOTAL_LIBS=0
ALIGNED_LIBS=0
NOT_ALIGNED_LIBS=0
UNKNOWN_LIBS=0

for arch in arm64-v8a x86_64; do
    echo "=== Checking $arch libraries (ELF alignment) ==="
    
    # Check if architecture directory exists
    if [ ! -d "$EXTRACT_DIR/lib/$arch" ]; then
        echo "  No $arch libraries found"
        echo ""
        continue
    fi
    
    # Use process substitution to avoid subshell issues with counters
    while IFS= read -r lib; do
        TOTAL_LIBS=$((TOTAL_LIBS + 1))
        basename_lib=$(basename "$lib")
        
        # Use readelf to check ELF segment alignment (what matters for 16KB page size)
        # Check the Align field in the LOAD segments - this is what Android checks at runtime
        ALIGNMENT_INFO=$($READELF -lW "$lib" 2>/dev/null | grep -E "LOAD|Align" | grep "Align" | head -1)
        
        if [ -z "$ALIGNMENT_INFO" ]; then
            echo -e "  ${YELLOW}⚠️${NC}  $basename_lib - Could not determine alignment"
            UNKNOWN_LIBS=$((UNKNOWN_LIBS + 1))
            # Show first LOAD segment for debugging
            $READELF -lW "$lib" 2>/dev/null | grep -A 2 "LOAD" | head -3 | sed 's/^/    /'
        else
            # Extract the alignment value (last field, in hex)
            ALIGN_VALUE=$(echo "$ALIGNMENT_INFO" | awk '{print $NF}')
            
            # Convert hex to decimal for comparison
            ALIGN_DEC=$(printf "%d" "$ALIGN_VALUE" 2>/dev/null || echo "0")
            
            # Check if alignment is >= 16KB (16384 bytes = 0x4000)
            if [ "$ALIGN_DEC" -ge 16384 ]; then
                echo -e "  ${GREEN}✅${NC} $basename_lib - 16KB aligned (ELF alignment: $ALIGN_VALUE = ${ALIGN_DEC} bytes)"
                ALIGNED_LIBS=$((ALIGNED_LIBS + 1))
            elif [ "$ALIGN_DEC" -eq 4096 ]; then
                echo -e "  ${RED}❌${NC} $basename_lib - Only 4KB aligned (ELF alignment: $ALIGN_VALUE = ${ALIGN_DEC} bytes) - NEEDS FIX"
                NOT_ALIGNED_LIBS=$((NOT_ALIGNED_LIBS + 1))
            else
                echo -e "  ${YELLOW}⚠️${NC}  $basename_lib - Alignment: $ALIGN_VALUE (${ALIGN_DEC} bytes) - Check if this is acceptable"
                UNKNOWN_LIBS=$((UNKNOWN_LIBS + 1))
            fi
        fi
    done < <(find "$EXTRACT_DIR/lib/$arch" -name "*.so" 2>/dev/null)
    echo ""
done

# Summary
echo "=========================================="
echo "Summary:"
echo "  Total 64-bit libraries checked: $TOTAL_LIBS"
echo -e "  ${GREEN}✅ 16KB aligned: $ALIGNED_LIBS${NC}"
echo -e "  ${RED}❌ Not aligned (4KB): $NOT_ALIGNED_LIBS${NC}"
if [ $UNKNOWN_LIBS -gt 0 ]; then
    echo -e "  ${YELLOW}⚠️  Unknown: $UNKNOWN_LIBS${NC}"
fi

# Cleanup
echo ""
read -p "Clean up extracted files? (y/n) " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    rm -rf "$EXTRACT_DIR"
    echo "Cleaned up extracted files"
fi

# Exit with error code if any libraries are not aligned
if [ $NOT_ALIGNED_LIBS -gt 0 ]; then
    exit 1
else
    exit 0
fi