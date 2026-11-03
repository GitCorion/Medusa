# Catálogos de descubrimiento (Firebase)

Sistema centralizado para que la app descubra contenido **sin llevar claves ni pedírselas al usuario**.

## Arquitectura

| Dato | Origen | Clave en el cliente |
|---|---|---|
| Plataformas, tendencias y popular regional | JSON estático en `https://medusa-catalogs.web.app/<REGION>/` (Firebase Hosting + CDN) | No |
| Recomendaciones, géneros, detalle, vídeos, temporada | Cloud Function `tmdbProxy` (`/api/tmdb/**`) con caché | No |
| Cruce con la biblioteca del usuario | Room (`collection_items`) en el dispositivo | — |
| Trakt | OAuth del usuario | No |

- El **GitHub Action** `.github/workflows/catalogs.yml` genera los catálogos a diario con la clave TMDB (secreto de GitHub) y publica el sitio `medusa-catalogs`.
- El **worker del dispositivo** descarga el `index.json` de su región, descarga solo si cambió la versión y cruza los `tmdbId` contra la biblioteca.
- Si la región no tiene catálogo, el dispositivo usa `tmdbProxy` (funciona igual, sin clave) y pide su generación.

## Regiones pre-generadas

`scripts/catalog/platforms.json` → `ES, MX, AR, CO, CL, PE, US, GB, FR, DE, IT, PT, BR`.

Para añadir una región: añádela a `regions` en `platforms.json` y ejecuta el generador (o espera al Action diario).

## Secretos

| Secreto | Dónde | Para qué |
|---|---|---|
| `TMDB_API_KEY` | GitHub Secrets + Firebase Secret Manager | Generar catálogos y proxy |
| `FIREBASE_TOKEN` | GitHub Secrets | Publicar Hosting/Functions desde CI |
| `GITHUB_TOKEN` | Firebase Secret Manager | Disparar el Action on-demand (PAT con `actions:write`) |

Sin `GITHUB_TOKEN`, el endpoint `/api/catalog/request` responde `disabled` y la región usa el proxy. Con él, la primera petición de una región sin catálogo lanza la generación.

## Comandos

```bash
# Generar catálogos en local (todas las regiones de platforms.json)
TMDB_API_KEY=... node scripts/catalog/generate-catalogs.mjs

# Solo una región
TMDB_API_KEY=... CATALOG_REGIONS=MX node scripts/catalog/generate-catalogs.mjs

# Publicar catálogos (sitio independiente del OTA)
firebase deploy --only hosting:catalogs --project medusa-fd388

# Publicar proxy y trigger on-demand
firebase deploy --only functions --project medusa-fd388 --force

# Publicar la app/OTA (no toca los catálogos)
./scripts/publish-ota.sh
```

## Endpoints del backend

- `GET /api/tmdb/<ruta>` → proxy TMDB con lista blanca y caché.
- `GET /api/catalog/request?region=XX` → `ready` | `queued` | `disabled`.
