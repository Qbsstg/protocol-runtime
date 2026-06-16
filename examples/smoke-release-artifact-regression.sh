#!/usr/bin/env sh
set -eu

ROOT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAVA_BIN=${JAVA_BIN:-java}
MVN_BIN=${MVN_BIN:-mvn}
STANDALONE_JAR=${STANDALONE_JAR:-}
DIST_TAR=${DIST_TAR:-}
DIST_ZIP=${DIST_ZIP:-}
OUT_DIR="$ROOT_DIR/target/runtime-app-release-artifact-regression-smoke"
UNPACK_DIR="$OUT_DIR/unpack"
LOG="$OUT_DIR/collector.log"
STATUS_COPY="$OUT_DIR/status.json"

rm -rf "$OUT_DIR"
mkdir -p "$UNPACK_DIR"

if [ -z "$STANDALONE_JAR" ] || [ -z "$DIST_TAR" ] || [ -z "$DIST_ZIP" ]; then
  rm -f "$ROOT_DIR"/runtime-app/target/runtime-app-*-standalone.jar
  rm -f "$ROOT_DIR"/runtime-app/target/runtime-app-*-distribution.tar.gz
  rm -f "$ROOT_DIR"/runtime-app/target/runtime-app-*-distribution.zip
  "$MVN_BIN" -q -pl runtime-app -am package
  STANDALONE_JAR=$(find "$ROOT_DIR/runtime-app/target" \
    -name 'runtime-app-*-standalone.jar' \
    -type f \
    -print \
    | sort \
    | tail -n 1)
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

for artifact in "$STANDALONE_JAR" "$DIST_TAR" "$DIST_ZIP"; do
  if [ -z "$artifact" ] || [ ! -s "$artifact" ]; then
    echo "release regression artifact is missing: $artifact" >&2
    exit 1
  fi
done
for checksum in "$DIST_TAR.sha256" "$DIST_TAR.sha512" "$DIST_ZIP.sha256" "$DIST_ZIP.sha512"; do
  if [ ! -s "$checksum" ]; then
    echo "release regression checksum is missing: $checksum" >&2
    exit 1
  fi
done

tar -xzf "$DIST_TAR" -C "$UNPACK_DIR"
APP_HOME=$(find "$UNPACK_DIR" -maxdepth 1 -type d -name 'protocol-runtime-*' -print | sort | tail -n 1)
if [ -z "$APP_HOME" ]; then
  echo "release regression package did not unpack to protocol-runtime-* directory" >&2
  exit 1
fi

CONFIG="$APP_HOME/conf/collector-regression.properties"
SINK="$APP_HOME/data/regression-records.ndjson"
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

"$JAVA_BIN" -jar "$STANDALONE_JAR" --validate --config "$CONFIG" >/dev/null
"$JAVA_BIN" -jar "$STANDALONE_JAR" \
  --self-check \
  --config "$CONFIG" \
  --operation-app-home "$APP_HOME" \
  --operation-package-metadata "$APP_HOME/package.properties" \
  --operation-package-integrity "regression-check" \
  > "$OUT_DIR/jar-self-check.json"
if ! grep -q '"command":"self-check"' "$OUT_DIR/jar-self-check.json"; then
  cat "$OUT_DIR/jar-self-check.json"
  echo "standalone jar self-check did not produce diagnostics" >&2
  exit 1
fi
"$JAVA_BIN" -jar "$STANDALONE_JAR" \
  --hot-check \
  --config "$CONFIG" \
  --hot-check-baseline "$HOT_CHECK_BASELINE" \
  > "$OUT_DIR/jar-hot-check.json"
if ! grep -q '"hotReloaded":false' "$OUT_DIR/jar-hot-check.json"; then
  cat "$OUT_DIR/jar-hot-check.json"
  echo "standalone jar hot-check did not report no hot reload" >&2
  exit 1
fi
"$JAVA_BIN" -jar "$STANDALONE_JAR" --dry-run --config "$CONFIG" --status-export "$STATUS" >/dev/null
if ! grep -q '"lifecycle":"CONFIGURED"' "$STATUS"; then
  cat "$STATUS"
  echo "standalone jar dry-run did not export configured status" >&2
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
  --artifact "$DIST_ZIP" \
  --checksum "$DIST_ZIP.sha512" \
  >/dev/null
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" validate --config "$CONFIG" >/dev/null
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" self-check --config "$CONFIG" > "$OUT_DIR/package-self-check.json"
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" hot-check --config "$CONFIG" > "$OUT_DIR/package-hot-check.json"
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" dry-run --config "$CONFIG" --status-file "$STATUS" >/dev/null
JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" status --status-file "$STATUS" > "$STATUS_COPY"

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
  echo "release regression collector did not start in time" >&2
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
  echo "release regression status command did not observe RUNNING lifecycle" >&2
  exit 1
fi

JAVA_BIN="$JAVA_BIN" "$APP_HOME/bin/protocol-runtime" stop --pid-file "$PID_FILE" >/dev/null
wait "$collector_pid" >/dev/null 2>&1 || true
collector_pid=""
trap - EXIT INT TERM

echo "release artifact regression smoke passed"
echo "standalone jar: $STANDALONE_JAR"
echo "distribution tar: $DIST_TAR"
echo "distribution zip: $DIST_ZIP"
echo "unpacked app: $APP_HOME"
echo "collector log: $LOG"
echo "status output: $STATUS_COPY"
