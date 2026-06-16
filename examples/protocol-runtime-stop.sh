#!/usr/bin/env sh
set -eu

APP_HOME=${APP_HOME:-/opt/protocol-runtime}
JAVA_BIN=${JAVA_BIN:-java}
PID_FILE=${PID_FILE:-"$APP_HOME/run/protocol-runtime.pid"}

if [ -x "$APP_HOME/bin/protocol-runtime" ]; then
  exec "$APP_HOME/bin/protocol-runtime" stop --pid-file "$PID_FILE" "$@"
fi

JAR=${JAR:-"$APP_HOME/lib/runtime-app-0.15.0-SNAPSHOT-standalone.jar"}
exec "$JAVA_BIN" -jar "$JAR" --stop --pid-file "$PID_FILE"
