#!/usr/bin/env bash
# Regenerate the launcher PNGs from the design SVGs.
#
# The adaptive icons (mipmap-anydpi-v26/*.xml) are hand-written vector drawables carrying the
# same paths — edit those alongside the SVGs if the artwork changes. These PNGs are the legacy
# pre-API-26 launcher icons.
#
# Usage:  tools/gen-icons.sh
set -euo pipefail
cd "$(dirname "$0")/.."

command -v rsvg-convert >/dev/null || { echo "rsvg-convert not found (librsvg2-bin)" >&2; exit 1; }

densities=(mdpi:48 hdpi:72 xhdpi:96 xxhdpi:144 xxxhdpi:192)

for entry in "${densities[@]}"; do
    density=${entry%%:*}
    px=${entry##*:}
    out="app/src/main/res/mipmap-${density}"
    rsvg-convert -w "$px" -h "$px" design/shiroikuma-renketsujoka-icon.svg \
        -o "${out}/ic_launcher.png"
    rsvg-convert -w "$px" -h "$px" design/shiroikuma-renketsujoka-clipboard-icon.svg \
        -o "${out}/clipboard_launcher.png"
    echo "wrote ${out}/{ic_launcher,clipboard_launcher}.png  (${px}px)"
done
