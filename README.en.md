<div align="center">
  <img src="app/src/main/res/drawable/ic_medusa_header.png" alt="Medusa TV" width="360">
  <p><strong>Your Jellyfin library, three meters away.</strong><br>
  A native Android TV and tablet client built with Kotlin and Compose TV.<br>
  A fully native interface with no Firebase, no analytics and no Play Store.</p>

  [![Release](https://img.shields.io/github/v/release/GitCorion/Medusa?style=flat-square&color=00D2FF&label=release)](https://github.com/GitCorion/Medusa/releases)
  [![Android](https://img.shields.io/badge/Android-8.0%2B-00D2FF?style=flat-square)](#requirements)
  [![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-00D2FF?style=flat-square)](https://kotlinlang.org)
  [![Compose TV](https://img.shields.io/badge/Compose%20TV-1.0.0--alpha07-00D2FF?style=flat-square)](https://developer.android.com/training/tv/use-apis/compose)
  [![License](https://img.shields.io/badge/license-GPL--3.0-00D2FF?style=flat-square)](LICENSE)

  <p>
    <a href="https://github.com/GitCorion/Medusa/releases/latest/download/Medusa.apk"><strong>Download the APK</strong></a>
    &nbsp;·&nbsp;
    <a href="https://gitcorion.github.io/Medusa/"><strong>Design study</strong></a>
    &nbsp;·&nbsp;
    <a href="README.md">Español</a>
  </p>
</div>

![Medusa TV home screen on a 1080p television](docs/assets/estudio/captures/inicio-1600.webp)

<p align="center"><sub>Home on a 1080p television.</sub></p>

**Contents:** [What it is](#what-it-is) · [Ecosystem](#ecosystem) · [Features](#features) · [Install](#install) · [Initial setup](#initial-setup) · [Screenshots](#screenshots) · [Architecture](#architecture) · [Build](#build) · [Documentation](#documentation) · [Privacy](#privacy-and-security) · [Troubleshooting](#troubleshooting)

## What it is

Medusa TV is a native Jellyfin client for the living room. It turns your self-hosted library into a
ten-foot interface built for a couch, a five-key remote and an OLED screen: full-bleed artwork,
high-contrast focus and no chrome competing with the content.

It is not a web wrapper or a hosted catalog. The app talks directly to **your** Jellyfin server,
stores the catalog on the device and only reaches the internet for metadata, optional static
catalogs, Trakt (if you link it) and signed updates from GitHub Releases.

## Status

- **Current stable:** `2.4.1` (`versionCode 1000167`).
- **Platform:** Android 8.0+ (API 26). Android TV, Google TV, Fire TV and tablet.
- **OTA channels:** stable and beta, with a selector in **Settings → About → Channel**.
- **Distribution:** signed APK on GitHub Releases. No Medusa account, Firebase or Play Store.

## Ecosystem

Medusa is the client piece. The other two are optional and only needed if you want catalog-on-demand
playback.

| Piece | Role | When you need it |
|---|---|---|
| [Medusa](https://github.com/GitCorion/Medusa) (this repository) | Native Android TV and tablet client. Plays through Jellyfin. | Always |
| [Medusa-Jellyfin-Plugin](https://github.com/GitCorion/Medusa-Jellyfin-Plugin) | Jellyfin↔TOD bridge: status, preparation and lease. It appears in Jellyfin as **Medusa**. | If you use TOD |
| [TOD](https://github.com/GitCorion/TOD) | Catalog on demand: sources, qBittorrent, `.strm` and range proxy. Optionally Prowlarr/Jackett and Radarr/Sonarr. | If you prepare undownloaded content |

```text
Medusa TV ──▶ Jellyfin ──▶ Medusa plugin ──▶ TOD :8773 ──▶ qBittorrent
                                                             ├── Prowlarr / Jackett
                                                             └── Radarr / Sonarr (API)
```

Medusa **never talks to TOD directly**. It talks to Jellyfin; the plugin translates the
conversation. The client never uses the proxy `play` URL: it plays through Jellyfin so transcoding,
subtitles and watch progress keep working. The full flow is in [`docs/PLAYBACK.md`](docs/PLAYBACK.md).

## Features

| Area | What is included |
|---|---|
| **Home** | Full-bleed hero with ClearLogo, favorite genres, platforms, Continue watching, Top 10 and genre carousels. Scrim, pagination and one primary action. |
| **Discover** | Format filters (all, catalog, movies, series) and a theme rail with **AND** filters. Each tag adds a filter; `BACK` removes the last one. Results update in the same carousel. |
| **Search** | Ten-foot keyboard, predictive suggestions, instant results and remote Jellyfin search. |
| **Detail** | Poster, editorial metadata, container-free synopsis, progress/favorite states, seasons and episodes, sagas and recommendations. |
| **Player** | Media3 + FFmpeg. DirectPlay, remux, HLS and transcoding; `.strm`; TOD preparation; subtitles; audio tracks; HDMI passthrough; next episode; Media Segments; automatic refresh-rate switching. |
| **Library and integrations** | Room 22 with FTS, local cache, parental filters, sagas, incremental sync, Trakt, TMDB with your own key and OTA from GitHub Releases. |
| **Personalization** | Cyan/amber/white accents, monochrome mode, button styles, particles, sounds, subtitle size and background, and reduced motion. |

### Design and behavior

- **Focus.** An 18 dp icon dock. Cards use a 1.5 dp border, `zIndex(10f)` and the title at full
  brightness; neighbours keep their opacity. On TV, `BACK` is the way back.
- **Performance.** `Modifier.blur()` and `RenderEffect` are banned. Cards neither scale nor dim
  their neighbours. The chrome has no autonomous animations; the hero rotates every 12 s when
  unfocused and seasonal particles are optional. The target is 60 fps on entry-level hardware.
- **Color and type.** DM Sans across the interface, ink `#04060A`, focus cyan `#00D2FF`,
  cobalt/aqua and amber/gold for ratings. Tokens live in `ui/theme/Color.kt` and
  [`DESIGN.md`](DESIGN.md).

## Install

### Requirements

- Android 8.0 or newer (API 26). Android TV, Google TV, Fire TV or tablet.
- A Jellyfin server reachable from the device, on the same network or over the internet.
- Space for the APK (about 15 MB) and its installation. No Medusa account or Google services are
  required to run the app.

### Download the APK

1. Open [the latest release](https://github.com/GitCorion/Medusa/releases/latest) in the TV browser,
   or download the APK on your phone and send it to the device.
2. Allow installing apps from unknown sources for the file manager or receiver you use.
3. Open `Medusa.apk` and install it.

You can also sideload it with ADB:

```bash
adb install -r Medusa.apk
adb shell am start -n tv.medusa.app/tv.medusa.app.MainActivity
```

> The `applicationId` is `tv.medusa.app` from 2.4.0. The published 2.3.12 APK still uses
> `tv.medusa.app.beta`: if you come from it, install the new build and uninstall the old one once.
> Android treats them as different apps and settings are not migrated.

### Updates

The app reads the [`docs/api/version.json`](docs/api/version.json) manifest on startup. When a new
version exists, it downloads `Medusa.apk` from the GitHub Release, verifies the **SHA-256**, the
maximum size and that the origin is GitHub, then launches the system installer. If the manifest
cannot be verified for seven days, the app **fails closed** instead of running an unverified
version. The manifest supports forced updates and a global kill switch.

There are two channels: **stable** (`latest`) and **beta** (pre-releases with their own manifest).
The channel is chosen in **Settings → About → Channel**; the APK value is only the default. Beta
reads both manifests and a later stable returns the device to the stable channel. Details in
[`docs/RELEASE.md`](docs/RELEASE.md).

## Initial setup

There is no separate onboarding wizard: everything is configured in **Settings**.

1. **Server.** Medusa looks for Jellyfin on the local network. If it does not appear, enter the full
   URL, including a subpath such as `https://host/jellyfin`. For a self-signed certificate, enable
   it explicitly and pin its SHA-256.
2. **Sign in.** Use a username and password, or **Quick Connect**: the TV shows a code and you
   authorize it from Jellyfin on your phone.
3. **Sync.** The first pass downloads the catalog into Room. Later passes are incremental and run in
   the background with WorkManager.
4. **Optional.** In **Platforms and channels** you choose region and platforms; in **Library and
   Trakt** you can link Trakt. The TMDB key is optional and stays on the device.
5. **Done.** Home, Discover and Search now work against your local catalog, even if the server is
   slow to respond.

## Screenshots

<p align="center">
  <img src="docs/assets/estudio/captures/descubrir-960.webp" width="49%" alt="Discover screen with filters, theme rail and cyan focus">
  <img src="docs/assets/estudio/captures/ficha-960.webp" width="49%" alt="Movie detail screen with poster and metadata">
</p>
<p align="center">
  <img src="docs/assets/estudio/captures/buscar-960.webp" width="49%" alt="Search with on-screen keyboard and results">
  <img src="docs/assets/estudio/captures/ajustes-960.webp" width="49%" alt="Settings with sidebar and server status">
</p>

<p align="center"><sub>Real captures from the app on a 1080p television. The top bar supports icons
only, text only or both; the captures use icons only.
<a href="https://gitcorion.github.io/Medusa/#pantallas">See the screens and the design decisions behind them</a>.</sub></p>

## Architecture

One activity, Navigation Compose and one state per screen. `MediaRepository` is a thin facade over
specialized repositories (local, Jellyfin, sync, TMDB and Trakt), with Room 22 + FTS, credentials
encrypted with Android Keystore and bounded background work through WorkManager.

Playback lives in `JellyfinPlaybackSourceResolver` + `PlayerViewModel` + `PlayerScreen`; the
candidate cascade and DeviceProfile are covered by golden tests. Full detail is in
[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) and the invariants are in
[`docs/PLAYBACK.md`](docs/PLAYBACK.md).

## Build

### Requirements

- JDK 17.
- Android SDK 36 (`compileSdk = 36`, `targetSdk = 36`).
- A recent Android Studio or Gradle from the terminal.

```bash
git clone https://github.com/GitCorion/Medusa.git
cd Medusa
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Tests and analysis

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
./gradlew :app:connectedDebugAndroidTest   # Room migrations, requires a device
```

### Signed release

Keys are not versioned. Use environment variables or a local `keystore.properties`:

```bash
KEYSTORE_PATH=/path/to/release.keystore \
KEYSTORE_PASSWORD=... \
KEY_ALIAS=... \
KEY_PASSWORD=... \
./gradlew :app:assembleRelease
```

Optional variable: `TRAKT_CLIENT_ID` at build time. `TRAKT_CLIENT_SECRET` is configured in Settings
and is not embedded. The TMDB key is **not** embedded: each user enters their own in Settings.

Publishing by tag and the [`release.yml`](.github/workflows/release.yml) workflow are described in
[`docs/RELEASE.md`](docs/RELEASE.md).

## Documentation

| Document | Purpose |
|---|---|
| [Design study](https://gitcorion.github.io/Medusa/) | The visual story of the product, with a focus demo, system, screens and decision log. |
| [`docs/ESTUDIO.md`](docs/ESTUDIO.md) | The same study in Markdown, with the story and captures. |
| [`DESIGN.md`](DESIGN.md) | Tokens, typography, structure, motion and Do/Don't rules. |
| [`PRODUCT.md`](PRODUCT.md) | Users, purpose, positioning and product constraints. |
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | Layers, flows, caches, workers, tests and technical decisions. |
| [`docs/PLAYBACK.md`](docs/PLAYBACK.md) | Candidate order, device profile, audio, subtitles and mistakes not to repeat. |
| [`docs/RELEASE.md`](docs/RELEASE.md) | GitHub Releases distribution, OTA, signing and secrets. |
| [`docs/CATALOGS.md`](docs/CATALOGS.md) | Regional discovery catalogs and manual publishing. |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Environment, style, tests and PR checklist. |
| [`SECURITY.md`](SECURITY.md) | How to report a vulnerability and what the app guarantees. |
| [`CHANGELOG.md`](CHANGELOG.md) | Current release changes and a link to the release history. |

## Privacy and security

Medusa does not collect data, show ads or include analytics or third-party crash reporting. The
Advertising ID permission is explicitly removed from the manifest. It only talks to:

- **Your Jellyfin** (always) and, if you use TOD, the Medusa plugin on the same server.
- **GitHub raw** for the OTA manifest, optional static catalogs and the tag dictionary.
- **TMDB** only if you configure your key, **Trakt** only if you link it and **YouTube nocookie** only
  when you open a trailer.

Credentials use AES/GCM with Android Keystore, TLS uses a SHA-256 pin for self-signed certificates,
and OTA is GitHub-only with a 150 MB maximum, mandatory SHA-256 and fail-closed behaviour. QR codes
are generated on the device. To report a vulnerability, follow [`SECURITY.md`](SECURITY.md) and do
not open a public issue with tokens, passwords, private IPs or captures of your server.

## Troubleshooting

| Symptom | What to try |
|---|---|
| Server not found | Check that both are on the same network, that Jellyfin listens on the right interface and that the firewall does not block discovery. Enter the URL manually. |
| Certificate error | For a self-signed certificate, enable it per server and pin the SHA-256. An expired certificate also fails. |
| A `.strm` does not start | The TV must never try the file's internal IP. Check that Jellyfin resolves the destination and read [`docs/PLAYBACK.md`](docs/PLAYBACK.md). |
| DTS/TrueHD audio is silent | Enable HDMI passthrough if your receiver supports it, or let Jellyfin transcode. There is no software DTS/TrueHD decoding. |
| Subtitles do not appear | Check the server subtitle endpoint and that the track exists in the MediaSource. |
| The update does not appear | Check that the `versionCode` is higher, the channel manifest is current and the APK is the `Medusa.apk` asset. |
| UI stutters | Disable particles and ambient sounds, check the system's reduced-motion setting and avoid servers forcing 4K transcoding. |

If none of that helps, open an [issue](https://github.com/GitCorion/Medusa/issues) with device model,
Android version, app version, Jellyfin version and a description of the flow. Never include tokens
or credentials.

## Contributing

Contributions are welcome, especially with a test that describes the bug. Before opening a PR:

1. Read [`CONTRIBUTING.md`](CONTRIBUTING.md).
2. Run `./gradlew :app:testDebugUnitTest` and `./gradlew :app:lintDebug`.
3. Do not break the playback invariants in [`docs/PLAYBACK.md`](docs/PLAYBACK.md).
4. Keep both translations (`values/strings.xml` and `values-en/strings.xml`).
5. Respect the visual rules: zero blur, zero per-card scaling and one active focus at a time.

Commits use a conventional style, for example
`fix(player): retry audio without losing the position`.

## Credits

[Medusa-Jellyfin-Plugin](https://github.com/GitCorion/Medusa-Jellyfin-Plugin) (GPL-3.0) and
[TOD](https://github.com/GitCorion/TOD) (MIT) complete catalog-on-demand playback. The rest of the
ecosystem is [Jellyfin](https://jellyfin.org), [TMDB](https://www.themoviedb.org),
[Trakt](https://trakt.tv), [Android Media3](https://developer.android.com/media/media3),
[Kotlin](https://kotlinlang.org), [Compose](https://developer.android.com/compose),
[Hilt](https://dagger.dev/hilt/), [Room](https://developer.android.com/training/data-storage/room),
[Coil](https://coil-kt.github.io/coil/), [Retrofit](https://square.github.io/retrofit/),
[OkHttp](https://square.github.io/okhttp/) and
[WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager).

*This product uses the TMDB API but is not endorsed or certified by TMDB.* DM Sans is distributed
under the SIL Open Font License. The *Night of the Living Dead* (1968) poster is public domain; the
remaining posters and captures belong to their owners and are shown for documentation purposes.

## License

Medusa TV is distributed under the **GNU General Public License v3.0**. See [`LICENSE`](LICENSE).

Medusa is an independent client and is not affiliated with or endorsed by Jellyfin, TMDB or Trakt.
Jellyfin is a trademark of its owners. The Medusa plugin is distributed under GPL-3.0 and TOD
under MIT, each in its own repository.

<div align="center">
  <p><strong>Built for Jellyfin. No cloud, no Medusa accounts and no tracking what you watch.</strong></p>
</div>
