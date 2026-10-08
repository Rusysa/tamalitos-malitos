#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
source tools/env.sh
mkdir -p docs/verification-evidence
python3 tools/test_config_safety.py 2>&1 | tee docs/verification-evidence/config-current.log
./gradlew assembleDebug testDebugUnitTest lintDebug assembleDebugAndroidTest --no-daemon --max-workers=1 2>&1 | tee docs/verification-evidence/build-current.log
if [[ "${1:-}" == "--emulator" ]]; then
    DEVICE="${TAMALITOS_QA_DEVICE:-emulator-5554}"
    [[ "$(adb -s "$DEVICE" shell getprop ro.kernel.qemu | tr -d '\r')" == "1" ]] || { printf 'Refusing tests on a physical/production device.\n'; exit 1; }
    adb -s "$DEVICE" install -r app/build/outputs/apk/debug/app-debug.apk
    adb -s "$DEVICE" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    adb -s "$DEVICE" shell am instrument -w -r com.tamalitos.malitos.test/androidx.test.runner.AndroidJUnitRunner | tee docs/verification-evidence/android-current.log
    python3 tools/collect_evidence.py docs/verification-evidence/android-current.log
else
    python3 tools/collect_evidence.py
    printf '\nAndroid runtime not run by this invocation; start an emulator then use --emulator.\n'
fi
