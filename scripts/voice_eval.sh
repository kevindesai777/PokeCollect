#!/bin/zsh
# Voice input on a phone without anyone talking: speaks test clips with macOS `say` (offline),
# pushes them with the dataset, and runs VoiceEvalTest, which plays each clip on the phone's speaker
# while the on-device recognizer listens. Media volume on the phone must be up.
# Usage: scripts/voice_eval.sh <device serial> <out name> [-e key value ...]
#   e.g. scripts/voice_eval.sh 38010DLJH001AJ base
#        scripts/voice_eval.sh 38010DLJH001AJ nobias -e biasing false -e model off
# Install the test APK first: ./gradlew :shared:installAndroidDeviceTest
SERIAL=$1; NAME=$2; shift 2
export PATH=$PATH:$HOME/Library/Android/sdk/platform-tools; export ANDROID_SERIAL=$SERIAL
cd "$(dirname $0)/.."
PKG=com.pixel.pokecollect.shared.test
CLIPS=build/voice/clips
mkdir -p $CLIPS build/voice

# name | voice | words per minute | what is said | expected cards (exact dataset names, comma-separated)
DEMO="Pikachu, Charizard, Bulbasaur, Squirtle, Eevee, Gengar, Snorlax, Mewtwo, Lucario, Greninja, Jigglypuff, Psyduck, Magikarp, Gyarados, Tyranitar, Umbreon, Sylveon, Garchomp, Rayquaza, Lugia, Blastoise, Venusaur, Machamp, Alakazam, Arcanine"
NEWER="Sprigatito, Fuecoco, Quaxly, Pawmi, Lechonk, Tinkatink, Kingambit, Annihilape, Iron Hands, Flutter Mane, Great Tusk, Roaring Moon"
while IFS='|' read -r clip voice rate text expected; do
  [[ -z $clip ]] && continue
  if [[ ! -f $CLIPS/$clip.wav ]]; then
    say -v "$voice" -r $rate --file-format=WAVE --data-format=LEI16@16000 -o $CLIPS/$clip.wav "$text"
  fi
  print -l -- ${(s:, :)expected} > $CLIPS/$clip.txt
done <<EOF
demo25-samantha|Samantha|165|$DEMO.|$DEMO
demo25-daniel|Daniel|165|$DEMO.|$DEMO
demo25-fast-reed|Reed (English (US))|210|${DEMO//,/}|$DEMO
newer-samantha|Samantha|160|$NEWER.|$NEWER
odd-samantha|Samantha|160|Mr. Mime, Farfetch'd, Porygon 2, Ho-Oh, Type Null, Flabébé, Nidoran female, Jangmo-o, Tapu Koko, Mime Junior.|Mr. Mime, Farfetch’d, Porygon2, Ho-Oh, Type: Null, Flabébé, Nidoran♀, Jangmo-o, Tapu Koko, Mime Jr.
sentence-flo|Flo (English (US))|170|I just bought two Pikachu, a Charmander, three Magikarp and a Snorlax.|Pikachu, Charmander, Magikarp, Snorlax
EOF

adb shell mkdir -p /data/local/tmp/voice
adb push $CLIPS/. /data/local/tmp/voice/ > /dev/null
adb push shared/src/commonMain/composeResources/files/pokemon.json /data/local/tmp/voice/ > /dev/null
adb shell pm grant $PKG android.permission.RECORD_AUDIO
REMOTE=/sdcard/Android/data/$PKG/files/voice/$NAME.log
adb shell rm -f $REMOTE
adb shell am instrument -w -e class com.pixel.pokecollect.voice.VoiceEvalTest#clips -e out $NAME "$@" $PKG/androidx.test.runner.AndroidJUnitRunner | grep -E "OK \(|FAILURES|Exception|Error" | head -5
adb pull $REMOTE build/voice/$NAME.log > /dev/null && cat build/voice/$NAME.log
