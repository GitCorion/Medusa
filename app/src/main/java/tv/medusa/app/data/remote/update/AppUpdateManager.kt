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
import okhttp3.Request
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
        private val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")
        private val ALLOWED_DOWNLOAD_HOSTS = setOf(
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com"
        )

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
     * Consulta el manifiesto OTA con reintentos ante fallos de red transitorios (WiFi de TV).
     * Devuelve el último [UpdateInfo] válido; en caso de fallo persistente, un [Result] fallido
     * (la app simplemente no mostrará el aviso, nunca bloqueará por un error de red).
     */
    suspend fun checkForUpdates(): Result<UpdateInfo> {
        var lastError: Throwable? = null
        for (attempt in 0 until CHECK_RETRIES) {
            val outcome = runCatching { fetchUpdateInfo() }
            outcome.getOrNull()?.let { info ->
                _updateInfo.value = info
                return Result.success(info)
            }
            lastError = outcome.exceptionOrNull()
            if (attempt < CHECK_RETRIES - 1) delay(CHECK_RETRY_DELAY_MS * (attempt + 1))
        }
        return Result.failure(lastError ?: IllegalStateException("Update check failed"))
    }

    private suspend fun fetchUpdateInfo(): UpdateInfo {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val currentVer = pInfo.versionName ?: "1.0"
        val currentVersionCode = PackageInfoCompat.getLongVersionCode(pInfo)

        val remoteVersion = manifestApi.getLatestVersion()
        if (remoteVersion.signatures.isNotEmpty()) {
            ServerVerificationUtils.updateSignatures(remoteVersion.signatures)
        }

        val isNewer = if (remoteVersion.versionCode > 0L) {
            remoteVersion.versionCode > currentVersionCode
        } else {
            val cleanRemote = remoteVersion.versionName.removePrefix("v").trim()
            val cleanCurrent = currentVer.removePrefix("v").trim()
            isVersionNewer(cleanCurrent, cleanRemote)
        }

        // Kill-switch global: corta el acceso a TODAS las versiones instaladas, exista o no un
        // reemplazo que descargar.
        val isKillSwitch = remoteVersion.killSwitch

        val isForceUpdate = isKillSwitch ||
            (remoteVersion.minSupportedVersionCode > 0L && currentVersionCode < remoteVersion.minSupportedVersionCode) ||
            (BuildConfig.ENABLE_IN_APP_UPDATER && remoteVersion.forceUpdate && isNewer)

        val hasValidUrl = !remoteVersion.apkUrl.isNullOrBlank()

        // Distribución pública por GitHub Releases: el manifiesto publicado por el workflow
        // describe siempre la última release y su asset APK.
        val hasUpdate = if (BuildConfig.ENABLE_IN_APP_UPDATER) {
            isKillSwitch || ((isNewer || isForceUpdate) && hasValidUrl)
        } else {
            isForceUpdate
        }

        val info = UpdateInfo(
            hasUpdate = hasUpdate,
            isForceUpdate = isForceUpdate,
            isKillSwitch = isKillSwitch,
            currentVersion = currentVer,
            latestVersion = remoteVersion.versionName.ifBlank { "v${remoteVersion.versionCode}" },
            releaseNotes = remoteVersion.releaseNotes,
            apkUrl = remoteVersion.apkUrl,
            apkSha256 = remoteVersion.apkSha256
        )
        return info
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

                // Cliente propio sin redirects: un 302 a otro host/scheme eludiría el allowlist.
                val client = NetworkClientFactory.okHttpClient.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()
                val request = Request.Builder().url(apkUrl).build()
                client.newCall(request).execute().use { response ->
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
}
