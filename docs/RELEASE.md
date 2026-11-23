# Release y actualizaciones (GitHub Releases)

Medusa se distribuye **públicamente desde GitHub Releases**, en dos canales y con una sola app
(`tv.medusa.app`). No hay Firebase ni Google Play.

## Canales

| Canal | Tag | GitHub Release | Manifiesto | Quién lo recibe |
|---|---|---|---|---|
| Estable | `v2.4.0` | Latest | `docs/api/version.json` | Todos |
| Beta | `v2.4.0-beta.1` | Pre-release | `docs/api/version-beta.json` | Solo los APK beta |

- El canal va **grabado en el APK** (`BuildConfig.UPDATE_CHANNEL`). No hay ajuste en la app.
- Un APK estable solo lee `version.json`. Uno beta lee **ambos** y se queda con el `versionCode`
  más alto; `killSwitch`, `minSupportedVersionCode` y `signatures` mandan desde el estable.
- Publicar un estable copia su manifiesto a `version-beta.json`: los equipos en beta pasan solos a
  la stable y quedan de nuevo en el canal estable.
- Si el manifiesto beta falla, el cliente beta usa el estable. Solo se bloquea (fail-closed) si
  fallan los dos durante siete días.

## Versión

| Campo | Origen |
|---|---|
| `versionName` | El tag sin la `v`: `2.4.0` o `2.4.0-beta.1` |
| `versionCode` | Máximo publicado en `main` + 1 (lo calcula el workflow) |

El workflow rechaza un tag que no sea posterior a la versión estable publicada y serializa los dos
canales para que el contador nunca se repita.

## Publicar

```bash
git pull --rebase            # el bot commitea los manifiestos en main
git tag v2.4.0-beta.1
git push origin v2.4.0-beta.1
```

El workflow `.github/workflows/release.yml`:

1. resuelve versión y canal desde el tag,
2. ejecuta los tests unitarios,
3. compila `:app:assembleRelease` firmado,
4. crea el Release con `Medusa.apk` (pre-release si el tag lleva `-beta.N`),
5. actualiza el manifiesto del canal y, en estable, también el de beta.

También se puede lanzar a mano: **Actions → Build & Publish → Run workflow**, eligiendo el tag en
el selector de ref. Con `publish` desmarcado solo compila y sube el artefacto.

Las notas salen de la sección correspondiente de `CHANGELOG.md` (versión exacta, versión base o
`[Unreleased]`). Para una estable conviene renombrar antes `[Unreleased]` a `[2.4.0]`.

## Entrar y salir de beta

- **Entrar:** instala una vez el APK del pre-release beta. A partir de ahí las betas llegan por OTA.
- **Salir:** instala la estable cuando se publique (su `versionCode` es mayor). Android no permite
  instalar una versión con `versionCode` inferior encima de una beta.
- Beta y estable son la **misma app**: no conviven instaladas; no hay dos iconos.

## Migración desde `tv.medusa.app.beta`

`v2.4.0` es la primera versión con `tv.medusa.app`. Para Android es **otra app**:

- Los equipos con la versión antigua no reciben esto como una actualización: el instalador añade
  una app nueva. Hay que **desinstalar la antigua** después de instalar la nueva.
- Ajustes, servidor Jellyfin, clave TMDB e inicio de sesión **no se migran**.
- La app antigua seguirá mostrando el aviso de actualización hasta que se desinstale; es normal.
- Las notas de la primera release lo indican, porque la app antigua las muestra antes de instalar.

## Secretos del repositorio

| Secreto | Uso |
|---|---|
| `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | Firma del APK |
| `TMDB_API_KEY` | Generación manual de catálogos (`catalogs.yml`) |
| `TRAKT_CLIENT_ID`, `TRAKT_CLIENT_SECRET` | Opcional; integración con Trakt |

La clave de TMDB del usuario se introduce en **Ajustes → Clave API de TMDB**; no se comparte.

## Verificar desde la tele

```bash
# Manifiestos que ve la app
curl -s https://raw.githubusercontent.com/GitCorion/Medusa/main/docs/api/version.json
curl -s https://raw.githubusercontent.com/GitCorion/Medusa/main/docs/api/version-beta.json

# APK del último Release
curl -sL -o /tmp/medusa.apk \
  https://github.com/GitCorion/Medusa/releases/latest/download/Medusa.apk
sha256sum /tmp/medusa.apk   # debe coincidir con apkSha256 del manifiesto
```

## Notas

- `docs/PLAN_PLAY_STORE.md`, `docs/PLAY_CONSOLE_SETUP.md`, `docs/PLAY_STORE_METADATA.md` y
  `docs/PLAN_MEDUSA_PROFESIONAL.md` son **documentación histórica** del antiguo plan de Play
  Store / Firebase y ya no describen el proceso de publicación.
- GitHub Pages publica el estudio y la privacidad desde `main` / `/docs`.
- Los catálogos de descubrimiento (`docs/catalogs/`) están aparcados y se generan a mano; ver
  `docs/CATALOGS.md`.
- Una beta con problemas se corrige publicando `vX.Y.Z-beta.N+1`; Android no permite instalar un
  `versionCode` inferior encima. Si el problema es grave, adelanta la estable.
