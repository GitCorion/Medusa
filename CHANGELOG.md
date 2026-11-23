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
- Medusa: el diccionario de etiquetas deja de intentar descargarse de GitHub. Vive compilado en
  la APK; el archivo remoto nunca existió y la petición solo generaba un 404 silencioso.

### Corregido

- Legibilidad y restos de la plantilla anterior detectados en un televisor real.
- Contraste de los textos secundarios sobre pergamino.
- Foco de la navegación y de los carruseles tras el cambio de estructura.
- Teclado de Búsqueda: modo **Libre** por defecto (nunca oculta letras). El modo Auto es opt-in,
  se guarda en DataStore y, si se activa, las letras sin resultados se atenúan sin desaparecer.
- Servidores con dirección IPv6 literal (`http://[2001:db8::1]:8096`): la normalización de URL y
  la extracción de host respetan los corchetes en lugar de cortar en el primer `:`.
- Etiquetas de Ajustes, cabeceras de sección y chips de género en caja oracional, sin tracking
  artificial. La ceja de nombre de serie en fichas de episodio deja de ir en mayúsculas.
- Metadatos separados por espacio estructural en hero, tarjetas, ficha y reproductor; se retiran
  los puntos medios (`·`, `•`).
- Fondo raíz en `AbyssVoid` (`#04060A`) en lugar de un cuarto negro. Nebulosa de Descubrir en
  cobalto, cian y ámbar de la paleta; scrubber del reproductor y selección de onboarding con el
  acento del sistema.
- Indicador de actualización en la barra superior: icono con descripción accesible en lugar del
  punto verde decorativo.
- Búsqueda: campo de 52 dp con el micrófono contenido (antes desbordaba), teclas a 18 sp y
  acciones a 11 sp, estado vacío con una línea de guía y tarjetas que llenan su celda de la
  cuadrícula (sin desbordar el borde derecho).
- Home: la fila de logos de plataformas lleva rótulo («Tus plataformas») y ya no aparece como un
  muro de marcas sin contexto.
- Home: la fila de plataformas se oculta si el usuario no ha configurado su clave TMDB, porque sin
  catálogo remoto sus pantallas quedaban vacías y parecían rotas.
- Ajustes: los interruptores de plataformas se ocultan hasta que hay clave TMDB; sin catálogo no
  filtraban nada y daban la impresión de estar averiados.
- Favoritos: se retira el corazón de cada tarjeta; en una pantalla donde todo es favorito era
  ruido sobre el arte.
- Hero: solo admite títulos con backdrop 16:9 real y sinopsis real (`HeroEligibility`). Los que
  no tienen material completo van a las filas; la portada ya no recorta pósters 2:3 ni rellena
  con plazas inventadas. Un backdrop que devuelve error se retira de la rotación.
- Ficha: la carga remota tiene timeout de 20 s con estado de error y botón Reintentar; antes
  podía quedarse en «Cargando…» indefinidamente.
- Pantalla de género: al entrar se enfoca el primer filtro de formato (antes no había foco
  visible hasta pulsar una dirección).
- Navegación: ARRIBA desde el contenido vuelve a la **pestaña activa** en Favoritos, Buscar y
  Ajustes, en lugar de caer siempre en Inicio.
- Ficha: si falta el backdrop, el póster ocupa el lateral derecho al 55 % de opacidad para que la
  ficha no quede como texto sobre negro; secciones en caja oracional y sinopsis de reserva más
  breve.
- Hero y ficha: el botón primario (Reproducir) lleva un tinte del acento en reposo y el icono en
  color, para leerse como acción principal sin competir con el relleno sólido del foco.
- Cargas de Descubrir y Plataformas: mensaje explícito en lugar de una medusa sola sin contexto.
  Sin pistas de teclas: en TV el botón BACK se sobreentiende y esas líneas ensucian la carga.
- Error de reproducción: tarjeta neutra sin borde rojo de 1 px; cuando no existe fuente
  reproducible se ofrece Volver en vez de un Reintentar en bucle.
- Tokens: 288 usos de `Color.White` pasan al token `TextPrimary`. `DESIGN.md` y `PRODUCT.md`
  quedan reconciliados con lo que se pinta (DM Sans, `#04060A`, cian `#00D2FF`, oro `#FACC15`).
- Overlay de preparación de torrent: cadenas localizadas (semillas y pares, versiones, ETA,
  estados de descarga), sin el emoji `⚡` ni «seeds/peers» en inglés.
- HUD del reproductor y cabeceras compactas: se retiran los puntos medios restantes entre
  metadatos y pistas; los botones de ajuste (`−`/`+`) usan iconos en lugar de glifos de texto.

### Seguridad

- Los códigos QR (QuickConnect de Jellyfin, activación de Trakt y política de privacidad) se
  generan en el dispositivo con ZXing. Se elimina `api.qrserver.com`, que recibía la URL del
  servidor y el código de emparejamiento.
- OTA: se siguen manualmente las redirecciones 302 de GitHub Releases, validando en cada salto
  HTTPS y el host contra el allowlist (máximo tres saltos). Antes la descarga fallaba siempre.
- Keystore: el descifrado ya no genera una clave nueva si la existente no está disponible (lo que
  dejaba ilegibles todas las credenciales); la caché en claro se purga al cerrar sesión.
- Release: ProGuard elimina también `Log.i`, que filtraba URL del servidor, `userId` y prefijos de
  token en logcat.

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
