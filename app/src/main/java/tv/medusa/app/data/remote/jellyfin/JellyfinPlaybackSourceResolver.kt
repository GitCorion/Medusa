package tv.medusa.app.data.remote.jellyfin

/**
 * Resuelve candidatos de reproducción para cualquier configuración de Jellyfin:
 *
 * - Servidores locales o remotos, HTTP o HTTPS, con o sin subruta.
 * - `.strm` normales, `.strm` con destino público, LAN, proxy interno o IP de Docker.
 * - Montajes rclone (1fichier, Google Drive, S3, WebDAV…), discos locales y bibliotecas normales.
 * - DirectPlay, DirectStream y transcodificación.
 *
 * Regla de oro: para `.strm` y para DirectPlay se usa primero el endpoint oficial de Jellyfin
 * `Videos/{id}/stream?static=true&MediaSourceId=...`. Es el que resuelve el `.strm` en el
 * servidor (por eso funciona con destinos internos de Docker/rclone/LAN) y el que devuelve
 * HTTP 206 + `Accept-Ranges`, imprescindible para el seek nativo.
 *
 * No se construyen HLS (`master.m3u8`/`main.m3u8`) ni se usa `MediaSourceInfo.Path` como
 * atajo: la reproducción siempre pasa por Jellyfin.
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

    fun buildCandidates(
        baseUrl: String,
        playableId: String,
        token: String,
        playSessionId: String,
        audioStreamIndex: Int?,
        mediaSource: JellyfinMediaSourceDto,
        alternativeMediaSources: List<JellyfinMediaSourceDto> = emptyList()
    ): List<String> {
        val candidates = linkedSetOf<String>()
        val mediaSourceId = mediaSource.id
        val container = mediaSource.container?.trim()?.lowercase().orEmpty()
        val isStrm = isStrm(mediaSource)

        fun add(rawUrl: String?) {
            if (rawUrl.isNullOrBlank()) return
            val fullUrl = JellyfinDeviceProfileBuilder.combineBaseUrlAndPath(baseUrl, rawUrl)
            candidates += JellyfinDeviceProfileBuilder.appendPlaybackParams(
                url = fullUrl,
                playSessionId = playSessionId,
                apiKey = token,
                mediaSourceId = mediaSourceId.takeIf { it.isNotBlank() },
                audioStreamIndex = audioStreamIndex
            )
        }

        val effectiveContainer = if (container.isNotBlank() && container != "strm") container else "mkv"

        if (isStrm) {
            // 1. Proxy del servidor: resuelve el .strm (Docker/rclone/LAN) con HTTP 206 y soporte de Range.
            if (mediaSourceId.isNotBlank()) {
                add("$baseUrl/Videos/$playableId/stream?static=true&MediaSourceId=$mediaSourceId")
                add("$baseUrl/Videos/$playableId/stream.$effectiveContainer?static=true&MediaSourceId=$mediaSourceId")
            }

            // 2. URLs que entrega Jellyfin (DirectStreamUrl estático oficial primero).
            add(mediaSource.directStreamUrl)

            // 3. Versiones alternativas mediante el mismo proxy estático.
            alternativeMediaSources
                .filter { it.id.isNotBlank() && it.id != mediaSourceId }
                .forEach { altSource ->
                    val altContainer = altSource.container?.trim()?.lowercase()
                    val effectiveAltContainer = if (!altContainer.isNullOrBlank() && altContainer != "strm") altContainer else "mkv"
                    add("$baseUrl/Videos/$playableId/stream?static=true&MediaSourceId=${altSource.id}")
                    add("$baseUrl/Videos/$playableId/stream.$effectiveAltContainer?static=true&MediaSourceId=${altSource.id}")
                }

            // 4. Remux y transcode como último recurso.
            if (mediaSourceId.isNotBlank()) {
                add("$baseUrl/Videos/$playableId/stream.$effectiveContainer?MediaSourceId=$mediaSourceId")
                add("$baseUrl/Videos/$playableId/stream?MediaSourceId=$mediaSourceId")
            }
            add(mediaSource.transcodingUrl)
        } else {
            // 1. Lo que decida Jellyfin.
            add(mediaSource.directStreamUrl)
            add(mediaSource.transcodingUrl)

            // 2. DirectPlay/DirectStream oficial de Jellyfin.
            if (mediaSourceId.isNotBlank()) {
                add("$baseUrl/Videos/$playableId/stream?static=true&MediaSourceId=$mediaSourceId")
                add("$baseUrl/Videos/$playableId/stream.$effectiveContainer?static=true&MediaSourceId=$mediaSourceId")
                add("$baseUrl/Videos/$playableId/stream.$effectiveContainer?MediaSourceId=$mediaSourceId")
                add("$baseUrl/Videos/$playableId/stream?MediaSourceId=$mediaSourceId")
            }
        }

        // Nunca dejar la lista vacía si hay un ítem reproducible.
        if (candidates.isEmpty()) {
            add("$baseUrl/Videos/$playableId/stream?static=true")
            add("$baseUrl/Videos/$playableId/stream.$effectiveContainer?static=true")
            add("$baseUrl/Videos/$playableId/stream.$effectiveContainer")
            add("$baseUrl/Videos/$playableId/stream")
        }

        return candidates.toList()
    }

    /**
     * Candidatos para un reintento forzado en servidor (decoder de audio/vídeo). Solo se usa
     * la `TranscodingUrl` que entrega Jellyfin; nunca DirectPlay ni HLS construido a mano.
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
        val transcodingUrl = mediaSource.transcodingUrl
        if (!transcodingUrl.isNullOrBlank()) {
            val fullUrl = JellyfinDeviceProfileBuilder.combineBaseUrlAndPath(baseUrl, transcodingUrl)
            candidates += JellyfinDeviceProfileBuilder.appendPlaybackParams(
                url = fullUrl,
                playSessionId = playSessionId,
                apiKey = token,
                mediaSourceId = mediaSource.id.takeIf { it.isNotBlank() },
                audioStreamIndex = audioStreamIndex
            )
        }
        return candidates.toList()
    }

    /**
     * URL de subtítulos: usa `DeliveryUrl` si Jellyfin la entrega y, si no, el endpoint
     * oficial `/Videos/{itemId}/{mediaSourceId}/Subtitles/{index}/Stream.{codec}?api_key=…`.
     */
    fun buildSubtitleUrl(
        baseUrl: String,
        playableId: String,
        mediaSourceId: String,
        stream: JellyfinMediaStreamDto,
        token: String
    ): String {
        val deliveryUrl = stream.deliveryUrl
        if (!deliveryUrl.isNullOrBlank()) {
            val combined = JellyfinDeviceProfileBuilder.combineBaseUrlAndPath(baseUrl, deliveryUrl)
            return JellyfinDeviceProfileBuilder.appendPlaybackParams(
                url = combined,
                apiKey = token
            )
        }

        val codec = stream.codec?.trim()?.takeIf { it.isNotBlank() } ?: "srt"
        return "$baseUrl/Videos/$playableId/$mediaSourceId/Subtitles/${stream.index}/Stream.$codec?api_key=$token"
    }
}
