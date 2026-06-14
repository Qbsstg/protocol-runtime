#!/usr/bin/env sh
set -eu

APP_HOME=${APP_HOME:-/opt/protocol-runtime}
JAVA_BIN=${JAVA_BIN:-java}
JAR=${JAR:-"$APP_HOME/runtime-app-0.13.0-SNAPSHOT-standalone.jar"}
PID_FILE=${PID_FILE:-"$APP_HOME/run/protocol-runtime.pid"}

exec "$JAVA_BIN" -jar "$JAR" --stop --pid-file "$PID_FILE"
