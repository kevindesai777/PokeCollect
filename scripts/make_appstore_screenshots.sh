#!/bin/bash
# Renders the App Store iPhone screenshots from appstore/graphics/screenshots.html: seven 1206x2622
# panels (6.3" Dynamic Island, the size App Store Connect requires), the whole panorama and a
# store-style preview strip. The same story as the Play set (scripts/make_play_screenshots.sh).
#
# Needs Google Chrome and six iOS captures in build/appstore/captures/ (gitignored: they show
# sprites), 1170x2532 from the iPhone 16e with a 9:41 status bar and the 395-card collection.
# Output goes to build/appstore/screenshots/ (gitignored for the same reason).
#
#   scripts/make_appstore_screenshots.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
SRC="file://$ROOT/appstore/graphics/screenshots.html"
OUT="$ROOT/build/appstore/screenshots"
NAMES=(1-track 2-find 3-generations 4-say 5-ask 6-offline 7-finish)
W=1206 H=2622

for f in 1-ask-answer 2-add-cards 3-collection 4-model-output 5-search 6-filter; do
  [ -f "$ROOT/build/appstore/captures/$f.png" ] || { echo "missing capture: build/appstore/captures/$f.png" >&2; exit 1; }
done
mkdir -p "$OUT"
rm -f "$OUT"/*.png

shot() {  # shot <width> <height> <output> <url>
  "$CHROME" --headless=new --hide-scrollbars --force-device-scale-factor=1 --virtual-time-budget=6000 \
    --window-size="$1,$2" --screenshot="$3" "$4" >/dev/null 2>&1
}

for i in "${!NAMES[@]}"; do
  shot $W $H "$OUT/${NAMES[$i]}.png" "$SRC?p=$((i + 1))"
  # App Store Connect rejects images with an alpha channel
  sips -s format jpeg "$OUT/${NAMES[$i]}.png" --out "$OUT/${NAMES[$i]}.jpg" >/dev/null
  sips -s format png "$OUT/${NAMES[$i]}.jpg" --out "$OUT/${NAMES[$i]}.png" >/dev/null
  rm "$OUT/${NAMES[$i]}.jpg"
done
shot $((W * 7)) $H "$OUT/panorama.png" "$SRC"

{
  echo '<!doctype html><meta charset="utf-8"><style>'
  echo 'body{margin:0;padding:40px;background:#fff;display:flex;gap:14px;width:max-content}'
  echo 'img{width:276px;height:600px;border-radius:22px;display:block;box-shadow:0 0 0 1px #dadce0}</style>'
  for n in "${NAMES[@]}"; do echo "<img src=\"$n.png\">"; done
} > "$OUT/preview.html"
shot 2096 680 "$OUT/preview.png" "file://$OUT/preview.html"

# Optional creative assets (iOS 27+): product page header 21:9 and search results 3:2
ART="file://$ROOT/appstore/graphics/creative-assets.html"
shot 3840 1646 "$OUT/header.png" "$ART?v=header"
shot 3840 2560 "$OUT/search-results.png" "$ART?v=search"

for n in "${NAMES[@]}" header search-results; do
  [ "$n" = header ] || [ "$n" = search-results ] && {
    sips -s format jpeg "$OUT/$n.png" --out "$OUT/$n.jpg" >/dev/null && sips -s format png "$OUT/$n.jpg" --out "$OUT/$n.png" >/dev/null && rm "$OUT/$n.jpg"; }
  echo "$n.png  $(sips -g pixelWidth -g pixelHeight -g hasAlpha "$OUT/$n.png" | awk 'NR>1{printf "%s ", $2}')"
done
