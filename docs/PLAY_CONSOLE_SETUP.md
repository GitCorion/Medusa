# Setup de Google Play (una vez) + flujo por tags

> Objetivo: que al empujar un tag `vX.Y.Z` el CI publique la beta por OTA **y** suba el AAB
> a **Play Internal testing** automáticamente.
>
> La beta vive en `tv.medusa.app.beta` (package propio) y Play en `tv.medusa.app`. Conviven sin
> conflicto. La beta es solo para tu tele y pruebas; Play es la versión pública.

---

## 1. Crear la app en Play Console

1. Play Console → **Crear app**.
   - Nombre: **Medusa**.
   - Idioma predeterminado: español.
   - Tipo: **App**, gratis.
2. Package: **`tv.medusa.app`** (no se puede cambiar después).
3. Ficha: marcar **Android TV** (la app declara `leanback required=true`, Play la clasifica como TV).
4. Rellenar lo mínimo para poder subir: política de privacidad
   (`https://medusa-fd388.web.app/privacy.html`), Data Safety, content rating y ficha de TV.

> Con cuenta **personal** creada después del 13/11/2023 hay que hacer después un
> **closed testing con 12 testers / 14 días** antes de pedir producción.

---

## 2. Service account para subir el AAB por CI

### 2.1 Google Cloud

1. [Google Cloud Console](https://console.cloud.google.com/) → selecciona/crea un proyecto
   (puede ser el mismo de Firebase: `medusa-fd388`).
2. **APIs y servicios → Biblioteca** → busca y habilita
   **Google Play Android Developer API**.
3. **IAM y administración → Cuentas de servicio → Crear cuenta de servicio**.
   - Nombre: `github-play-publisher`.
   - Crear y continuar → sin roles → Listo.
4. Entra en la cuenta de servicio → **Claves → Añadir clave → Crear clave → JSON**.
   Descarga el archivo (es el que va a GitHub).

### 2.2 Play Console

5. **Usuarios y permisos → Invitar nuevos usuarios**.
6. Email: el de la cuenta de servicio (`...@....iam.gserviceaccount.com`).
7. Permisos: **Release to testing tracks** (y, si quieres producción desde CI,
   *Release to production*). Guardar.
   - Para la primera subida, Play puede pedir que subas el primer AAB a mano desde la consola
     antes de aceptar subidas automáticas.

### 2.3 GitHub

8. Repo → **Settings → Secrets and variables → Actions → New repository secret**:
   - `PLAY_SERVICE_ACCOUNT_JSON` → pega el **contenido completo** del JSON descargado.

Si este secret **no** existe, el workflow no falla: simplemente se salta la subida a Play y
sigue publicando la OTA beta.

---

## 3. Secretos que usa el workflow

| Secret | Para qué |
|---|---|
| `KEYSTORE_B64` | Keystore de release en base64 (firma del AAB y del APK beta). |
| `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | Credenciales del keystore. |
| `FIREBASE_TOKEN` | Desplegar el OTA beta en Firebase Hosting. |
| `PLAY_SERVICE_ACCOUNT_JSON` | Subir el AAB a Play Internal (opcional). |
| `TMDB_API_KEY`, `TRAKT_CLIENT_ID`, `TRAKT_CLIENT_SECRET` | Opcionales, se inyectan en BuildConfig. |

---

## 4. Flujo de release

```bash
# 1) Sube versión en app/build.gradle.kts:
#    defaultConfig.versionCode   → base Play (93, 94…)
#    defaultConfig.versionName   → 2.2.46
#    beta.versionCode            → 1_000_000 + base
# 2) Commit y tag
git tag v2.2.46
git push origin v2.2.46
```

El CI, en un tag `v*`:

1. Ejecuta los tests unitarios.
2. Compila `assembleBetaRelease` (APK) y `bundlePlayRelease` (AAB).
3. Publica la **OTA beta** en Firebase.
4. Sube el **AAB a Play Internal testing** (si hay secret).

Luego, en Play Console: **Internal → Closed (12 testers/14 días) → Production** con
staged rollout. Es promocionar el mismo AAB, no recompilar.

---

## 5. Migración de la beta antigua (una sola vez)

El canal beta cambió de package (`tv.medusa.app` → `tv.medusa.app.beta`). La tele con la beta
antigua **no se actualiza sola** a la nueva:

1. Instala la nueva beta una vez: `https://medusa-fd388.web.app/Medusa.apk`
   (o desde Ajustes → Buscar actualizaciones en la beta antigua, que instalará la `.beta` aparte).
2. A partir de ahí, la `.beta` ya se actualiza sola por OTA.
3. Desinstala la beta antigua (`tv.medusa.app`) cuando quieras; o déjala y luego instala la de
   Play encima (mismo package) si algún día quieres probarla en la tele.
