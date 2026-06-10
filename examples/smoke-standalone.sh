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

curl -fsS "http://127.0.0.1:$management_port/health" >/dev/null
curl -fsS "http://127.0.0.1:$management_port/status" > "$STATUS"

"$JAVA_BIN" "$ROOT_DIR/examples/Iec104SendSinglePoint.java" 127.0.0.1 "$port"

i=0
while [ "$i" -lt 50 ]; do
  if [ -s "$SINK" ] \
    && curl -fsS "http://127.0.0.1:$management_port/readiness" >/dev/null 2>&1 \
    && curl -fsS "http://127.0.0.1:$management_port/status" > "$STATUS" \
    && grep -q '"kind":"record"' "$SINK" \
    && grep -q "Protocol Runtime collector status state=RUNNING" "$LOG" \
    && grep -q '"readiness":"READY"' "$STATUS"; then
    echo "standalone collector smoke passed"
    echo "collector log: $LOG"
    echo "sink output: $SINK"
    echo "management status: $STATUS"
    exit 0
  fi
  i=$((i + 1))
  sleep 0.2
done

cat "$LOG"
[ -f "$SINK" ] && cat "$SINK"
[ -f "$STATUS" ] && cat "$STATUS"
echo "collector did not write a parsed record in time" >&2
exit 1
