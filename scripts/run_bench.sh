#!/bin/zsh
# Runs LatencyBenchTest on a connected device and saves its log lines.
# Usage: scripts/run_bench.sh <device serial> <out file> [-e key value ...]
# Install the test APK first: ./gradlew :shared:installAndroidDeviceTest
# Then: python3 scripts/aggregate_bench.py <out files...>
# The test runs detached on the device and writes its own log file, so a USB disconnect doesn't
# end the run; this script waits for the device-side END line (or the process exiting) and pulls it.
SERIAL=$1; OUT=$2; shift 2
export PATH=$PATH:$HOME/Library/Android/sdk/platform-tools; export ANDROID_SERIAL=$SERIAL
PKG=com.pixel.pokecollect.shared.test
NAME=$(basename $OUT .log)
REMOTE=/sdcard/Android/data/$PKG/files/bench/$NAME.log
mkdir -p $(dirname $OUT)
adb wait-for-device
adb shell rm -f $REMOTE
adb shell "nohup am instrument -w -e class com.pixel.pokecollect.model.LatencyBenchTest#${BENCH_METHOD:-bench} -e out $NAME $* $PKG/androidx.test.runner.AndroidJUnitRunner > /data/local/tmp/$NAME.instrument.txt 2>&1 &"
sleep 10
while true; do
  adb wait-for-device
  if adb shell cat $REMOTE 2>/dev/null | grep -qE "^(END|ENGINEFAIL)"; then break; fi
  if ! adb shell pidof $PKG > /dev/null 2>&1; then echo "test process exited without END"; break; fi
  sleep 10
done
adb pull $REMOTE $OUT > /dev/null 2>&1 || touch $OUT
adb shell cat /data/local/tmp/$NAME.instrument.txt | grep -E "OK \(|FAILURES|Exception" | head -3
grep -E "^(START|ENGINE|ENGINEFAIL|END|MODELINFO)" $OUT || true
echo "$(grep -c BENCHROW $OUT) rows -> $OUT"
