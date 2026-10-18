# Plan de lanzamiento: Google Play + canal beta Firebase

> Estado: **en ejecución** · Última actualización: 2026-10-18
> Objetivo: publicar Medusa en Google Play **sin perder** el canal beta de Firebase que usas
> en la tele de casa, y sin que la build de Play se autoactualice fuera de Play.

---

## 1. Principio

Una sola base de código, **dos canales**:

| Canal | Para quién | Distribución | Actualización |
|---|---|---|---|
| **beta** | Tu tele de casa y pruebas rápidas | Firebase Hosting (APK) | OTA propio, inmediato |
| **play** | Usuarios reales | Google Play (AAB) | Google Play |

La build de **Play nunca descarga un APK**. La build **beta sí** puede, porque no se distribuye
por Play (es sideloaded).

---

## 2. Modelo de canales

### Situación de partida (la que tenemos)

- `beta` y `play` comparten `applicationId` (`tv.medusa.app`) y `versionCode`.
- Mismo keystore para firmar.
- Un único `docs/api/version.json` y un único `docs/Medusa.apk`.
- El diálogo de actualización obligatoria descarga el APK de Firebase **también en la build de
  Play** → violación de política de Play.

### Modelo adoptado (dos packages)

Son **dos apps distintas del mismo código**, separadas por *flavor*:

| | **beta (Firebase)** | **play (Google Play)** |
|---|---|---|
| `applicationId` | `tv.medusa.app.beta` | `tv.medusa.app` |
| Artefacto | `app-beta-release.apk` | `app-play-release.aab` |
| Firma | Tu keystore | Upload key + **Play App Signing** |
| Updater propio | **ON** | **OFF** |
| `versionName` | `2.2.46-beta` | `2.2.46` |
| `versionCode` | `1_000_093`… | `93`… |
| Manifiesto | `docs/api/version.json` completo | Solo `killSwitch` / `minSupportedVersionCode` |

- **Conviven en el mismo dispositivo** (datos y actualizaciones independientes).
- Firebase tiene dados de alta los dos packages; `app/google-services.json` incluye ambos.
- Único coste: **una reinstalación** de la beta en la tele la primera vez (cambia de package).

> Regla: la identidad pública para siempre es `tv.medusa.app` (Play). La beta vive aparte en
> `tv.medusa.app.beta` y puede evolucionar sin afectar a los usuarios de Play.

---

## 3. Versionado

- **Play**: `91`, `92`, `93`… (`versionCode` siempre creciente; Play rechaza repetir o bajar).
- **Beta**: `1_000_091`, `1_000_092`… (offset de 1.000.000 sobre el de Play).
- **`versionName`**: Play `2.2.43`; beta `2.2.43-beta`.
- El offset garantiza que:
  - la tele beta siempre recibe la OTA (su código es mayor que cualquier Play);
  - Play y beta se distinguen en soporte y en Crashlytics;
  - nunca se instala por accidente una build de Play encima de la beta en la tele.

`docs/api/version.json` publica el `versionCode`/`versionName` **de la beta**. La build de Play
lo ignora para actualizaciones (ver §5).

---

## 4. Firma

- **Play**: subes el AAB firmado con tu *upload key*; Google lo re-firma con la *app signing key*.
- **Beta**: APK firmado con tu keystore actual (el mismo de siempre).
- No se versiona ningún keystore ni `keystore.properties`.
- Si algún día quieres que la build de Play pueda actualizar la beta **en el sitio** (mismo
  package), hay que inscribir Play App Signing usando tu keystore actual como app signing key.
  Si no, la build de Play no se puede instalar encima de la beta (firma distinta). En este plan
  asumimos que la tele se queda en beta, así que no es necesario.
- **Antes del primer upload**: decidir esto. Es una puerta de un solo sentido.

---

## 5. Flujo de actualización (el botón de Firebase)

### Beta (Firebase)

1. Consulta `version.json`.
2. Si `versionCode` remoto > instalado y hay `apkUrl` → muestra banner.
3. Descarga el APK, verifica SHA-256 y paquete, e instala con `REQUEST_INSTALL_PACKAGES`.
4. `killSwitch` y `minSupportedVersionCode` bloquean también.

### Play

1. **No** se ofrece descargar el APK de Firebase.
2. La build de Play **ignora** `versionCode`, `versionName` y `apkUrl` del manifiesto.
3. Solo usa `killSwitch` y `minSupportedVersionCode` (bloqueo global).
4. Si hay que actualizar, el botón abre la ficha de Play:
   `market://details?id=tv.medusa.app` (fallback `https://play.google.com/store/apps/details?id=...`).
5. Mejora futura: **Play In-App Updates** (Play Core) en lugar de abrir la ficha.

La sección de actualizaciones de Ajustes sigue oculta en Play (`ENABLE_IN_APP_UPDATER = false`).

---

## 6. Manifiesto OTA

- `docs/api/version.json` sigue siendo el manifiesto de la **beta**.
- `versionCode`/`versionName`/`apkUrl`/`apkSha256` describen la beta.
- `killSwitch` y `minSupportedVersionCode` son compartidos y se aplican a ambos canales.
- No se publica el APK de Play en Firebase.

---

## 7. Testing tracks de Play

| Track | Uso | Revisión |
|---|---|---|
| **Internal testing** | Tú + 2-3 personas, build `play` real | Sin revisión |
| **Closed testing** | 12 testers / 14 días si la cuenta es personal | Revisión inicial |
| **Production** | Usuarios reales | Revisión |

- Las cuentas **personales** creadas después del 13/11/2023 necesitan **12 testers opt-in
  durante 14 días consecutivos** antes de pedir producción. Las cuentas de organización
  (con D-U-N-S) están exentas.
- Firebase **no** cuenta para ese requisito: hay que subir el AAB a Play.
- Recomendado: mantener Firebase para la tele y usar Internal/Closed testing para validar la
  build firmada por Play en paralelo.

---

## 8. Fases y estado

### Fase 0 — Separar canales sin romper la tele · **HECHO (2026-10-18)**

- [x] Plan escrito.
- [x] `versionCode` por flavor (`play` 91, `beta` 1.000.091).
- [x] `versionNameSuffix = "-beta"` en beta (`2.2.44-beta`).
- [x] La build de Play ignora actualizaciones de `version.json` (solo kill-switch/versión mínima).
- [x] El botón de actualización de Play abre la ficha de Play (nunca el APK de Firebase).
- [x] `publish-ota.sh` lee la versión del APK beta real (no del `defaultConfig`).
- [x] Verificar que la beta sigue actualizando la tele por OTA (publicada 2.2.44-beta, `versionCode` 1.000.091).

### Fase 1 — Bloqueadores de Play

- [x] **Billing 7.1.1 → 9.1.0** y adaptación de `queryProductDetailsAsync`.
- [x] **API level**: **TV-only** decidido el 2026-10-18. `targetSdk 35` cumple el mínimo de
      Android TV (API 34+); no se sube a 36 para evitar regresiones de edge-to-edge/orientación
      de móvil. Manifest: `leanback required=true`, `touchscreen required=false`.
- [ ] **Data Safety**: declarar Crashlytics/Analytics; `AD_ID` eliminado del manifest.
- [x] Quitar el auto-update de Firebase del flavor Play.
- [ ] Revisar permisos: `WRITE_EPG_DATA` solo si se usa para Watch Next.

### Fase 2 — Seguridad

- [ ] Sacar `TRAKT_CLIENT_SECRET` y la `TMDB_API_KEY` del binario (proxy en Firebase Functions
      o equivalente). Mientras no se haga, asumir que son extraíbles.
- [x] Quitar `Log.i` con prefijo de token (`PlayerViewModel`).
- [ ] `CryptoUtils`: no degradar a texto plano si falla el Keystore.
- [ ] Verificación server-side de compras (Play Developer API) y trial no reseteable borrando datos.

### Fase 3 — Calidad y CI

- [x] CI: ejecutar tests unitarios antes de compilar.
- [ ] CI: añadir `lintPlayRelease` (requiere limpiar avisos existentes).
- [ ] Ejecutar `AppDatabaseMigrationTest` en emulador.
- [ ] Quitar dependencia `firebase-firestore` (no se usa).
- [ ] Actualizar dependencias antiguas (core-ktx, media3, Firebase BOM…).

### Fase 3.5 — Red y latencia (bloqueos ISP / LaLiga / Cloudflare)

Contexto: en España, las órdenes judiciales de LaLiga bloquean rangos de IP compartidos y
Cloudflare cae como daño colateral. Síntoma: todo va lento o el vídeo se queda cargando.

- [x] **El vídeo usa el mismo OkHttp que la API** (`OkHttpDataSource`): hereda la preferencia
      IPv6, el pool de conexiones y los timeouts. Antes ExoPlayer resolvía por DNS del sistema
      (IPv4 primero) y caía por la ruta bloqueada aunque la API fuera por IPv6.
- [x] Buffer más generoso (min 30 s / max 90 s, 10 s tras rebuffer) para redes inestables.
- [x] Detección de la página de bloqueo de LaLiga también en el stream (`Videos/`, `Audio/`).
- [x] No se fuerza `Accept: application/json` en vídeo/audio.
- [ ] **Servidor (lo importante)**: Jellyfin detrás de Cloudflare **proxied (nube naranja)** es
      mala idea para vídeo (ToS + latencia + bloqueos). Opciones:
      - Poner el dominio en **DNS only (nube gris)** para que el cliente vaya directo al origen.
      - Habilitar **IPv6 extremo a extremo** (AAAA + IPv6 en el servidor + ISP): suele esquivar
        los bloqueos de LaLiga sobre IPv4.
      - Usar un host con **IP propia no compartida** (VPS) fuera de rangos de Cloudflare.
      - Como último recurso, **VPN (WireGuard)** en la tele para saltar el bloqueo del operador.
- [ ] Decidir si añadir en Ajustes un **servidor alternativo / forzar IPv6**.

### Fase 4 — Separación de packages · **HECHO (2026-10-18)**

- [x] `applicationIdSuffix = ".beta"` → beta `tv.medusa.app.beta`, Play `tv.medusa.app`.
- [x] Alta de `tv.medusa.app.beta` en Firebase (proyecto `medusa-fd388`) y `google-services.json`
      con ambos clientes.
- [x] CI: al tag `v*`, sube el AAB a **Play Internal testing** automáticamente
      (`PLAY_SERVICE_ACCOUNT_JSON`; si falta, se omite y sigue la OTA).
- [ ] Reinstalación única de la beta en la tele (instalar `https://medusa-fd388.web.app/Medusa.apk`).
- [ ] Configurar el service account de Play y el secret (ver `docs/PLAY_CONSOLE_SETUP.md`).

### Fase 5 — Publicación

- [ ] Store listing: icono 512, feature graphic, capturas TV, descripción, política de privacidad.
- [ ] Content rating, Data Safety, declaración de anuncios (no hay).
- [ ] Internal testing → closed testing (12/14) → producción.
- [ ] Play App Signing y upload key custodiados.

---

## 9. Comandos

```bash
# Beta para la tele (APK + OTA Firebase)
OTA_RELEASE_NOTES="…" ./scripts/publish-ota.sh

# AAB para Play (no publica OTA)
./gradlew :app:bundlePlayRelease

# Tests antes de publicar
./gradlew :app:testPlayDebugUnitTest

# Verificar la beta publicada
curl -s https://medusa-fd388.web.app/api/version.json
shasum -a 256 docs/Medusa.apk
```

- Tag de release: `v2.2.43` (compila AAB + APK beta).
- Tag de beta intermedia: `v2.2.43-beta.1` (solo OTA, sin tocar Play).

---

## 10. Checklist de Play Console

- [ ] Cuenta de desarrollador verificada (personal vs organización).
- [ ] App creada con package `tv.medusa.app`.
- [ ] Play App Signing configurado.
- [ ] AAB subido a Internal testing.
- [ ] Store listing (es/en), gráficos y capturas de TV.
- [ ] Política de privacidad pública (`docs/privacy.html`).
- [ ] Data Safety (analytics, crash logs, compras, IDs de dispositivo).
- [ ] Content rating.
- [ ] Declaración de TV (si aplica) y banner.
- [ ] Closed testing 12 testers / 14 días (si cuenta personal).
- [ ] Producción.

---

## 11. Decisiones pendientes

1. ~~**¿TV-only o móvil+TV?**~~ **Decidido: TV-only** (2026-10-18).
2. **¿Se migra la beta a `.beta`?** Recomendado a medio plazo; requiere una reinstalación.
3. **¿Proxy de TMDB/Trakt?** Necesario para no publicar secretos.
4. **¿Verificación server-side de compras?** Recomendado antes de escalar.

---

## 12. Registro de ejecución

### 2026-10-18 — Fase 0 completada

- Flavor `play`: `versionCode 91`, `versionName 2.2.44`, sin updater.
- Flavor `beta`: `versionCode 1_000_091`, `versionName 2.2.44-beta`, updater ON.
- `AppUpdateManager`: la build de Play solo atiende `killSwitch`/`minSupportedVersionCode`;
  ignora `versionCode`, `versionName`, `apkUrl` y `forceUpdate`.
- El diálogo de actualización de Play abre `market://details?id=tv.medusa.app`.
  `downloadAndInstall` rechaza ejecutarse si `ENABLE_IN_APP_UPDATER = false`.
- `AD_ID` eliminado del manifest (Data Safety más simple).
- `Log.i` con prefijo de token eliminado.
- Billing 7.1.1 → 9.1.0 y adaptación de `queryProductDetailsAsync`.
- CI ejecuta `testPlayDebugUnitTest` antes de compilar.
- `publish-ota.sh` lee `versionCode`/`versionName` del APK beta real.
- Verificado: tests OK, `assembleBetaRelease` OK, `bundlePlayRelease` OK.
- OTA publicada y verificada: `2.2.44-beta` / `1000091`, SHA-256
  `96bdb70b1f2fffcf38b2f1f903139e30bb76fa9795481cd8229c8e0290be726c`, firma
  `b59b0af2620583b3215da6c3f443067a641618d1f946b7dd997f47dab5e9ca09`.

### 2026-10-18 — TV-only decidido

- Manifest: `android.software.leanback required=true` y `touchscreen required=false`.
  Play filtrará móviles/tablets; la app se publica en la categoría Android TV.
- `targetSdk 35` se mantiene: cumple el mínimo de Android TV (API 34+) y evita las
  regresiones de Android 16 en móvil.
- Recordatorio Play TV: banner, `LEANBACK_LAUNCHER`, capturas 1920×1080 y ficha de TV.

### 2026-10-18 — 2.2.45-beta: red/latencia

- Reproductor: `OkHttpDataSource` con el cliente OkHttp de la app (preferencia IPv6, pool,
  timeouts) en lugar de `DefaultHttpDataSource` (DNS del sistema, IPv4 primero).
- Buffer: min 30 s / max 90 s / 10 s tras rebuffer; back buffer 30 s.
- `NetworkClientFactory`: detección del bloqueo de LaLiga también en `Videos/`/`Audio/`; no se
  fuerza `Accept: application/json` en media.
- OTA publicada y verificada: `2.2.45-beta` / `1000092`, SHA-256
  `a524c7cd5ffd004b17bd3730019858c7122ad17ccad2427c9e8b1bae3c13752b`.



