#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${WORKSPACE_FOLDER:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
# Copy everything under src/ into the RetroMCP workspace src/.
SRC_DIR="${ROOT_DIR}/src"
RMCP_SRC="${ROOT_DIR}/tools/rmcp/minecraft/src"
MOD_ENTRY="${MOD_ENTRY:-mod_BetaMoon.java}"
LUAJ_JAR="${ROOT_DIR}/lib/luaj-jse-3.0.1.jar"
LUAJ_LIB_DIR="${ROOT_DIR}/tools/rmcp/libraries/org/luaj/luaj-jse/3.0.1"
LUAJ_MINECRAFT_LIB_DIR="${ROOT_DIR}/tools/rmcp/minecraft/libraries/org/luaj/luaj-jse/3.0.1"

if [[ ! -d "${RMCP_SRC}" ]]; then
  echo "RetroMCP workspace not found at ${RMCP_SRC}. Run scripts/setup_rmcp.sh first." >&2
  exit 1
fi

# Clean out old mod sources in RMCP workspace to avoid duplicates.
rm -f "${RMCP_SRC}/${MOD_ENTRY}"
rm -rf "${RMCP_SRC}/piggo"
rm -rf "${RMCP_SRC}/org/luaj"

if command -v rsync >/dev/null 2>&1; then
  rsync -av "${SRC_DIR}/" "${RMCP_SRC}/"
else
  # Fallback for environments without rsync (e.g., default Windows shells).
  cp -R "${SRC_DIR}/." "${RMCP_SRC}/"
fi

if [[ -f "${LUAJ_JAR}" ]]; then
  mkdir -p "${LUAJ_LIB_DIR}"
  cp "${LUAJ_JAR}" "${LUAJ_LIB_DIR}/luaj-jse-3.0.1.jar"
  mkdir -p "${LUAJ_MINECRAFT_LIB_DIR}"
  cp "${LUAJ_JAR}" "${LUAJ_MINECRAFT_LIB_DIR}/luaj-jse-3.0.1.jar"
fi
echo "Synced mod sources into ${RMCP_SRC}"
