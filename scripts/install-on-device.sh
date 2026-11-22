#!/usr/bin/env bash
set -euo pipefail

# Builds and installs the FOSS debug APK on the first Android device advertised by
# Wireless ADB. Pairing is still a one-time Android/ADB action; after that this
# script discovers the current TLS endpoint automatically, even if the port changes.

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK_PATH="$PROJECT_DIR/app/build/outputs/apk/foss/debug/gallery-28-foss-debug.apk"

if [[ "${1:-}" == "--no-build" ]]; then
    shift
    BUILD_APK=0
else
    BUILD_APK=1
fi

if [[ "$#" -gt 0 ]]; then
    echo "Usage: $0 [--no-build]" >&2
    exit 2
fi

find_adb() {
    if [[ -n "${ANDROID_ADB:-}" && -x "$ANDROID_ADB" ]]; then
        printf '%s\n' "$ANDROID_ADB"
        return
    fi
    if command -v adb >/dev/null 2>&1; then
        command -v adb
        return
    fi

    local candidate
    for candidate in \
        "${ANDROID_HOME:-}/platform-tools/adb" \
        "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
        "${HOME:-}/Library/Android/sdk/platform-tools/adb"; do
        if [[ -x "$candidate" ]]; then
            printf '%s\n' "$candidate"
            return
        fi
    done

    echo "adb wurde nicht gefunden. Installiere Android Platform Tools oder setze ANDROID_ADB." >&2
    exit 1
}

ADB="$(find_adb)"

if [[ "$BUILD_APK" -eq 1 ]]; then
    "$PROJECT_DIR/gradlew" :app:assembleFossDebug --no-daemon
fi

if [[ ! -f "$APK_PATH" ]]; then
    echo "APK nicht gefunden: $APK_PATH" >&2
    exit 1
fi

DEVICE=""

# Android 11+ advertises a TLS connect endpoint through mDNS. The endpoint port
# can change, so do not persist a hard-coded IP:port in the project.
while IFS= read -r endpoint; do
    [[ -z "$endpoint" ]] && continue
    "$ADB" connect "$endpoint" >/dev/null 2>&1 || true
    if [[ "$("$ADB" -s "$endpoint" get-state 2>/dev/null || true)" == "device" ]]; then
        DEVICE="$endpoint"
        break
    fi
done < <("$ADB" mdns services 2>/dev/null | awk 'NR > 1 && $3 ~ /^[0-9]+(\.[0-9]+){3}:[0-9]+$/ { print $3 }')

# Fallback for USB, classic adb connect, or environments without mDNS.
if [[ -z "$DEVICE" ]]; then
    DEVICE="$("$ADB" devices | awk '$2 == "device" && $1 ~ /^[0-9]+(\.[0-9]+){3}:[0-9]+$/ { print $1; exit }')"
fi

if [[ -z "$DEVICE" ]]; then
    echo "Kein erreichbares Android-Gerät gefunden." >&2
    echo "Einmalig: Entwickleroptionen > Drahtlose Fehlerbehebung > Mit Pairing-Code koppeln." >&2
    echo "Danach dieses Skript erneut ausführen." >&2
    exit 1
fi

echo "Installiere auf $DEVICE ..."
"$ADB" -s "$DEVICE" install -r "$APK_PATH"
echo "Fertig: $APK_PATH"
