#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${WORKSPACE_FOLDER:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
TOOLS_DIR="${ROOT_DIR}/tools"
RMCP_DIR="${TOOLS_DIR}/rmcp"
CLI_JAR="${RMCP_DIR}/RetroMCP-Java-CLI.jar"
VERSION_ID="b1.7.3"

mkdir -p "${TOOLS_DIR}" "${RMCP_DIR}"

# Java 8 path (Windows install). Override with JAVA_BIN or JAVA_HOME if desired.
JAVA_BIN_DEFAULT="C:\\Program Files\\Eclipse Adoptium\\jdk-8.0.472.8-hotspot\\bin\\java.exe"
JAVA_BIN="${JAVA_BIN:-}"
if [[ -z "$JAVA_BIN" && -n "${JAVA_HOME:-}" ]]; then
  JAVA_BIN="${JAVA_HOME}/bin/java"
fi

if [[ -z "$JAVA_BIN" ]]; then
  if command -v cygpath >/dev/null 2>&1; then
    JAVA_BIN="$(cygpath -u "$JAVA_BIN_DEFAULT")"
  else
    JAVA_BIN="$JAVA_BIN_DEFAULT"
  fi
fi

if [[ ! -x "$JAVA_BIN" && ! "$(command -v java 2>/dev/null)" == "" ]]; then
  JAVA_BIN="$(command -v java)"
fi

if [[ ! -x "$JAVA_BIN" ]]; then
  echo "Java not found. Set JAVA_BIN or install JDK 8 at $JAVA_BIN_DEFAULT" >&2
  exit 1
fi

if [[ ! -f "${CLI_JAR}" ]]; then
  echo "Downloading RetroMCP-Java CLI..."
  curl -L -o "${CLI_JAR}" "https://github.com/MCPHackers/RetroMCP-Java/releases/download/v1.0/RetroMCP-Java-CLI.jar"
fi

cd "${RMCP_DIR}"

echo "Running RetroMCP setup for ${VERSION_ID}..."
"${JAVA_BIN}" -jar "${CLI_JAR}" setup -setup="${VERSION_ID}"

echo "Decompiling ${VERSION_ID}..."
"${JAVA_BIN}" -jar "${CLI_JAR}" decompile

echo "RetroMCP workspace ready at ${RMCP_DIR}/minecraft"
