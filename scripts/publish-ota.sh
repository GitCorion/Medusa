#!/usr/bin/env bash
#
# Prepara una distribución pública por GitHub Releases: compila el APK firmado y actualiza
# docs/api/version.json con la versión, el SHA-256 y la URL del asset del Release.
#
# Uso:
#   ./scripts/publish-ota.sh                       # compila y genera el manifiesto
#   SKIP_BUILD=1 APK_URL=... ./scripts/publish-ota.sh   # reutiliza el APK ya compilado
#
# La creación del Release y la subida del APK la hace .github/workflows/release.yml.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

APK_SRC="app/build/outputs/apk/release/app-release.apk"
REPO="${GITHUB_REPOSITORY:-GitCorion/Medusa}"
TAG="${GITHUB_REF_NAME:-v2.3.12}"
APK_URL="${APK_URL:-https://github.com/${REPO}/releases/download/${TAG}/Medusa.apk}"

if [ "${SKIP_BUILD:-0}" != "1" ]; then
  echo "▶ Compilando :app:assembleRelease…"
  ./gradlew :app:assembleRelease
fi

if [ ! -f "$APK_SRC" ]; then
  echo "✗ No se encontró el APK en: $APK_SRC" >&2
  echo "  Compílalo con: ./gradlew :app:assembleRelease" >&2
  exit 1
fi

find_aapt() {
  if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/build-tools" ]; then
    ls "$ANDROID_HOME"/build-tools/*/aapt 2>/dev/null | sort -V | tail -1
  elif [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "$ANDROID_SDK_ROOT/build-tools" ]; then
    ls "$ANDROID_SDK_ROOT"/build-tools/*/aapt 2>/dev/null | sort -V | tail -1
  elif [ -d "$HOME/Library/Android/sdk/build-tools" ]; then
    ls "$HOME/Library/Android/sdk/build-tools"/*/aapt 2>/dev/null | sort -V | tail -1
  fi
}

AAPT_BIN="$(find_aapt)"
if [ -n "$AAPT_BIN" ] && [ -x "$AAPT_BIN" ]; then
  BADGING="$("$AAPT_BIN" dump badging "$APK_SRC" 2>/dev/null | head -1)"
  VERSION_CODE="$(printf '%s' "$BADGING" | sed -nE "s/.*versionCode='([0-9]+)'.*/\1/p")"
  VERSION_NAME="$(printf '%s' "$BADGING" | sed -nE "s/.*versionName='([^']+)'.*/\1/p")"
else
  VERSION_CODE="$(grep -oE 'versionCode[[:space:]]*=[[:space:]]*[0-9_]+' app/build.gradle.kts | head -1 | sed -E 's/[^0-9]//g')"
  VERSION_NAME="$(grep -oE 'versionName[[:space:]]*=[[:space:]]*"[^"]+"' app/build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
fi

if [ -z "$VERSION_CODE" ] || [ -z "$VERSION_NAME" ]; then
  echo "✗ No pude leer versionCode/versionName del APK ($APK_SRC)" >&2
  exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
  APK_SHA="$(sha256sum "$APK_SRC" | awk '{print $1}')"
else
  APK_SHA="$(shasum -a 256 "$APK_SRC" | awk '{print $1}')"
fi

echo "▶ Medusa $VERSION_NAME (versionCode $VERSION_CODE) · SHA-256 $APK_SHA"

VERSION_JSON=docs/api/version.json \
VERSION_CODE="$VERSION_CODE" \
VERSION_NAME="$VERSION_NAME" \
APK_URL="$APK_URL" \
APK_SHA="$APK_SHA" \
OTA_RELEASE_NOTES="${OTA_RELEASE_NOTES:-}" \
python3 scripts/update_version_json.py

echo "✓ Manifiesto listo en docs/api/version.json"
echo "  Descarga: $APK_URL"
