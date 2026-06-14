#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAVA_BIN=${JAVA_BIN:-java}
MVN_BIN=${MVN_BIN:-mvn}
OUT_DIR="$ROOT_DIR/target/runtime-app-smoke"
CONFIG="$OUT_DIR/collector.properties"
LOG="$OUT_DIR/collector.log"
SINK="$OUT_DIR/records.ndjson"
STATUS="$OUT_DIR/status.json"
DRY_STATUS="$OUT_DIR/dry-run-status.json"
RUNTIME_STATUS="$OUT_DIR/runtime/run/status.json"
PID_FILE="$OUT_DIR/runtime/run/protocol-runtime.pid"
UNAUTHORIZED="$OUT_DIR/unauthorized.json"
NOT_FOUND="$OUT_DIR/not-found.json"
METHOD_NOT_ALLOWED="$OUT_DIR/method-not-allowed.json"
MALFORMED="$OUT_DIR/malformed.json"
AUTH_HEADER="Authorization: Bearer smoke-management-token"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"
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
collector.profile=smoke
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
collector.source.id=iec104:smoke
collector.backpressure=ACCEPT
collector.sink.type=file
collector.sink.file=$SINK
collector.iec104.strictAsduParsing=false
collector.management.enabled=true
collector.management.host=127.0.0.1
collector.management.port=0
collector.management.healthPath=/health
collector.management.readinessPath=/readiness
collector.management.statusPath=/status
collector.management.access=token
collector.management.token=smoke-management-token
collector.management.requestLogging.enabled=true
collector.management.healthHistory.maxEntries=8
EOF

"$JAVA_BIN" -jar "$JAR" --validate --config "$CONFIG" >/dev/null
"$JAVA_BIN" -jar "$JAR" --dry-run --config "$CONFIG" --status-export "$DRY_STATUS" >/dev/null
if ! grep -q '"lifecycle":"CONFIGURED"' "$DRY_STATUS"; then
  cat "$DRY_STATUS"
  echo "dry-run did not export configured status JSON" >&2
  exit 1
fi

"$JAVA_BIN" -jar "$JAR" --config "$CONFIG" > "$LOG" 2>&1 &
collector_pid=$!

cleanup() {
  kill "$collector_pid" >/dev/null 2>&1 || true
  wait "$collector_pid" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

port=""
management_port=""
i=0
while [ "$i" -lt 50 ]; do
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

if [ ! -s "$PID_FILE" ] || ! grep -q "$collector_pid" "$PID_FILE"; then
  cat "$LOG"
  [ -f "$PID_FILE" ] && cat "$PID_FILE"
  echo "collector did not write the configured pid file" >&2
  exit 1
fi

http_code=$(curl -sS -o "$UNAUTHORIZED" -w "%{http_code}" "http://127.0.0.1:$management_port/status" || true)
if [ "$http_code" != "401" ] || ! grep -q '"code":"unauthorized"' "$UNAUTHORIZED"; then
  cat "$LOG"
  cat "$UNAUTHORIZED"
  echo "management token rejection did not return stable unauthorized JSON" >&2
  exit 1
fi

http_code=$(curl -sS -H "$AUTH_HEADER" -o "$NOT_FOUND" -w "%{http_code}" \
  "http://127.0.0.1:$management_port/missing" || true)
if [ "$http_code" != "404" ] || ! grep -q '"code":"not_found"' "$NOT_FOUND"; then
  cat "$LOG"
  cat "$NOT_FOUND"
  echo "management not-found path did not return stable JSON" >&2
  exit 1
fi

http_code=$(curl -sS -X POST -H "$AUTH_HEADER" -o "$METHOD_NOT_ALLOWED" -w "%{http_code}" \
  "http://127.0.0.1:$management_port/status" || true)
if [ "$http_code" != "405" ] || ! grep -q '"code":"method_not_allowed"' "$METHOD_NOT_ALLOWED"; then
  cat "$LOG"
  cat "$METHOD_NOT_ALLOWED"
  echo "management method-not-allowed path did not return stable JSON" >&2
  exit 1
fi

http_code=$(printf 'bad' | curl -sS -X GET --data-binary @- -H "$AUTH_HEADER" \
  -o "$MALFORMED" -w "%{http_code}" "http://127.0.0.1:$management_port/status" || true)
if [ "$http_code" != "400" ] || ! grep -q '"code":"malformed_request"' "$MALFORMED"; then
  cat "$LOG"
  cat "$MALFORMED"
  echo "management malformed request path did not return stable JSON" >&2
  exit 1
fi

curl -fsS -H "$AUTH_HEADER" "http://127.0.0.1:$management_port/health" >/dev/null
curl -fsS -H "$AUTH_HEADER" "http://127.0.0.1:$management_port/status" > "$STATUS"

"$JAVA_BIN" "$ROOT_DIR/examples/Iec104SendSinglePoint.java" 127.0.0.1 "$port"

i=0
while [ "$i" -lt 50 ]; do
  if [ -s "$SINK" ] \
    && curl -fsS -H "$AUTH_HEADER" "http://127.0.0.1:$management_port/readiness" >/dev/null 2>&1 \
    && curl -fsS -H "$AUTH_HEADER" "http://127.0.0.1:$management_port/status" > "$STATUS" \
    && grep -q '"kind":"record"' "$SINK" \
    && grep -q "Protocol Runtime collector status state=RUNNING" "$LOG" \
    && grep -q '"readiness":"READY"' "$STATUS" \
    && grep -q '"mode":"token"' "$STATUS" \
    && grep -q '"rejectedRequestCount":1' "$STATUS" \
    && grep -q '"healthHistory"' "$STATUS" \
    && grep -q '"statusCounts"' "$STATUS" \
    && grep -q '"lifecycle":"RUNNING"' "$RUNTIME_STATUS"; then
    "$JAVA_BIN" -jar "$JAR" --stop --pid-file "$PID_FILE" >/dev/null
    wait "$collector_pid" >/dev/null 2>&1 || true
    "$JAVA_BIN" -jar "$JAR" --stop --pid-file "$PID_FILE" >/dev/null
    trap - EXIT INT TERM
    echo "standalone collector smoke passed"
    echo "collector log: $LOG"
    echo "sink output: $SINK"
    echo "management status: $STATUS"
    echo "runtime status export: $RUNTIME_STATUS"
    exit 0
  fi
  i=$((i + 1))
  sleep 0.2
done

cat "$LOG"
[ -f "$SINK" ] && cat "$SINK"
[ -f "$STATUS" ] && cat "$STATUS"
[ -f "$UNAUTHORIZED" ] && cat "$UNAUTHORIZED"
[ -f "$NOT_FOUND" ] && cat "$NOT_FOUND"
[ -f "$METHOD_NOT_ALLOWED" ] && cat "$METHOD_NOT_ALLOWED"
[ -f "$MALFORMED" ] && cat "$MALFORMED"
[ -f "$RUNTIME_STATUS" ] && cat "$RUNTIME_STATUS"
echo "collector did not write a parsed record in time" >&2
exit 1
