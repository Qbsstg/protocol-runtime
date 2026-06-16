#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAVA_BIN=${JAVA_BIN:-java}
MVN_BIN=${MVN_BIN:-mvn}
OUT_DIR="$ROOT_DIR/target/runtime-app-distribution-smoke"
UNPACK_DIR="$OUT_DIR/unpack"
LOG="$OUT_DIR/collector.log"
DUPLICATE_LOG="$OUT_DIR/duplicate-start.log"
CONFLICT_LOG="$OUT_DIR/port-conflict.log"
MISSING_JAVA_LOG="$OUT_DIR/missing-java.log"
STATUS_COPY="$OUT_DIR/status-from-script.json"
VERSION_OUTPUT="$OUT_DIR/version.txt"
BAD_CHECKSUM="$OUT_DIR/bad.sha256"
MISSING_CHECKSUM="$OUT_DIR/missing.sha256"
BAD_CHECKSUM_LOG="$OUT_DIR/bad-checksum.log"
MISSING_CHECKSUM_LOG="$OUT_DIR/missing-checksum.log"
AUTH_HEADER="Authorization: Bearer smoke-management-token"

rm -rf "$OUT_DIR"
mkdir -p "$UNPACK_DIR"
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

if [ -z "$DIST_TAR" ] || [ ! -s "$DIST_TAR" ]; then
  echo "distribution tar.gz was not built" >&2
  exit 1
fi
if [ -z "$DIST_ZIP" ] || [ ! -s "$DIST_ZIP" ]; then
  echo "distribution zip was not built" >&2
  exit 1
fi
for checksum in "$DIST_TAR.sha256" "$DIST_TAR.sha512" "$DIST_ZIP.sha256" "$DIST_ZIP.sha512"; do
  if [ ! -s "$checksum" ]; then
    echo "distribution checksum was not built: $checksum" >&2
    exit 1
  fi
done

tar -xzf "$DIST_TAR" -C "$UNPACK_DIR"
APP_HOME=$(find "$UNPACK_DIR" -maxdepth 1 -type d -name 'protocol-runtime-*' -print | sort | tail -n 1)
if [ -z "$APP_HOME" ]; then
  echo "distribution package did not unpack to protocol-runtime-* directory" >&2
  exit 1
fi

for path in bin/protocol-runtime bin/protocol-runtime-stop conf logs data run tmp lib docs examples; do
  if [ ! -e "$APP_HOME/$path" ]; then
    echo "distribution package is missing $path" >&2
    exit 1
  fi
done
if [ ! -x "$APP_HOME/bin/protocol-runtime" ] || [ ! -x "$APP_HOME/bin/protocol-runtime-stop" ]; then
  echo "distribution bin scripts are not executable" >&2
  exit 1
fi
if [ ! -s "$APP_HOME/package.properties" ]; then
  echo "distribution package is missing package.properties" >&2
  exit 1
fi

CONFIG="$APP_HOME/conf/collector-smoke.properties"
SINK="$APP_HOME/data/smoke-records.ndjson"
STATUS="$APP_HOME/run/status.json"
PID_FILE="$APP_HOME/run/protocol-runtime.pid"
cp "$APP_HOME/conf/collector.properties" "$CONFIG"
cat >> "$CONFIG" <<EOF
collector.runtime.dir=$APP_HOME
collector.runtime.pidFile=run/protocol-runtime.pid
collector.runtime.statusFile=run/status.json
collector.tcp.port=0
collector.management.port=0
collector.management.access=token
collector.management.token=smoke-management-token
collector.sink.file=$SINK
EOF

if JAVA_BIN="$OUT_DIR/missing-java" "$APP_HOME/bin/protocol-runtime" validate --config "$CONFIG" \
    > "$MISSING_JAVA_LOG" 2>&1; then
  cat "$MISSING_JAVA_LOG"
  echo "distribution script accepted a missing JAVA_BIN" >&2
  exit 1
fi
if ! grep -q "JAVA_BIN is not executable" "$MISSING_JAVA_LOG"; then
  cat "$MISSING_JAVA_LOG"
  echo "missing JAVA_BIN did not produce a clear diagnostic" >&2
  exit 1
fi

JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" java-check >/dev/null
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" version > "$VERSION_OUTPUT"
if ! grep -q '^runtime.version=' "$VERSION_OUTPUT" \
    || ! grep -q '^java.version=' "$VERSION_OUTPUT" \
    || ! grep -q '^package.layout=' "$VERSION_OUTPUT"; then
  cat "$VERSION_OUTPUT"
  echo "distribution version command did not print required diagnostics" >&2
  exit 1
fi
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" verify-package >/dev/null
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" verify-package \
  --artifact "$DIST_TAR" \
  --checksum "$DIST_TAR.sha256" \
  >/dev/null
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" verify-package \
  --artifact "$DIST_ZIP" \
  --checksum "$DIST_ZIP.sha512" \
  >/dev/null
if JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" verify-package \
    --artifact "$DIST_TAR" \
    --checksum "$MISSING_CHECKSUM" \
    > "$MISSING_CHECKSUM_LOG" 2>&1; then
  cat "$MISSING_CHECKSUM_LOG"
  echo "distribution verify-package accepted a missing checksum file" >&2
  exit 1
fi
if ! grep -q "checksum file does not exist" "$MISSING_CHECKSUM_LOG"; then
  cat "$MISSING_CHECKSUM_LOG"
  echo "missing checksum did not produce a clear diagnostic" >&2
  exit 1
fi
printf '%064d\n' 0 > "$BAD_CHECKSUM"
if JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" verify-package \
    --artifact "$DIST_TAR" \
    --checksum "$BAD_CHECKSUM" \
    > "$BAD_CHECKSUM_LOG" 2>&1; then
  cat "$BAD_CHECKSUM_LOG"
  echo "distribution verify-package accepted a bad checksum" >&2
  exit 1
fi
if ! grep -q "checksum mismatch" "$BAD_CHECKSUM_LOG"; then
  cat "$BAD_CHECKSUM_LOG"
  echo "bad checksum did not produce a clear diagnostic" >&2
  exit 1
fi
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" validate --config "$CONFIG" >/dev/null
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" dry-run --config "$CONFIG" --status-file "$STATUS" >/dev/null
if ! grep -q '"lifecycle":"CONFIGURED"' "$STATUS"; then
  cat "$STATUS"
  echo "distribution dry-run did not export configured status JSON" >&2
  exit 1
fi
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" status --status-file "$STATUS" > "$STATUS_COPY"
if ! grep -q '"lifecycle":"CONFIGURED"' "$STATUS_COPY"; then
  cat "$STATUS_COPY"
  echo "distribution status command did not read the dry-run status JSON" >&2
  exit 1
fi

BAD_CONFIG="$APP_HOME/conf/collector-invalid.properties"
cp "$CONFIG" "$BAD_CONFIG"
cat >> "$BAD_CONFIG" <<EOF
collector.tcp.port=70000
EOF
if JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" validate --config "$BAD_CONFIG" \
    > "$OUT_DIR/invalid-config.log" 2>&1; then
  cat "$OUT_DIR/invalid-config.log"
  echo "distribution validate accepted an invalid TCP port" >&2
  exit 1
fi

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

port=""
management_port=""
i=0
while [ "$i" -lt 150 ]; do
  if grep -q "Protocol Runtime collector started" "$LOG" \
    && grep -q "Protocol Runtime management started" "$LOG"; then
    port=$(sed -n 's/.*Protocol Runtime collector started.* port=\([0-9][0-9]*\) .*/\1/p' "$LOG" | tail -n 1)
    management_port=$(sed -n 's/.*Protocol Runtime management started.* port=\([0-9][0-9]*\) .*/\1/p' "$LOG" | tail -n 1)
    break
  fi
  if ! kill -0 "$collector_pid" >/dev/null 2>&1; then
    cat "$LOG"
    exit 1
  fi
  i=$((i + 1))
  sleep 0.2
done

if [ -z "$port" ] || [ -z "$management_port" ]; then
  cat "$LOG"
  echo "distribution collector did not start in time" >&2
  exit 1
fi
if [ ! -s "$PID_FILE" ] || ! grep -q "$collector_pid" "$PID_FILE"; then
  cat "$LOG"
  [ -f "$PID_FILE" ] && cat "$PID_FILE"
  echo "distribution collector did not write the configured pid file" >&2
  exit 1
fi

if JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" start --config "$CONFIG" \
    > "$DUPLICATE_LOG" 2>&1; then
  cat "$DUPLICATE_LOG"
  echo "distribution script accepted a duplicate start" >&2
  exit 1
fi
if ! grep -q "collector already appears to be running" "$DUPLICATE_LOG"; then
  cat "$DUPLICATE_LOG"
  echo "duplicate start did not produce a clear diagnostic" >&2
  exit 1
fi

CONFLICT_RUNTIME="$OUT_DIR/conflict-runtime"
CONFLICT_CONFIG="$APP_HOME/conf/collector-conflict.properties"
cp "$CONFIG" "$CONFLICT_CONFIG"
cat >> "$CONFLICT_CONFIG" <<EOF
collector.runtime.dir=$CONFLICT_RUNTIME
collector.runtime.pidFile=run/protocol-runtime.pid
collector.runtime.statusFile=run/status.json
collector.tcp.port=$port
collector.management.enabled=false
collector.sink.file=$CONFLICT_RUNTIME/data/records.ndjson
EOF
if JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" start \
    --config "$CONFLICT_CONFIG" \
    --pid-file "$CONFLICT_RUNTIME/run/protocol-runtime.pid" \
    > "$CONFLICT_LOG" 2>&1; then
  cat "$CONFLICT_LOG"
  echo "distribution script accepted a conflicting TCP port" >&2
  exit 1
fi

curl -fsS -H "$AUTH_HEADER" "http://127.0.0.1:$management_port/health" >/dev/null
curl -fsS -H "$AUTH_HEADER" "http://127.0.0.1:$management_port/status" > "$STATUS_COPY"

"$JAVA_BIN" "$ROOT_DIR/examples/Iec104SendSinglePoint.java" 127.0.0.1 "$port"

i=0
while [ "$i" -lt 150 ]; do
  JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" status --status-file "$STATUS" > "$STATUS_COPY" || true
  if [ -s "$SINK" ] \
    && curl -fsS -H "$AUTH_HEADER" "http://127.0.0.1:$management_port/readiness" >/dev/null 2>&1 \
    && grep -q '"kind":"record"' "$SINK" \
    && grep -q '"lifecycle":"RUNNING"' "$STATUS_COPY"; then
    JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" stop --pid-file "$PID_FILE" >/dev/null
    wait "$collector_pid" >/dev/null 2>&1 || true
    collector_pid=""
    JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime-stop" --pid-file "$PID_FILE" >/dev/null
    trap - EXIT INT TERM
    echo "distribution package smoke passed"
    echo "distribution tar: $DIST_TAR"
    echo "distribution zip: $DIST_ZIP"
    echo "unpacked app: $APP_HOME"
    echo "collector log: $LOG"
    echo "sink output: $SINK"
    echo "status output: $STATUS_COPY"
    exit 0
  fi
  i=$((i + 1))
  sleep 0.2
done

cat "$LOG"
[ -f "$SINK" ] && cat "$SINK"
[ -f "$STATUS" ] && cat "$STATUS"
[ -f "$STATUS_COPY" ] && cat "$STATUS_COPY"
echo "distribution collector did not write a parsed record in time" >&2
exit 1
