package tv.medusa.app.data.remote.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
 * Manifiesto OTA servido por Firebase Hosting en
 * `https://medusa-fd388.web.app/api/version.json`.
 *
 * El repositorio de GitHub es privado, por lo que los assets de sus Releases no son descargables
 * sin autenticación: el canal público de actualización es Firebase.
 */
@Serializable
data class FirebaseVersionDto(
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

interface FirebaseUpdateApiService {
    @GET("api/version.json")
    suspend fun getLatestVersion(): FirebaseVersionDto
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

    private val firebaseApi: FirebaseUpdateApiService =
        NetworkClientFactory.createService(ApiConfig.FIREBASE_OTA_BASE_URL, FirebaseUpdateApiService::class.java)

    companion object {
        private const val CHECK_RETRIES = 3
        private const val CHECK_RETRY_DELAY_MS = 3_000L

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

        val remoteVersion = firebaseApi.getLatestVersion()
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

        // El manifiesto OTA describe la beta. La build de Play NO debe interpretar su
        // versionCode/apkUrl como una actualización: solo comparte el kill-switch y la versión
        // mínima soportada. Si no, Play vería la beta (1_000_091) como "más nueva" y ofrecería
        // descargar un APK de Firebase, que es justo lo que Play prohíbe.
        val hasUpdate = if (BuildConfig.ENABLE_IN_APP_UPDATER) {
            isKillSwitch || ((isNewer || isForceUpdate) && hasValidUrl)
        } else {
            // Play: solo bloqueos globales (kill-switch o versión mínima). Nunca "hay una
            // versión nueva" basada en el manifiesto de la beta.
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
        // Defensa en profundidad: la build de Play nunca debe descargar/instalar un APK propio.
        if (!BuildConfig.ENABLE_IN_APP_UPDATER) {
            return Result.failure(IllegalStateException("Self-update disabled in Play flavor"))
        }
        _isDownloading.value = true
        _downloadProgress.value = 0f
        return withContext(Dispatchers.IO) {
            runCatching {
                if (!apkUrl.startsWith("http://") && !apkUrl.startsWith("https://")) {
                    throw IllegalArgumentException("Invalid apkUrl scheme: $apkUrl")
                }

                val isDirectApk = apkUrl.endsWith(".apk", ignoreCase = true) ||
                        apkUrl.contains(".apk?", ignoreCase = true) ||
                        apkUrl.contains("/download", ignoreCase = true)

                if (!isDirectApk) {
                    // Portal externo (p. ej. Firebase App Distribution): abrir en navegador.
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(apkUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(browserIntent)
                    return@runCatching
                }

                // Cliente compartido (timeouts y configuración de red coherentes con el resto de la app).
                val request = Request.Builder().url(apkUrl).build()
                NetworkClientFactory.okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw Exception(context.getString(R.string.update_error_apk_http, response.code))
                    }

                    val body = response.body ?: throw Exception(context.getString(R.string.update_error_empty_body))
                    val totalBytes = body.contentLength()

                    val apkFile = File(context.cacheDir, "update.apk")
                    if (apkFile.exists()) apkFile.delete()

                    body.byteStream().use { input ->
                        FileOutputStream(apkFile).use { output ->
                            val buffer = ByteArray(8192)
                            var bytesRead: Int
                            var downloaded = 0L
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloaded += bytesRead
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

                    // Verificación de integridad: el SHA-256 del APK descargado debe coincidir con el
                    // declarado en version.json (canal de confianza por HTTPS). Evita instalar un APK alterado.
                    if (!expectedSha256.isNullOrBlank()) {
                        val actualSha256 = ServerVerificationUtils.sha256File(apkFile)
                        if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                            apkFile.delete()
                            throw Exception(context.getString(R.string.update_error_integrity))
                        }
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
