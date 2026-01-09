#!/usr/bin/env bash
set -euo pipefail

# Build and copy the mod into the MultiMC instance mods folder.

ROOT_DIR="${WORKSPACE_FOLDER:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
WORKSPACE_DIR="${ROOT_DIR}/tools/rmcp/minecraft"

MOD_NAME="${MOD_NAME:-BetaMoon}"
OUTPUT_DIR="${ROOT_DIR}/tools/rmcp/minecraft/build"
OUTPUT_JAR="${OUTPUT_DIR}/${MOD_NAME}.jar"
LUAJ_JAR="${ROOT_DIR}/lib/luaj-jse-3.0.1.jar"

# Adjust this to your MultiMC instance mods path.
# Default (Windows): E:\Program Files\MultiMC\instances\b1.7.3_modding\.minecraft\mods
: "${MULTIMC_WIN_PATH:=E:\\Program Files\\MultiMC\\instances\\b1.7.3_modding\\.minecraft\\mods}"
if [[ -z "${MULTIMC_MODS_PATH:-}" ]]; then
  if command -v cygpath >/dev/null 2>&1; then
    MULTIMC_MODS_PATH=$(cygpath -u "${MULTIMC_WIN_PATH}")
  else
    MULTIMC_MODS_PATH="/mnt/e/Program Files/MultiMC/instances/b1.7.3_modding/.minecraft/mods"
  fi
fi

if [[ ! -d "${WORKSPACE_DIR}" ]]; then
  echo "RetroMCP workspace not found at ${WORKSPACE_DIR}. Run setup and build first." >&2
  exit 1
fi

mkdir -p "${OUTPUT_DIR}"
tmp_zip_dir="$(mktemp -d)"

# Pack everything from the RetroMCP reobf output into the jar.
REOBF_DIR=""
for candidate in "${WORKSPACE_DIR}/reobf/client" "${WORKSPACE_DIR}/reobf/minecraft" "${WORKSPACE_DIR}/reobf" "${WORKSPACE_DIR}/bin"; do
  if [[ -d "${candidate}" ]]; then
    REOBF_DIR="${candidate}"
    break
  fi
done

if [[ -z "${REOBF_DIR}" ]]; then
  echo "Reobf output not found under ${WORKSPACE_DIR}. Run the build task first." >&2
  rm -rf "${tmp_zip_dir}"
  exit 1
fi
cp -R "${REOBF_DIR}/." "${tmp_zip_dir}/"

# Merge LuaJ classes if the jar is present.
if [[ -f "${LUAJ_JAR}" ]]; then
  ( cd "${tmp_zip_dir}" && jar xf "${LUAJ_JAR}" )
fi

# Include all resources if present (e.g., mcmod.info, licenses).
if [[ -d "${ROOT_DIR}/resources" ]] && compgen -G "${ROOT_DIR}/resources/*" > /dev/null; then
  mkdir -p "${tmp_zip_dir}/resources"
  cp -R "${ROOT_DIR}/resources/." "${tmp_zip_dir}/resources/"
fi

( cd "${tmp_zip_dir}" && jar cf "${OUTPUT_JAR}" . )
rm -rf "${tmp_zip_dir}"

mkdir -p "${MULTIMC_MODS_PATH}"
cp "${OUTPUT_JAR}" "${MULTIMC_MODS_PATH}/${MOD_NAME}.jar"

echo "Mod jar created at ${OUTPUT_JAR}"
echo "Copied to MultiMC mods folder: ${MULTIMC_MODS_PATH}/${MOD_NAME}.jar"
