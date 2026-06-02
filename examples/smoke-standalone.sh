#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAVA_BIN=${JAVA_BIN:-java}
MVN_BIN=${MVN_BIN:-mvn}
JAR="$ROOT_DIR/runtime-app/target/runtime-app-0.3.0-SNAPSHOT-standalone.jar"
OUT_DIR="$ROOT_DIR/target/runtime-app-smoke"
CONFIG="$OUT_DIR/collector.properties"
LOG="$OUT_DIR/collector.log"
SINK="$OUT_DIR/records.ndjson"

rm -rf "$OUT_DIR"
mkdir -p "$OUT_DIR"

"$MVN_BIN" -q -pl runtime-app -am package

cat > "$CONFIG" <<EOF
collector.tcp.host=127.0.0.1
collector.tcp.port=0
collector.source.id=iec104:smoke
collector.backpressure=ACCEPT
collector.sink.type=file
collector.sink.file=$SINK
collector.iec104.strictAsduParsing=false
EOF

"$JAVA_BIN" -jar "$JAR" --config "$CONFIG" > "$LOG" 2>&1 &
collector_pid=$!

cleanup() {
  kill "$collector_pid" >/dev/null 2>&1 || true
  wait "$collector_pid" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

port=""
i=0
while [ "$i" -lt 50 ]; do
  if grep -q "Protocol Runtime collector started" "$LOG"; then
    port=$(sed -n 's/.* port=\([0-9][0-9]*\) .*/\1/p' "$LOG" | tail -n 1)
    break
  fi
  if ! kill -0 "$collector_pid" >/dev/null 2>&1; then
    cat "$LOG"
    exit 1
  fi
  i=$((i + 1))
  sleep 0.2
done

if [ -z "$port" ]; then
  cat "$LOG"
  echo "collector did not start in time" >&2
  exit 1
fi

"$JAVA_BIN" "$ROOT_DIR/examples/Iec104SendSinglePoint.java" 127.0.0.1 "$port"

i=0
while [ "$i" -lt 50 ]; do
  if [ -s "$SINK" ] \
    && grep -q '"kind":"record"' "$SINK" \
    && grep -q "Protocol Runtime collector status state=RUNNING" "$LOG"; then
    echo "standalone collector smoke passed"
    echo "collector log: $LOG"
    echo "sink output: $SINK"
    exit 0
  fi
  i=$((i + 1))
  sleep 0.2
done

cat "$LOG"
[ -f "$SINK" ] && cat "$SINK"
echo "collector did not write a parsed record in time" >&2
exit 1
