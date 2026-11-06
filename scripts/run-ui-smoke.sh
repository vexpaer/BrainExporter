#!/usr/bin/env bash
set -euo pipefail

# Pull captures while the emulator is still alive, including on test failures.
trap 'adb pull /sdcard/Download/brainexporter-ui ui-screenshots || true' EXIT
./gradlew --no-daemon :plugin-ui-monitor:connectedDebugAndroidTest
