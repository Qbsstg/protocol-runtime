#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAVA_BIN=${JAVA_BIN:-java}
MVN_BIN=${MVN_BIN:-mvn}
RUN_SECONDS=${RUN_SECONDS:-8}
OUT_DIR="$ROOT_DIR/target/runtime-app-long-running-smoke"
UNPACK_DIR="$OUT_DIR/unpack"
LOG="$OUT_DIR/collector.log"
STATUS_COPY="$OUT_DIR/status.json"
SELF_CHECK_OUTPUT="$OUT_DIR/self-check.json"
AUTH_HEADER="Authorization: Bearer long-running-smoke-token"

rm -rf "$OUT_DIR"
mkdir -p "$UNPACK_DIR"
rm -f "$ROOT_DIR"/runtime-app/target/runtime-app-*-distribution.tar.gz

"$MVN_BIN" -q -pl runtime-app -am package
DIST_TAR=$(find "$ROOT_DIR/runtime-app/target" \
  -name 'runtime-app-*-distribution.tar.gz' \
  -type f \
  -print \
  | sort \
  | tail -n 1)

if [ -z "$DIST_TAR" ] || [ ! -s "$DIST_TAR" ]; then
  echo "long-running smoke distribution tar.gz was not built" >&2
  exit 1
fi

tar -xzf "$DIST_TAR" -C "$UNPACK_DIR"
APP_HOME=$(find "$UNPACK_DIR" -maxdepth 1 -type d -name 'protocol-runtime-*' -print | sort | tail -n 1)
if [ -z "$APP_HOME" ]; then
  echo "long-running smoke package did not unpack to protocol-runtime-* directory" >&2
  exit 1
fi

CONFIG="$APP_HOME/conf/collector-long-running.properties"
SINK="$APP_HOME/data/long-running-records.ndjson"
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
collector.management.token=long-running-smoke-token
collector.sink.file=$SINK
collector.sink.failedRecords.enabled=true
collector.sink.failedRecords.dir=data/failed-records
collector.sink.failedRecords.maxSamples=8
EOF

JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" self-check --config "$CONFIG" > "$SELF_CHECK_OUTPUT"
if ! grep -q '"status":"PASS"' "$SELF_CHECK_OUTPUT" \
    || ! grep -q '"failedRecords"' "$SELF_CHECK_OUTPUT"; then
  cat "$SELF_CHECK_OUTPUT"
  echo "long-running smoke self-check did not pass" >&2
  exit 1
fi

collector_pid=""
cleanup() {
  if [ -n "$collector_pid" ]; then
    JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" stop --pid-file "$PID_FILE" >/dev/null 2>&1 || true
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
  echo "long-running smoke collector did not start in time" >&2
  exit 1
fi

"$JAVA_BIN" "$ROOT_DIR/examples/Iec104SendSinglePoint.java" 127.0.0.1 "$port"
i=0
while [ "$i" -lt 150 ]; do
  JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" status --status-file "$STATUS" > "$STATUS_COPY" || true
  if [ -s "$SINK" ] \
    && grep -q '"kind":"record"' "$SINK" \
    && curl -fsS -H "$AUTH_HEADER" "http://127.0.0.1:$management_port/readiness" >/dev/null 2>&1 \
    && grep -q '"lifecycle":"RUNNING"' "$STATUS_COPY"; then
    break
  fi
  i=$((i + 1))
  sleep 0.2
done
if [ "$i" -ge 150 ]; then
  cat "$LOG"
  [ -f "$SINK" ] && cat "$SINK"
  [ -f "$STATUS_COPY" ] && cat "$STATUS_COPY"
  echo "long-running smoke collector did not become ready after the first record" >&2
  exit 1
fi

deadline=$(( $(date +%s) + RUN_SECONDS ))
sent=1
while [ "$(date +%s)" -lt "$deadline" ]; do
  "$JAVA_BIN" "$ROOT_DIR/examples/Iec104SendSinglePoint.java" 127.0.0.1 "$port"
  sent=$((sent + 1))
  curl -fsS -H "$AUTH_HEADER" "http://127.0.0.1:$management_port/health" >/dev/null
  curl -fsS -H "$AUTH_HEADER" "http://127.0.0.1:$management_port/readiness" >/dev/null
  curl -fsS -H "$AUTH_HEADER" "http://127.0.0.1:$management_port/status" > "$STATUS_COPY"
  JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" status --status-file "$STATUS" > "$STATUS_COPY" || true
  sleep 1
done

if [ "$sent" -lt 2 ]; then
  cat "$LOG"
  echo "long-running smoke did not exercise enough runtime iterations" >&2
  exit 1
fi
if ! grep -q '"lifecycle":"RUNNING"' "$STATUS_COPY"; then
  cat "$LOG"
  cat "$STATUS_COPY"
  echo "long-running smoke did not keep RUNNING status evidence" >&2
  exit 1
fi
if [ ! -s "$SINK" ] || ! grep -q '"kind":"record"' "$SINK"; then
  cat "$LOG"
  [ -f "$SINK" ] && cat "$SINK"
  echo "long-running smoke did not write file sink record evidence" >&2
  exit 1
fi
if ! grep -q "Protocol Runtime collector started" "$LOG" \
    || ! grep -q "Protocol Runtime management started" "$LOG"; then
  cat "$LOG"
  echo "long-running smoke log evidence is incomplete" >&2
  exit 1
fi

JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" stop --pid-file "$PID_FILE" >/dev/null
wait "$collector_pid" >/dev/null 2>&1 || true
collector_pid=""
trap - EXIT INT TERM

echo "long-running smoke passed"
echo "distribution tar: $DIST_TAR"
echo "unpacked app: $APP_HOME"
echo "collector log: $LOG"
echo "sink output: $SINK"
echo "status output: $STATUS_COPY"
