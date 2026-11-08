# Reproducción “todo terreno” — memoria técnica

Esta aplicación **tiene que reproducir todo** lo que Jellyfin pueda servir, en **cualquier
servidor** y con **cualquier configuración** del usuario:

- `.strm` normales (destino público).
- `.strm` cuyo destino es una IP interna de Docker o un proxy interno.
- Montajes rclone, discos locales, bibliotecas normales y versiones alternativas.
- DirectPlay, DirectStream y transcodificación HLS.
- Servidores HTTP/HTTPS, LAN, remotos, con subruta, IPv6 o certificado autofirmado.
- Audio AAC/MP3/FLAC/Opus/AC3/EAC3 y DTS/TrueHD mediante passthrough o transcodificación.

## 1. Arquitectura de reproducción

La resolución de fuentes vive en un único sitio:

- `JellyfinPlaybackSourceResolver` — construye la lista de candidatos y las URLs de subtítulos.
- `PlayerViewModel.loadStreamUrl()` — negocia PlaybackInfo, arranca la sesión y monta el player.
- `PlayerViewModel.retryWithTranscoding()` — renegocia audio/versión y reintentos de decoder.
- `PlayerScreen` — prueba los candidatos en orden y cae a transcodificación completa si todos fallan.

**Nunca** se debe volver a repartir esta lógica entre el ViewModel y el reproductor, ni
duplicarla en los tests.

## 2. Orden de candidatos

### 2.1. `.strm` (incluye Docker, proxy interno y rclone)

1. `Videos/{id}/stream?static=true&MediaSourceId=...`
   - Es el candidato principal.
   - Jellyfin resuelve el `.strm` **en el servidor**.
   - Funciona aunque el destino sea `172.21.x.x`, `10.x.x.x`, `192.168.x.x` o `.local`.
   - Devuelve HTTP 206 + `Accept-Ranges`, necesario para seek nativo.
2. `Videos/{id}/stream?MediaSourceId=...` (remux).
3. `DirectStreamUrl` que entregue PlaybackInfo.
4. `TranscodingUrl` que entregue PlaybackInfo.
5. `stream?static=true` de las versiones alternativas.

No se usa el `Path` del MediaSource ni se construyen `master.m3u8`/`main.m3u8`. Todo el
tráfico de vídeo pasa por los endpoints oficiales de Jellyfin.

### 2.2. Contenido normal (MKV, MP4, TS, etc.)

1. `DirectStreamUrl` de Jellyfin.
2. `TranscodingUrl` de Jellyfin.
3. `stream?static=true&MediaSourceId=...`.
4. `stream?MediaSourceId=...`.
5. `stream.{container}?static=true&MediaSourceId=...`.

Si todo falla, `PlayerScreen` pide transcodificación completa usando la `TranscodingUrl` del
servidor. La lista **nunca puede quedar vacía** si hay un ítem reproducible.

## 3. Perfil de dispositivo

`JellyfinDeviceProfileBuilder` declara en `DirectPlayProfiles` todos los contenedores que
Media3 puede abrir:

```
mkv, mp4, m4v, mov, webm, ts, m2ts, mts, m2t,
flv, 3gp, 3g2, ogv, ogg, mpg, mpeg, ps, vob,
strm, m3u8, mpd
```

- `strm` es obligatorio. Sin él, Jellyfin deja de ofrecer DirectPlay para `.strm`.
- AVI/ASF/WMV se dejan **fuera** del DirectPlay porque Media3 no tiene extractor para ellos.
  Jellyfin los transcodifica y la app los reproduce igualmente.
- El perfil de transcodificación es HLS/TS con H.264 + AAC como salida universal.

### 3.1. Serialización del DeviceProfile (crítico)

Kotlinx Serialization **omite por defecto** los campos cuyo valor coincide con su valor por
defecto. Si eso pasa en el DeviceProfile, Jellyfin aplica sus propios defaults:

- `DirectPlayProfiles[].Type` → `Audio` en vez de `Video`.
- `PlayableMediaTypes` → `[]`.
- `TranscodingProfiles[]` → `Container: ""`, `Type: "Audio"`, códecs vacíos.
- `SubtitleProfiles[].Method` → ausente.
- `MaxStreamingBitrate` / `MaxStaticBitrate` → ausentes o `0`.

Por eso **todas las llamadas Retrofit de Jellyfin usan `NetworkClientFactory.jellyfinJson`**,
que tiene:

```kotlin
encodeDefaults = true
explicitNulls = false
```

Y se construyen con `NetworkClientFactory.createJellyfinService(...)`, no con
`createService(...)`. El JSON compartido (Trakt/TMDB/updater) no se toca.

El test `JellyfinDeviceProfileGoldenTest` serializa el perfil real generado por
`JellyfinDeviceProfileBuilder.buildProfileFromCodecs(...)` con ese mismo `Json` y verifica
campo por campo. El test `JellyfinSerializationWiringTest` captura con MockWebServer el body
real de `POST /Sessions/Capabilities/Full` y de los eventos de sesión.

## 4. Audio

- Baseline declarada siempre: `aac`, `mp3`, `flac`, `opus`, `ac3`, `eac3`.
- `FFMPEG_SURROUND_AUDIO_CODECS` solo contiene `ac3` y `eac3`.
- `dts`/`truehd` solo se declaran si `AudioCapabilities` confirma passthrough real.
- DTS/TrueHD no se decodifican en software: si no hay passthrough, los transcodifica Jellyfin.
- Fallo de decoder:
  - audio (`ERROR_CODE_AUDIO_TRACK_INIT_FAILED` o causa de audio) → reintento único de audio
    en servidor.
  - vídeo o causa indeterminada (`ERROR_CODE_DECODER_INIT_FAILED`) → transcodificación
    completa, sin gastar el presupuesto de audio.
- Los reintentos mantienen el mismo `PlaySessionId`, la posición actual y usan
  `enableDirectPlay=false` y `allowAudioStreamCopy=false`.

## 5. Subtítulos

- Se usa `DeliveryUrl` si Jellyfin la entrega.
- Si no existe, se usa el endpoint oficial
  `/Videos/{id}/{mediaSourceId}/Subtitles/{index}/Stream.{codec}?api_key=...`.
- También aplica a `.strm` con subtítulos sidecar.

## 6. Cualquier servidor

- HTTP y HTTPS en cualquier IP/host, sin rangos LAN hardcodeados.
- Certificado autofirmado: opt-in explícito por servidor + pin SHA-256.
- Sin CAs de usuario.
- Subrutas tipo `https://host/jellyfin` soportadas por `combineBaseUrlAndPath`.
- IPv6, redirecciones y CDNs soportados por OkHttp.
- `PlaySessionId`, `api_key`, `MediaSourceId` y `AudioStreamIndex` se añaden sin duplicar y
  sin distinguir mayúsculas.

## 7. Errores que NO se deben volver a cometer

### 7.1. Reproducción `.strm` (versión 2.2.89)

Se rompió `.strm` por dos cambios:

1. Se eliminó `strm` de `DirectPlayProfiles`.
2. Se redujo la reproducción a **solo** `DirectStreamUrl` y `TranscodingUrl`, borrando la
   detección `isStrm`, `stream?static=true`, el remux y las versiones alternativas.

Consecuencia: Jellyfin no devuelve `DirectStreamUrl`/`TranscodingUrl` para un `.strm` en
DirectPlay, la lista de candidatos quedaba vacía y se ejecutaba `failFatalPlayback()`.

### 7.2. DeviceProfile corrupto (versión 2.2.90)

El `Json` de `NetworkClientFactory` no tenía `encodeDefaults = true`. Los objetos Kotlin eran
correctos, pero al serializar se omitían `Type`, `PlayableMediaTypes`, `Container`, códecs y
bitrates porque coincidían con sus defaults. Jellyfin los sustituyó por `Audio`, `[]` y
cadenas vacías.

Consecuencia: el perfil enviado estaba corrupto y cualquier transcodificación podía fallar
aunque el `.strm` funcionara por el endpoint `static=true`.

**Reglas para no repetirlo:**

- No reducir nunca la reproducción a solo las URLs de PlaybackInfo.
- No quitar `strm` del perfil de contenedores.
- No borrar `stream?static=true`, el remux ni las versiones alternativas.
- No tratar una IP Docker/LAN de un `.strm` como alcanzable desde el TV.
- No dejar que una lista vacía llegue a `failFatalPlayback()` sin haber probado los fallbacks.
- No usar `createService(...)` para Jellyfin: usar `createJellyfinService(...)`.
- No quitar `encodeDefaults = true` de `jellyfinJson`.
- No duplicar la lógica del resolvedor en los tests: los tests deben llamar al código real.
- No construir `master.m3u8`/`main.m3u8` ni usar `MediaSourceInfo.Path` como atajo.

## 8. Verificación mínima antes de publicar

```bash
./gradlew :app:testPlayDebugUnitTest
./gradlew :app:lintPlayRelease
./gradlew :app:assemblePlayRelease :app:assembleBetaRelease :app:bundlePlayRelease
```

Los tests cubren:

- `PlayerResolutionTest`: `.strm` con destino Docker interno → proxy `static=true` primero e
  IP interna nunca directa; rclone/DirectPlay sin URLs de servidor; transcoding; reintento
  forzado; `PlaySessionId`, token, `MediaSourceId` y `AudioStreamIndex`; subpath `/jellyfin`;
  subtítulos con y sin `DeliveryUrl`; perfil con `strm` y sin AVI.
- `JellyfinDeviceProfileGoldenTest`: serializa el perfil real del builder y verifica
  `PlayableMediaTypes`, `Type: "Video"`, contenedores, códecs, bitrates y subtítulos.
- `JellyfinSerializationWiringTest`: MockWebServer captura el body real de
  `POST /Sessions/Capabilities/Full`, `PlaybackStart` y `PlaybackProgress`.

Prueba manual obligatoria en TV real:

1. `.strm` normal (destino público).
2. `.strm` con destino `172.21.x.x` o proxy interno (el TV no debe intentar esa IP).
3. `.strm` sobre montaje rclone.
4. MKV/MP4 local en DirectPlay.
5. Contenido que obligue a transcodificar.
6. Subtítulos externos.
7. Pista DTS/TrueHD sin passthrough (debe transcodificar, no quedarse en negro).
