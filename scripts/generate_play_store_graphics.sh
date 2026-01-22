#!/bin/bash

# Generate Play Store graphics for UDP Trigger
# Creates feature graphic (1024x512) and app icon (512x512)

set -e

echo "=== UDP Trigger - Play Store Graphics Generator ==="
echo ""

OUTPUT_DIR="play_store_assets"
mkdir -p "$OUTPUT_DIR"

# Check for ImageMagick
if ! command -v convert &> /dev/null && ! command -v magick &> /dev/null; then
    echo "⚠ ImageMagick not found!"
    echo "  Install with: brew install imagemagick"
    echo ""
    echo "Creating placeholder SVG files instead..."
    CREATE_SVG=true
else
    CREATE_SVG=false
fi

# Colors from app theme (Material 3)
PRIMARY="#6750A4"
PRIMARY_LIGHT="#EADDFF"
PRIMARY_ON="#FFFFFF"
BACKGROUND="#6750A4"
TEXT="#FFFFFF"

# Function to generate feature graphic
generate_feature_graphic() {
    echo "Generating 1024x512 feature graphic..."

    if [ "$CREATE_SVG" = true ]; then
        cat > "$OUTPUT_DIR/feature_graphic.svg" <<'EOF'
<svg width="1024" height="512" xmlns="http://www.w3.org/2000/svg">
  <rect width="1024" height="512" fill="#6750A4"/>

  <!-- App Icon Circle -->
  <circle cx="150" cy="256" r="80" fill="#EADDFF"/>
  <text x="150" y="280" font-family="sans-serif" font-size="48" text-anchor="middle" fill="#6750A4" font-weight="bold">UDP</text>

  <!-- App Name -->
  <text x="300" y="200" font-family="sans-serif" font-size="72" fill="#FFFFFF" font-weight="bold">UDP Trigger</text>

  <!-- Tagline -->
  <text x="300" y="280" font-family="sans-serif" font-size="36" fill="#EADDFF">Low-latency packet transmission</text>

  <!-- Features -->
  <text x="300" y="340" font-family="sans-serif" font-size="28" fill="#FFFFFF">• Automation &amp; Macros</text>
  <text x="300" y="380" font-family="sans-serif" font-size="28" fill="#FFFFFF">• Network Discovery</text>
  <text x="300" y="420" font-family="sans-serif" font-size="28" fill="#FFFFFF">• Hardware Keyboard Support</text>
  <text x="300" y="460" font-family="sans-serif" font-size="28" fill="#FFFFFF">• Background Service</text>
</svg>
EOF
        echo "  ✓ Created: $OUTPUT_DIR/feature_graphic.svg"
        echo "    Convert to PNG with: inkscape feature_graphic.svg -o feature_graphic.png"
    else
        # Create PNG using ImageMagick
        convert -size 1024x512 xc:"#6750A4" "$OUTPUT_DIR/temp_bg.png"

        # Add app icon (circle)
        convert "$OUTPUT_DIR/temp_bg.png" \
            -fill "#EADDFF" \
            -draw "circle 150,256 80" \
            "$OUTPUT_DIR/temp1.png"

        # Add text (simple representation)
        convert "$OUTPUT_DIR/temp1.png" \
            -fill "#FFFFFF" \
            -font Helvetica-Bold \
            -pointsize 72 \
            -gravity northwest \
            -annotate +300+200 "UDP Trigger" \
            -pointsize 36 \
            -annotate +300+280 "Low-latency packet transmission" \
            "$OUTPUT_DIR/feature_graphic.png"

        rm -f "$OUTPUT_DIR/temp_bg.png" "$OUTPUT_DIR/temp1.png"
        echo "  ✓ Created: $OUTPUT_DIR/feature_graphic.png"
    fi
}

# Function to generate app icon (512x512)
generate_app_icon() {
    echo "Generating 512x512 app icon..."

    if [ "$CREATE_SVG" = true ]; then
        cat > "$OUTPUT_DIR/app_icon.svg" <<'EOF'
<svg width="512" height="512" xmlns="http://www.w3.org/2000/svg">
  <defs>
    <linearGradient id="grad" x1="0%" y1="0%" x2="100%" y2="100%">
      <stop offset="0%" style="stop-color:#6750A4;stop-opacity:1" />
      <stop offset="100%" style="stop-color:#4F378B;stop-opacity:1" />
    </linearGradient>
  </defs>

  <!-- Background -->
  <rect width="512" height="512" fill="url(#grad)" rx="80"/>

  <!-- Icon symbol - Network/UDP representation -->
  <g transform="translate(256,256)">
    <!-- Outer circle -->
    <circle r="120" fill="none" stroke="#FFFFFF" stroke-width="12"/>

    <!-- UDP packet representation -->
    <path d="M-60,-40 L60,40 M-60,40 L60,-40" stroke="#FFFFFF" stroke-width="12" stroke-linecap="round"/>

    <!-- Signal waves -->
    <path d="M90,-90 Q120,-90 150,-90" fill="none" stroke="#EADDFF" stroke-width="8" stroke-linecap="round"/>
    <path d="M100,-110 Q150,-110 200,-110" fill="none" stroke="#EADDFF" stroke-width="8" stroke-linecap="round"/>
  </g>

  <!-- Safe zone reference (invisible, for design reference) -->
  <circle cx="256" cy="256" r="196" fill="none" stroke="#000000" stroke-opacity="0.1" stroke-width="2"/>
</svg>
EOF
        echo "  ✓ Created: $OUTPUT_DIR/app_icon.svg"
        echo "    Convert to PNG with: inkscape app_icon.svg -o app_icon.png"
    else
        # Create PNG using ImageMagick
        convert -size 512x512 xc:"#6750A4" "$OUTPUT_DIR/temp.png"

        # Add icon elements (simplified)
        # Outer circle
        convert "$OUTPUT_DIR/temp.png" \
            -fill "none" \
            -stroke "#FFFFFF" \
            -strokewidth 8 \
            -draw "circle 256,256 160" \
            "$OUTPUT_DIR/temp2.png"

        # Add UDP symbol (simple X shape)
        convert "$OUTPUT_DIR/temp2.png" \
            -fill "#FFFFFF" \
            -draw "line 196,196 316,316" \
            -draw "line 316,196 196,316" \
            "$OUTPUT_DIR/app_icon.png"

        rm -f "$OUTPUT_DIR/temp.png" "$OUTPUT_DIR/temp2.png"
        echo "  ✓ Created: $OUTPUT_DIR/app_icon.png"
    fi
}

# Function to generate screenshot placeholder
generate_screenshot_placeholder() {
    echo "Generating screenshot template..."

    cat > "$OUTPUT_DIR/screenshot_template.svg" <<'EOF'
<svg width="1080" height="1920" xmlns="http://www.w3.org/2000/svg">
  <!-- Phone frame mockup -->
  <rect width="1080" height="1920" fill="#E8DEF8"/>

  <!-- Status bar -->
  <rect width="1080" height="64" fill="#6750A4"/>
  <circle cx="60" cy="32" r="12" fill="#FFFFFF"/>
  <rect x="90" y="28" width="200" height="8" rx="4" fill="#FFFFFF"/>
  <rect x="950" y="24" width="16" height="16" rx="2" fill="#FFFFFF"/>
  <rect x="980" y="26" width="16" height="12" rx="2" fill="#FFFFFF"/>

  <!-- App header -->
  <rect y="64" width="1080" height="120" fill="#EADDFF"/>
  <text x="40" y="140" font-family="sans-serif" font-size="40" fill="#1C1B1F" font-weight="bold">UDP Trigger</text>

  <!-- Main content area - Config section -->
  <rect y="184" width="1080" height="300" fill="#FFFFFF"/>
  <text x="40" y="230" font-family="sans-serif" font-size="28" fill="#1C1B1F">Host Configuration</text>
  <rect x="40" y="250" width="1000" height="60" rx="8" fill="#E8DEF8"/>
  <text x="60" y="290" font-family="sans-serif" font-size="24" fill="#49454F">192.168.1.100</text>
  <rect x="40" y="320" width="480" height="60" rx="8" fill="#E8DEF8"/>
  <text x="60" y="360" font-family="sans-serif" font-size="24" fill="#49454F">5000</text>

  <!-- Connect button -->
  <rect x="40" y="400" width="300" height="60" rx="30" fill="#6750A4"/>
  <text x="190" y="440" font-family="sans-serif" font-size="24" fill="#FFFFFF" text-anchor="middle">Connect</text>

  <!-- Status indicator -->
  <circle cx="900" cy="430" r="12" fill="#4CAF50"/>
  <text x="925" y="440" font-family="sans-serif" font-size="20" fill="#4CAF50">Connected</text>

  <!-- Send button -->
  <rect y="500" width="1080" height="100" fill="#6750A4"/>
  <text x="540" y="565" font-family="sans-serif" font-size="36" fill="#FFFFFF" text-anchor="middle">SEND UDP</text>

  <!-- Recent packets -->
  <rect y="620" width="1080" height="400" fill="#FFFFFF"/>
  <text x="40" y="670" font-family="sans-serif" font-size="28" fill="#1C1B1F">Recent Packets</text>

  <!-- Packet entries -->
  <rect x="40" y="700" width="1000" height="60" rx="8" fill="#F3EDF7"/>
  <text x="60" y="740" font-family="sans-serif" font-size="20" fill="#49454F">192.168.1.100:5000 • TRIGGER • 123ms ago</text>

  <rect x="40" y="770" width="1000" height="60" rx="8" fill="#F3EDF7"/>
  <text x="60" y="810" font-family="sans-serif" font-size="20" fill="#49454F">192.168.1.100:5000 • DATA • 456ms ago</text>

  <rect x="40" y="840" width="1000" height="60" rx="8" fill="#F3EDF7"/>
  <text x="60" y="880" font-family="sans-serif" font-size="20" fill="#49454F">192.168.1.100:5000 • PING • 789ms ago</text>

  <!-- Settings panel -->
  <rect y="1040" width="1080" height="400" fill="#FFFFFF"/>
  <text x="40" y="1090" font-family="sans-serif" font-size="28" fill="#1C1B1F">Settings</text>

  <!-- Settings entries -->
  <rect x="40" y="1120" width="1000" height="50" rx="8" fill="#E8DEF8"/>
  <text x="60" y="1155" font-family="sans-serif" font-size="20" fill="#49454F">Sound Effects</text>
  <circle cx="980" cy="1145" r="16" fill="#6750A4"/>

  <rect x="40" y="1180" width="1000" height="50" rx="8" fill="#E8DEF8"/>
  <text x="60" y="1215" font-family="sans-serif" font-size="20" fill="#49454F">Haptic Feedback</text>
  <circle cx="980" cy="1205" r="16" fill="#6750A4"/>

  <rect x="40" y="1240" width="1000" height="50" rx="8" fill="#E8DEF8"/>
  <text x="60" y="1275" font-family="sans-serif" font-size="20" fill="#49454F">Auto Reconnect</text>
  <rect x="950" y="1255" width="40" height="20" rx="10" fill="#6750A4"/>

  <rect x="40" y="1300" width="1000" height="50" rx="8" fill="#E8DEF8"/>
  <text x="60" y="1335" font-family="sans-serif" font-size="20" fill="#49454F">Background Service</text>
  <rect x="950" y="1315" width="40" height="20" rx="10" fill="#6750A4"/>

  <!-- Navigation bar -->
  <rect y="1480" width="1080" height="440" fill="#6750A4"/>

  <!-- Nav items -->
  <circle cx="135" cy="1640" r="24" fill="#EADDFF"/>
  <text x="135" y="1750" font-family="sans-serif" font-size="14" fill="#FFFFFF" text-anchor="middle">Trigger</text>

  <circle cx="270" cy="1640" r="24" fill="#FFFFFF" opacity="0.6"/>
  <text x="270" y="1750" font-family="sans-serif" font-size="14" fill="#FFFFFF" text-anchor="middle">History</text>

  <circle cx="405" cy="1640" r="24" fill="#FFFFFF" opacity="0.6"/>
  <text x="405" y="1750" font-family="sans-serif" font-size="14" fill="#FFFFFF" text-anchor="middle">Settings</text>

  <circle cx="540" cy="1640" r="24" fill="#FFFFFF" opacity="0.6"/>
  <text x="540" y="1750" font-family="sans-serif" font-size="14" fill="#FFFFFF" text-anchor="middle">Macros</text>

  <circle cx="675" cy="1640" r="24" fill="#FFFFFF" opacity="0.6"/>
  <text x="675" y="1750" font-family="sans-serif" font-size="14" fill="#FFFFFF" text-anchor="middle">Auto</text>

  <circle cx="810" cy="1640" r="24" fill="#FFFFFF" opacity="0.6"/>
  <text x="810" y="1750" font-family="sans-serif" font-size="14" fill="#FFFFFF" text-anchor="middle">Widgets</text>

  <circle cx="945" cy="1640" r="24" fill="#FFFFFF" opacity="0.6"/>
  <text x="945" y="1750" font-family="sans-serif" font-size="14" fill="#FFFFFF" text-anchor="middle">About</text>

  <!-- Home indicator -->
  <rect x="450" y="1880" width="180" height="5" rx="2.5" fill="#FFFFFF"/>
</svg>
EOF
    echo "  ✓ Created: $OUTPUT_DIR/screenshot_template.svg"
    echo "    This is a mockup - replace with actual screenshots"
}

# Generate all graphics
generate_feature_graphic
echo ""
generate_app_icon
echo ""
generate_screenshot_placeholder

echo ""
echo "=== Summary ==="
echo ""
echo "Created files in $OUTPUT_DIR/:"
ls -lh "$OUTPUT_DIR/" 2>/dev/null
echo ""

if [ "$CREATE_SVG" = true ]; then
    echo "⚠ SVG files created - convert to PNG with:"
    echo "   inkscape file.svg -o file.png"
    echo "   or"
    echo "   convert file.svg file.png"
else
    echo "✓ PNG files generated - ready for Play Store!"
fi

echo ""
echo "=== Next Steps ==="
echo ""
echo "1. Review generated graphics:"
echo "   open $OUTPUT_DIR/"
echo ""
echo "2. Take actual screenshots from the app:"
echo "   a) Install app on emulator or device"
echo "   b) Navigate to key screens"
echo "   c) Take screenshots (Power + Volume Down)"
echo "   d) Transfer to computer"
echo ""
echo "3. Upload to Play Store Console:"
echo "   - App icon: $OUTPUT_DIR/app_icon.png"
echo "   - Feature graphic: $OUTPUT_DIR/feature_graphic.png"
echo "   - Screenshots: Your actual screenshots"
echo ""
