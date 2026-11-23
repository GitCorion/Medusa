# Release y actualizaciones (GitHub Releases)

Medusa se distribuye **públicamente desde GitHub Releases**. No hay Firebase ni Google Play.

## Modelo

| Artefacto | Ubicación | Consumidor |
|---|---|---|
| APK firmado | Asset `Medusa.apk` del Release de GitHub | La app (`AppUpdateManager`) |
| Manifiesto OTA | `docs/api/version.json` en `main` (raw.githubusercontent.com) | La app, en cada arranque |
| Catálogos de descubrimiento | `docs/catalogs/<REGION>/*.json` en `main` | `CatalogRemoteDataSource` |
| Estudio y privacidad | GitHub Pages desde `/docs` (`index.html`, `privacy.html`) | Navegador; Ajustes → Acerca de |

El `applicationId` se mantiene en `tv.medusa.app.beta` para que los usuarios de la beta
reciban el Release como una actualización, no como una app nueva.

## Publicar

1. Sube un tag `v*`:
   ```bash
   git tag v2.3.13 && git push origin v2.3.13
   ```
2. El workflow `.github/workflows/release.yml`:
   - ejecuta tests unitarios,
   - compila `:app:assembleRelease` firmado,
   - crea el Release con el APK como asset `Medusa.apk`,
   - actualiza `docs/api/version.json` en `main` con `versionCode`, `versionName`, `apkUrl` y `apkSha256`.

También puedes dispararlo a mano con `workflow_dispatch`.

## Secretos del repositorio

| Secreto | Uso |
|---|---|
| `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | Firma del APK |
| `TMDB_API_KEY` | Generación nocturna de catálogos (`catalogs.yml`) |
| `TRAKT_CLIENT_ID`, `TRAKT_CLIENT_SECRET` | Opcional; integración con Trakt |

La clave de TMDB del usuario se introduce en **Ajustes → Clave API de TMDB**; no se comparte.

## Verificar desde la tele

```bash
# Manifiesto que ve la app
curl -s https://raw.githubusercontent.com/GitCorion/Medusa/main/docs/api/version.json

# APK del último Release
curl -sL -o /tmp/medusa.apk \
  https://github.com/GitCorion/Medusa/releases/latest/download/Medusa.apk
sha256sum /tmp/medusa.apk   # debe coincidir con apkSha256 del manifiesto
```

## Notas

- `docs/PLAN_PLAY_STORE.md`, `docs/PLAY_CONSOLE_SETUP.md`, `docs/PLAY_STORE_METADATA.md`
  y `docs/PLAN_MEDUSA_PROFESIONAL.md` son **documentación histórica** del antiguo plan de Play
  Store / Firebase y ya no describen el proceso de publicación.
- Para habilitar GitHub Pages: Settings → Pages → Source: `main` / `/docs`.
