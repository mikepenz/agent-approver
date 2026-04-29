#!/usr/bin/env bash
#
# generate-icons.sh
#
# Generates app icons for Agent Belay from SVG sources.
# Produces:
#   - icons/app.icns        (macOS traditional icon)
#   - icons/AgentBelay.icon (macOS 26+ layered icon, no background)
#   - icons/app.ico            (Windows)
#   - icons/app.png            (Linux, 512x512)
#
# Requirements: rsvg-convert (librsvg), iconutil (macOS), ImageMagick (convert)
#   brew install librsvg imagemagick
#
# The Claude logo (bi-claude) is MIT licensed (Bootstrap Authors).
# The badge-check icon is ISC licensed (Lucide Contributors).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
ICONS_DIR="$PROJECT_DIR/icons"
TMP_DIR="$(mktemp -d)"

trap 'rm -rf "$TMP_DIR"' EXIT

mkdir -p "$ICONS_DIR"

# --- SVG Sources ---

# Claude logo SVG (white filled, for dark backgrounds / macOS layered foreground)
cat > "$TMP_DIR/claude-white.svg" << 'SVGEOF'
<svg xmlns="http://www.w3.org/2000/svg" fill="white" viewBox="0 0 16 16">
  <path d="m3.127 10.604 3.135-1.76.053-.153-.053-.085H6.11l-.525-.032-1.791-.048-1.554-.065-1.505-.08-.38-.081L0 7.832l.036-.234.32-.214.455.04 1.009.069 1.513.105 1.097.064 1.626.17h.259l.036-.105-.089-.065-.068-.064-1.566-1.062-1.695-1.121-.887-.646-.48-.327-.243-.306-.104-.67.435-.48.585.04.15.04.593.456 1.267.981 1.654 1.218.242.202.097-.068.012-.049-.109-.181-.9-1.626-.96-1.655-.428-.686-.113-.411a2 2 0 0 1-.068-.484l.496-.674L4.446 0l.662.089.279.242.411.94.666 1.48 1.033 2.014.302.597.162.553.06.17h.105v-.097l.085-1.134.157-1.392.154-1.792.052-.504.25-.605.497-.327.387.186.319.456-.045.294-.19 1.23-.37 1.93-.243 1.29h.142l.161-.16.654-.868 1.097-1.372.484-.545.565-.601.363-.287h.686l.505.751-.226.775-.707.895-.585.759-.839 1.13-.524.904.048.072.125-.012 1.897-.403 1.024-.186 1.223-.21.553.258.06.263-.218.536-1.307.323-1.533.307-2.284.54-.028.02.032.04 1.029.098.44.024h1.077l2.005.15.525.346.315.424-.053.323-.807.411-3.631-.863-.872-.218h-.12v.073l.726.71 1.331 1.202 1.667 1.55.084.383-.214.302-.226-.032-1.464-1.101-.565-.497-1.28-1.077h-.084v.113l.295.432 1.557 2.34.08.718-.112.234-.404.141-.444-.08-.911-1.28-.94-1.44-.759-1.291-.093.053-.448 4.821-.21.246-.484.186-.403-.307-.214-.496.214-.98.258-1.28.21-1.016.19-1.263.112-.42-.008-.028-.092.012-.953 1.307-1.448 1.957-1.146 1.227-.274.109-.477-.247.045-.44.266-.39 1.586-2.018.956-1.25.617-.723-.004-.105h-.036l-4.212 2.736-.75.096-.324-.302.04-.496.154-.162 1.267-.871z"/>
</svg>
SVGEOF

# Badge-check SVG (green with white checkmark)
cat > "$TMP_DIR/badge-green.svg" << 'SVGEOF'
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none">
  <path d="M3.85 8.62a4 4 0 0 1 4.78-4.77 4 4 0 0 1 6.74 0 4 4 0 0 1 4.78 4.78 4 4 0 0 1 0 6.74 4 4 0 0 1-4.77 4.78 4 4 0 0 1-6.75 0 4 4 0 0 1-4.78-4.77 4 4 0 0 1 0-6.76Z" fill="#4CAF50"/>
  <path d="m9 12 2 2 4-4" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
</svg>
SVGEOF

# Claude logo white (for app icon)
cat > "$TMP_DIR/claude-black.svg" << 'SVGEOF'
<svg xmlns="http://www.w3.org/2000/svg" fill="white" viewBox="0 0 16 16">
  <path d="m3.127 10.604 3.135-1.76.053-.153-.053-.085H6.11l-.525-.032-1.791-.048-1.554-.065-1.505-.08-.38-.081L0 7.832l.036-.234.32-.214.455.04 1.009.069 1.513.105 1.097.064 1.626.17h.259l.036-.105-.089-.065-.068-.064-1.566-1.062-1.695-1.121-.887-.646-.48-.327-.243-.306-.104-.67.435-.48.585.04.15.04.593.456 1.267.981 1.654 1.218.242.202.097-.068.012-.049-.109-.181-.9-1.626-.96-1.655-.428-.686-.113-.411a2 2 0 0 1-.068-.484l.496-.674L4.446 0l.662.089.279.242.411.94.666 1.48 1.033 2.014.302.597.162.553.06.17h.105v-.097l.085-1.134.157-1.392.154-1.792.052-.504.25-.605.497-.327.387.186.319.456-.045.294-.19 1.23-.37 1.93-.243 1.29h.142l.161-.16.654-.868 1.097-1.372.484-.545.565-.601.363-.287h.686l.505.751-.226.775-.707.895-.585.759-.839 1.13-.524.904.048.072.125-.012 1.897-.403 1.024-.186 1.223-.21.553.258.06.263-.218.536-1.307.323-1.533.307-2.284.54-.028.02.032.04 1.029.098.44.024h1.077l2.005.15.525.346.315.424-.053.323-.807.411-3.631-.863-.872-.218h-.12v.073l.726.71 1.331 1.202 1.667 1.55.084.383-.214.302-.226-.032-1.464-1.101-.565-.497-1.28-1.077h-.084v.113l.295.432 1.557 2.34.08.718-.112.234-.404.141-.444-.08-.911-1.28-.94-1.44-.759-1.291-.093.053-.448 4.821-.21.246-.484.186-.403-.307-.214-.496.214-.98.258-1.28.21-1.016.19-1.263.112-.42-.008-.028-.092.012-.953 1.307-1.448 1.957-1.146 1.227-.274.109-.477-.247.045-.44.266-.39 1.586-2.018.956-1.25.617-.723-.004-.105h-.036l-4.212 2.736-.75.096-.324-.302.04-.496.154-.162 1.267-.871z"/>
</svg>
SVGEOF

echo "=== Generating icon PNGs ==="

# Function to compose the app icon: Claude logo + badge in bottom-right
compose_icon() {
    local size=$1
    local output=$2
    local bg_color="${3:-none}"  # "none" for transparent

    local logo_size=$((size * 80 / 100))
    local logo_offset=$(( (size - logo_size) / 2 ))
    local badge_size=$((size * 45 / 100))
    local badge_offset=$((size - badge_size - size * 2 / 100))

    # Render Claude logo
    rsvg-convert -w "$logo_size" -h "$logo_size" "$TMP_DIR/claude-black.svg" -o "$TMP_DIR/logo_${size}.png"

    # Render badge
    rsvg-convert -w "$badge_size" -h "$badge_size" "$TMP_DIR/badge-green.svg" -o "$TMP_DIR/badge_${size}.png"

    # Compose: background + logo centered + badge bottom-right
    if [ "$bg_color" = "none" ]; then
        magick -size "${size}x${size}" xc:none \
            "$TMP_DIR/logo_${size}.png" -geometry "+${logo_offset}+${logo_offset}" -composite \
            "$TMP_DIR/badge_${size}.png" -geometry "+${badge_offset}+${badge_offset}" -composite \
            "$output"
    else
        magick -size "${size}x${size}" "xc:${bg_color}" \
            "$TMP_DIR/logo_${size}.png" -geometry "+${logo_offset}+${logo_offset}" -composite \
            "$TMP_DIR/badge_${size}.png" -geometry "+${badge_offset}+${badge_offset}" -composite \
            "$output"
    fi
}

# Function to compose foreground-only (no background, for macOS layered icon)
compose_foreground() {
    local size=$1
    local output=$2

    local logo_size=$((size * 80 / 100))
    local logo_offset=$(( (size - logo_size) / 2 ))
    local badge_size=$((size * 45 / 100))
    local badge_offset=$((size - badge_size - size * 2 / 100))

    # White Claude logo for foreground layer
    rsvg-convert -w "$logo_size" -h "$logo_size" "$TMP_DIR/claude-white.svg" -o "$TMP_DIR/fg_logo_${size}.png"
    rsvg-convert -w "$badge_size" -h "$badge_size" "$TMP_DIR/badge-green.svg" -o "$TMP_DIR/fg_badge_${size}.png"

    magick -size "${size}x${size}" xc:none \
        "$TMP_DIR/fg_logo_${size}.png" -geometry "+${logo_offset}+${logo_offset}" -composite \
        "$TMP_DIR/fg_badge_${size}.png" -geometry "+${badge_offset}+${badge_offset}" -composite \
        "$output"
}

# --- macOS .icns (traditional) ---
echo "Generating macOS .icns..."

ICONSET_DIR="$TMP_DIR/app.iconset"
mkdir -p "$ICONSET_DIR"

# Required sizes for .icns: 16, 32, 64, 128, 256, 512, 1024 (and @2x variants)
for size in 16 32 64 128 256 512 1024; do
    compose_icon "$size" "$ICONSET_DIR/icon_${size}x${size}.png"
done

# @2x variants (named as half-size @2x)
cp "$ICONSET_DIR/icon_32x32.png"   "$ICONSET_DIR/icon_16x16@2x.png"
cp "$ICONSET_DIR/icon_64x64.png"   "$ICONSET_DIR/icon_32x32@2x.png"
cp "$ICONSET_DIR/icon_256x256.png" "$ICONSET_DIR/icon_128x128@2x.png"
cp "$ICONSET_DIR/icon_512x512.png" "$ICONSET_DIR/icon_256x256@2x.png"
cp "$ICONSET_DIR/icon_1024x1024.png" "$ICONSET_DIR/icon_512x512@2x.png"

# Remove non-standard sizes from iconset
rm -f "$ICONSET_DIR/icon_64x64.png" "$ICONSET_DIR/icon_1024x1024.png"

if command -v iconutil &>/dev/null; then
    iconutil -c icns "$ICONSET_DIR" -o "$ICONS_DIR/app.icns"
    echo "  Created icons/app.icns"
else
    echo "  WARNING: iconutil not found (macOS only). Skipping .icns generation."
    echo "  The iconset is at: $ICONSET_DIR"
fi

# --- macOS 26+ Layered Icon ---
echo "Generating macOS layered icon..."

LAYERED_DIR="$ICONS_DIR/AgentBelay.icon"
mkdir -p "$LAYERED_DIR/Assets"

# Foreground: Claude logo + badge on transparent background (NO solid background)
compose_foreground 1024 "$LAYERED_DIR/Assets/FrontImage.png"

# Background: empty/transparent (no background for layered icon)
magick -size 1024x1024 xc:none "$LAYERED_DIR/Assets/BackImage.png"

# icon.json descriptor
cat > "$LAYERED_DIR/icon.json" << 'JSONEOF'
{
  "format-version": 1,
  "assets": [
    { "role": "front", "filename": "Assets/FrontImage.png" },
    { "role": "back", "filename": "Assets/BackImage.png" }
  ]
}
JSONEOF
echo "  Created icons/AgentBelay.icon/"

# --- Windows .ico ---
echo "Generating Windows .ico..."

ICO_SIZES=(16 24 32 48 64 128 256)
ICO_INPUTS=()
for size in "${ICO_SIZES[@]}"; do
    compose_icon "$size" "$TMP_DIR/ico_${size}.png" "#1A1A2E"
    ICO_INPUTS+=("$TMP_DIR/ico_${size}.png")
done

if command -v magick &>/dev/null; then
    magick "${ICO_INPUTS[@]}" "$ICONS_DIR/app.ico"
    echo "  Created icons/app.ico"
else
    echo "  WARNING: ImageMagick 'magick' not found. Skipping .ico generation."
fi

# --- Linux PNG (512x512) ---
echo "Generating Linux PNG..."
compose_icon 512 "$ICONS_DIR/app.png"
echo "  Created icons/app.png"

echo ""
echo "=== Done! ==="
echo "Icons generated in: $ICONS_DIR/"
ls -la "$ICONS_DIR/"
