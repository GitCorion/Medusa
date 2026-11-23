# Changelog

Todos los cambios relevantes de Medusa TV se documentan aquí. El formato sigue
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el proyecto usa
[Semantic Versioning](https://semver.org/lang/es/).

Los binarios y las notas completas de cada versión se publican en
[GitHub Releases](https://github.com/GitCorion/Medusa/releases). Las versiones anteriores a 2.3.12
no tienen entrada detallada en este archivo; su histórico está en los Releases y en `git log`.

## [Unreleased]

2.4.0 en preparación. La capa visual y los cambios de esta sección ya están publicados en la beta
**2.4.0-beta.3** (23 de noviembre de 2026); la estable sigue en 2.3.12.

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

[Unreleased]: https://github.com/GitCorion/Medusa/compare/v2.3.12...HEAD
[2.3.12]: https://github.com/GitCorion/Medusa/releases/tag/v2.3.12
