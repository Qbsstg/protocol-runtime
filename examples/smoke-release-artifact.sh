#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAVA_BIN=${JAVA_BIN:-java}
MVN_BIN=${MVN_BIN:-mvn}
DIST_TAR=${DIST_TAR:-}
DIST_ZIP=${DIST_ZIP:-}
OUT_DIR="$ROOT_DIR/target/runtime-app-release-artifact-smoke"
UNPACK_DIR="$OUT_DIR/unpack"
LOG="$OUT_DIR/collector.log"
STATUS_COPY="$OUT_DIR/status.json"
AUTHORITY_NOTE="$OUT_DIR/artifacts.txt"
SELF_CHECK_OUTPUT="$OUT_DIR/self-check.json"
HOT_CHECK_OUTPUT="$OUT_DIR/hot-check.json"

rm -rf "$OUT_DIR"
mkdir -p "$UNPACK_DIR"

if [ -z "$DIST_TAR" ] || [ -z "$DIST_ZIP" ]; then
  rm -f "$ROOT_DIR"/runtime-app/target/runtime-app-*-distribution.tar.gz
  rm -f "$ROOT_DIR"/runtime-app/target/runtime-app-*-distribution.zip
  "$MVN_BIN" -q -pl runtime-app -am package
  DIST_TAR=$(find "$ROOT_DIR/runtime-app/target" \
    -name 'runtime-app-*-distribution.tar.gz' \
    -type f \
    -print \
    | sort \
    | tail -n 1)
  DIST_ZIP=$(find "$ROOT_DIR/runtime-app/target" \
    -name 'runtime-app-*-distribution.zip' \
    -type f \
    -print \
    | sort \
    | tail -n 1)
fi

if [ -z "$DIST_TAR" ] || [ ! -s "$DIST_TAR" ]; then
  echo "release artifact tar.gz is missing; set DIST_TAR or build locally" >&2
  exit 1
fi
if [ -z "$DIST_ZIP" ] || [ ! -s "$DIST_ZIP" ]; then
  echo "release artifact zip is missing; set DIST_ZIP or build locally" >&2
  exit 1
fi
for checksum in "$DIST_TAR.sha256" "$DIST_TAR.sha512" "$DIST_ZIP.sha256" "$DIST_ZIP.sha512"; do
  if [ ! -s "$checksum" ]; then
    echo "release artifact checksum is missing: $checksum" >&2
    exit 1
  fi
done

{
  echo "tar=$DIST_TAR"
  echo "zip=$DIST_ZIP"
} > "$AUTHORITY_NOTE"

tar -xzf "$DIST_TAR" -C "$UNPACK_DIR"
APP_HOME=$(find "$UNPACK_DIR" -maxdepth 1 -type d -name 'protocol-runtime-*' -print | sort | tail -n 1)
if [ -z "$APP_HOME" ]; then
  echo "release artifact did not unpack to protocol-runtime-* directory" >&2
  exit 1
fi

JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" java-check >/dev/null
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" version > "$OUT_DIR/version.txt"
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" verify-package >/dev/null
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" verify-package \
  --artifact "$DIST_TAR" \
  --checksum "$DIST_TAR.sha256" \
  >/dev/null
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" verify-package \
  --artifact "$DIST_TAR" \
  --checksum "$DIST_TAR.sha512" \
  >/dev/null
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" verify-package \
  --artifact "$DIST_ZIP" \
  --checksum "$DIST_ZIP.sha256" \
  >/dev/null
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" verify-package \
  --artifact "$DIST_ZIP" \
  --checksum "$DIST_ZIP.sha512" \
  >/dev/null

CONFIG="$APP_HOME/conf/collector-release-smoke.properties"
SINK="$APP_HOME/data/release-smoke-records.ndjson"
STATUS="$APP_HOME/run/status.json"
PID_FILE="$APP_HOME/run/protocol-runtime.pid"
HOT_CHECK_BASELINE="$APP_HOME/run/config.hotcheck.properties"
cp "$APP_HOME/conf/collector.properties" "$CONFIG"
cat >> "$CONFIG" <<EOF
collector.runtime.dir=$APP_HOME
collector.runtime.pidFile=run/protocol-runtime.pid
collector.runtime.statusFile=run/status.json
collector.tcp.port=0
collector.management.enabled=false
collector.sink.file=$SINK
EOF

JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" validate --config "$CONFIG" >/dev/null
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" self-check --config "$CONFIG" > "$SELF_CHECK_OUTPUT"
if ! grep -q '"command":"self-check"' "$SELF_CHECK_OUTPUT" \
    || ! grep -q '"status":"PASS"' "$SELF_CHECK_OUTPUT"; then
  cat "$SELF_CHECK_OUTPUT"
  echo "release artifact self-check did not report PASS" >&2
  exit 1
fi
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" hot-check \
  --config "$CONFIG" \
  --hot-check-baseline "$HOT_CHECK_BASELINE" \
  > "$HOT_CHECK_OUTPUT"
if ! grep -q '"command":"hot-check"' "$HOT_CHECK_OUTPUT" \
    || ! grep -q '"hotReloaded":false' "$HOT_CHECK_OUTPUT"; then
  cat "$HOT_CHECK_OUTPUT"
  echo "release artifact hot-check did not report non-reload diagnostics" >&2
  exit 1
fi
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" dry-run --config "$CONFIG" --status-file "$STATUS" >/dev/null

collector_pid=""
cleanup() {
  if [ -n "$collector_pid" ]; then
    kill "$collector_pid" >/dev/null 2>&1 || true
    wait "$collector_pid" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT INT TERM

JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" start --config "$CONFIG" > "$LOG" 2>&1 &
collector_pid=$!

i=0
while [ "$i" -lt 150 ]; do
  if grep -q "Protocol Runtime collector started" "$LOG"; then
    break
  fi
  if ! kill -0 "$collector_pid" >/dev/null 2>&1; then
    cat "$LOG"
    exit 1
  fi
  i=$((i + 1))
  sleep 0.2
done

if ! grep -q "Protocol Runtime collector started" "$LOG"; then
  cat "$LOG"
  echo "release artifact collector did not start in time" >&2
  exit 1
fi

i=0
while [ "$i" -lt 150 ]; do
  JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" status --status-file "$STATUS" > "$STATUS_COPY" || true
  if grep -q '"lifecycle":"RUNNING"' "$STATUS_COPY"; then
    break
  fi
  i=$((i + 1))
  sleep 0.2
done

if ! grep -q '"lifecycle":"RUNNING"' "$STATUS_COPY"; then
  cat "$LOG"
  cat "$STATUS_COPY"
  echo "release artifact status command did not observe RUNNING lifecycle" >&2
  exit 1
fi

JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" stop --pid-file "$PID_FILE" >/dev/null
wait "$collector_pid" >/dev/null 2>&1 || true
collector_pid=""
trap - EXIT INT TERM

echo "release artifact smoke passed"
echo "distribution tar: $DIST_TAR"
echo "distribution zip: $DIST_ZIP"
echo "unpacked app: $APP_HOME"
echo "collector log: $LOG"
echo "status output: $STATUS_COPY"
