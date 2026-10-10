#!/usr/bin/env bash

# Runs the Android behavior suite through the platform instrumentation runner.
#
# AGP 9's host-side Android Test Engine currently reports a false failure on
# some physical devices even when AndroidJUnitRunner returns a complete OK
# result. This script validates the runner's actual result, checks the expected
# test count, and is the command used by the emulator jobs in CI.

set -euo pipefail

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

./gradlew \
  :ads-core:installDebugAndroidTest \
  :ads-compose:installDebugAndroidTest \
  :billing-google-play:installDebugAndroidTest \
  :app:installDebug \
  :app:installDebugAndroidTest \
  :consumer-r8-smoke:installDebug \
  :consumer-r8-smoke:installDebugAndroidTest \
  :consumer-r8-smoke:assembleRelease \
  --stacktrace

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

adb install -r "${consumer_release_apk}"
adb logcat -c
adb shell am force-stop "${consumer_package}"
adb shell am start -W -n "${consumer_package}/.ConsumerSmokeActivity"
sleep 2

if adb logcat -d -v brief | grep -Fq "FATAL EXCEPTION: main"; then
  echo "The minified consumer application crashed on startup." >&2
  exit 1
fi
