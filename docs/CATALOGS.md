# Catálogos de descubrimiento

Los catálogos son **JSON estáticos versionados en el repositorio**, bajo `docs/catalogs/<REGION>/`.
La app los descarga desde `raw.githubusercontent.com` — no hay Hosting ni CDN de terceros.

| Contenido | Origen | Coste para el usuario |
|---|---|---|
| Tendencias, popular y plataformas por región | `docs/catalogs/<REGION>/{index,trending_day,popular}.json` | Una descarga JSON pequeña por región |

## Generación

`.github/workflows/catalogs.yml` se ejecuta cada día (y de forma manual) con el secreto
`TMDB_API_KEY` del repositorio:

```bash
TMDB_API_KEY=... node scripts/catalog/generate-catalogs.mjs
```

El workflow hace commit de los JSON generados en `main`, de modo que están disponibles
inmediatamente en `https://raw.githubusercontent.com/GitCorion/Medusa/main/docs/catalogs/`.

> **Requisito de publicación:** la carpeta `docs/catalogs/` debe estar versionada para que
> `raw.githubusercontent.com` la sirva. El `.gitignore` actual la excluye, así que un fork debe
> quitar esa regla o añadir los ficheros con `git add -f docs/catalogs` antes de publicar. Si una
> región no existe, la app usa el fallback de TMDB en vivo (solo con clave de usuario) y lo que
> pueda resolver de la biblioteca local.

## Región sin catálogo

Si no existe catálogo para la región del dispositivo, la app usa el fallback de TMDB en vivo
(solo si el usuario ha configurado su propia clave API en Ajustes) hasta el siguiente release.
No se lanza ninguna generación bajo demanda.
