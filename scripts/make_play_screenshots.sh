#!/bin/bash
# Renders the Play phone screenshots from play/graphics/screenshots.html: seven 1080x1920 panels
# of one panorama, plus the whole panorama and a store-style preview strip.
#
# Needs Google Chrome and the six app captures in build/play/screenshots/ (gitignored: they show
# sprites). Those are 1080x1920 emulator screenshots (`wm size 1080x1920`, `wm density 420`,
# demo-mode status bar). Output goes to build/play/screenshots-v2/ (gitignored for the same reason).
#
#   scripts/make_play_screenshots.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
SRC="file://$ROOT/play/graphics/screenshots.html"
OUT="$ROOT/build/play/screenshots-v2"
NAMES=(1-track 2-find 3-generations 4-say 5-ask 6-offline 7-finish)

for f in 1-ask-answer 2-add-cards 3-collection 4-model-output 5-search 6-filter; do
  [ -f "$ROOT/build/play/screenshots/$f.png" ] || { echo "missing capture: build/play/screenshots/$f.png" >&2; exit 1; }
done
mkdir -p "$OUT"
rm -f "$OUT"/*.png  # panels are named by position, so an older order would leave strays

shot() {  # shot <width> <height> <output> <url>
  "$CHROME" --headless=new --hide-scrollbars --force-device-scale-factor=1 --virtual-time-budget=6000 \
    --window-size="$1,$2" --screenshot="$3" "$4" >/dev/null 2>&1
}

for i in "${!NAMES[@]}"; do
  shot 1080 1920 "$OUT/${NAMES[$i]}.png" "$SRC?p=$((i + 1))"
done
shot 7560 1920 "$OUT/panorama.png" "$SRC"

# A strip the way the store shows it: small, rounded, with a gap between the panels
{
  echo '<!doctype html><meta charset="utf-8"><style>'
  echo 'body{margin:0;padding:40px;background:#fff;display:flex;gap:14px;width:max-content}'
  echo 'img{width:300px;height:533px;border-radius:14px;display:block;box-shadow:0 0 0 1px #dadce0}</style>'
  for n in "${NAMES[@]}"; do echo "<img src=\"$n.png\">"; done
} > "$OUT/preview.html"
shot 2264 613 "$OUT/preview.png" "file://$OUT/preview.html"

for n in "${NAMES[@]}"; do
  echo "$n.png  $(sips -g pixelWidth -g pixelHeight -g hasAlpha "$OUT/$n.png" | awk 'NR>1{printf "%s ", $2}')"
done
