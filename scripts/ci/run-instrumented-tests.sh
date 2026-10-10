#!/usr/bin/env bash

# Runs prebuilt Android behavior-test artifacts through the platform
# instrumentation runner.
#
# AGP 9's host-side Android Test Engine currently reports a false failure on
# some physical devices even when AndroidJUnitRunner returns a complete OK
# result. GitHub Actions assembles the APKs before it starts the emulator; that
# avoids competing with the AVD for CPU and memory. This script validates the
# runner's actual result, checks the expected test count, and is the command
# used by the emulator jobs in CI.

set -euo pipefail

readonly device_ready_timeout_seconds=60

require_file() {
  local file="$1"
  if [[ ! -f "${file}" ]]; then
    echo "Required prebuilt APK is missing: ${file}" >&2
    exit 1
  fi
}

wait_for_responsive_device() {
  adb wait-for-device

  for ((attempt = 1; attempt <= device_ready_timeout_seconds; attempt++)); do
    if [[ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] &&
      adb shell true >/dev/null 2>&1; then
      return
    fi
    sleep 1
  done

  echo "The Android emulator did not become responsive within ${device_ready_timeout_seconds} seconds." >&2
  adb devices -l >&2 || true
  exit 1
}

install_apk() {
  local apk="$1"
  require_file "${apk}"
  adb install -r -t "${apk}"
}

run_suite() {
  local name="$1"
  local instrumentation="$2"
  local expected_tests="$3"
  local output

  echo "::group::${name} (${expected_tests} tests)"
  if ! output="$(adb shell am instrument -r -w "${instrumentation}" 2>&1)"; then
    printf '%s\n' "${output}"
    echo "::endgroup::"
    return 1
  fi
  printf '%s\n' "${output}"

  if grep -Fq "INSTRUMENTATION_FAILED" <<<"${output}" ||
    grep -Fq "FAILURES!!!" <<<"${output}" ||
    ! grep -Fq "OK (${expected_tests} test" <<<"${output}"; then
    echo "${name} did not produce a successful AndroidJUnitRunner result." >&2
    echo "::endgroup::"
    return 1
  fi
  echo "::endgroup::"
}

wait_for_responsive_device

install_apk "ads-core/build/outputs/apk/androidTest/debug/ads-core-debug-androidTest.apk"
install_apk "ads-compose/build/outputs/apk/androidTest/debug/ads-compose-debug-androidTest.apk"
install_apk "billing-google-play/build/outputs/apk/androidTest/debug/billing-google-play-debug-androidTest.apk"
install_apk "app/build/outputs/apk/debug/app-debug.apk"
install_apk "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
install_apk "consumer-r8-smoke/build/outputs/apk/debug/consumer-r8-smoke-debug.apk"
install_apk "consumer-r8-smoke/build/outputs/apk/androidTest/debug/consumer-r8-smoke-debug-androidTest.apk"

run_suite "ads-core" \
  "ua.lujek.monetizekit.core.test/androidx.test.runner.AndroidJUnitRunner" \
  7
run_suite "ads-compose" \
  "ua.lujek.monetizekit.compose.test/androidx.test.runner.AndroidJUnitRunner" \
  2
run_suite "billing-google-play" \
  "ua.lujek.monetizekit.billing.googleplay.test/androidx.test.runner.AndroidJUnitRunner" \
  2
run_suite "sample app" \
  "ua.lujek.monetizekit.sample.test/androidx.test.runner.AndroidJUnitRunner" \
  2
run_suite "consumer R8 smoke" \
  "ua.lujek.monetizekit.consumer.r8smoke.test/androidx.test.runner.AndroidJUnitRunner" \
  1

readonly consumer_release_apk="consumer-r8-smoke/build/outputs/apk/release/consumer-r8-smoke-release.apk"
readonly consumer_package="ua.lujek.monetizekit.consumer.r8smoke"

require_file "${consumer_release_apk}"
adb install -r "${consumer_release_apk}"
adb logcat -c
adb shell am force-stop "${consumer_package}"
adb shell am start -W -n "${consumer_package}/.ConsumerSmokeActivity"
sleep 2

if adb logcat -d -v brief | grep -Fq "FATAL EXCEPTION: main"; then
  echo "The minified consumer application crashed on startup." >&2
  exit 1
fi
