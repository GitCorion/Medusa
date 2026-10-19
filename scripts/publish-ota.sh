#!/usr/bin/env bash
#
# Publica la BETA por OTA en Firebase Hosting (firebase.json → public: docs).
#
# El repositorio de GitHub es privado, así que sus Releases no sirven para actualizar a los
# usuarios: este script deja el APK y el manifiesto servidos públicamente por Firebase.
#
# Uso:
#   ./scripts/publish-ota.sh                         # compila betaRelease y publica
#   SKIP_BUILD=1 ./scripts/publish-ota.sh            # usa el APK ya compilado
#   OTA_RELEASE_NOTES="Notas…" ./scripts/publish-ota.sh
#   APK_SPARK=1 ./scripts/publish-ota.sh             # solo si volvieras al plan gratuito Spark
#
# Requiere: firebase CLI (login previo con `firebase login`, o FIREBASE_TOKEN en CI).
#
# El APK se copia dentro de docs/ (ignorado por git) y se despliega junto a docs/api/version.json.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

APK_SRC="app/build/outputs/apk/beta/release/app-beta-release.apk"
VERSION_JSON="docs/api/version.json"
OTA_BASE_URL="https://medusa-fd388.web.app"
FIREBASE_PROJECT="medusa-fd388"

# --- Nombre público del binario ---------------------------------------------
# Plan Blaze (pago por uso, ACTIVO): Hosting sirve el .apk normal → canal limpio y estándar.
# Plan Spark (gratis): PROHÍBE ficheros ejecutables (*.apk). Si algún día volvieras a Spark,
#   publica con APK_SPARK=1: el binario se sirve sin extensión en una ruta con "/download",
#   que la app reconoce como descarga directa (ver AppUpdateManager.downloadAndInstall).
if [ "${APK_SPARK:-0}" = "1" ]; then
  APK_DST="docs/download/medusa"
  APK_URL="$OTA_BASE_URL/download/medusa"
else
  APK_DST="docs/Medusa.apk"
  APK_URL="$OTA_BASE_URL/Medusa.apk"
fi

if [ "${SKIP_BUILD:-0}" != "1" ]; then
  echo "▶ Compilando :app:assembleBetaRelease…"
  ./gradlew :app:assembleBetaRelease
fi

if [ ! -f "$APK_SRC" ]; then
  echo "✗ No se encontró el APK en: $APK_SRC" >&2
  echo "  Compílalo con: ./gradlew :app:assembleBetaRelease" >&2
  exit 1
fi

# La versión se lee del APK beta real, no de `defaultConfig`: el flavor beta usa un
# versionCode con offset (1_000_000+) y un versionName con sufijo "-beta". Si leyéramos
# el primer `versionCode` del Gradle, publicaríamos el de Play (91) y la tele beta no
# detectaría la actualización.
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
  # Fallback sin SDK: leer el bloque beta y reconstruir el versionName.
  VERSION_CODE="$(awk '/create\("beta"\)/{b=1} b && /versionCode[[:space:]]*=/{gsub(/[^0-9]/,"",$0); print; exit}' app/build.gradle.kts)"
  BASE_VERSION_NAME="$(grep -oE 'versionName[[:space:]]*=[[:space:]]*"[^"]+"' app/build.gradle.kts | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
  VERSION_NAME="${BASE_VERSION_NAME}-beta"
fi

if [ -z "$VERSION_CODE" ] || [ -z "$VERSION_NAME" ]; then
  echo "✗ No pude leer versionCode/versionName del APK beta ($APK_SRC)" >&2
  exit 1
fi

echo "▶ Medusa $VERSION_NAME (versionCode $VERSION_CODE)"

mkdir -p "$(dirname "$APK_DST")" docs/api
cp "$APK_SRC" "$APK_DST"

if command -v sha256sum >/dev/null 2>&1; then
  APK_SHA="$(sha256sum "$APK_DST" | awk '{print $1}')"
else
  APK_SHA="$(shasum -a 256 "$APK_DST" | awk '{print $1}')"
fi
echo "▶ SHA-256: $APK_SHA"

VERSION_JSON="$VERSION_JSON" \
VERSION_CODE="$VERSION_CODE" \
VERSION_NAME="$VERSION_NAME" \
APK_URL="$APK_URL" \
APK_SHA="$APK_SHA" \
OTA_RELEASE_NOTES="${OTA_RELEASE_NOTES:-}" \
python3 scripts/update_version_json.py

echo "▶ Desplegando en Firebase Hosting (proyecto $FIREBASE_PROJECT)…"
FIREBASE_ARGS=(--only hosting --project "$FIREBASE_PROJECT")
if [ -n "${FIREBASE_TOKEN:-}" ]; then
  FIREBASE_ARGS+=(--token "$FIREBASE_TOKEN")
fi
firebase deploy "${FIREBASE_ARGS[@]}"

echo "✓ OTA publicado:"
echo "    manifiesto : $OTA_BASE_URL/api/version.json"
echo "    APK        : $APK_URL"
