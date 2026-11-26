# Release y actualizaciones (GitHub Releases)

Medusa se distribuye desde **GitHub Releases**, en dos canales y con una sola app
(`tv.medusa.app`).

## Canales

| Canal | Tag | GitHub Release | Manifiesto | Quién lo recibe |
|---|---|---|---|---|
| Estable | `v2.4.1` | Latest | `docs/api/version.json` | Todos |
| Beta | `v2.4.1-beta.N` | Pre-release | `docs/api/version-beta.json` | Quien elija beta |

- El canal **por defecto** es el grabado en el APK (`BuildConfig.UPDATE_CHANNEL`), pero el usuario
  puede cambiarlo en **Ajustes → Acerca de → Canal**. La elección se guarda en la app y manda
  sobre el valor del APK.
- El canal estable solo lee `version.json`. El canal beta lee **ambos** y se queda con el
  `versionCode` más alto; `killSwitch`, `minSupportedVersionCode` y `signatures` mandan desde el
  estable.
- Publicar un estable copia su manifiesto a `version-beta.json`: los equipos en beta pasan solos a
  la stable y quedan de nuevo en el canal estable.
- Si el manifiesto beta falla, el cliente beta usa el estable. Solo se bloquea (fail-closed) si
  fallan los dos durante siete días.

## Versión

| Campo | Origen |
|---|---|
| `versionName` | El tag sin la `v`: `2.4.1` o `2.4.1-beta.N` |
| `versionCode` | Máximo publicado en `main` + 1 (lo calcula el workflow) |

El workflow rechaza un tag que no sea posterior a la versión estable publicada y serializa los dos
canales para que el contador nunca se repita.

## Publicar

```bash
git pull --rebase            # el bot commitea los manifiestos en main
git tag v2.4.1-beta.1
git push origin v2.4.1-beta.1
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
`[Unreleased]`). Para una estable conviene renombrar antes `[Unreleased]` a `[2.4.1]` o a la
versión que corresponda.

## Entrar y salir de beta

- **Entrar:** elige **Beta** en Ajustes → Acerca de. También puedes instalar una vez el APK del
  pre-release; la preferencia guardada tiene prioridad a partir de ahí.
- **Salir:** elige **Estable** en el mismo ajuste. Si vienes de una beta con `versionCode` mayor,
  no se puede bajar a una estable anterior: el equipo vuelve a estable con la siguiente publicación
  estable.
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
| `TRAKT_CLIENT_ID` | Opcional; integración con Trakt en build-time. El secret se configura en Ajustes |

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

- GitHub Pages publica el estudio y la privacidad desde `main` / `/docs`.
- Los catálogos de descubrimiento (`docs/catalogs/`) están aparcados y se generan a mano; ver
  `docs/CATALOGS.md`.
- Una beta con problemas se corrige publicando `vX.Y.Z-beta.N+1`; Android no permite instalar un
  `versionCode` inferior encima. Si el problema es grave, adelanta la estable.
