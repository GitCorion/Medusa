<div align="center">
  <img src="app/src/main/res/drawable/ic_medusa_header.png" alt="Medusa TV" width="320">
  <p>Cliente nativo de Jellyfin para Android TV y tablet, desarrollado en Kotlin y Compose TV.</p>

  [![Release](https://img.shields.io/github/v/release/GitCorion/Medusa?style=flat-square&color=00D2FF&label=release)](https://github.com/GitCorion/Medusa/releases)
  [![Android](https://img.shields.io/badge/Android-8.0%2B-00D2FF?style=flat-square)](#instalación)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-00D2FF?style=flat-square)](https://kotlinlang.org)
  [![Compose TV](https://img.shields.io/badge/Compose%20TV-1.0.0--alpha07-00D2FF?style=flat-square)](https://developer.android.com/training/tv/use-apis/compose)
  [![License](https://img.shields.io/badge/license-GPL--3.0-00D2FF?style=flat-square)](LICENSE)

  <p>
    <a href="https://github.com/GitCorion/Medusa/releases/latest/download/Medusa.apk"><strong>Descargar APK</strong></a>
    &nbsp;·&nbsp;
    <a href="https://gitcorion.github.io/Medusa/"><strong>Sistema de diseño</strong></a>
    &nbsp;·&nbsp;
    <a href="README.en.md">English</a>
  </p>
</div>

![Inicio de Medusa TV en un televisor de 1080p](docs/assets/estudio/captures/inicio-1600.webp)

Medusa TV es un cliente nativo de Jellyfin para Android TV, Google TV, Fire TV
y tablet. Sincroniza el catálogo en una base local (Room) para navegar sin
latencia de red y reproduce mediante Media3 y FFmpeg.

## Ecosistema

Medusa funciona de forma independiente con cualquier servidor Jellyfin. Si
además utilizas [TOD](https://github.com/GitCorion/TOD) para contenido bajo
demanda (`.strm`), el plugin para Jellyfin enlaza ambos servicios:

| Pieza | Rol | Cuándo hace falta |
|---|---|---|
| [Medusa](https://github.com/GitCorion/Medusa) (este repositorio) | Cliente nativo para Android TV y tablet. Reproduce desde Jellyfin. | Siempre |
| [Medusa-Jellyfin-Plugin](https://github.com/GitCorion/Medusa-Jellyfin-Plugin) | Puente Jellyfin↔TOD: estado, preparación, lease y negociación de versiones. | Solo si usas TOD |
| [TOD](https://github.com/GitCorion/TOD) | Biblioteca bajo demanda: releases, cola de qBittorrent, `.strm` y streaming por rangos. | Solo para contenido bajo demanda |

```text
Medusa TV ──▶ Jellyfin ──▶ plugin Medusa ──▶ TOD :8773 ──▶ qBittorrent
                                                             ├── Prowlarr / Jackett
                                                             └── Radarr / Sonarr (API)
```

Todas las peticiones de reproducción pasan por Jellyfin para conservar
transcodificación, subtítulos y progreso visto (ver [`docs/PLAYBACK.md`](docs/PLAYBACK.md)).

## Funcionalidades

| Área | Detalle |
|---|---|
| **Inicio** | Cabecera con ClearLogo, géneros favoritos, plataformas, Continuar viendo, Top 10 y carruseles por género. |
| **Descubrir** | Filtros de formato y rail de temáticas combinables (`AND`; `BACK` deshace el último filtro). |
| **Búsqueda** | Teclado en pantalla adaptado al mando, sugerencias locales y búsqueda remota en Jellyfin. |
| **Ficha** | Póster, metadatos, sinopsis, progreso/favoritos, temporadas y episodios, sagas y recomendaciones. |
| **Reproductor** | Media3 + FFmpeg: DirectPlay, remux, HLS, transcodificación, `.strm`, preparación TOD, subtítulos, pistas de audio, passthrough HDMI, Media Segments y cambio automático de tasa de refresco. |
| **Biblioteca e integraciones** | Caché local en Room, filtros parentales, sincronización incremental, Trakt, TMDB (clave propia) y actualizaciones OTA desde GitHub Releases. |
| **Personalización** | Colores de acento, modo monocromo, estilos de botón, partículas opcionales, tamaño/fondo de subtítulos y reducción de movimiento. |

## Capturas

<p align="center">
  <img src="docs/assets/estudio/captures/descubrir-960.webp" width="49%" alt="Pantalla Descubrir con filtros, rail de temáticas y foco">
  <img src="docs/assets/estudio/captures/ficha-960.webp" width="49%" alt="Ficha de una película con póster y metadatos">
</p>
<p align="center">
  <img src="docs/assets/estudio/captures/buscar-960.webp" width="49%" alt="Búsqueda con teclado en pantalla y resultados">
  <img src="docs/assets/estudio/captures/ajustes-960.webp" width="49%" alt="Ajustes con barra lateral y estado del servidor">
</p>

## Instalación

Requiere **Android 8.0+ (API 26)** y un servidor Jellyfin accesible (HTTP/HTTPS,
LAN, subruta o certificado autofirmado con pin SHA-256).

- **APK:** descarga `Medusa.apk` desde [GitHub Releases](https://github.com/GitCorion/Medusa/releases/latest) e instálalo en el dispositivo o por ADB:
  ```bash
  adb install -r Medusa.apk
  ```
- **Inicio de sesión:** en **Ajustes**, selecciona el servidor detectado (o introduce la URL) y autentícate con usuario/contraseña o **Quick Connect**.
- **Actualizaciones OTA:** comprueba GitHub Releases al arrancar con verificación SHA-256; los canales **estable** y **beta** se cambian en **Ajustes → Acerca de → Canal**.

## Compilación

Requiere **JDK 17** y **Android SDK 36**.

```bash
git clone https://github.com/GitCorion/Medusa.git
cd Medusa
./gradlew :app:assembleDebug          # APK en app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest      # Tests unitarios
./gradlew :app:lintDebug              # Lint
```

Para firmar una release (`assembleRelease`), pasa `KEYSTORE_PATH`,
`KEYSTORE_PASSWORD`, `KEY_ALIAS` y `KEY_PASSWORD` por entorno o `keystore.properties`
(ver [`docs/RELEASE.md`](docs/RELEASE.md)).

## Documentación

| Documento | Contenido |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Capas, repositorios, Room, workers y pruebas |
| [`docs/PLAYBACK.md`](docs/PLAYBACK.md) | Candidatos de reproducción, `DeviceProfile`, audio y subtítulos |
| [`docs/RELEASE.md`](docs/RELEASE.md) | Publicación en GitHub Releases, OTA y firma |
| [`docs/ESTUDIO.md`](docs/ESTUDIO.md) / [`DESIGN.md`](DESIGN.md) | Sistema visual, tokens y decisiones de interfaz |
| [`docs/CATALOGS.md`](docs/CATALOGS.md) | Catálogos estáticos de descubrimiento por región |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Guía de contribución y estilo de commits |
| [`SECURITY.md`](SECURITY.md) | Política de seguridad y reporte de vulnerabilidades |
| [`CHANGELOG.md`](CHANGELOG.md) | Historial de cambios |

## Problemas frecuentes

| Síntoma | Comprobación |
|---|---|
| No detecta el servidor | Verifica que dispositivo y servidor estén en la misma red y sin bloqueo de firewall; introduce la URL manualmente. |
| Error de certificado | Habilita certificados autofirmados para ese servidor y fija su huella SHA-256. |
| Un `.strm` no inicia | Comprueba que el televisor no intenta resolver una IP interna del servidor (ver [`docs/PLAYBACK.md`](docs/PLAYBACK.md)). |
| Audio DTS/TrueHD sin sonido | Activa passthrough HDMI si tu equipo lo soporta o deja que Jellyfin transcodifique. |
| Sin subtítulos | Comprueba que la pista exista en el `MediaSource` y que el endpoint de subtítulos responda. |

## Licencia

**GNU General Public License v3.0** ([`LICENSE`](LICENSE)).
*This product uses the TMDB API but is not endorsed or certified by TMDB.*
DM Sans se distribuye bajo SIL Open Font License.
