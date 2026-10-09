#!/usr/bin/env bats

bats_require_minimum_version 1.5.0

setup() {
  export HEALTH_URL="https://example.com/api/health"
  export HEALTH_TRIES=3
  export HEALTH_WAIT_SECONDS=0

  # A stand-in for curl that fails until it has been called CURL_FAILURES
  # times, and counts its calls in a file.
  stub_dir="$BATS_TEST_TMPDIR/bin"
  mkdir -p "$stub_dir"
  cat >"$stub_dir/curl" <<'STUB'
#!/usr/bin/env bash
calls_file="$BATS_TEST_TMPDIR/curl-calls"
calls=$(($(cat "$calls_file" 2>/dev/null || echo 0) + 1))
echo "$calls" >"$calls_file"
[ "$calls" -gt "${CURL_FAILURES:-0}" ]
STUB
  chmod +x "$stub_dir/curl"
  PATH="$stub_dir:$PATH"
}

@test "passes when the app answers after a slow start" {
  run env CURL_FAILURES=2 bash lib/resources/deploy/check-health.sh

  [ "$status" -eq 0 ]
  [ "$(cat "$BATS_TEST_TMPDIR/curl-calls")" -eq 3 ]
}

@test "fails after the last try" {
  run --separate-stderr env CURL_FAILURES=99 bash lib/resources/deploy/check-health.sh

  [ "$status" -eq 1 ]
  [ "$(cat "$BATS_TEST_TMPDIR/curl-calls")" -eq 3 ]
}
