#!/usr/bin/env bash
# Runs connectedDebugAndroidTest while recording the emulator screen in the
# background. screenrecord caps each clip at 180s, so we loop until the test
# finishes, then upload the segments as an artifact from the workflow.
#
# Expects $INSTRUMENTATION_NB_SETUP_KEY in the environment.

set +e

mkdir -p screen-recordings
adb shell mkdir -p /sdcard/recordings

(
  i=0
  while [ -f /tmp/record_active ]; do
    seg=$(printf "seg_%03d.mp4" "$i")
    adb shell screenrecord --time-limit 180 --bit-rate 4000000 "/sdcard/recordings/$seg"
    i=$((i + 1))
  done
) &
REC_LOOP_PID=$!
touch /tmp/record_active

./gradlew --no-daemon connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.notClass=io.netbird.client.NetworkConnectivityStressTest \
  -Pandroid.testInstrumentationRunnerArguments.setupKey="$INSTRUMENTATION_NB_SETUP_KEY"
TEST_EXIT=$?

rm -f /tmp/record_active
adb shell pkill -SIGINT screenrecord 2>/dev/null || true
wait "$REC_LOOP_PID" 2>/dev/null || true
sleep 3
adb pull /sdcard/recordings ./screen-recordings/ || true

exit $TEST_EXIT
