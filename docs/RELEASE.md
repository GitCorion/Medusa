# Flujo de release — Medusa

> Documento **del repo**. No se publica: `firebase.json` ignora `**/*.md` en Hosting.
> Manual más amplio (local, en `.gitignore`): `MEMORIA_INSTRUCCIONES.md` (§3 distribución,
> §4 publicar, §7 chuleta de comandos, §8 firma y claves).

---

## 1. Canales

| Canal | Artefacto | Firma | Actualizador interno | Para qué |
|---|---|---|---|---|
| **beta** | `app/build/outputs/apk/beta/release/app-beta-release.apk` → `docs/Medusa.apk` | tu keystore de release | **ON** (`ENABLE_IN_APP_UPDATER=true`) | **Tele de casa y pruebas de beta por OTA** |
| **play** | `app/build/outputs/bundle/playRelease/app-play-release.aab` | tu keystore al compilar; **Google la re-firma al subirlo a Play** | OFF | Publicar en Play Store |

- Los dos flavors usan el **mismo** `applicationId` (`tv.medusa.app`) → **no puedes tener los dos
  canales en la misma tele**: la firma de Play es distinta y la instalación falla. Para pasar de uno
  a otro hay que desinstalar (se pierden sesión y ajustes).
- OTA pública (Firebase Hosting): APK en `https://medusa-fd388.web.app/Medusa.apk`
  y manifiesto en `https://medusa-fd388.web.app/api/version.json`.

## 2. Fuente de verdad de la versión

- `app/build.gradle.kts`:
  - `defaultConfig`: versión base de **Play** (`versionCode` bajo, `versionName` sin sufijo).
  - flavor `beta`: `versionCode = 1_000_000 + base` y `versionNameSuffix = "-beta"`.
- `docs/api/version.json` es el manifiesto que leen las apps. Lo escribe
  `scripts/update_version_json.py` con el build recién hecho. `publish-ota.sh` lee el
  `versionCode`/`versionName` **del APK beta real** (no del `defaultConfig`), para que el
  offset y el sufijo `-beta` lleguen bien a la tele.
  - Sobrescribe `versionCode`, `versionName`, `apkUrl`, `apkSha256` (y `releaseNotes` solo si le
    pasas `OTA_RELEASE_NOTES`).
  - **Preserva** los campos de control: `minSupportedVersionCode`, `forceUpdate`, `killSwitch`,
    `signatures`.
- La app beta se actualiza si el `versionCode` del manifiesto es **mayor** que el instalado.
- La build de **Play ignora** `versionCode`/`apkUrl`/`forceUpdate`: solo usa `killSwitch` y
  `minSupportedVersionCode`. Nunca descarga APKs.

## 3. Release de la beta (lo habitual)

Requisitos: `firebase` CLI logueado (`firebase login`) y `keystore.properties` presente (§8).

```bash
# 1) Sube la versión base en defaultConfig (Play) y el offset de beta (versionCode = 1_000_000 + base)
#    versionName del defaultConfig; el sufijo "-beta" lo añade el flavor.
# 2) Compila y publica (calcula SHA-256, escribe version.json y despliega)
OTA_RELEASE_NOTES="Qué cambia en esta versión" ./scripts/publish-ota.sh
```

Si ya compilaste el APK (`./gradlew :app:assembleBetaRelease`), reutilízalo con `SKIP_BUILD=1`:

```bash
SKIP_BUILD=1 OTA_RELEASE_NOTES="…" ./scripts/publish-ota.sh
```

### Verificación obligatoria (en vivo)

```bash
curl -s https://medusa-fd388.web.app/api/version.json      # versión y notas nuevas
shasum -a 256 docs/Medusa.apk                              # == apkSha256 del manifiesto

curl -s -o /tmp/medusa.apk https://medusa-fd388.web.app/Medusa.apk
shasum -a 256 /tmp/medusa.apk                              # == mismo SHA (lo que se sirve)

"$ANDROID_HOME/build-tools/<versión>/apksigner" verify --print-certs /tmp/medusa.apk
#   → CN=Alejandro Herra Bueno, OU=Medusa, …  (misma clave que las versiones anteriores)
```

### AAB de Play en la misma versión

Para no divergir de lo publicado en el OTA:

```bash
./gradlew :app:bundlePlayRelease   # → app/build/outputs/bundle/playRelease/app-play-release.aab
```

El AAB lleva el flavor `play` (sin actualizador interno). El APK de `outputs/apk/play/` solo se
genera con `:app:assemblePlayRelease` y **no se usa** para el canal OTA.

## 4. Release automático por CI (opcional)

`.github/workflows/release.yml`:

- `push` de un tag `vX.Y.Z` → compila `assembleBetaRelease` + `bundlePlayRelease`, guarda los
  artefactos en el workflow y **publica el OTA** (`SKIP_BUILD=1 ./scripts/publish-ota.sh`) usando
  `FIREBASE_TOKEN`.
- O a mano: GitHub → Actions → *Build & Publish Medusa (Firebase OTA)* → **Run workflow**
  (con `deploy` marcado, opcionalmente `release_notes`).
- Secretos necesarios del repo: `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`,
  `FIREBASE_TOKEN` (+ `TMDB_API_KEY`, `TRAKT_CLIENT_ID`, `TRAKT_CLIENT_SECRET` opcionales).
- Ojo: el CI **recompila**, así que el SHA-256 no coincide con tu build local. Si publicas la misma
  versión dos veces, el manifiesto se queda con el del último deploy (las notas se conservan si no
  pasas `OTA_RELEASE_NOTES`).

## 5. Actualizar en la tele

Ajustes → **Buscar actualizaciones** (canal beta), o la comprobación automática al abrir la app:
descarga el APK del manifiesto, verifica SHA-256 y paquete, e instala encima. Al ser la misma firma,
no se pierde sesión ni ajustes.

## 6. Kill-switch y versión mínima

- `killSwitch: true` → la app queda bloqueada con un diálogo no descartable hasta actualizar.
- `minSupportedVersionCode` → bloquea cualquier versión por debajo de ese `versionCode`.
- Se editan a mano en `docs/api/version.json` y se despliegan solo con:

```bash
firebase deploy --only hosting --project medusa-fd388
```

## 7. Firma y claves (⚠️ lo más crítico)

- `keystore.properties` (**gitignored**): `KEYSTORE_PATH`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`,
  `KEY_PASSWORD` (+ `TMDB_API_KEY`, `TRAKT_CLIENT_ID`, `TRAKT_CLIENT_SECRET`). `envOr()` en
  `app/build.gradle.kts` lee primero la variable de entorno y, si no, ese fichero.
- `app/keystore.jks` (**gitignored**, regla `*.jks`). **Sin él no se puede publicar ni actualizar**
  las teles ya instaladas: guarda copia y contraseñas fuera del repo.
- Certificado en uso: `CN=Alejandro Herra Bueno, OU=Medusa, O=Medusa, L=Córdoba, ST=Córdoba, C=ES`,
  SHA-256 `b59b0af2620583b3215da6c3f443067a641618d1f946b7dd997f47dab5e9ca09`. Si algo se publicara con
  otra huella, **no actualizaría** en las teles.
- Comprobación rápida de que nada sensible está versionado:

```bash
git check-ignore keystore.properties app/keystore.jks docs/Medusa.apk   # los tres deben salir
```

## 8. Chuleta

| Qué | Dónde / comando |
|---|---|
| APK beta para la tele | `app/build/outputs/apk/beta/release/app-beta-release.apk` |
| APK que sirve el OTA | `docs/Medusa.apk` (gitignored, pero **sí** se despliega) |
| AAB para Play | `app/build/outputs/bundle/playRelease/app-play-release.aab` |
| Publicar beta | `OTA_RELEASE_NOTES="…" ./scripts/publish-ota.sh` |
| Manifiesto | `docs/api/version.json` |
| Tests | `./gradlew :app:testBetaDebugUnitTest` |
| Desplegar solo hosting | `firebase deploy --only hosting --project medusa-fd388` |

## 9. Errores típicos

- **Publicar sin subir el `versionCode`** → nadie ve la actualización.
- `docs/Medusa.apk` y el `apkSha256` del manifiesto **desincronizados** (editar el manifiesto a mano
  o desplegar sin el APK).
- Instalar el APK del OTA **encima de uno de Play** → falla por firma (hay que desinstalar antes).
- Poner el flavor `play` en la tele de casa → se queda sin actualizaciones OTA.
- **Perder el keystore o sus contraseñas** → fin del canal OTA para las teles ya instaladas.
