#!/bin/zsh
# Runs one part of ModelEvalTest's final held-out evaluation on a connected Android device and
# saves its log lines. Usage: scripts/run_eval_part.sh <part 1-4> <device serial> <out dir>
# Then: python3 scripts/aggregate_eval.py <out dir>/final_part*.log --failures
set -e
PART=$1; SERIAL=$2; OUT=${3:-build/eval}
export PATH=$PATH:$HOME/Library/Android/sdk/platform-tools; export ANDROID_SERIAL=$SERIAL
mkdir -p $OUT
adb logcat -c
./gradlew :shared:connectedAndroidDeviceTest "-Pandroid.testInstrumentationRunnerArguments.class=com.pixel.pokecollect.model.ModelEvalTest#finalV2Part$PART" --console=plain > $OUT/final_part$PART.gradle.log 2>&1 || true
# Gradle can give up before the device finishes; wait for the device-side end marker.
for i in $(seq 1 120); do adb logcat -d -s PokeCollect.Eval:I | grep -q "PART $PART end" && break; sleep 5; done
adb logcat -d -s PokeCollect.Eval:I | sed 's/^.*PokeCollect.Eval: //' > $OUT/final_part$PART.log
echo "part $PART: $(grep -c EVALROW $OUT/final_part$PART.log) rows; $(grep 'PART' $OUT/final_part$PART.log | tr '\n' ' ')"
