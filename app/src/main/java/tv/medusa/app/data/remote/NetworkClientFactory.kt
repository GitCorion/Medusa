package tv.medusa.app.data.remote

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import tv.medusa.app.R
import tv.medusa.app.util.DeviceUtils
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.net.InetAddress
import java.net.Socket
import java.net.URI
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

object NetworkClientFactory {

    /** Contexto de aplicación, fijado desde [initCache], para mensajes de red localizables. */
    private var appContext: Context? = null

    val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    /**
     * JSON exclusivo para la API de Jellyfin.
     *
     * `encodeDefaults = true` es obligatorio: kotlinx.serialization omite por defecto los
     * campos que coinciden con su valor por defecto. En el DeviceProfile eso hacía que
     * `Type: "Video"`, `PlayableMediaTypes` y todo el perfil de transcodificación se
     * omitieran, y Jellyfin los sustituyera por sus defaults (`Type: "Audio"`, listas vacías,
     * contenedores y códecs vacíos).
     */
    internal val jellyfinJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    /**
     * Trust manager estricto del sistema. En Android se construye desde `AndroidCAStore`
     * excluyendo las entradas de usuario (prefijo `user:`), de modo que las CAs instaladas
     * manualmente en el dispositivo nunca se aceptan. En JVM se usa el almacén por defecto.
     */
    private val systemTrustManager: X509TrustManager = run {
        // Fail-closed en Android: si no se puede construir el almacén filtrado, se aborta en
        // lugar de caer en el almacén por defecto (que incluye CAs instaladas por el usuario).
        // En JVM (tests unitarios) no existe AndroidCAStore: se permite el almacén por defecto.
        val systemKeyStore = try {
            val androidStore = java.security.KeyStore.getInstance("AndroidCAStore")
            androidStore.load(null, null)
            val filtered = java.security.KeyStore.getInstance(java.security.KeyStore.getDefaultType())
            filtered.load(null, null)
            val aliases = androidStore.aliases()
            while (aliases.hasMoreElements()) {
                val alias = aliases.nextElement()
                if (alias.startsWith("user:", ignoreCase = true)) continue
                androidStore.getCertificate(alias)?.let { filtered.setCertificateEntry(alias, it) }
            }
            filtered
        } catch (e: java.security.KeyStoreException) {
            null // JVM sin AndroidCAStore
        } catch (e: java.security.NoSuchAlgorithmException) {
            null // JVM sin AndroidCAStore
        } catch (e: Exception) {
            throw IllegalStateException("No se pudo construir el almacén de CAs del sistema", e)
        }

        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(systemKeyStore)
        factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
            ?: throw IllegalStateException("No system X509TrustManager available")
    }

    /**
     * Trust manager con pin. Primero delega en las CAs del sistema; si el certificado no es
     * válido, solo lo acepta cuando el fingerprint SHA-256 de la hoja coincide exactamente con
     * el que el usuario fijó para su servidor. Nunca acepta certificados arbitrarios.
     */
    private class PinnedTrustManager(
        private val delegate: X509TrustManager,
        private val fingerprintProvider: () -> String?,
        private val fingerprintHasher: (ByteArray) -> String,
        private val fingerprintMatcher: (String?, String?) -> Boolean
    ) : X509TrustManager {

        @Suppress("UNCHECKED_CAST")
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            delegate.checkClientTrusted(chain as Array<X509Certificate>?, authType)
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            try {
                @Suppress("UNCHECKED_CAST")
                delegate.checkServerTrusted(chain as Array<X509Certificate>?, authType)
                return
            } catch (e: CertificateException) {
                // Se evalúa el pin más abajo.
            }
            val expected = fingerprintProvider()
                ?: throw CertificateException("Certificado no confiado y sin pin configurado")
            val leaf = chain?.firstOrNull()
                ?: throw CertificateException("Cadena de certificados vacía")
            val actual = fingerprintHasher(leaf.encoded)
            if (!fingerprintMatcher(actual, expected)) {
                throw CertificateException("El fingerprint del certificado no coincide con el pin")
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
    }

    private val pinnedTrustManager = PinnedTrustManager(
        delegate = systemTrustManager,
        fingerprintProvider = { pinnedFingerprintSha256 },
        fingerprintHasher = { sha256Fingerprint(it) },
        fingerprintMatcher = { a, b -> sameFingerprint(a, b) }
    )

    /** Contexto TLS estricto (CAs del sistema) para hosts públicos. */
    private val strictSslContext: SSLContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(systemTrustManager), SecureRandom())
    }

    /** Contexto TLS con pin para el host exacto del servidor que optó por autofirmado. */
    private val pinnedSslContext: SSLContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf(pinnedTrustManager), SecureRandom())
    }

    @Volatile
    private var customTrustedServerHost: String? = null

    @Volatile
    private var allowSelfSignedForTrustedHost: Boolean = false

    @Volatile
    private var pinnedFingerprintSha256: String? = null

    /**
     * Registra el host exacto del servidor Jellyfin y, si el usuario lo autorizó
     * explícitamente, el fingerprint SHA-256 del certificado autofirmado. El resto de hosts
     * (TMDB, Trakt, GitHub, CDNs) sigue usando validación TLS estricta del sistema.
     */
    fun setTrustedServer(
        serverUrl: String?,
        allowSelfSigned: Boolean = false,
        certificateFingerprintSha256: String? = null
    ) {
        val host = serverUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { URI(normalizeUrl(it)) }.getOrNull()?.host?.lowercase() }

        customTrustedServerHost = host
        pinnedFingerprintSha256 = certificateFingerprintSha256
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        allowSelfSignedForTrustedHost =
            allowSelfSigned && host != null && pinnedFingerprintSha256 != null
    }

    private fun isSelfSignedPermittedHost(hostname: String?): Boolean {
        if (hostname.isNullOrBlank()) return false
        if (!allowSelfSignedForTrustedHost) return false
        val trusted = customTrustedServerHost ?: return false
        return hostname.equals(trusted, ignoreCase = true)
    }

    /**
     * Descarga el certificado del servidor y devuelve su fingerprint SHA-256 en formato
     * `AA:BB:...`. El trust manager de la sonda **no acepta** el certificado: solo captura la
     * cadena y lanza [CertificateException], de modo que el handshake falla siempre.
     *
     * Es una operación de red bloqueante: debe invocarse fuera del hilo principal.
     */
    fun fetchCertificateFingerprint(serverUrl: String): String {
        val clean = normalizeUrl(serverUrl)
        require(clean.startsWith("https://", ignoreCase = true)) {
            "El pin de certificado solo aplica a servidores HTTPS"
        }

        val captured = AtomicReference<Array<X509Certificate>?>(null)
        val captureTrustManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                throw CertificateException("Sonda de certificado: no se aceptan certificados de cliente")
            }

            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                @Suppress("UNCHECKED_CAST")
                captured.set(chain?.copyOf() as Array<X509Certificate>?)
                throw CertificateException("Sonda de certificado: cadena capturada")
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

        val probeContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(captureTrustManager), SecureRandom())
        }

        val probeClient = OkHttpClient.Builder()
            .sslSocketFactory(probeContext.socketFactory, captureTrustManager)
            // La sonda nunca completa la petición: el trust manager lanza antes.
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

        runCatching {
            probeClient.newCall(
                Request.Builder()
                    .url(clean)
                    .head()
                    .build()
            ).execute().close()
        }

        val chain = captured.get()
            ?: throw IOException("No se pudo obtener el certificado del servidor")
        val leaf = chain.firstOrNull()
            ?: throw IOException("El servidor no presentó certificado")
        return sha256Fingerprint(leaf.encoded)
    }

    /** Fingerprint SHA-256 con separadores para mostrar en ajustes. */
    fun sha256Fingerprint(encodedCertificate: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(encodedCertificate)
        return digest.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
    }

    /** Compara fingerprints ignorando separadores, mayúsculas y espacios. */
    fun sameFingerprint(a: String?, b: String?): Boolean {
        if (a.isNullOrBlank() || b.isNullOrBlank()) return false
        fun normalize(value: String) =
            value.replace(":", "").replace(" ", "").trim().lowercase()
        return normalize(a) == normalize(b)
    }

    /**
     * Fábrica TLS consciente del host. OkHttp la invoca con el host destino
     * (`createSocket(socket, host, port, autoClose)`), de modo que solo el host exacto del
     * servidor con pin usa el contexto con certificado fijado. Todos los demás hosts usan
     * las CAs del sistema.
     */
    private val hostAwareSslSocketFactory: SSLSocketFactory = object : SSLSocketFactory() {
        private val strict: SSLSocketFactory = strictSslContext.socketFactory
        private val pinned: SSLSocketFactory = pinnedSslContext.socketFactory

        private fun factoryFor(host: String?): SSLSocketFactory =
            if (isSelfSignedPermittedHost(host)) pinned else strict

        override fun getDefaultCipherSuites(): Array<String> = strict.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = strict.supportedCipherSuites

        override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket =
            factoryFor(host).createSocket(s, host, port, autoClose)

        override fun createSocket(host: String, port: Int): Socket =
            factoryFor(host).createSocket(host, port)

        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
            factoryFor(host).createSocket(host, port, localHost, localPort)

        override fun createSocket(host: InetAddress, port: Int): Socket =
            factoryFor(host.hostAddress ?: host.hostName).createSocket(host, port)

        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
            factoryFor(address.hostAddress ?: address.hostName).createSocket(address, port, localAddress, localPort)
    }

    @Volatile
    var okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .sslSocketFactory(hostAwareSslSocketFactory, pinnedTrustManager)
        // No hay bypass global: para cualquier host distinto del servidor configurado se
        // delega en el verificador del sistema. Solo el host exacto con pin válido relaja el
        // nombre, y aun así exige que el fingerprint del certificado coincida.
        .hostnameVerifier { hostname, session ->
            if (isSelfSignedPermittedHost(hostname)) {
                val expected = pinnedFingerprintSha256
                val actual = runCatching {
                    session.peerCertificates.firstOrNull()?.let { sha256Fingerprint(it.encoded) }
                }.getOrNull()
                expected != null && actual != null && sameFingerprint(actual, expected)
            } else {
                HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
            }
        }
        .followRedirects(true)
        .followSslRedirects(true)
        .connectionPool(okhttp3.ConnectionPool(30, 5, TimeUnit.MINUTES))
        .dispatcher(okhttp3.Dispatcher().apply {
            maxRequests = 64
            maxRequestsPerHost = 20 // Permite carga simultánea y fluida de carátulas desde el mismo host
        })
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val urlPath = original.url.encodedPath
            val isMediaRequest = urlPath.contains("Videos/", ignoreCase = true) ||
                urlPath.contains("Audio/", ignoreCase = true)
            val isImageRequest = urlPath.contains("Images/", ignoreCase = true) ||
                original.url.host.contains("tmdb.org", ignoreCase = true) ||
                original.header("Accept")?.contains("image", ignoreCase = true) == true ||
                urlPath.endsWith(".jpg", ignoreCase = true) ||
                urlPath.endsWith(".jpeg", ignoreCase = true) ||
                urlPath.endsWith(".png", ignoreCase = true) ||
                urlPath.endsWith(".webp", ignoreCase = true)

            val requestBuilder = original.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Google TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

            // Solo forzar Accept: application/json en llamadas API de datos, NUNCA en vídeo ni en imágenes
            if (!isMediaRequest && !isImageRequest && original.header("Accept").isNullOrBlank()) {
                requestBuilder.header("Accept", "application/json, text/plain, */*")
            }

            // Jellyfin 12+ compatibility: Jellyfin 12 disables EnableLegacyAuthorization by default,
            // rejecting X-Emby-Authorization and strictly requiring Authorization: MediaBrowser ...
            // We ensure both headers are present for backwards and forwards compatibility.
            val authHeader = original.header("Authorization")
            val embyAuthHeader = original.header("X-Emby-Authorization")

            if (!authHeader.isNullOrBlank() && embyAuthHeader.isNullOrBlank()) {
                requestBuilder.header("X-Emby-Authorization", authHeader)
            } else if (!embyAuthHeader.isNullOrBlank() && authHeader.isNullOrBlank()) {
                requestBuilder.header("Authorization", embyAuthHeader)
            }

            // Also synchronize token headers if passed separately
            val token = original.header("X-Emby-Token") ?: original.header("X-MediaBrowser-Token")
            if (!token.isNullOrBlank()) {
                if (original.header("X-Emby-Token").isNullOrBlank()) {
                    requestBuilder.header("X-Emby-Token", token)
                }
                if (original.header("X-MediaBrowser-Token").isNullOrBlank()) {
                    requestBuilder.header("X-MediaBrowser-Token", token)
                }
                if (authHeader.isNullOrBlank() && embyAuthHeader.isNullOrBlank()) {
                    val stableDevId = tv.medusa.app.util.DeviceUtils.cachedDeviceId ?: "MedusaTV"
                    val defaultAuth = "MediaBrowser Client=\"Medusa\", Device=\"AndroidTV\", DeviceId=\"$stableDevId\", Version=\"${tv.medusa.app.BuildConfig.VERSION_NAME}\", Token=\"$token\""
                    requestBuilder.header("Authorization", defaultAuth)
                    requestBuilder.header("X-Emby-Authorization", defaultAuth)
                }
            }

            val request = requestBuilder.build()
            val response = chain.proceed(request)

            val contentType = response.body?.contentType()?.toString()?.lowercase() ?: ""
            // Se incluyen las rutas de vídeo/audio para detectar también el bloqueo de LaLiga en
            // el propio stream (antes solo se miraba la API y el player se quedaba "cargando").
            val isApiCall = request.url.encodedPath.contains("System/Info", ignoreCase = true)
                    || request.url.encodedPath.contains("Users/", ignoreCase = true)
                    || request.url.encodedPath.contains("QuickConnect", ignoreCase = true)
                    || request.url.encodedPath.contains("Items", ignoreCase = true)
                    || request.url.encodedPath.contains("Videos/", ignoreCase = true)
                    || request.url.encodedPath.contains("Audio/", ignoreCase = true)

            if (isApiCall && contentType.contains("text/html")) {
                val peekBody = response.peekBody(2048).string()
                if (peekBody.contains("LaLiga", ignoreCase = true) || peekBody.contains("bloqueado", ignoreCase = true) || peekBody.contains("Sentencia", ignoreCase = true)) {
                    throw IOException(appContext?.getString(R.string.network_isp_block) ?: "Tu red u operador no puede conectar con este servidor. Comprueba la conexión o intenta con otra red.")
                }
            }
            response
        }
        .apply {
            if (tv.medusa.app.BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor { message ->
                    // BASIC solo registra método + URL, pero la URL puede llevar api_key/token.
                    Log.d("OkHttp", redactLogMessage(message))
                }.apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                    redactHeader("Authorization")
                    redactHeader("X-Emby-Authorization")
                    redactHeader("X-Emby-Token")
                    redactHeader("X-MediaBrowser-Token")
                    redactHeader("trakt-api-key")
                    redactHeader("Cookie")
                    redactHeader("Set-Cookie")
                })
            }
        }
        .build()

    /**
     * Oculta credenciales en las líneas de log de OkHttp. Se marca `internal` para poder
     * cubrirlo con tests JVM sin exponerlo como API pública.
     */
    internal fun redactLogMessage(message: String): String = message
        // Cabecera MediaBrowser: Token="..." (se procesa antes que los query params para
        // conservar las comillas y distinguirla de un token de URL).
        .replace(
            Regex("(?i)(Token\\s*=\\s*\")[^\"]+(\")"),
            "$1REDACTED$2"
        )
        // Query params sensibles (api_key=, token=, X-Emby-Token=…)
        .replace(
            Regex("(?i)(api_key|apikey|access_token|token|x-emby-token|x-mediabrowser-token|secret|code|sig|trakt-api-key)=([^&\\s\"]+)"),
            "$1=REDACTED"
        )

    private data class ServiceCacheKey(
        val baseUrl: String,
        val serviceClass: Class<*>,
        val jsonIdentity: Int
    )

    private val serviceCache = ConcurrentHashMap<ServiceCacheKey, Any>()

    fun initCache(context: Context) {
        appContext = context.applicationContext
        if (okHttpClient.cache != null) return
        val cacheDir = File(context.cacheDir, "http_cache")
        val cache = okhttp3.Cache(cacheDir, 50L * 1024 * 1024) // 50 MB
        okHttpClient = okHttpClient.newBuilder()
            .cache(cache)
            .addNetworkInterceptor { chain ->
                val request = chain.request()
                val response = chain.proceed(request)
                if (request.url.host.contains("themoviedb.org", ignoreCase = true) && response.isSuccessful) {
                    response.newBuilder()
                        .header("Cache-Control", "public, max-age=604800") // 7 days disk cache for TMDB
                        .removeHeader("Pragma")
                        .build()
                } else {
                    response
                }
            }
            .build()
        serviceCache.clear()
    }

    fun normalizeUrl(rawUrl: String): String {
        var url = rawUrl.trim()
        if (url.isEmpty()) return ""

        // Strip fragments (#...) and query parameters (?...)
        url = url.substringBefore("#").substringBefore("?")

        // Determine if it looks like an IP or localhost
        val withoutProto = url.substringAfter("://")
        val hostPart = withoutProto.substringBefore("/").substringBefore(":")
        val isIpOrLocalhost = hostPart.equals("localhost", ignoreCase = true)
                || hostPart.startsWith("127.0.0.1")
                || hostPart.startsWith("10.0.2.2")
                || hostPart.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))

        // If scheme is missing:
        // Domains (e.g. jellyfin.example.com) default to https://
        // Local IPs/localhost default to http://
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = if (isIpOrLocalhost) "http://$url" else "https://$url"
        }

        url = url.trimEnd('/')

        // Strip web interface paths if user copied URL from browser (e.g. /web, /web/index.html)
        url = url.replace(Regex("/web(/index\\.html)?/?$", RegexOption.IGNORE_CASE), "")
        url = url.trimEnd('/')

        val scheme = if (url.startsWith("https://", ignoreCase = true)) "https://" else "http://"
        var rest = url.substring(scheme.length)

        // If on emulator and user entered localhost or 127.0.0.1, auto-map to 10.0.2.2 (host machine)
        if (DeviceUtils.isEmulator()) {
            if (rest.startsWith("localhost", ignoreCase = true)) {
                rest = "10.0.2.2" + rest.substring("localhost".length)
            } else if (rest.startsWith("127.0.0.1")) {
                rest = "10.0.2.2" + rest.substring("127.0.0.1".length)
            }
        }

        // Only append port 8096 for local HTTP connections without a port (e.g. 192.168.1.50 or 10.0.2.2)
        // Never append port 8096 for HTTPS or external domain names (like jellyfin.example.com)
        val currentHost = rest.substringBefore("/").substringBefore(":")
        val isLocalTarget = currentHost.equals("localhost", ignoreCase = true)
                || currentHost.startsWith("10.0.2.2")
                || currentHost.startsWith("127.0.0.1")
                || currentHost.matches(Regex("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$"))

        if (scheme == "http://" && isLocalTarget && !rest.contains(":")) {
            val h = rest.substringBefore("/")
            val p = if (rest.contains("/")) "/" + rest.substringAfter("/") else ""
            rest = "$h:8096$p"
        }

        return "$scheme$rest"
    }

    /**
     * Servicio Retrofit genérico (Trakt, TMDB, updater…). Usa el JSON compartido, que no
     * fuerza la serialización de defaults.
     */
    fun <T> createService(baseUrl: String, serviceClass: Class<T>): T =
        createServiceInternal(baseUrl, serviceClass, json)

    /**
     * Servicio Retrofit de Jellyfin. Usa [jellyfinJson] para que DeviceProfile, capabilities
     * y eventos de sesión viajen completos aunque sus valores coincidan con los defaults.
     */
    fun <T> createJellyfinService(baseUrl: String, serviceClass: Class<T>): T =
        createServiceInternal(baseUrl, serviceClass, jellyfinJson)

    @Suppress("UNCHECKED_CAST")
    private fun <T> createServiceInternal(
        baseUrl: String,
        serviceClass: Class<T>,
        serializerJson: Json
    ): T {
        val cleanUrl = normalizeUrl(baseUrl)
        val validUrl = if (cleanUrl.endsWith("/")) cleanUrl else "$cleanUrl/"
        val cacheKey = ServiceCacheKey(
            baseUrl = validUrl,
            serviceClass = serviceClass,
            jsonIdentity = System.identityHashCode(serializerJson)
        )

        return serviceCache.getOrPut(cacheKey) {
            val contentType = "application/json".toMediaType()
            Retrofit.Builder()
                .baseUrl(validUrl)
                .client(okHttpClient)
                .addConverterFactory(serializerJson.asConverterFactory(contentType))
                .build()
                .create(serviceClass) as Any
        } as T
    }
}
