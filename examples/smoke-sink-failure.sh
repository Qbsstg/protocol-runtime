#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAVA_BIN=${JAVA_BIN:-java}
MVN_BIN=${MVN_BIN:-mvn}
OUT_DIR="$ROOT_DIR/target/runtime-sink-failure-smoke"
CONFIG="$OUT_DIR/collector.properties"
LOG="$OUT_DIR/collector.log"
STATUS="$OUT_DIR/status.json"
FAILED_DIR="$OUT_DIR/runtime/data/failed-records"
BLOCKED_DIR="$OUT_DIR/blocked-sink"
SINK="$BLOCKED_DIR/records.ndjson"
PID_FILE="$OUT_DIR/runtime/run/protocol-runtime.pid"
AUTH_HEADER="Authorization: Bearer sink-failure-smoke-token"

rm -rf "$OUT_DIR"
mkdir -p "$FAILED_DIR"
mkdir -p "$BLOCKED_DIR"
rm -f "$ROOT_DIR"/runtime-app/target/runtime-app-*-standalone.jar

"$MVN_BIN" -q -pl runtime-app -am package

JAR=$(find "$ROOT_DIR/runtime-app/target" \
  -name 'runtime-app-*-standalone.jar' \
  -type f \
  -print \
  | sort \
  | tail -n 1)

if [ -z "$JAR" ]; then
  echo "standalone runtime-app jar was not built" >&2
  exit 1
fi

cat > "$CONFIG" <<EOF
collector.profile=sink-failure-smoke
collector.runtime.dir=$OUT_DIR/runtime
collector.runtime.confDir=conf
collector.runtime.logsDir=logs
collector.runtime.dataDir=data
collector.runtime.runDir=run
collector.runtime.tmpDir=tmp
collector.runtime.pidFile=run/protocol-runtime.pid
collector.runtime.statusFile=run/status.json
collector.runtime.logFile=logs/protocol-runtime.log
collector.runtime.createDirectories=true
collector.tcp.host=127.0.0.1
collector.tcp.port=0
collector.source.id=iec104:sink-failure
collector.backpressure=ACCEPT
collector.backpressure.sinkFailureThreshold=1
collector.backpressure.sinkFailureDecision=RETRY_LATER
collector.sink.type=file
collector.sink.file=$SINK
collector.sink.failedRecords.enabled=true
collector.sink.failedRecords.dir=$FAILED_DIR
collector.sink.failedRecords.maxSamples=4
collector.management.enabled=true
collector.management.host=127.0.0.1
collector.management.port=0
collector.management.access=token
collector.management.token=sink-failure-smoke-token
EOF

"$JAVA_BIN" -jar "$JAR" --validate --config "$CONFIG" >/dev/null
chmod 500 "$BLOCKED_DIR"

"$JAVA_BIN" -jar "$JAR" --config "$CONFIG" > "$LOG" 2>&1 &
collector_pid=$!

cleanup() {
  chmod 700 "$BLOCKED_DIR" >/dev/null 2>&1 || true
  kill "$collector_pid" >/dev/null 2>&1 || true
  wait "$collector_pid" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

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
  echo "collector did not start in time" >&2
  exit 1
fi

"$JAVA_BIN" "$ROOT_DIR/examples/Iec104SendSinglePoint.java" 127.0.0.1 "$port"

i=0
while [ "$i" -lt 150 ]; do
  curl -fsS -H "$AUTH_HEADER" "http://127.0.0.1:$management_port/status" > "$STATUS" || true
  if grep -q '"sinkFailureCount":1' "$STATUS" \
    && grep -q '"lastSinkDeliveryFailureType":"FILESYSTEM_ERROR"' "$STATUS" \
    && grep -q '"readiness":"NOT_READY"' "$STATUS" \
    && grep -q '"failedRecords"' "$STATUS" \
    && find "$FAILED_DIR" -name 'failed-*.json' -type f -print | grep -q .; then
    "$JAVA_BIN" -jar "$JAR" --stop --pid-file "$PID_FILE" >/dev/null
    wait "$collector_pid" >/dev/null 2>&1 || true
    trap - EXIT INT TERM
    chmod 700 "$BLOCKED_DIR" >/dev/null 2>&1 || true
    echo "sink failure smoke passed"
    echo "collector log: $LOG"
    echo "status output: $STATUS"
    echo "failed-record samples: $FAILED_DIR"
    exit 0
  fi
  i=$((i + 1))
  sleep 0.2
done

chmod 700 "$BLOCKED_DIR" >/dev/null 2>&1 || true
cat "$LOG"
[ -f "$STATUS" ] && cat "$STATUS"
find "$FAILED_DIR" -name 'failed-*.json' -type f -print
echo "sink failure smoke did not observe sink failure isolation in time" >&2
exit 1
