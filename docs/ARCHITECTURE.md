# Arquitectura de Medusa TV

Este documento describe cómo está construida la app, qué piezas se comunican con qué y por qué
se tomaron las decisiones que no son obvias. Está pensado para alguien que va a tocar el código
por primera vez, no para repetir el organigrama de carpetas.

**Versión documentada:** 2.4.1 de `main` (`versionCode 1000167`); estable publicada: 2.4.1.
**Plataforma:** Android TV, Google TV, Fire TV y tablet. `minSdk 26`, `compileSdk` y `targetSdk` 36.

## 1. Vista general

Medusa es una aplicación de una sola actividad. `MainActivity` monta el tema, resuelve las
preferencias iniciales y entrega el control a `MedusaApp`, que declara el grafo de navegación con
Navigation Compose. Cada pantalla tiene su ViewModel; los ViewModels no hablan con Retrofit ni con
Room directamente, sino con una fachada de datos.

```text
MainActivity
└── MedusaTheme
    └── MedusaApp + NavHost
        ├── HomeScreen            HomeViewModel
        ├── MedusaScreen          MedusaViewModel
        ├── FavoritesScreen       FavoritesViewModel
        ├── SearchScreen          SearchViewModel
        ├── DetailScreen          DetailViewModel
        ├── PlayerScreen          PlayerViewModel
        ├── BrandScreen           BrandViewModel
        ├── GenreScreen           GenreViewModel
        ├── SeasonalScreen        SeasonalViewModel
        └── SettingsScreen        SettingsViewModel
```

La navegación de primer nivel es `home`, `medusa`, `favorites`, `search` y `settings`; las rutas
con argumentos son `detail/{itemId}`, `player/{itemId}`, `brand/{brandId}`, `genre/{genreName}` y
`seasonal/{eventId}`. En TV, la tecla física `BACK` es la única affordance de retroceso. En tablet,
Marca, Género y Especiales muestran `TvBackButton`: un chip de 36 dp de alto y 8 dp de radio.

## 2. Stack

| Capa | Tecnología |
|---|---|
| UI | Kotlin, Jetpack Compose TV (`androidx.tv`), Material 3 para superficies no TV |
| Navegación | Navigation Compose |
| Estado | ViewModel + `StateFlow` + `collectAsStateWithLifecycle` |
| Inyección | Hilt + KSP |
| Red | Retrofit 2.11, OkHttp 4.12, kotlinx.serialization |
| Persistencia | Room 22 con FTS, DataStore Preferences, snapshots en fichero |
| Imágenes | Coil 2.7 con caché de disco de 1 GB |
| Reproducción | Media3 1.5.1 + decodificador FFmpeg de Jellyfin |
| Trabajo de fondo | WorkManager 2.9 + HiltWorker |
| Rendimiento | Baseline Profile, perfiles de arranque, `dexLayoutOptimization` |
| Pruebas | JUnit, MockWebServer, Room MigrationTestHelper, Compose UI Test |

## 3. Datos

### 3.1. `MediaRepository`

Es una **fachada delgada** y un composition root de colaboradores. Conserva la API pública que
consumen ViewModels, casos de uso y workers, pero delega:

| Colaborador | Responsabilidad |
|---|---|
| `LocalMediaRepository` | Consultas Room, deduplicado, resolución local, estadísticas de biblioteca |
| `JellyfinMediaRemoteRepository` | Lecturas y escrituras puntuales contra Jellyfin |
| `MediaSyncRepository` | Sincronización masiva, paginación, progreso y primado de caché |
| `TmdbRepository` | Metadatos, pósters y caché de votos |
| `TraktRepository` | OAuth por dispositivo, scrobbling, listas y colecciones |
| `MediaAccessPolicy` | Restricciones parentales por IDs permitidos |
| `MediaCaches` | Cachés TTL en memoria |

La razón de la fachada es histórica y práctica: antes era una clase de casi 1.900 líneas. Separar
los colaboradores permitió probarlos y sustituirlos sin reescribir cada pantalla.

### 3.2. Room 22

Entidades principales:

- `JellyfinMediaEntity`: catálogo normalizado, metadatos, progreso, colecciones.
- `JellyfinMediaFts` y `JellyfinMediaGenresFts`: búsqueda de texto completo.
- `HomeSectionCacheEntity`: secciones de Inicio ya calculadas.
- `TmdbVoteCacheEntity`: votos y popularidad con TTL.
- `AllowedMediaIdEntity`: IDs permitidos por la política parental.
- `SeasonalItemEntity` / `SeasonalIndexMetaEntity`: especiales estacionales.
- `CollectionItemEntity` / `CollectionMetaEntity`: sagas y colecciones.

Las migraciones se exportan a `app/schemas` y se validan con `AppDatabaseMigrationTest` mediante
`MigrationTestHelper`. No se usa `fallbackToDestructiveMigration` como estrategia normal.

### 3.3. Preferencias y credenciales

`UserPreferencesRepository` expone un `Flow<UserPreferences>` respaldado por DataStore
(`medusa_settings`). Las credenciales de Jellyfin y Trakt se cifran con `CryptoUtils`:

- AES/GCM/NoPadding con una clave AES del **Android Keystore** (`medusa_credentials_key`).
- Formato `enc:` + Base64(iv + ciphertext).
- Si el Keystore falla, la escritura falla; nunca se guarda un token en claro.
- Los valores legacy sin prefijo se aceptan y migran de forma transparente.

### 3.4. Inicio

`GetHomeFeedUseCase` compone el feed con una estrategia de caché en cascada:

1. Caché en memoria (se emite de inmediato si tiene al menos 4 secciones y menos de 5 minutos).
2. Snapshot en fichero (`HomeSnapshotFileStore`) para arranques en frío.
3. Caché de secciones en Room.
4. Generación en vivo: continuar viendo, tendencias, estrenos, añadido recientemente, porque
   viste, contexto temporal, sagas, plataformas, Trakt, joyas, top de biblioteca, recomendaciones y
   géneros.

Las secciones se ordenan por `HomeSectionKind`, no por el título, y se generan con concurrencia
acotada, timeouts y `supervisorScope` para que una fuente lenta no bloquee el resto. El TTL del
feed es de 15 minutos.

## 4. Sincronización

`JellyfinSyncWorker` es el punto de entrada del trabajo pesado:

1. Lee servidor, usuario y token de los `inputData` o de las preferencias.
2. Si faltan credenciales, falla con un error explícito.
3. Llama a `MediaRepository.syncJellyfinLibrary()` con sincronización incremental basada en
   `jellyfinLastSync` o completa si se fuerza.
4. Si termina bien, ejecuta `ANALYZE` la primera vez y `PRAGMA optimize` después, solo cuando la
   app no está visible ni reproduciendo.
5. Encola `OverviewEnrichmentWorker` para completar sinopsis y metadatos.

Otros workers:

- `CollectionIndexWorker`: indexa sagas y colecciones para las filas de Inicio.
- `CollectionIndexScheduler`: decide cuándo ejecutarlo.
- `OverviewEnrichmentWorker`: rellena descripciones que Jellyfin no entrega.

La sincronización rápida (`syncQuickInit`) y el primado de caché permiten pintar Inicio antes de
que termine la biblioteca completa. `MediaSyncRepository` publica `SyncProgress` como `StateFlow`
para que la barra superior muestre el estado sin bloquear la navegación.

## 5. Red

`NetworkClientFactory` centraliza OkHttp y Retrofit:

- **Dos JSON.** El JSON general ignora claves desconocidas; el JSON de Jellyfin además usa
  `encodeDefaults = true` y `explicitNulls = false`. Sin `encodeDefaults`, el `DeviceProfile`
  pierde `Type: Video`, `PlayableMediaTypes` y el perfil de transcodificación. Hay una prueba
  golden que serializa el perfil real y una prueba MockWebServer que captura el body de
  `POST /Sessions/Capabilities/Full`.
- **TLS estricto.** Se construye un trust manager desde `AndroidCAStore` excluyendo las entradas
  `user:`. Las CAs instaladas manualmente no se aceptan. Un certificado autofirmado solo se admite
  si el usuario fijó su SHA-256 y coincide con la hoja de la cadena.
- **Caché de imágenes.** Coil usa OkHttp compartido, caché de memoria al 10 % y 1 GB de disco.
- **Subrutas.** `combineBaseUrlAndPath` soporta `https://host/jellyfin`, IPv6 y redirecciones.
- **Logs.** El interceptor de logging redacta tokens, `api_key` y parámetros sensibles.

Endpoints externos centralizados en `ApiConfig`:

| Base | Uso |
|---|---|
| `raw.githubusercontent.com/GitCorion/Medusa/main/` | Manifiesto OTA, catálogos, diccionario de etiquetas |
| `api.themoviedb.org/3/` | Metadatos si el usuario configura su clave |
| `api.trakt.tv/` | Trakt, solo si se vincula |
| `image.tmdb.org` | Pósters y backdrops |
| `youtube-nocookie.com` | Tráilers incrustados |

Los códigos QR (QuickConnect de Jellyfin, activación de Trakt y política de privacidad) se generan localmente con ZXing; el payload no se envía a ningún servicio externo.

## 6. Reproducción

La lógica vive en un solo sitio y no debe duplicarse:

- `JellyfinPlaybackSourceResolver` construye la lista de candidatos y las URLs de subtítulos.
- `PlayerViewModel.loadStreamUrl()` negocia `PlaybackInfo`, arranca la sesión y monta el player.
- `PlayerViewModel.retryWithTranscoding()` renegocia audio o versión tras un fallo de decoder.
- `PlayerScreen` prueba los candidatos en orden y cae a transcodificación completa.

Orden para `.strm`:

1. `stream?static=true&MediaSourceId=...` (Jellyfin resuelve el destino en el servidor).
2. Remux `stream?MediaSourceId=...`.
3. `DirectStreamUrl`.
4. `TranscodingUrl`.
5. `stream?static=true` de versiones alternativas.

Orden para contenido normal: `DirectStreamUrl`, `TranscodingUrl`, `stream?static=true`,
`stream?MediaSourceId`, `stream.{container}?static=true` y, por último, transcodificación completa.

El perfil de dispositivo declara `mkv, mp4, m4v, mov, webm, ts, m2ts, mts, m2t, flv, 3gp, 3g2,
ogv, ogg, mpg, mpeg, ps, vob, strm, m3u8, mpd`. `strm` es obligatorio. AVI/ASF/WMV quedan fuera
porque Media3 no tiene extractor; Jellyfin los transcodifica.

Audio: baseline `aac, mp3, flac, opus, ac3, eac3`; DTS/TrueHD solo si hay passthrough real. Los
reintentos conservan `PlaySessionId`, posición y `MediaSourceId`.

### 6.1. TOD (catálogo bajo demanda)

Medusa no habla con TOD directamente. El plugin
[Catalogo](https://github.com/GitCorion/Medusa-Jellyfin-Plugin) expone `Capabilities`, `Status` y
`Prepare` bajo `/Catalogo/*`, traduce el `.strm` a una clave de TOD y mantiene un lease de
reproducción. El cliente consulta el estado, pide `Prepare` si hace falta, muestra el buffer hasta
que `stream_ready` es verdadero y reproduce a través de Jellyfin (nunca la URL `play` del proxy).
El plugin requiere Jellyfin 12.4+ y un proxy TOD en loopback. Sin plugin, el `.strm` sigue siendo
un ítem normal de Jellyfin y solo se reproduce si ya está preparado.

Los detalles completos, incluidos los errores que rompieron `.strm` en versiones pasadas, están en
[`PLAYBACK.md`](PLAYBACK.md). Es lectura obligatoria antes de tocar el reproductor.

## 7. Actualizaciones OTA

`AppUpdateManager` consulta `docs/api/version.json` con reintentos:

- El manifiesto incluye `versionCode`, `versionName`, `minSupportedVersionCode`, `forceUpdate`,
  `killSwitch`, `releaseNotes`, `apkUrl`, `apkSha256` y firmas.
- La descarga solo se permite desde `github.com`, `objects.githubusercontent.com` o
  `release-assets.githubusercontent.com`.
- Tamaño máximo: 150 MB. SHA-256 obligatorio y formato validado.
- Si la red falla, se usa el último manifiesto validado con TTL de siete días. Si tampoco hay uno
  fresco, la app entra en modo fail-closed.
- El `killSwitch` bloquea todas las versiones, haya o no reemplazo.
- El canal por defecto es `BuildConfig.UPDATE_CHANNEL`, pero el usuario puede elegir Estable o
  Beta en Ajustes → Acerca de. La preferencia guardada manda; el canal beta lee ambos manifiestos y
  se queda con el `versionCode` más alto.
- La instalación usa `FileProvider` y `REQUEST_INSTALL_PACKAGES`.

## 8. Interfaz y foco

`MainActivity` aplica `MedusaTheme`, inyecta los `CompositionLocal` de acento, monocromo, tema de
indicadores, estilo de logos y tamaño de ventana, y monta el `NavHost`.

- **Dock de iconos.** `TvTopBar` monta cinco cápsulas de 18 dp de radio con iconos de 18 dp. El
  foco no exige OK; la barra admite solo iconos, solo texto o ambos. Sin etiquetas ruidosas por
  defecto.
- **Foco.** Las tarjetas usan borde de 1,5 dp `primary` + `zIndex(10f)` + título a plena
  luminosidad. Las vecinas conservan su opacidad y no hay escala de tarjeta. Es la decisión de
  v2.3.12 que se mantiene.
- **Reglas de 10 pies.** Gutter de 48 dp, hero a sangre, una acción primaria por pantalla y
  navegación con la tecla física BACK en TV.
- **Movimiento.** El chrome no tiene animaciones autónomas: el foco se lee por borde, relleno,
  `zIndex` y luminosidad. Excepciones acotadas: el hero rota cada 12 s cuando no tiene foco, las
  partículas estacionales son opcionales y hay pulsos de carga. Se respeta la escala de animación
  del sistema.
- **Cero blur.** No se usa `Modifier.blur()` ni `RenderEffect` en ninguna pantalla.
- **Tablet.** `WindowSizeClass` adapta anchos y la navegación táctil. En TV no se muestran botones
  de volver; en tablet, Marca, Género y Especiales usan `TvBackButton` de 36 dp.

## 9. Rendimiento

- Baseline Profile generado en `baselineprofile/` y guardado en `app/src/<variant>/generated`.
- `dexLayoutOptimization = true` para el arranque.
- Caché de imágenes de 1 GB y resize en servidor (`JellyfinImageUtils`).
- Partículas escaladas según el dispositivo; `AppForeground` baja el ritmo del enriquecimiento.
- `LazyRow`/`LazyColumn` con claves estables y sin animaciones por tarjeta.
- No hay escala por tarjeta ni spotlight: el foco se lee por borde, zIndex y luminosidad del título.
- Los workers pesados se posponen mientras hay reproducción o la app está visible.

## 10. Pruebas

36 clases de prueba unitaria (213 casos) y una instrumentada (5 casos). Las más importantes por
contrato:

| Prueba | Qué protege |
|---|---|
| `PlayerResolutionTest` | Orden de candidatos, `.strm`, subrutas, subtítulos, reintentos, DeviceProfile |
| `JellyfinDeviceProfileGoldenTest` | Serialización real del perfil con `encodeDefaults` |
| `JellyfinSerializationWiringTest` | Body real de `POST /Sessions/Capabilities/Full` y eventos |
| `AppDatabaseMigrationTest` | Migraciones de Room |
| `GetHomeFeedUseCaseTest` | Prioridad, caché y composición del feed |
| `MediaAccessPolicyTest` | Restricciones parentales |
| `SearchPredictiveEngineTest` | Sugerencias y teclado |
| `CryptoUtils` (indirecto) | Cifrado y migración de credenciales |

Comandos:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest
```

## 11. Catálogos de descubrimiento

`CatalogRemoteDataSource` descarga JSON compactos de `docs/catalogs/<REGION>/`:

- `index.json`: versión, plataformas disponibles, tendencias y populares.
- `trending_day.json`, `popular.json`, `netflix_top10.json` y `platforms/*.json`.

Cada ítem es `[id, isTv, popularity, title, year]`. La app los cruza con la biblioteca local; no
pide miles de páginas a TMDB desde el dispositivo.

**Nota operativa:** el workflow `catalogs.yml` regenera la carpeta **manualmente** con
`workflow_dispatch` y `TMDB_API_KEY`; no hay cron. En el árbol actual `docs/catalogs/` está
excluido por `.gitignore`; si preparas un
fork o publicas desde cero, asegúrate de versionar esa carpeta (`git add -f docs/catalogs` o quita
la regla) para que `raw.githubusercontent.com` la sirva. Si una región no existe, la app usa TMDB
en vivo cuando el usuario configuró su clave y muestra lo que pueda de la biblioteca local.

## 12. Seguridad

- Sin Firebase, analytics, crash reporting ni Advertising ID.
- Credenciales cifradas con Keystore; fallo cerrado.
- TLS con CAs del sistema solamente; pin SHA-256 para autofirmados.
- OTA con origen permitido, tamaño máximo, SHA-256 y kill-switch.
- Sin WebView para la interfaz; los tráilers usan el reproductor incrustado de YouTube nocookie.
- Los logs redactan secretos.

## 13. Deuda conocida

- La capa táctil de tablet merece una pasada propia; el modo solo iconos se pensó para un mando.
- `TvPalette` conserva nombres históricos (`TextSecondaryCool`, `FocusInk`) que ya no describen sus
  valores actuales.
- No hay un benchmark automático de frames en CI; la regla de cero blur se revisa en código.
- La carpeta de catálogos no está versionada en el árbol actual (ver nota operativa).

## 14. Dónde tocar

| Quiero… | Empieza en |
|---|---|
| Añadir una pantalla | `ui/screens/<nombre>/`, `MainActivity.kt` y un ViewModel con Hilt |
| Añadir una fuente de datos | `data/remote/`, `data/repository/` y un método en la fachada |
| Cambiar colores o tipografía | `ui/theme/Color.kt`, `ui/theme/Type.kt`, `DESIGN.md` |
| Cambiar la navegación o el foco | `ui/components/TvTopBar.kt`, `ui/theme/TvDesignSystem.kt` y los componentes de tarjeta |
| Tocar reproducción | `JellyfinPlaybackSourceResolver`, `PlayerViewModel`, `PlayerScreen` y `PLAYBACK.md` |
| Cambiar el feed de Inicio | `GetHomeFeedUseCase` y `HomeViewModel` |
| Añadir un worker | `data/worker/`, `di/` y `WorkManager` |
| Cambiar el OTA | `AppUpdateManager`, `docs/api/version.json`, `RELEASE.md` |
