#!/usr/bin/env bash
# ============================================================
#  GameFlow LB — Run from distributable ZIP
#  Requires: Java 21+ on PATH or JAVA_HOME
# ============================================================

set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"

JAR_PATH="$ROOT/gameflow-lb.jar"
if [ ! -f "$JAR_PATH" ]; then
    echo "[ERROR] gameflow-lb.jar not found at: $JAR_PATH" >&2
    exit 1
fi

# Locate Java
JAVA_CMD=""

check_java() {
    local cmd="$1"
    if [ -x "$cmd" ]; then
        local ver
        ver=$("$cmd" -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{print ($1 == 1 ? $2 : $1)}')
        if [ -n "$ver" ] && [ "$ver" -ge 21 ] 2>/dev/null; then
            JAVA_CMD="$cmd"
            return 0
        fi
    fi
    return 1
}

# 1. Check java on PATH
if command -v java &>/dev/null; then
    check_java "$(command -v java)" || true
fi

# 2. Check JAVA_HOME
if [ -z "$JAVA_CMD" ] && [ -n "${JAVA_HOME:-}" ]; then
    check_java "${JAVA_HOME}/bin/java" || true
fi

# 3. Check common Linux/macOS directories
if [ -z "$JAVA_CMD" ]; then
    for candidate in /usr/lib/jvm/java-2* /usr/lib/jvm/temurin-2* /Library/Java/JavaVirtualMachines/*/Contents/Home; do
        if [ -d "$candidate" ]; then
            check_java "$candidate/bin/java" && break
        fi
    done
fi

if [ -z "$JAVA_CMD" ]; then
    echo "[ERROR] Java 21+ is required. Install it or set JAVA_HOME." >&2
    exit 1
fi

echo ""
echo "  ╔═══════════════════════════════════════╗"
echo "  ║   GameFlow LB - Load Balancer Sim     ║"
echo "  ║   http://localhost:8080               ║"
echo "  ╚═══════════════════════════════════════╝"
echo ""
echo "Starting GameFlow LB with $JAVA_CMD..."

exec "$JAVA_CMD" \
    -Djava.rmi.server.hostname=127.0.0.1 \
    -jar "$JAR_PATH"
