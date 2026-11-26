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
            val requiresServerTranscode =
                !mediaSource.supportsDirectPlay &&
                    !mediaSource.supportsDirectStream &&
                    !mediaSource.transcodingUrl.isNullOrBlank()
            if (requiresServerTranscode) {
                add(mediaSource.transcodingUrl)
            }

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

    private val DV_NAME_REGEX = Regex("(^|[^a-z0-9])(dv|dovi|dolby)([^a-z0-9]|$)", RegexOption.IGNORE_CASE)

    fun isFourKSource(mediaSource: JellyfinMediaSourceDto): Boolean {
        val name = mediaSource.name?.lowercase().orEmpty()
        if (name.contains("4k") || name.contains("uhd") || name.contains("2160")) {
            return true
        }
        return mediaSource.mediaStreams.any { stream ->
            stream.type.equals("Video", ignoreCase = true) &&
                ((stream.width ?: 0) > 1920 || (stream.height ?: 0) > 1200)
        }
    }

    fun isDolbyVisionSource(mediaSource: JellyfinMediaSourceDto): Boolean {
        val name = mediaSource.name.orEmpty()
        if (DV_NAME_REGEX.containsMatchIn(name)) {
            return true
        }
        return mediaSource.mediaStreams.any { stream ->
            if (!stream.type.equals("Video", ignoreCase = true)) return@any false
            val range = stream.videoRangeType?.lowercase().orEmpty()
            val codec = stream.codec?.lowercase().orEmpty()
            val title = stream.displayTitle?.lowercase().orEmpty()
            range.contains("dovi") ||
                range.contains("dolby") ||
                codec.contains("dvhe") ||
                codec.contains("dvh1") ||
                title.contains("dolby vision")
        }
    }

    fun isSourceCompatible(
        mediaSource: JellyfinMediaSourceDto,
        supports4K: Boolean,
        supportsDolbyVision: Boolean
    ): Boolean {
        if (!supports4K && isFourKSource(mediaSource)) return false
        if (!supportsDolbyVision && isDolbyVisionSource(mediaSource)) return false
        return true
    }

    private fun pickBestCompatibleSource(sources: List<JellyfinMediaSourceDto>): JellyfinMediaSourceDto? {
        val p1080H264 = sources.firstOrNull { ms ->
            val name = ms.name?.lowercase().orEmpty()
            (name.contains("1080") || name.contains("fhd")) &&
                (name.contains("h264") || name.contains("avc") || name.contains("eng+esp") || name.contains("multi"))
        }
        if (p1080H264 != null) return p1080H264

        val p1080 = sources.firstOrNull { ms ->
            val name = ms.name?.lowercase().orEmpty()
            name.contains("1080") || name.contains("fhd")
        }
        return p1080 ?: sources.firstOrNull()
    }

    /**
     * Selecciona la versión inicial óptima según las capacidades de hardware del dispositivo.
     * Evita preparar o abrir DirectPlay de versiones 4K o Dolby Vision cuando el dispositivo no las soporta.
     */
    fun selectPreferredMediaSource(
        mediaSources: List<JellyfinMediaSourceDto>?,
        preferredMediaSourceId: String?,
        supports4K: Boolean,
        supportsDolbyVision: Boolean
    ): JellyfinMediaSourceDto? {
        if (mediaSources.isNullOrEmpty()) return null
        if (!preferredMediaSourceId.isNullOrBlank()) {
            return mediaSources.firstOrNull { it.id == preferredMediaSourceId } ?: mediaSources.firstOrNull()
        }
        val first = mediaSources.first()
        if (mediaSources.size == 1 || isSourceCompatible(first, supports4K, supportsDolbyVision)) {
            return first
        }
        val compatible = mediaSources.filter { isSourceCompatible(it, supports4K, supportsDolbyVision) }
        return pickBestCompatibleSource(compatible) ?: first
    }

    /**
     * Busca una alternativa no intentada tras un fallo de decodificador de vídeo antes de
     * recurrir a transcodificación completa en el servidor.
     */
    fun selectFallbackMediaSource(
        availableVersions: List<JellyfinMediaSourceDto>,
        attemptedIds: Set<String>,
        supports4K: Boolean,
        supportsDolbyVision: Boolean
    ): JellyfinMediaSourceDto? {
        val untried = availableVersions.filter { it.id.isNotBlank() && it.id !in attemptedIds }
        if (untried.isEmpty()) return null

        val compatible = untried.filter { isSourceCompatible(it, supports4K, supportsDolbyVision) }
        if (compatible.isNotEmpty()) {
            return pickBestCompatibleSource(compatible)
        }
        return if (supports4K && supportsDolbyVision) untried.firstOrNull() else null
    }
}
