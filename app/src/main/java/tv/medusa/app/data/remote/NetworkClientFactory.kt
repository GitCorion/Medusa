package tv.medusa.app.data.remote

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import tv.medusa.app.R
import tv.medusa.app.util.DeviceUtils
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.net.InetAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
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
     * Trust manager permisivo: acepta certificados self-signed. Se usa **solo** para
     * hosts locales/privados (LAN, localhost, docker) donde el bypass de TLS
     * es necesario porque el usuario despliega su propio Jellyfin con certificados propios.
     */
    private val trustAllCerts = arrayOf<X509TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    /** Contexto TLS permisivo (trust-all) para hosts locales/privados. */
    private val relaxedSslContext: SSLContext = SSLContext.getInstance("TLS").apply {
        init(null, trustAllCerts, SecureRandom())
    }

    /** Contexto TLS estricto (CAs del sistema) para hosts públicos. */
    private val strictSslContext: SSLContext = SSLContext.getInstance("TLS").apply {
        init(null, null, null)
    }

    /**
     * Fábrica TLS consciente del host. OkHttp la invoca con el host destino
     * (`createSocket(socket, host, port, autoClose)`), lo que permite validar con las CAs del
     * sistema los hosts públicos y conservar el bypass (self-signed) únicamente en la LAN.
     * Así el endurecimiento aplica también a Coil y a las llamadas directas, no solo a Retrofit.
     */
    private val hostAwareSslSocketFactory: SSLSocketFactory = object : SSLSocketFactory() {
        private val strict: SSLSocketFactory = strictSslContext.socketFactory
        private val relaxed: SSLSocketFactory = relaxedSslContext.socketFactory

        private fun factoryFor(host: String?): SSLSocketFactory =
            if (host != null && isLocalHost(host)) relaxed else strict

        override fun getDefaultCipherSuites(): Array<String> = strict.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = strict.supportedCipherSuites

        override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket =
            factoryFor(host).createSocket(s, host, port, autoClose)

        override fun createSocket(host: String, port: Int): Socket =
            factoryFor(host).createSocket(host, port)

        override fun createSocket(host: String, port: Int, localHost: InetAddress, localPort: Int): Socket =
            factoryFor(host).createSocket(host, port, localHost, localPort)

        override fun createSocket(host: InetAddress, port: Int): Socket =
            relaxed.createSocket(host, port)

        override fun createSocket(address: InetAddress, port: Int, localAddress: InetAddress, localPort: Int): Socket =
            relaxed.createSocket(address, port, localAddress, localPort)
    }

    private fun isLocalHost(hostname: String): Boolean {
        return hostname.equals("localhost", ignoreCase = true)
                || hostname.startsWith("127.0.0.1")
                || hostname.startsWith("10.")
                || hostname.startsWith("192.168.")
                || hostname.matches(Regex("^172\\.(1[6-9]|2[0-9]|3[0-1])\\..*"))
                || hostname.endsWith(".local", ignoreCase = true)
                || hostname.endsWith(".lan", ignoreCase = true)
                || hostname.endsWith(".home", ignoreCase = true)
    }

    @Volatile
    var okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .sslSocketFactory(hostAwareSslSocketFactory, trustAllCerts[0])
        .hostnameVerifier { hostname, session ->
            if (isLocalHost(hostname)) {
                true
            } else {
                javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier().verify(hostname, session)
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

            // Las llamadas a TMDB pasan por nuestro proxy: no enviar nunca al servidor una clave
            // guardada en el dispositivo. El proxy inyecta la clave real server-side.
            if (original.url.host.contains("medusa-fd388.web.app", ignoreCase = true) &&
                original.url.encodedPath.startsWith("/api/tmdb/")
            ) {
                requestBuilder.url(
                    original.url.newBuilder()
                        .setQueryParameter("api_key", ApiConfig.TMDB_PROXY_TOKEN)
                        .build()
                )
            }

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
                    val defaultAuth = "MediaBrowser Client=\"Medusa\", Device=\"AndroidTV\", DeviceId=\"MedusaTV\", Version=\"1.0.0\", Token=\"$token\""
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
                addInterceptor(HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BASIC
                })
            }
        }
        .build()

    private val serviceCache = ConcurrentHashMap<Pair<String, Class<*>>, Any>()

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

    @Suppress("UNCHECKED_CAST")
    fun <T> createService(baseUrl: String, serviceClass: Class<T>): T {
        val cleanUrl = normalizeUrl(baseUrl)
        val validUrl = if (cleanUrl.endsWith("/")) cleanUrl else "$cleanUrl/"

        return serviceCache.getOrPut(Pair(validUrl, serviceClass)) {
            val contentType = "application/json".toMediaType()
            Retrofit.Builder()
                .baseUrl(validUrl)
                .client(okHttpClient)
                .addConverterFactory(json.asConverterFactory(contentType))
                .build()
                .create(serviceClass) as Any
        } as T
    }
}
