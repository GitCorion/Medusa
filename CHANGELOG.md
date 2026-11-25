# Changelog

Todos los cambios relevantes de Medusa TV se documentan aquí. El formato sigue
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el proyecto usa
[Semantic Versioning](https://semver.org/lang/es/).

Los binarios y las notas completas de cada versión se publican en
[GitHub Releases](https://github.com/GitCorion/Medusa/releases). Las versiones anteriores a 2.3.12
no tienen entrada detallada en este archivo; su histórico está en los Releases y en `git log`.

## [Unreleased]

## [2.4.2-beta.8] - 2026-11-26

### Cambiado

- Sincronización con Jellyfin: se incrementa `SYNC_ALGORITHM_VERSION` a `5` para forzar una única resincronización completa automática tras actualizar, purgando títulos y episodios eliminados del servidor y recuperando elementos omitidos en sincronizaciones incrementales previas.

## [2.4.2-beta.7] - 2026-11-25

### Corregido

- Reproductor On-Demand (TOD): se desactiva la lectura asíncrona de Cues al final del MKV en TOD para evitar errores de `Range` EOF, se recuperan bloqueos/rebuffer y errores HTTP 503 consultando `/Medusa/Status/{itemId}` sin quemar pistas de audio y se renueva/libera el *lease* en el servidor (`Medusa/Lease/{itemId}`).
- Reproductor: ajuste en los candidatos de reproducción directa (.strm y fuentes alternativas) con extensión efectiva para mejorar la compatibilidad con Jellyfin.
- Sincronización con Jellyfin: se purgan de la caché local los títulos borrados en Jellyfin (tanto en sincronización delta como completa) junto a sus episodios huérfanos, y se evita marcar como vistas las series vacías sin episodios (`totalItemCount <= 0`).

## [2.4.2-beta.6] - 2026-11-24

### Corregido

- Overlay TOD: la barra, la ETA y el aviso de "conectando con peers" salen del mismo valor
  (búfer contiguo o datos descargados). Antes la barra podía ir al 98 % mientras el texto decía
  que seguía conectando, o al revés.
- Reproductor: si tras 45 s montado no ha entrado ni un byte, se reintenta una vez la resolución
  del stream. Cubre el estado colgado en "Cargando reproducción…" que dejaba un fallo previo de
  preparación, sin peticiones al servidor.
- Reproductor: el `PlayerStreamInfo` de respaldo de `retryWithTranscoding` conserva `isOnDemand`,
  para que el watchdog de 15 min no vuelva a 75 s después de un reintento.

## [2.4.2-beta.5] - 2026-11-24

### Corregido

- Reproductor: se eleva el timeout de resolución inicial a 15 minutos (`TORRENT_PREP_TIMEOUT_MS`) para streams On-Demand (TOD), evitando que el watchdog de 75 s salte prematuramente mientras Jellyfin y el enjambre torrent completan el buffer inicial.
- Overlay TOD: corrección del cálculo y visualización del buffer cuando `buffer_bytes` es nulo o 0 pero `downloaded_bytes` ya contiene datos (>0 MB), garantizando que el porcentaje y la barra de progreso reflejen el avance real en la interfaz.

## [2.4.2-beta.4] - 2026-11-23

### Corregido

- Reproductor: se inhibe el timeout de resolución de 75 s mientras el overlay de preparación TOD esté activo, y se prioriza el overlay frente a la carga de contenido. Se valida además el estado real del reproductor tras alcanzar STATE_READY para evitar falsos positivos durante la reproducción.

### Eliminado

- Restos del antiguo explorador espacial (constelación/nebula): canvas, motores, atractores, grid
  y sus tests, sin referencias desde la UI. Descubrir queda documentado como un rail de temáticas
  con filtros AND y `BACK` para deshacer.

### Cambiado

- Rebranding: el puente pasa de Catalogo a Medusa.
- README reescrito como landing: estado actual, instalación, configuración inicial, arquitectura,
  privacidad y enlaces a los documentos técnicos. `README.en.md` queda alineado.
- Pantallas de carga del reproductor y de preparación de torrent sin tarjetas contenedoras: layout
  plano, menos iconos y adornos, textos e indicadores mínimos. La medusa vuelve al buffering.

### Corregido

- Favoritos abre ordenado por año descendente y el corazón de favorito vuelve a mostrarse en todas
  las tarjetas. Los badges de visto y favorito usan el acento activo.
- Comprobación anti-rollback del APK descargado: se usa `PackageInfoCompat` para no llamar a
  `longVersionCode` en API 26–27.

### Documentación

- `DESIGN.md` se reescribe con los tokens, radios, tipografía, movimiento y pantallas reales.
- Se sincronizan versiones, conteo de pruebas, canales OTA, comandos Gradle, botón táctil y
  catálogos manuales en README, PRODUCT, CONTRIBUTING, SECURITY y `docs/`.

## [2.4.1] - 2026-11-23

### Añadido

- Selector de canal en Ajustes → Acerca de: **Estable** o **Beta**. La elección se guarda y el
  actualizador lee el manifiesto del canal correspondiente.
- Aviso al cambiar de canal: desde una beta más nueva no se puede bajar a una estable anterior;
  el equipo vuelve a estable con la siguiente publicación estable.

## [2.4.0] - 2026-11-23

Primera estable de la etapa 2.4.0. Sustituye a `2.3.12`, cambia la identidad a `tv.medusa.app` y
cierra la beta pública.

> **Migración desde `tv.medusa.app.beta`:** instala esta versión y desinstala la antigua. Android
> las trata como apps distintas; ajustes, servidor Jellyfin y clave TMDB no se migran.

### Añadido

- Identidad cinematográfica: tinta `#04060A`, cian eléctrico `#00D2FF`, cobalto `#0B5EFF`, aqua
  `#8BFFFF`, ámbar `#E5A44D`, oro `#D4AF37` y nota `#FACC15`.
- DM Sans variable como única familia tipográfica, con escala de diez pies (36/24/16/13/11).
- Dock de iconos de 18 dp, foco de 1,5 dp sobre el póster, `zIndex(10f)` y título a plena
  luminosidad.
- Estudio de diseño en `docs/index.html` y `docs/ESTUDIO.md`.
- Documentación: README bilingüe, arquitectura, contribución, seguridad, changelog, licencia
  GPL-3.0, plantillas de issue y de PR.
- TOD con el plugin de Jellyfin: `Prepare`, buffer y `stream_ready`.
- Canales estable y beta con manifiestos OTA separados, `versionCode` automático que nunca
  retrocede y migración a `tv.medusa.app`.
- QR local para enlaces y activaciones, sin depender de un generador externo.
- Top 10 con números tipográficos de titanio y anchura adaptativa para leer el 10 completo.

### Cambiado

- La app pasa a `tv.medusa.app`. Los equipos que venían de `tv.medusa.app.beta` deben instalar
  esta versión una vez y desinstalar la antigua.
- Se retira el sistema Pro/VIP heredado de la etapa comercial: edición, compras, trial,
  entitlement de servidor y badge PRO. Todas las funciones quedan disponibles sin licencia.
- La navegación vuelve al dock de iconos: el foco no exige OK y la barra admite iconos, texto o
  ambos.
- Las tarjetas conservan su opacidad y no escalan; el foco se lee por borde, zIndex y el título.
- Se eliminan el spotlight, la atenuación de vecinos y las animaciones autónomas.
- Los neutros pasan a zinc y grafito; el texto principal es blanco puro sobre tinta abisal.
- Se retira Fraunces y la estructura editorial. La única voz display variable son los ClearLogo.

### Corregido

- Legibilidad y restos de la plantilla anterior detectados en un televisor real.
- Contraste de los textos secundarios y de los metadatos.
- Foco de la navegación y de los carruseles tras el cambio de estructura.
- Botones de acción y diálogos: el reposo es neutro y solo el foco usa el acento; antes el botón
  primario se pintaba como enfocado siempre.
- Play del hero, la ficha y el reproductor: reposo neutro como el resto de acciones, con el acento
  reservado al foco.
- Ficha: el botón Reproducir recupera el foco al entrar y al volver del reproductor, sin exigir
  mover el mando.
- Buscar: los pósters pasan a tres columnas y quedan por debajo del tamaño de Descubrir.
- Buscar: el micrófono y el modo de teclado son accesibles con ARRIBA y DERECHA desde las teclas.

### Descartado

- La metáfora de corriente ondulada, sonar y batimetría.
- La navegación tipográfica con subrayado.
- La capa Material TV con tabs pill, escala 1,1x y chips de 48 dp.

### Seguridad

- Sin cambios de modelo de seguridad en esta pasada. Se mantienen el cifrado de credenciales, el
  pin TLS, la verificación SHA-256 del OTA y el modo fail-closed.

## [2.3.12] - 2026-11-22

### Añadido

- Distribución pública mediante APK firmado en GitHub Releases.
- Manifiesto OTA en `docs/api/version.json` con `versionCode`, `versionName`, `forceUpdate`,
  `killSwitch`, `apkUrl` y `apkSha256`.
- Actualización desde la app con verificación de origen, tamaño y SHA-256.

### Cambiado

- La distribución deja de depender de Firebase y de Google Play. La app se actualiza desde
  Releases.
- El `applicationId` se mantiene en `tv.medusa.app.beta` para conservar la continuidad de la beta.

### Notas

- `versionCode`: `1000159`. `versionName`: `2.3.12`.
- APK: `Medusa.apk`, 15,2 MB.
- SHA-256 publicado en el manifiesto OTA y en la página del Release.

[2.4.1]: https://github.com/GitCorion/Medusa/releases/tag/v2.4.1
[2.4.0]: https://github.com/GitCorion/Medusa/releases/tag/v2.4.0
[2.3.12]: https://github.com/GitCorion/Medusa/releases/tag/v2.3.12
