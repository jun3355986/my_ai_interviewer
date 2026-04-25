#!/usr/bin/env sh
set -eu

if [ -z "${APP_MODULE:-}" ]; then
  echo "[ERROR] APP_MODULE is required, e.g. ai-interviewer-gateway" >&2
  exit 1
fi

JAR_PATH="/opt/apps/${APP_MODULE}.jar"
if [ ! -f "${JAR_PATH}" ]; then
  echo "[ERROR] Jar not found for module: ${APP_MODULE}" >&2
  echo "[INFO] Available jars:" >&2
  ls -1 /opt/apps >&2 || true
  exit 1
fi

echo "[INFO] Starting ${APP_MODULE} with ${JAR_PATH}" >&2
exec java ${JAVA_OPTS:-} -jar "${JAR_PATH}"
