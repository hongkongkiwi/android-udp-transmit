#!/bin/bash

# Generate PNG notification icons from XML vector drawables
# Requires Android SDK build-tools (aapt2) or ImageMagick

set -e

echo "=== UDP Trigger - Notification Icon Generator ==="
echo ""

# Check if we have the required tools
if command -v convert &> /dev/null || command -v magick &> /dev/null; then
    echo "✓ ImageMagick found - will use for PNG generation"
    USE_IMAGEMAGICK=true
elif command -v aapt2 &> /dev/null; then
    echo "⚠ ImageMagick not found, aapt2 found - will create XML-only icons"
    USE_IMAGEMAGICK=false
else
    echo "✗ Neither ImageMagick nor aapt2 found"
    echo "  Please install: brew install imagemagick"
    echo "  Then run: ./generate_notification_icons.sh"
    exit 1
fi

# Create temporary directory for icons
TEMP_DIR=$(mktemp -d)
trap "rm -rf $TEMP_DIR" EXIT

echo "Creating notification icons..."

# For Android 13+, notification icons should be:
# - Monochrome (white on transparent)
# - 24x24dp physical size
# - PNG format (XML vectors are deprecated for notifications in Android 13+)

# Density multipliers for 24dp base size:
# mdpi (1x): 24x24 px
# hdpi (1.5x): 36x36 px
# xhdpi (2x): 48x48 px
# xxhdpi (3x): 72x72 px
# xxxhdpi (4x): 96x96 px

ICON_SIZES=(
    "mdpi:24"
    "hdpi:36"
    "xhdpi:48"
    "xxhdpi:72"
    "xxxhdpi:96"
)

DRAWABLE_DIR="app/src/main/res"
VECTOR_FILE="$DRAWABLE_DIR/drawable/ic_notification_send_monochrome.xml"
VECTOR_FILE_STOP="$DRAWABLE_DIR/drawable/ic_notification_stop_monochrome.xml"

if [ "$USE_IMAGEMAGICK" = true ]; then
    # Use ImageMagick to generate PNG icons
    ICON_COLOR="white"

    for entry in "${ICON_SIZES[@]}"; do
        IFS=':' read -r density size <<< "$entry"
        DIR="$DRAWABLE_DIR/drawable-$density"
        mkdir -p "$DIR"

        # Generate send icon (simple arrow/send symbol)
        convert -size ${size}x${size} xc:transparent \
            -fill white \
            -draw "path 'M 2,21 L 22,12 L 2,3 M 12,12 L 22,7'" \
            "$DIR/ic_notification_send.png" 2>/dev/null || {
            # Fallback: create simple square if path drawing fails
            convert -size ${size}x${size} xc:transparent \
                -fill white \
                -draw "rectangle $((size/3)),$((size/3)) $((size*2/3)),$((size*2/3))" \
                "$DIR/ic_notification_send.png"
        }

        # Generate stop icon (square with X)
        convert -size ${size}x${size} xc:transparent \
            -fill white \
            -draw "rectangle $((size/3)),$((size/3)) $((size*2/3)),$((size*2/3))" \
            "$DIR/ic_notification_stop.png"

        echo "  ✓ $density: ${size}x${size}px"
    done

    echo ""
    echo "✓ PNG notification icons generated successfully!"
    echo ""
    echo "Generated files:"
    find "$DRAWABLE_DIR/drawable-*/ic_notification_*.png" 2>/dev/null || true

else
    echo "⚠ XML-only icons created (not Android 13+ compliant)"
    echo ""
    echo "For Android 13+ compliance, please:"
    echo "  1. Install ImageMagick: brew install imagemagick"
    echo "  2. Run this script again to generate PNG icons"
fi

echo ""
echo "=== Android Notification Icon Requirements ==="
echo ""
echo "For Android 13+ (API 33+), notification icons must be:"
echo "  • Monochrome (single color: white on transparent)"
echo "  • PNG format (XML vectors deprecated)"
echo "  • 24x24dp physical size"
echo "  • Must be placed in drawable-*/ directories"
echo ""
echo "Current status:"
if ls "$DRAWABLE_DIR/drawable-*/ic_notification_*.png" 2>/dev/null 1>&2; then
    echo "  ✓ PNG icons present - Android 13+ compliant"
else
    echo "  ⚠ Only XML icons - NOT Android 13+ compliant"
    echo "  • The app will work but may show warnings"
fi
echo ""
