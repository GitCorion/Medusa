#!/usr/bin/env bash
#
# Prepara una distribución pública por GitHub Releases: compila el APK firmado y actualiza
# el manifiesto OTA del canal con la versión, el SHA-256 y la URL del asset del Release.
#
# Canales:
#   OTA_CHANNEL=stable (def.) → docs/api/version.json + copia a docs/api/version-beta.json
#   OTA_CHANNEL=beta          → solo docs/api/version-beta.json (hereda flags del estable)
#
# Uso:
#   GITHUB_REF_NAME=v2.4.0 ./scripts/publish-ota.sh            # compila y genera el manifiesto
#   GITHUB_REF_NAME=v2.4.0 SKIP_BUILD=1 ./scripts/publish-ota.sh  # reutiliza el APK ya compilado
#
# La creación del Release y la subida del APK la hace .github/workflows/release.yml.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

APK_SRC="app/build/outputs/apk/release/app-release.apk"
REPO="${GITHUB_REPOSITORY:-GitCorion/Medusa}"
TAG="${GITHUB_REF_NAME:-}"
CHANNEL="${OTA_CHANNEL:-stable}"
APK_URL="${APK_URL:-https://github.com/${REPO}/releases/download/${TAG}/Medusa.apk}"

if [ -z "$TAG" ]; then
  echo "✗ Define GITHUB_REF_NAME con el tag a publicar (p. ej. v2.4.0 o v2.4.0-beta.1)" >&2
  exit 1
fi

case "$CHANNEL" in
  stable)
    MANIFEST="docs/api/version.json"
    ;;
  beta)
    MANIFEST="docs/api/version-beta.json"
    # La beta hereda kill-switch, mínimo soportado y firmas del manifiesto estable.
    cp docs/api/version.json "$MANIFEST"
    ;;
  *)
    echo "✗ OTA_CHANNEL no válido: $CHANNEL (usa stable o beta)" >&2
    exit 1
    ;;
esac

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

# Permite inyectar la versión ya resuelta por el workflow; si falta, se lee del APK con aapt.
VERSION_CODE="${VERSION_CODE:-}"
VERSION_NAME="${VERSION_NAME:-}"

AAPT_BIN="$(find_aapt || true)"
if { [ -z "$VERSION_CODE" ] || [ -z "$VERSION_NAME" ]; } && [ -n "$AAPT_BIN" ] && [ -x "$AAPT_BIN" ]; then
  BADGING="$("$AAPT_BIN" dump badging "$APK_SRC" 2>/dev/null | head -1)"
  VERSION_CODE="$(printf '%s' "$BADGING" | sed -nE "s/.*versionCode='([0-9]+)'.*/\1/p")"
  VERSION_NAME="$(printf '%s' "$BADGING" | sed -nE "s/.*versionName='([^']+)'.*/\1/p")"
fi

if [ -z "$VERSION_CODE" ] || [ -z "$VERSION_NAME" ]; then
  echo "✗ No pude determinar versionCode/versionName." >&2
  echo "  Define VERSION_CODE y VERSION_NAME o instala Android build-tools (aapt)." >&2
  exit 1
fi

if command -v sha256sum >/dev/null 2>&1; then
  APK_SHA="$(sha256sum "$APK_SRC" | awk '{print $1}')"
else
  APK_SHA="$(shasum -a 256 "$APK_SRC" | awk '{print $1}')"
fi

echo "▶ Medusa $VERSION_NAME (versionCode $VERSION_CODE, canal $CHANNEL) · SHA-256 $APK_SHA"

VERSION_JSON="$MANIFEST" \
VERSION_CODE="$VERSION_CODE" \
VERSION_NAME="$VERSION_NAME" \
APK_URL="$APK_URL" \
APK_SHA="$APK_SHA" \
OTA_RELEASE_NOTES="${OTA_RELEASE_NOTES:-Medusa $VERSION_NAME}" \
python3 scripts/update_version_json.py

# Un estable también cierra la beta: los equipos en beta reciben la stable en la siguiente
# comprobación y quedan de nuevo en el canal estable.
if [ "$CHANNEL" = "stable" ]; then
  cp "$MANIFEST" docs/api/version-beta.json
fi

echo "✓ Manifiesto listo en $MANIFEST"
echo "  Descarga: $APK_URL"
