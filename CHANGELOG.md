# Changelog

Todos los cambios relevantes de Medusa TV se documentan aquí. El formato sigue
[Keep a Changelog](https://keepachangelog.com/es-ES/1.1.0/) y el proyecto usa
[Semantic Versioning](https://semver.org/lang/es/).

Los binarios y las notas completas de cada versión se publican en
[GitHub Releases](https://github.com/GitCorion/Medusa/releases). Las versiones anteriores a 2.3.12
no tienen entrada detallada en este archivo; su histórico está en los Releases y en `git log`.

## [Unreleased]

Trabajo posterior al tag `v2.3.12`, todavía sin versión publicada. Es la pasada de identidad que
documenta el [estudio](docs/ESTUDIO.md).

### Añadido

- Identidad abisal: tinta `#050A0E`, corriente `#64E0D3`, violeta de medusa `#B9A7E3`, ámbar
  halógeno `#F2B441`, coral `#E8836B`, pergamino `#EDE8DC`, sedimento `#8C9A96` y grafito húmedo
  `#6E7C78`.
- Space Grotesk variable como única familia tipográfica, empaquetada en `app/src/main/res/font/`.
- Corriente orgánica para foco y selección: `CurrentRule`, `currentUnderline`,
  `BathymetricBackdrop` y `SonarPulse` en `ui/components/OceanIdentity.kt`.
- Navegación tipográfica en la barra superior: pestañas con corriente en lugar de píldoras
  rellenas.
- Hero batimétrico con arcos estáticos, anillo de contacto y un solo pulso de sonar.
- Estudio de diseño en `docs/index.html` y `docs/ESTUDIO.md`.
- Documentación: README bilingüe, arquitectura, contribución, seguridad, changelog, licencia
  GPL-3.0, plantillas de issue y de PR.

### Cambiado

- El foco deja de ser un bloque relleno: anillo de 1,5 dp, título de sedimento a pergamino y
  corriente bajo el título.
- Ajustes pasa a barra lateral plana, sin tarjetas contenedoras.
- Se retira Fraunces y la estructura editorial. La única voz display variable son los ClearLogo.
- Se retira el escalado por tarjeta y la atenuación de vecinos para conservar 60 fps y evitar el
  temblor horizontal.

### Corregido

- Legibilidad y restos de la plantilla anterior detectados en un televisor real.
- Contraste de los textos secundarios sobre pergamino.
- Foco de la navegación y de los carruseles tras el cambio de estructura.

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
