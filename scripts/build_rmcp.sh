#!/usr/bin/env bash
set -euo pipefail

# Build RetroMCP client: recompile, reobfuscate, build.

# Workspace root is the script's parent/.. in POSIX form.
ROOT_DIR="${WORKSPACE_FOLDER:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

WORKDIR="${ROOT_DIR}/tools/rmcp"
CLI_JAR="${WORKDIR}/RetroMCP-Java-CLI.jar"
WRAPPER_DIR="${WORKDIR}/_cli_wrapper"
WRAPPER_SRC="${WRAPPER_DIR}/CliWrapper.java"
WRAPPER_CLASS="${WRAPPER_DIR}/CliWrapper.class"

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

if [[ ! -d "$WORKDIR" ]]; then
  echo "RetroMCP workspace not found at $WORKDIR" >&2
  exit 1
fi

if [[ ! -f "$CLI_JAR" ]]; then
  echo "RetroMCP CLI jar not found at $CLI_JAR" >&2
  exit 1
fi

ensure_cli_wrapper() {
  if [[ -f "${WRAPPER_CLASS}" ]]; then
    return
  fi
  mkdir -p "${WRAPPER_DIR}"
  cat > "${WRAPPER_SRC}" <<'EOF'
public final class CliWrapper {
    public static void main(String[] args) throws Exception {
        try {
            org.mcphackers.mcp.main.MainCLI.main(args);
        } catch (Throwable t) {
            System.err.println("RetroMCP wrapper caught exception:");
            t.printStackTrace(System.err);
            if (t instanceof Exception) {
                throw (Exception) t;
            }
            throw new RuntimeException(t);
        }
    }
}
EOF
  local javac_bin="javac"
  if ! command -v "${javac_bin}" >/dev/null 2>&1; then
    javac_bin="${JAVA_BIN%java}javac"
  fi
  "${javac_bin}" -cp "${CLI_JAR}" "${WRAPPER_SRC}"
}

run_cli() {
  ensure_cli_wrapper
  "${JAVA_BIN}" -cp "${CLI_JAR}:${WRAPPER_DIR}" CliWrapper "$@"
}

cd "$WORKDIR"
LOG_DIR="${WORKDIR}/logs"
mkdir -p "${LOG_DIR}"
LOG_FILE="${LOG_DIR}/build-$(date +%Y%m%d-%H%M%S).log"

run_step() {
  local label="$1"
  shift
  echo "=== ${label} ===" | tee -a "${LOG_FILE}"
  if command -v script >/dev/null 2>&1; then
    local cmd_str=""
    printf -v cmd_str '%q ' "$@"
    if ! script -q -e -a -c "${cmd_str}" "${LOG_FILE}"; then
      echo "Step '${label}' failed. See ${LOG_FILE}" >&2
      tail -n 200 "${LOG_FILE}" >&2
      exit 1
    fi
    return
  fi
  if command -v stdbuf >/dev/null 2>&1; then
    if ! stdbuf -oL -eL "$@" 2>&1 | tee -a "${LOG_FILE}"; then
      echo "Step '${label}' failed. See ${LOG_FILE}" >&2
      tail -n 200 "${LOG_FILE}" >&2
      exit 1
    fi
    return
  fi
  if ! "$@" 2>&1 | tee -a "${LOG_FILE}"; then
    echo "Step '${label}' failed. See ${LOG_FILE}" >&2
    tail -n 200 "${LOG_FILE}" >&2
    exit 1
  fi
}

ensure_cli_wrapper
CLI_CMD_BASE=("${JAVA_BIN}" -cp "${CLI_JAR}:${WRAPPER_DIR}" CliWrapper)

run_step "recompile" "${CLI_CMD_BASE[@]}" recompile
run_step "reobfuscate" "${CLI_CMD_BASE[@]}" reobfuscate
run_step "build" "${CLI_CMD_BASE[@]}" build

echo "RetroMCP build completed. Log: ${LOG_FILE}"
