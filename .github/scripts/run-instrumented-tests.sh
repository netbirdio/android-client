#!/usr/bin/env bash
# Runs connectedDebugAndroidTest while capturing the emulator screen and
# logcat in the background. screenrecord caps each clip at 180s, so we loop
# until the test finishes; logcat streams continuously to a file. Both end
# up in screen-recordings/ and are uploaded as an artifact from the workflow.
#
# Expects $INSTRUMENTATION_NB_SETUP_KEY in the environment.

set +e

mkdir -p screen-recordings
adb shell mkdir -p /sdcard/recordings

adb logcat -c
adb logcat -v threadtime > screen-recordings/logcat.log 2>&1 &
LOGCAT_PID=$!

# Sentinel must exist before the background loop starts; otherwise the first
# iteration sees no file and exits immediately.
touch /tmp/record_active
(
  i=0
  while [ -f /tmp/record_active ]; do
    seg=$(printf "seg_%03d.mp4" "$i")
    echo "[record] starting $seg"
    adb shell screenrecord --time-limit 180 --bit-rate 4000000 "/sdcard/recordings/$seg"
    echo "[record] $seg exited"
    i=$((i + 1))
  done
  echo "[record] loop ended"
) &
REC_LOOP_PID=$!

./gradlew --no-daemon connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notClass=io.netbird.client.NetworkConnectivityStressTest \
  -Pandroid.testInstrumentationRunnerArguments.setupKey="$INSTRUMENTATION_NB_SETUP_KEY"
TEST_EXIT=$?

rm -f /tmp/record_active
adb shell pkill -SIGINT screenrecord 2>/dev/null || true
wait "$REC_LOOP_PID" 2>/dev/null || true
sleep 3
adb pull /sdcard/recordings ./screen-recordings/ || true

kill "$LOGCAT_PID" 2>/dev/null || true
wait "$LOGCAT_PID" 2>/dev/null || true

exit $TEST_EXIT
