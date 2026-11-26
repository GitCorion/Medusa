<div align="center">
  <img src="app/src/main/res/drawable/ic_medusa_header.png" alt="Medusa TV" width="360">
  <p><strong>Tu biblioteca de Jellyfin, diseñada para el televisor.</strong><br>
  Cliente nativo para Android TV y tablet, escrito en Kotlin y Compose TV.<br>
  Interfaz 100% nativa, sin Firebase, sin analítica y sin Play Store.</p>

  [![Release](https://img.shields.io/github/v/release/GitCorion/Medusa?style=flat-square&color=00D2FF&label=release)](https://github.com/GitCorion/Medusa/releases)
  [![Android](https://img.shields.io/badge/Android-8.0%2B-00D2FF?style=flat-square)](#requisitos)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-00D2FF?style=flat-square)](https://kotlinlang.org)
  [![Compose TV](https://img.shields.io/badge/Compose%20TV-1.0.0--alpha07-00D2FF?style=flat-square)](https://developer.android.com/training/tv/use-apis/compose)
  [![License](https://img.shields.io/badge/license-GPL--3.0-00D2FF?style=flat-square)](LICENSE)

  <p>
    <a href="https://github.com/GitCorion/Medusa/releases/latest/download/Medusa.apk"><strong>Descargar el APK</strong></a>
    &nbsp;·&nbsp;
    <a href="https://gitcorion.github.io/Medusa/"><strong>Estudio de diseño</strong></a>
    &nbsp;·&nbsp;
    <a href="README.en.md">English</a>
  </p>
</div>

![Inicio de Medusa TV en un televisor de 1080p](docs/assets/estudio/captures/inicio-1600.webp)

<p align="center"><sub>Inicio en un televisor de 1080p.</sub></p>

**Contenido:** [Qué es](#qué-es) · [Ecosistema](#ecosistema) · [Funciones](#funciones) · [Instalación](#instalación) · [Configuración inicial](#configuración-inicial) · [Capturas](#capturas) · [Arquitectura](#arquitectura) · [Compilar](#compilar) · [Documentación](#documentación) · [Privacidad](#privacidad-y-seguridad) · [Problemas](#problemas-frecuentes)

## Qué es

Medusa TV es un cliente Jellyfin nativo para el salón. Convierte tu biblioteca autoalojada en una
interfaz pensada para el sofá, el mando a distancia y pantallas OLED: carátulas a pantalla completa,
foco de alto contraste y cero adornos que compitan con el contenido.

No es un envoltorio web ni un catálogo alojado. La app habla directamente con **tu** servidor
Jellyfin, guarda el catálogo en local y solo sale a internet para metadatos, catálogos estáticos
opcionales, Trakt (si lo vinculas) y actualizaciones firmadas de GitHub Releases.

## Estado

- **Estable actual:** `2.4.1` (`versionCode 1000167`).
- **Plataforma:** Android 8.0+ (API 26). Android TV, Google TV, Fire TV y tablet.
- **Canales OTA:** estable y beta, con selector en **Ajustes → Acerca de → Canal**.
- **Distribución:** APK firmado en GitHub Releases. Sin cuenta de Medusa, Firebase ni Play Store.

## Ecosistema

Medusa es la pieza de cliente. Las otras dos son opcionales y solo hacen falta si quieres catálogo
bajo demanda.

| Pieza | Rol | Cuándo la necesitas |
|---|---|---|
| [Medusa](https://github.com/GitCorion/Medusa) (este repositorio) | Cliente nativo para Android TV y tablet. Reproduce desde Jellyfin. | Siempre |
| [Medusa-Jellyfin-Plugin](https://github.com/GitCorion/Medusa-Jellyfin-Plugin) | Puente Jellyfin↔TOD: estado, preparación, lease y negociación de versiones. En Jellyfin aparece como **Medusa**. | Si usas TOD |
| [TOD](https://github.com/GitCorion/TOD) | Biblioteca bajo demanda: releases, cola de qBittorrent, `.strm` y streaming por rangos. Opcionalmente Prowlarr/Jackett y Radarr/Sonarr. | Si preparas contenido que no está descargado |

```text
Medusa TV ──▶ Jellyfin ──▶ plugin Medusa ──▶ TOD :8773 ──▶ qBittorrent
                                                             ├── Prowlarr / Jackett
                                                             └── Radarr / Sonarr (API)
```

Medusa **no habla con TOD directamente**. Habla con Jellyfin; el plugin traduce la conversación. El
cliente nunca usa la URL `play` de TOD: reproduce a través de Jellyfin para conservar la
transcodificación, los subtítulos y el progreso de visionado. El flujo completo está en
[`docs/PLAYBACK.md`](docs/PLAYBACK.md).

## Funciones

| Área | Qué incluye |
|---|---|
| **Inicio** | Cabecera a pantalla completa con ClearLogo, géneros favoritos, plataformas, Continuar viendo, Top 10 y carruseles por género. Scrim, paginación y una sola acción primaria. |
| **Descubrir** | Filtros de formato (todo, catálogo, películas, series) y rail de temáticas con filtros **AND**. Cada etiqueta se suma; `BACK` deshace la última. Resultados en el mismo carrusel. |
| **Búsqueda** | Teclado en pantalla adaptado al mando, sugerencias predictivas, resultados instantáneos y búsqueda remota en Jellyfin. |
| **Ficha** | Póster, metadatos editoriales, sinopsis sin panel, estados de progreso/favorito, temporadas y episodios, sagas y recomendaciones. |
| **Reproductor** | Media3 + FFmpeg. DirectPlay, remux, HLS y transcodificación; `.strm`; preparación TOD; subtítulos; pistas de audio; passthrough HDMI; siguiente episodio; Media Segments; frecuencia de refresco automática. |
| **Biblioteca e integraciones** | Room 22 con FTS, caché local, filtros parentales, sagas, sincronización incremental, Trakt, TMDB con clave propia y OTA desde GitHub Releases. |
| **Personalización** | Acentos cian/ámbar/blanco, modo monocromo, estilos de botón, partículas, sonidos, tamaño y fondo de subtítulos y movimiento reducido. |

### Diseño y comportamiento

- **Foco.** Un dock de iconos de 18 dp. Las tarjetas usan borde de 1,5 dp, `zIndex(10f)` y el
  título a plena luminosidad; las vecinas conservan su opacidad. En TV, `BACK` es el retroceso.
- **Rendimiento.** `Modifier.blur()` y `RenderEffect` están prohibidos. Las tarjetas no escalan ni
  atenúan a sus vecinas. El chrome no tiene animaciones autónomas; el hero rota cada 12 s cuando no
  tiene foco y las partículas estacionales son opcionales. El objetivo es 60 fps en hardware de
  entrada.
- **Color y tipografía.** DM Sans en toda la interfaz, tinta `#04060A`, cian de foco `#00D2FF`,
  cobalto/aqua y ámbar/oro para valoraciones. Los tokens viven en `ui/theme/Color.kt` y
  [`DESIGN.md`](DESIGN.md).

## Instalación

### Requisitos

- Android 8.0 o superior (API 26). Android TV, Google TV, Fire TV o tablet.
- Un servidor Jellyfin accesible desde el dispositivo, en la misma red o por internet.
- Espacio para el APK (unos 15 MB) y su instalación. No hace falta cuenta de Medusa ni servicios de
  Google para funcionar.

### Descargar el APK

1. Abre [la última versión](https://github.com/GitCorion/Medusa/releases/latest) en el navegador de
   la tele o descarga el APK en el móvil y envíalo al dispositivo.
2. Permite instalar apps de origen desconocido para el explorador o el receptor que uses.
3. Abre `Medusa.apk` e instala.

También puedes instalarlo por ADB:

```bash
adb install -r Medusa.apk
adb shell am start -n tv.medusa.app/tv.medusa.app.MainActivity
```

> El `applicationId` es `tv.medusa.app` desde 2.4.0. El APK publicado 2.3.12 todavía usa
> `tv.medusa.app.beta`: si vienes de esa versión, instala la nueva y desinstala la antigua una vez.
> Android las trata como apps distintas y los ajustes no se migran.

### Actualizaciones

La app consulta el manifiesto [`docs/api/version.json`](docs/api/version.json) en cada arranque. Si
hay una versión nueva, descarga `Medusa.apk` del GitHub Release, comprueba el **SHA-256**, el tamaño
máximo y que el origen sea GitHub, y lanza el instalador del sistema. Si el manifiesto no se puede
verificar durante siete días, la app bloquea la actualización en lugar de continuar con una versión sin
comprobar. El manifiesto admite actualización forzada y un kill-switch global.

Hay dos canales: **estable** (`latest`) y **beta** (pre-releases con manifiesto propio). El canal se
elige en **Ajustes → Acerca de → Canal**; el valor del APK es solo el valor por defecto. Beta lee
ambos manifiestos y una estable posterior devuelve el equipo al canal estable. Detalles en
[`docs/RELEASE.md`](docs/RELEASE.md).

## Configuración inicial

No hay un asistente de onboarding separado: todo se configura desde **Ajustes**.

1. **Servidor.** Medusa busca Jellyfin en la red local. Si no aparece, introduce la URL completa,
   incluida una subruta como `https://host/jellyfin`. Si el certificado es autofirmado, actívalo de
   forma explícita y fija su SHA-256.
2. **Sesión.** Entra con usuario y contraseña o con **Quick Connect**: la tele muestra un código y
   tú lo autorizas desde Jellyfin en el móvil.
3. **Sincronización.** La primera pasada descarga el catálogo y lo guarda en Room. Las siguientes
   son incrementales y se ejecutan en segundo plano con WorkManager.
4. **Opcionales.** En **Plataformas y cadenas** eliges región y plataformas; en **Biblioteca y
   Trakt** puedes vincular Trakt. La clave de TMDB es opcional y se guarda en el dispositivo.
5. **Listo.** Inicio, Descubrir y Buscar ya trabajan contra tu catálogo local, incluso si el servidor
   tarda en responder.

## Capturas

<p align="center">
  <img src="docs/assets/estudio/captures/descubrir-960.webp" width="49%" alt="Pantalla Descubrir con filtros, rail de temáticas y foco cian">
  <img src="docs/assets/estudio/captures/ficha-960.webp" width="49%" alt="Ficha de una película con póster y metadatos">
</p>
<p align="center">
  <img src="docs/assets/estudio/captures/buscar-960.webp" width="49%" alt="Búsqueda con teclado en pantalla y resultados">
  <img src="docs/assets/estudio/captures/ajustes-960.webp" width="49%" alt="Ajustes con barra lateral y estado del servidor">
</p>

<p align="center"><sub>Capturas reales de la app en un televisor de 1080p. La barra superior admite
solo iconos, solo texto o ambos; las capturas usan el modo solo iconos.
<a href="https://gitcorion.github.io/Medusa/#pantallas">Ver las pantallas y sus decisiones de diseño</a>.</sub></p>

## Arquitectura

Una sola actividad, Navigation Compose y un estado por pantalla. `MediaRepository` es una fachada
delgada sobre repositorios especializados (local, Jellyfin, sincronización, TMDB y Trakt), con
Room 22 + FTS, credenciales cifradas con Android Keystore y trabajo de fondo acotado con WorkManager.

El reproductor vive en `JellyfinPlaybackSourceResolver` + `PlayerViewModel` + `PlayerScreen`; la
cascada de candidatos y el DeviceProfile están cubiertos por pruebas golden. El detalle completo
está en [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) y las invariantes en
[`docs/PLAYBACK.md`](docs/PLAYBACK.md).

## Compilar

### Requisitos

- JDK 17.
- Android SDK 36 (`compileSdk = 36`, `targetSdk = 36`).
- Android Studio reciente o Gradle desde la terminal.

```bash
git clone https://github.com/GitCorion/Medusa.git
cd Medusa
./gradlew :app:assembleDebug
```

El APK de depuración queda en `app/build/outputs/apk/debug/app-debug.apk`.

### Pruebas y análisis

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest   # migraciones de Room, requiere dispositivo
```

### Release firmado

Las claves no se versionan. Puedes usar variables de entorno o un `keystore.properties` local:

```bash
KEYSTORE_PATH=/ruta/release.keystore \
KEYSTORE_PASSWORD=... \
KEY_ALIAS=... \
KEY_PASSWORD=... \
./gradlew :app:assembleRelease
```

Variable opcional: `TRAKT_CLIENT_ID` en build-time. El `TRAKT_CLIENT_SECRET` se configura en Ajustes
y no se embebe. La clave de TMDB **no** se embebe: cada usuario introduce la suya en Ajustes.

La publicación por tag y el workflow [`release.yml`](.github/workflows/release.yml) están descritos
en [`docs/RELEASE.md`](docs/RELEASE.md).

## Documentación

| Documento | Para qué |
|---|---|
| [Estudio de diseño](https://gitcorion.github.io/Medusa/) | La historia visual del producto, con demo de foco, sistema, capturas y registro de decisiones. |
| [`docs/ESTUDIO.md`](docs/ESTUDIO.md) | El mismo estudio en Markdown, con el relato y las capturas. |
| [`DESIGN.md`](DESIGN.md) | Tokens, tipografía, estructura, movimiento y reglas Do/Don't. |
| [`PRODUCT.md`](PRODUCT.md) | Usuarios, propósito, posicionamiento y restricciones de producto. |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Capas, flujos, cachés, workers, pruebas y decisiones técnicas. |
| [`docs/PLAYBACK.md`](docs/PLAYBACK.md) | Orden de candidatos, perfil de dispositivo, audio, subtítulos y errores que no se deben repetir. |
| [`docs/RELEASE.md`](docs/RELEASE.md) | Distribución por GitHub Releases, OTA, firma y secretos. |
| [`docs/CATALOGS.md`](docs/CATALOGS.md) | Catálogos de descubrimiento por región y publicación manual. |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Entorno, estilo, pruebas y checklist de PR. |
| [`SECURITY.md`](SECURITY.md) | Cómo reportar una vulnerabilidad y qué garantiza la app. |
| [`CHANGELOG.md`](CHANGELOG.md) | Cambios de la versión actual y enlace al histórico de Releases. |

## Privacidad y seguridad

Medusa no recopila datos, no muestra publicidad y no incluye analítica ni informes de fallos de
terceros. El permiso de Advertising ID se elimina explícitamente del manifiesto. Solo habla con:

- **Tu Jellyfin** (siempre) y, si usas TOD, el plugin Medusa en el mismo servidor.
- **GitHub raw** para el manifiesto OTA, los catálogos estáticos opcionales y el diccionario de
  etiquetas.
- **TMDB** solo si configuras tu clave, **Trakt** solo si lo vinculas y **YouTube nocookie** solo al
  abrir un tráiler.

Credenciales AES/GCM con Android Keystore, TLS con pin SHA-256 para certificados autofirmados y OTA
con origen GitHub, 150 MB máximo, SHA-256 obligatorio y bloqueo ante fallos de verificación. Los códigos QR se generan en el
dispositivo. Para reportar una vulnerabilidad, sigue [`SECURITY.md`](SECURITY.md) y no abras un issue
público con tokens, contraseñas, IPs privadas ni capturas de tu servidor.

## Problemas frecuentes

| Síntoma | Qué probar |
|---|---|
| No encuentra el servidor | Comprueba que estén en la misma red, que Jellyfin escuche en la interfaz correcta y que el firewall no bloquee el descubrimiento. Introduce la URL a mano. |
| Error de certificado | Si usas un certificado autofirmado, actívalo por servidor y fija el SHA-256. Un certificado caducado también falla. |
| Un `.strm` no arranca | La tele nunca debe intentar la IP interna del archivo. Revisa que Jellyfin resuelva el destino y lee [`docs/PLAYBACK.md`](docs/PLAYBACK.md). |
| Audio DTS/TrueHD en silencio | Activa passthrough HDMI si tu receptor lo soporta o deja que Jellyfin transcodifique. No hay decodificación software de DTS/TrueHD. |
| No aparecen subtítulos | Comprueba el endpoint de subtítulos del servidor y que la pista exista en el MediaSource. |
| La actualización no aparece | Verifica que el `versionCode` sea mayor, que el manifiesto del canal esté actualizado y que el APK sea el asset `Medusa.apk`. |
| Tirones en la interfaz | Desactiva partículas y sonidos ambientales, revisa el modo de movimiento reducido del sistema y evita servidores que fuerzan transcodificación 4K. |

Si nada de eso encaja, abre un [issue](https://github.com/GitCorion/Medusa/issues) con modelo de
dispositivo, versión de Android, versión de la app, versión de Jellyfin y una descripción del flujo.
Nunca incluyas tokens ni credenciales.

## Contribuir

Las contribuciones son bienvenidas, sobre todo si vienen con una prueba que describa el fallo. Antes
de abrir un PR:

1. Lee [`CONTRIBUTING.md`](CONTRIBUTING.md).
2. Ejecuta `./gradlew :app:testDebugUnitTest` y `./gradlew :app:lintDebug`.
3. No rompas las invariantes de reproducción de [`docs/PLAYBACK.md`](docs/PLAYBACK.md).
4. Mantén las dos traducciones (`values/strings.xml` y `values-en/strings.xml`).
5. Respeta las reglas visuales: cero blur, cero escala por tarjeta y un solo foco activo.

Los commits siguen un estilo convencional en español, por ejemplo
`fix(player): reintentar audio sin perder la posición`.

## Créditos

[Medusa-Jellyfin-Plugin](https://github.com/GitCorion/Medusa-Jellyfin-Plugin) (GPL-3.0) y
[TOD](https://github.com/GitCorion/TOD) (MIT) completan el soporte opcional bajo demanda. El resto del
ecosistema es [Jellyfin](https://jellyfin.org), [TMDB](https://www.themoviedb.org),
[Trakt](https://trakt.tv), [Android Media3](https://developer.android.com/media/media3),
[Kotlin](https://kotlinlang.org), [Compose](https://developer.android.com/compose),
[Hilt](https://dagger.dev/hilt/), [Room](https://developer.android.com/training/data-storage/room),
[Coil](https://coil-kt.github.io/coil/), [Retrofit](https://square.github.io/retrofit/),
[OkHttp](https://square.github.io/okhttp/) y
[WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager).

*This product uses the TMDB API but is not endorsed or certified by TMDB.* DM Sans se distribuye
bajo SIL Open Font License. El cartel de *Night of the Living Dead* (1968) es de dominio público; el
resto de pósters y capturas pertenecen a sus titulares y se muestran con fines de documentación.

## Licencia

Medusa TV se distribuye bajo **GNU General Public License v3.0**. Consulta [`LICENSE`](LICENSE).

Medusa es un cliente independiente y no está afiliado ni respaldado por Jellyfin, TMDB o Trakt.
Jellyfin es una marca de sus titulares. El plugin Medusa se distribuye bajo GPL-3.0 y TOD bajo MIT,
cada uno en su repositorio.

<div align="center">
  <p><strong>Hecho para Jellyfin. Sin nube, sin cuentas de Medusa y sin rastrear lo que ves.</strong></p>
</div>
