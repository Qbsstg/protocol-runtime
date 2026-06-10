#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAVA_BIN=${JAVA_BIN:-java}
MVN_BIN=${MVN_BIN:-mvn}
OUT_DIR="$ROOT_DIR/target/runtime-app-http-smoke"
CONFIG="$OUT_DIR/collector-http.properties"
LOG="$OUT_DIR/collector.log"
SINK="$OUT_DIR/records.ndjson"
RESPONSE="$OUT_DIR/response.json"
STATUS="$OUT_DIR/status.json"

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
collector.source.id=iec104:http-smoke
collector.protocol=iec104
collector.http.listeners=http-main
collector.http.listener.http-main.host=127.0.0.1
collector.http.listener.http-main.port=0
collector.http.listener.http-main.path=/ingress
collector.http.listener.http-main.source=default
collector.http.listener.http-main.sourceIdMode=CONFIGURED
collector.http.listener.http-main.maxPayloadBytes=65536
collector.http.listener.http-main.responseMode=ACK_ON_ACCEPT
collector.http.listener.http-main.workerThreads=1
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
EOF

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
  if grep -q "Protocol Runtime collector started transport=http" "$LOG" \
    && grep -q "Protocol Runtime management started" "$LOG"; then
    port=$(sed -n 's/.*Protocol Runtime collector started transport=http.* port=\([0-9][0-9]*\) .*/\1/p' "$LOG" | tail -n 1)
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
  echo "HTTP collector did not start in time" >&2
  exit 1
fi

curl -fsS "http://127.0.0.1:$management_port/health" >/dev/null
curl -fsS "http://127.0.0.1:$management_port/status" > "$STATUS"

printf '\x68\x0E\x00\x00\x00\x00\x01\x01\x03\x00\x01\x00\x01\x00\x00\x01' \
  | curl -fsS -X POST --data-binary @- \
      -H 'Content-Type: application/octet-stream' \
      "http://127.0.0.1:$port/ingress" > "$RESPONSE"

i=0
while [ "$i" -lt 50 ]; do
  if [ -s "$SINK" ] \
    && curl -fsS "http://127.0.0.1:$management_port/readiness" >/dev/null 2>&1 \
    && curl -fsS "http://127.0.0.1:$management_port/status" > "$STATUS" \
    && grep -q '"kind":"record"' "$SINK" \
    && grep -q '"sourceId":"iec104:http-smoke"' "$SINK" \
    && grep -q "Protocol Runtime collector status state=RUNNING" "$LOG" \
    && grep -q "httpListeners=\[http-main@" "$LOG" \
    && grep -q '"readiness":"READY"' "$STATUS"; then
    echo "standalone HTTP collector smoke passed"
    echo "collector log: $LOG"
    echo "sink output: $SINK"
    echo "response output: $RESPONSE"
    echo "management status: $STATUS"
    exit 0
  fi
  i=$((i + 1))
  sleep 0.2
done

cat "$LOG"
[ -f "$SINK" ] && cat "$SINK"
[ -f "$RESPONSE" ] && cat "$RESPONSE"
[ -f "$STATUS" ] && cat "$STATUS"
echo "HTTP collector did not write a parsed record in time" >&2
exit 1
