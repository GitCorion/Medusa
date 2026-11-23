package tv.medusa.app.data.remote.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import retrofit2.http.GET
import tv.medusa.app.BuildConfig
import tv.medusa.app.R
import tv.medusa.app.data.remote.ApiConfig
import tv.medusa.app.data.remote.NetworkClientFactory
import tv.medusa.app.util.ServerVerificationUtils
import java.io.File
import java.io.FileOutputStream

/**
 * Manifiesto de actualizaciones publicado en el repositorio público, junto al APK de cada
 * GitHub Release. La app lo lee desde `docs/api/version.json` y descarga el asset firmado
 * del Release. No hay backend propio ni servicios de terceros implicados.
 */
@Serializable
data class UpdateManifestDto(
    @SerialName("versionCode") val versionCode: Long = 0L,
    @SerialName("versionName") val versionName: String = "",
    @SerialName("minSupportedVersionCode") val minSupportedVersionCode: Long = 0L,
    @SerialName("forceUpdate") val forceUpdate: Boolean = false,
    /** Kill-switch global: bloquea TODAS las versiones, haya o no reemplazo publicado. */
    @SerialName("killSwitch") val killSwitch: Boolean = false,
    @SerialName("releaseNotes") val releaseNotes: String? = null,
    @SerialName("apkUrl") val apkUrl: String? = null,
    @SerialName("apkSha256") val apkSha256: String? = null,
    @SerialName("signatures") val signatures: List<String> = emptyList()
)

interface UpdateManifestApiService {
    @GET("api/version.json")
    suspend fun getLatestVersion(): UpdateManifestDto
}

data class UpdateInfo(
    val hasUpdate: Boolean,
    val isForceUpdate: Boolean = false,
    val isKillSwitch: Boolean = false,
    val currentVersion: String,
    val latestVersion: String,
    val releaseNotes: String?,
    val apkUrl: String?,
    val apkSha256: String? = null
)

class AppUpdateManager(private val context: Context) {

    private val manifestApi: UpdateManifestApiService =
        NetworkClientFactory.createService(ApiConfig.UPDATE_MANIFEST_BASE_URL, UpdateManifestApiService::class.java)

    companion object {
        private const val CHECK_RETRIES = 3
        private const val CHECK_RETRY_DELAY_MS = 3_000L
        private const val MAX_APK_BYTES = 150L * 1024 * 1024
        /** Si no podemos validar el manifiesto en 7 días, asumimos bloqueo (fail-closed). */
        private const val MAX_MANIFEST_AGE_MS = 7L * 24 * 60 * 60 * 1000
        private const val PREFS_NAME = "medusa_update_manifest"
        private const val KEY_MANIFEST = "manifest_json"
        private const val KEY_CHECKED_AT = "checked_at"
        private val updateJson = Json { ignoreUnknownKeys = true }
        private val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")
        private val ALLOWED_DOWNLOAD_HOSTS = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com"
        )

        /** Saltos máximos tolerados al descargar un APK (GitHub: 1→objects). */
        private const val MAX_DOWNLOAD_REDIRECTS = 3

        private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
        val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

        private val _downloadProgress = MutableStateFlow<Float?>(null)
        val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

        private val _isDownloading = MutableStateFlow(false)
        val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

        private var hasAutoChecked = false
    }

    suspend fun checkAutomatically() {
        if (hasAutoChecked) return
        hasAutoChecked = true
        checkForUpdates()
    }

    /**
     * Consulta el manifiesto OTA con reintentos. Si la red falla, se usa el último manifiesto
     * validado (con TTL de 7 días). Si tampoco hay uno fresco, se entra en modo fail-closed:
     * la app se bloquea con un aviso en vez de seguir ejecutando una versión no verificable.
     */
    suspend fun checkForUpdates(): Result<UpdateInfo> {
        var lastError: Throwable? = null
        for (attempt in 0 until CHECK_RETRIES) {
            val outcome = runCatching { fetchUpdateInfo() }
            outcome.getOrNull()?.let { info ->
                _updateInfo.value = info
                runCatching {
                    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(KEY_MANIFEST, updateJson.encodeToString(lastFetchedManifest))
                        .putLong(KEY_CHECKED_AT, System.currentTimeMillis())
                        .apply()
                }
                return Result.success(info)
            }
            lastError = outcome.exceptionOrNull()
            if (attempt < CHECK_RETRIES - 1) delay(CHECK_RETRY_DELAY_MS * (attempt + 1))
        }
        return Result.success(failClosedUpdateInfo(lastError))
    }

    /** Manifiesto crudo de la última consulta correcta; se cachea para uso offline. */
    private var lastFetchedManifest: UpdateManifestDto = UpdateManifestDto()

    private fun failClosedUpdateInfo(lastError: Throwable?): UpdateInfo {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedJson = prefs.getString(KEY_MANIFEST, null)
        val checkedAt = prefs.getLong(KEY_CHECKED_AT, 0L)
        val fresh = cachedJson != null && System.currentTimeMillis() - checkedAt <= MAX_MANIFEST_AGE_MS
        val cached = if (fresh) runCatching {
            updateJson.decodeFromString<UpdateManifestDto>(cachedJson!!)
        }.getOrNull() else null
        if (cached == null) {
            android.util.Log.e("AppUpdateManager", "Sin manifiesto verificable; bloqueando por seguridad", lastError)
            return UpdateInfo(
                hasUpdate = true,
                isForceUpdate = true,
                isKillSwitch = true,
                currentVersion = currentVersionName(),
                latestVersion = "desconocida",
                releaseNotes = context.getString(R.string.update_unverifiable),
                apkUrl = null,
                apkSha256 = null
            )
        }
        return buildUpdateInfo(cached)
    }

    private fun currentVersionName(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0" }
            .getOrDefault("1.0")

    private suspend fun fetchUpdateInfo(): UpdateInfo {
        val remoteVersion = manifestApi.getLatestVersion()
        if (remoteVersion.signatures.isNotEmpty()) {
            ServerVerificationUtils.updateSignatures(remoteVersion.signatures)
        }
        lastFetchedManifest = remoteVersion
        return buildUpdateInfo(remoteVersion)
    }

    private fun buildUpdateInfo(remoteVersion: UpdateManifestDto): UpdateInfo {
        val pInfo = runCatching { context.packageManager.getPackageInfo(context.packageName, 0) }.getOrNull()
        val currentVer = pInfo?.versionName ?: "1.0"
        val currentVersionCode = pInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
        val isNewer = if (remoteVersion.versionCode > 0L) {
            remoteVersion.versionCode > currentVersionCode
        } else {
            isVersionNewer(currentVer.removePrefix("v").trim(), remoteVersion.versionName.removePrefix("v").trim())
        }
        val isKillSwitch = remoteVersion.killSwitch
        val isForceUpdate = isKillSwitch ||
            (remoteVersion.minSupportedVersionCode > 0L && currentVersionCode < remoteVersion.minSupportedVersionCode) ||
            (BuildConfig.ENABLE_IN_APP_UPDATER && remoteVersion.forceUpdate && isNewer)
        val hasValidUrl = !remoteVersion.apkUrl.isNullOrBlank()
        val hasUpdate = if (BuildConfig.ENABLE_IN_APP_UPDATER) {
            isKillSwitch || ((isNewer || isForceUpdate) && hasValidUrl)
        } else {
            isForceUpdate
        }
        return UpdateInfo(
            hasUpdate = hasUpdate,
            isForceUpdate = isForceUpdate,
            isKillSwitch = isKillSwitch,
            currentVersion = currentVer,
            latestVersion = remoteVersion.versionName.ifBlank { "v${remoteVersion.versionCode}" },
            releaseNotes = remoteVersion.releaseNotes,
            apkUrl = remoteVersion.apkUrl,
            apkSha256 = remoteVersion.apkSha256
        )
    }

    private fun isVersionNewer(current: String, latest: String): Boolean {
        if (current == latest) return false
        val curParts = current.split(".").mapNotNull { it.toIntOrNull() }
        val latParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val maxLen = maxOf(curParts.size, latParts.size)
        for (i in 0 until maxLen) {
            val c = curParts.getOrElse(i) { 0 }
            val l = latParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }

    suspend fun downloadAndInstall(
        apkUrl: String,
        expectedSha256: String? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> {
        if (!BuildConfig.ENABLE_IN_APP_UPDATER) {
            return Result.failure(IllegalStateException("Self-update disabled in this build"))
        }
        _isDownloading.value = true
        _downloadProgress.value = 0f
        return withContext(Dispatchers.IO) {
            runCatching {
                val downloadUri = runCatching { Uri.parse(apkUrl) }.getOrNull()
                    ?: throw IllegalArgumentException("Invalid apkUrl")
                // Fail-closed: solo HTTPS y solo el host de los GitHub Releases del proyecto.
                if (!downloadUri.scheme.equals("https", ignoreCase = true) ||
                    downloadUri.host !in ALLOWED_DOWNLOAD_HOSTS
                ) {
                    throw IllegalArgumentException("Untrusted apkUrl: $apkUrl")
                }

                // Fail-closed: sin hash SHA-256 válido no se instala nada.
                if (expectedSha256 == null || !SHA256_REGEX.matches(expectedSha256)) {
                    throw IllegalArgumentException("Missing or invalid APK SHA-256 in update manifest")
                }

                // Cliente propio sin redirects automáticos: un 302 a otro host/scheme eludiría
                // el allowlist. GitHub Releases responde siempre 302 hacia
                // objects.githubusercontent.com, así que se sigue el salto a mano validando
                // cada Location contra el mismo allowlist (máx. MAX_DOWNLOAD_REDIRECTS).
                val client = NetworkClientFactory.okHttpClient.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
                executeFollowingAllowedRedirects(client, apkUrl).use { response ->
                    if (!response.isSuccessful) {
                        throw Exception(context.getString(R.string.update_error_apk_http, response.code))
                    }

                    val body = response.body ?: throw Exception(context.getString(R.string.update_error_empty_body))
                    val totalBytes = body.contentLength()
                    if (totalBytes > MAX_APK_BYTES) {
                        throw IllegalArgumentException("APK demasiado grande: $totalBytes bytes")
                    }

                    val apkFile = File(context.cacheDir, "update.apk")
                    if (apkFile.exists()) apkFile.delete()

                    body.byteStream().use { input ->
                        FileOutputStream(apkFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var downloaded = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                if (!currentCoroutineContext().isActive) throw kotlinx.coroutines.CancellationException("OTA cancelado")
                                downloaded += bytesRead
                                if (downloaded > MAX_APK_BYTES) {
                                    throw IllegalArgumentException("APK demasiado grande")
                                }
                                output.write(buffer, 0, bytesRead)
                                if (totalBytes > 0) {
                                    val prog = downloaded.toFloat() / totalBytes
                                    _downloadProgress.value = prog
                                    onProgress(prog)
                                }
                            }
                            output.flush()
                        }
                    }

                    if (apkFile.length() == 0L) {
                        apkFile.delete()
                        throw Exception(context.getString(R.string.update_error_empty_body))
                    }

                    // Verificación de integridad obligatoria (fail-closed, validada arriba).
                    val actualSha256 = ServerVerificationUtils.sha256File(apkFile)
                    if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                        apkFile.delete()
                        throw Exception(context.getString(R.string.update_error_integrity))
                    }

                    // Protección anti-rollback: el APK no puede ser más antiguo que el instalado.
                    val downloadedVersion = context.packageManager
                        .getPackageArchiveInfo(apkFile.absolutePath, 0)?.longVersionCode ?: 0L
                    if (downloadedVersion < PackageInfoCompat.getLongVersionCode(
                            context.packageManager.getPackageInfo(context.packageName, 0)
                        )
                    ) {
                        apkFile.delete()
                        throw IllegalArgumentException("El APK es más antiguo que la versión instalada")
                    }

                    // Iniciar instalación nativa de Android (el instalador rechaza firmas distintas).
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                    }
                    context.startActivity(intent)
                }
            }.also {
                _isDownloading.value = false
                _downloadProgress.value = null
            }
        }
    }

    /**
     * Sigue las redirecciones de descarga a mano (OkHttp va con `followRedirects=false`) para
     * que un 302 de GitHub Releases funcione sin abrir la puerta a un Open Redirect: cada salto
     * debe ser HTTPS y aterrizar en un host del allowlist. Lanza si se agotan los saltos.
     *
     * El llamador es responsable de cerrar la respuesta devuelta.
     */
    private fun executeFollowingAllowedRedirects(client: okhttp3.OkHttpClient, apkUrl: String): Response {
        var currentUrl = apkUrl
        repeat(MAX_DOWNLOAD_REDIRECTS + 1) {
            val request = Request.Builder().url(currentUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isRedirect) return response

            response.use { redirect ->
                val location = redirect.header("Location")
                    ?: throw IllegalStateException(context.getString(R.string.update_error_redirect))
                val next = redirect.request.url.resolve(location)
                    ?: throw IllegalStateException(context.getString(R.string.update_error_redirect))
                if (!next.isHttps || next.host !in ALLOWED_DOWNLOAD_HOSTS) {
                    throw IllegalArgumentException("Untrusted redirect: $next")
                }
                currentUrl = next.toString()
            }
        }
        throw IllegalStateException(context.getString(R.string.update_error_redirect))
    }
}
