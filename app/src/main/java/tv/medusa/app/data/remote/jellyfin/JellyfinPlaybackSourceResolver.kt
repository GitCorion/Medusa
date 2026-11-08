package tv.medusa.app.data.remote.jellyfin

import java.net.URI

/**
 * Resuelve candidatos de reproducción para cualquier configuración de Jellyfin:
 *
 * - Servidores locales o remotos, HTTP o HTTPS.
 * - `.strm` cuyo destino es una URL pública, una URL de LAN o una IP interna de Docker.
 * - Montajes rclone, discos locales y bibliotecas normales.
 * - DirectPlay, DirectStream y transcodificación HLS.
 *
 * Regla de oro: para `.strm` y para DirectPlay se usa primero el endpoint oficial de Jellyfin
 * `Videos/{id}/stream?static=true&MediaSourceId=...`. Es el único que resuelve el `.strm` en el
 * servidor (por eso funciona con IPs internas de Docker/rclone) y el que devuelve HTTP 206 con
 * `Accept-Ranges`, imprescindible para el seek nativo.
 *
 * Las URLs de `DirectStreamUrl`/`TranscodingUrl` que entrega PlaybackInfo se respetan y se
 * usan después (o antes, en contenido normal), sin inventar parámetros de transcodificación
 * salvo el HLS oficial de último recurso.
 */
internal object JellyfinPlaybackSourceResolver {

    /**
     * Contenedores que Media3 puede abrir directamente. AVI/ASF/WMV no tienen extractor en
     * Media3: se excluyen para que Jellyfin los transcodifique en lugar de intentar un
     * DirectPlay que fallaría en ExoPlayer.
     */
    internal val DIRECT_PLAY_CONTAINERS = listOf(
        "mkv", "mp4", "m4v", "mov", "webm",
        "ts", "m2ts", "mts", "m2t",
        "flv", "3gp", "3g2", "ogv", "ogg",
        "mpg", "mpeg", "ps", "vob",
        "strm", "m3u8", "mpd"
    )

    fun isStrm(mediaSource: JellyfinMediaSourceDto): Boolean {
        val container = mediaSource.container?.trim()?.lowercase().orEmpty()
        val path = mediaSource.path?.trim().orEmpty()
        val protocol = mediaSource.protocol?.trim().orEmpty()
        return container == "strm" ||
            path.endsWith(".strm", ignoreCase = true) ||
            protocol.equals("Http", ignoreCase = true)
    }

    /**
     * Determina si el TV puede alcanzar directamente un host. Las IPs privadas, localhost,
     * Docker (172.16/12), LAN y mDNS se consideran solo alcanzables desde el servidor, así que
     * no se ponen como candidatas directas: se usa el proxy `static=true` de Jellyfin.
     */
    fun isReachableFromClient(host: String?): Boolean {
        val clean = host?.trim()?.removePrefix("[")?.removeSuffix("]")?.lowercase()
        if (clean.isNullOrBlank()) return false
        if (clean == "localhost" || clean.endsWith(".local")) return false
        if (clean == "::1" || clean.startsWith("fe80:") || clean.startsWith("fc") || clean.startsWith("fd")) {
            return false
        }

        val parts = clean.split(".")
        if (parts.size == 4) {
            val a = parts[0].toIntOrNull() ?: return true
            val b = parts[1].toIntOrNull() ?: return true
            return when {
                a == 10 -> false
                a == 127 -> false
                a == 169 && b == 254 -> false
                a == 172 && b in 16..31 -> false
                a == 192 && b == 168 -> false
                else -> true
            }
        }
        return true
    }

    /**
     * Si el `.strm` apunta a un host privado/Docker que el TV no alcanza, ofrece también la
     * misma URL apuntando al host del servidor Jellyfin (mismo esquema, puerto y query). Es
     * un fallback para instalaciones donde ese servicio está publicado en el host del server.
     */
    fun rewritePrivateTargetToServerHost(rawUrl: String?, baseUrl: String): String? {
        if (rawUrl.isNullOrBlank()) return null
        if (!rawUrl.startsWith("http://", ignoreCase = true) &&
            !rawUrl.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return null
        val host = uri.host ?: return null
        if (isReachableFromClient(host)) return null

        val serverHost = runCatching { URI(baseUrl).host }.getOrNull() ?: return null
        if (serverHost.isBlank() || serverHost.equals(host, ignoreCase = true)) return null

        val schemeSeparator = rawUrl.indexOf("://").takeIf { it >= 0 } ?: return null
        val remainder = rawUrl.substring(schemeSeparator + 3)
        val authorityEnd = remainder.indexOfAny(charArrayOf('/', '?', '#')).let {
            if (it >= 0) it else remainder.length
        }
        val suffix = remainder.substring(authorityEnd)

        val hostForUrl = if (serverHost.contains(':')) "[$serverHost]" else serverHost
        val hostPort = buildString {
            append(hostForUrl)
            if (uri.port >= 0) append(':').append(uri.port)
        }
        val newAuthority = if (uri.userInfo.isNullOrBlank()) hostPort else "${uri.userInfo}@$hostPort"
        return rawUrl.substring(0, schemeSeparator + 3) + newAuthority + suffix
    }

    fun buildCandidates(
        baseUrl: String,
        playableId: String,
        token: String,
        playSessionId: String,
        audioStreamIndex: Int?,
        mediaSource: JellyfinMediaSourceDto,
        alternativeMediaSources: List<JellyfinMediaSourceDto> = emptyList(),
        extractedStrmUrl: String? = null
    ): List<String> {
        val candidates = linkedSetOf<String>()
        val mediaSourceId = mediaSource.id
        val container = mediaSource.container?.trim()?.lowercase().orEmpty()
        val isStrm = isStrm(mediaSource)

        fun add(rawUrl: String?) {
            if (rawUrl.isNullOrBlank()) return
            val fullUrl = JellyfinDeviceProfileBuilder.combineBaseUrlAndPath(baseUrl, rawUrl)
            val withParams = JellyfinDeviceProfileBuilder.appendPlaybackParams(
                url = fullUrl,
                playSessionId = playSessionId,
                apiKey = token,
                mediaSourceId = mediaSourceId.takeIf { it.isNotBlank() },
                audioStreamIndex = audioStreamIndex
            )
            candidates += withParams
        }

        fun addReachableAbsolute(rawUrl: String?) {
            if (rawUrl.isNullOrBlank()) return
            if (!rawUrl.startsWith("http://", ignoreCase = true) &&
                !rawUrl.startsWith("https://", ignoreCase = true)
            ) {
                return
            }
            val host = runCatching { URI(rawUrl).host }.getOrNull()
            if (isReachableFromClient(host)) add(rawUrl)
        }

        fun addHlsFallback() {
            if (mediaSourceId.isNotBlank()) {
                add("$baseUrl/Videos/$playableId/master.m3u8?MediaSourceId=$mediaSourceId")
            } else {
                add("$baseUrl/Videos/$playableId/master.m3u8")
            }
        }

        if (isStrm) {
            // 1. Proxy del servidor: resuelve el .strm (Docker/rclone/LAN) y permite seek.
            if (mediaSourceId.isNotBlank()) {
                add("$baseUrl/Videos/$playableId/stream?static=true&MediaSourceId=$mediaSourceId")
                add("$baseUrl/Videos/$playableId/stream?MediaSourceId=$mediaSourceId")
            }

            // 2. URLs que entrega Jellyfin.
            add(mediaSource.directStreamUrl)
            add(mediaSource.transcodingUrl)

            // 2b. Fallback si el .strm apunta a Docker/LAN y ese servicio está publicado
            // en el host del servidor Jellyfin.
            add(rewritePrivateTargetToServerHost(mediaSource.path, baseUrl))
            add(rewritePrivateTargetToServerHost(extractedStrmUrl, baseUrl))

            // 3. Versiones alternativas mediante el mismo proxy.
            alternativeMediaSources
                .filter { it.id.isNotBlank() && it.id != mediaSourceId }
                .forEach { add("$baseUrl/Videos/$playableId/stream?static=true&MediaSourceId=${it.id}") }

            // 4. Destino directo del .strm o Path HTTP, solo si el TV puede alcanzarlo.
            addReachableAbsolute(mediaSource.path)
            addReachableAbsolute(extractedStrmUrl)

            // 5. Último recurso: HLS oficial del servidor.
            addHlsFallback()
        } else {
            // 1. Lo que decida Jellyfin.
            add(mediaSource.directStreamUrl)
            add(mediaSource.transcodingUrl)

            // 2. DirectPlay/DirectStream oficial de Jellyfin.
            if (mediaSourceId.isNotBlank()) {
                add("$baseUrl/Videos/$playableId/stream?static=true&MediaSourceId=$mediaSourceId")
                add("$baseUrl/Videos/$playableId/stream?MediaSourceId=$mediaSourceId")
                if (container.isNotBlank() && container != "strm") {
                    add("$baseUrl/Videos/$playableId/stream.$container?static=true&MediaSourceId=$mediaSourceId")
                }
            }

            // 3. Path HTTP directo (rclone/remoto) solo si es alcanzable.
            addReachableAbsolute(mediaSource.path)

            // 4. HLS oficial del servidor.
            addHlsFallback()
        }

        // Nunca dejar la lista vacía si hay un ítem reproducible.
        if (candidates.isEmpty()) {
            add("$baseUrl/Videos/$playableId/stream?static=true")
            add("$baseUrl/Videos/$playableId/stream")
            addHlsFallback()
        }

        return candidates.toList()
    }

    /**
     * Candidatos para un reintento forzado en servidor (decoder de audio/vídeo). No se
     * devuelve ningún DirectPlay: primero la `TranscodingUrl` de Jellyfin y, si falta, el HLS
     * oficial.
     */
    fun buildTranscodeOnlyCandidates(
        baseUrl: String,
        playableId: String,
        token: String,
        playSessionId: String,
        audioStreamIndex: Int?,
        mediaSource: JellyfinMediaSourceDto
    ): List<String> {
        val candidates = linkedSetOf<String>()

        fun add(rawUrl: String?) {
            if (rawUrl.isNullOrBlank()) return
            val fullUrl = JellyfinDeviceProfileBuilder.combineBaseUrlAndPath(baseUrl, rawUrl)
            candidates += JellyfinDeviceProfileBuilder.appendPlaybackParams(
                url = fullUrl,
                playSessionId = playSessionId,
                apiKey = token,
                mediaSourceId = mediaSource.id.takeIf { it.isNotBlank() },
                audioStreamIndex = audioStreamIndex
            )
        }

        add(mediaSource.transcodingUrl)

        if (mediaSource.id.isNotBlank()) {
            add(
                "$baseUrl/Videos/$playableId/master.m3u8" +
                    "?MediaSourceId=${mediaSource.id}" +
                    "&VideoCodec=h264&AudioCodec=aac,mp3,ac3&MaxHeight=1080&MaxWidth=1920"
            )
            add("$baseUrl/Videos/$playableId/master.m3u8?MediaSourceId=${mediaSource.id}")
        } else {
            add("$baseUrl/Videos/$playableId/master.m3u8")
        }

        return candidates.toList()
    }
}
