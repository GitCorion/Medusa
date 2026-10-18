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

### Modelo adoptado ahora (fase 0)

Mantener **el mismo `applicationId`** para no romper la beta instalada en tu tele, pero separar
lo que importa:

| | **beta (Firebase)** | **play (Google Play)** |
|---|---|---|
| `applicationId` | `tv.medusa.app` | `tv.medusa.app` |
| Artefacto | `app-beta-release.apk` | `app-play-release.aab` |
| Firma | Tu keystore | Upload key + **Play App Signing** |
| Updater propio | **ON** | **OFF** |
| `versionName` | `2.2.43-beta` | `2.2.43` |
| `versionCode` | `1_000_091`, `1_000_092`… | `91`, `92`… |
| Manifiesto | `docs/api/version.json` completo | Solo `killSwitch` / `minSupportedVersionCode` |

**Consecuencia importante:** al compartir package, la beta y la build de Play **no pueden
convivir en el mismo dispositivo**. Tu tele se queda siempre en beta; la build de Play va a
móviles/TV de usuarios. Si algún día quieres probar la de Play en la tele, hay que desinstalar
la beta (se pierden ajustes) o cambiar de dispositivo.

### Estado objetivo recomendado (fase 4, opcional)

Separar packages para que puedan convivir:

- Play: `tv.medusa.app`
- Beta: `tv.medusa.app.beta` (`applicationIdSuffix = ".beta"`)

Requiere dar de alta `tv.medusa.app.beta` en Firebase (Analytics/Crashlytics) y **reinstalar
una vez** la beta en la tele. Por eso no se hace en la fase 0: primero lanzamos sin tocar tu
tele y después, si quieres, migramos con calma.

> Regla: el package que se queda para siempre con la identidad pública es `tv.medusa.app`
> (el de Play). La beta es la que debe moverse si algún día se separan.

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

### Fase 4 — Separación opcional de packages

- [ ] `applicationIdSuffix = ".beta"`.
- [ ] Alta de `tv.medusa.app.beta` en Firebase + `app/src/beta/google-services.json`.
- [ ] Reinstalación única de la beta en la tele.

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


