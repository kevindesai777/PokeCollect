#!/usr/bin/env bash
# Checks a release bundle before it goes to Play: signed, the model in the AI pack, sprites and
# licence texts in the base, no INTERNET / AD_ID permission, no 32-bit libraries.
#
#   scripts/check_bundle.sh [path/to/app.aab]   (default: the bundleRelease output)
set -uo pipefail

aab="${1:-$(cd "$(dirname "$0")/.." && pwd)/androidApp/build/outputs/bundle/release/androidApp-release.aab}"
[[ -f "$aab" ]] || { echo "No bundle at $aab" >&2; exit 1; }

failed=0
ok()  { echo "  ok    $1"; }
bad() { echo "  FAIL  $1"; failed=1; }

listing="$(unzip -l "$aab")"
manifest="$(bundletool dump manifest --bundle="$aab")"

# File size, not du: du counts disk blocks, which differ between identical builds.
echo "$(basename "$aab"): $(awk -v b="$(stat -f %z "$aab")" 'BEGIN { printf "%.1f MB", b / 1e6 }')"
echo "$manifest" | grep -oE '(package|versionCode|versionName|minSdkVersion|targetSdkVersion)="[^"]+"' | sed 's/^/        /'

if jarsigner -verify "$aab" 2>&1 | grep -q "jar verified"; then
  ok "signed ($(keytool -printcert -jarfile "$aab" 2>/dev/null | grep -m1 'Owner:' | sed 's/^ *Owner: //'))"
else
  bad "not signed: create keystore.properties (see androidApp/build.gradle.kts) and rebuild"
fi

grep -q "gemma_pack/assets/gemma3-1b-it-int4.litertlm" <<<"$listing" && ok "model in gemma_pack" || bad "model missing from gemma_pack (scripts/stage_model.sh)"
bundletool dump manifest --bundle="$aab" --module=gemma_pack 2>/dev/null | grep -q "dist:fast-follow" \
  && ok "gemma_pack is fast-follow" || bad "gemma_pack is not fast-follow"

sprites=$(grep -c 'files/sprites/.*\.png' <<<"$listing")
(( sprites > 0 )) && ok "$sprites sprites" || bad "no sprites (scripts/fetch_sprites.py, then ./gradlew :shared:clean)"

missing=0
for f in Gemma-Terms-of-Use NOTICE Apache-2.0 SpaceGrotesk-OFL JetBrainsMono-OFL; do
  grep -q "files/licenses/$f.txt" <<<"$listing" || { bad "licence text missing: $f.txt"; missing=1; }
done
(( missing == 0 )) && ok "licence texts"

for p in android.permission.INTERNET com.google.android.gms.permission.AD_ID; do
  grep -q "\"$p\"" <<<"$manifest" && bad "unexpected permission $p"
done
grep -qE '"android.permission.(INTERNET|.*AD_ID)"' <<<"$manifest" || ok "no INTERNET or AD_ID permission"

grep -qE "lib/(armeabi-v7a|x86)/" <<<"$listing" && bad "32-bit libraries in the bundle" || ok "64-bit libraries only"
grep -q 'android:debuggable="true"' <<<"$manifest" && bad "debuggable" || ok "not debuggable"

exit $failed
