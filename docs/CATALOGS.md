# Catálogos de descubrimiento

Los catálogos son **JSON estáticos** que la app descarga de `raw.githubusercontent.com`. No hay
Hosting ni CDN de terceros.

| Contenido | Origen | Coste para el usuario |
|---|---|---|
| Tendencias, popular y plataformas por región | `docs/catalogs/<REGION>/{index,trending_day,popular}.json` | Una descarga JSON pequeña por región |

## Estado actual: sin publicar

`docs/catalogs/` **no está versionado** (sigue en `.gitignore`). Mientras no se publique, la app
no encuentra catálogo y **oculta la fila de plataformas** a quien no haya configurado su clave
TMDB, para no abrir pantallas vacías. Con clave TMDB, el descubrimiento funciona contra la API en
vivo.

## Publicación manual

No hay generación automática. Para publicar o refrescar los catálogos:

1. Ejecutar el workflow **Refresh discovery catalogs** desde Actions → *Run workflow*.
2. El workflow usa el secreto `TMDB_API_KEY`, genera `docs/catalogs/` y hace commit en `main`
   con `git add -f` (la carpeta sigue ignorada para evitar commits accidentales de 17 MB).
3. A partir de ese momento los JSON están disponibles en
   `https://raw.githubusercontent.com/GitCorion/Medusa/main/docs/catalogs/`.

También se puede generar en local:

```bash
TMDB_API_KEY=... node scripts/catalog/generate-catalogs.mjs
git add -f docs/catalogs
```

Publicar los catálogos no basta por sí solo para que reaparezca la fila sin clave: hoy
`HomeViewModel.hasDiscoverySource` solo mira la clave TMDB. Para aprovecharlos sin clave hay que
ampliar esa condición para que también consulte si existe catálogo remoto (el punto de extensión
está comentado en el código). Si se quiere dejar de publicarlos, basta con borrar `docs/catalogs/`
del repositorio; la app no cambia de comportamiento.

## Región sin catálogo

Si no existe catálogo para la región del dispositivo, la app usa el fallback de TMDB en vivo
(solo si el usuario ha configurado su propia clave API en Ajustes). No se lanza ninguna
generación bajo demanda.
