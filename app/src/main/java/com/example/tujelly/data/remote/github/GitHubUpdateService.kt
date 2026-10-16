package com.example.tujelly.data.remote.github

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.tujelly.data.remote.NetworkClientFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.http.GET
import retrofit2.http.Path
import java.io.File
import java.io.FileOutputStream

@Serializable
data class FirebaseVersionDto(
    @SerialName("versionCode") val versionCode: Long = 0L,
    @SerialName("versionName") val versionName: String = "",
    @SerialName("minSupportedVersionCode") val minSupportedVersionCode: Long = 0L,
    @SerialName("forceUpdate") val forceUpdate: Boolean = false,
    @SerialName("releaseNotes") val releaseNotes: String? = null,
    @SerialName("apkUrl") val apkUrl: String? = null
)

@Serializable
data class GitHubAssetDto(
    @SerialName("name") val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    @SerialName("size") val size: Long = 0L
)

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("name") val name: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("assets") val assets: List<GitHubAssetDto> = emptyList()
)

interface FirebaseUpdateApiService {
    @GET("api/version.json")
    suspend fun getLatestVersion(): FirebaseVersionDto
}

interface GitHubApiService {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun getLatestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GitHubReleaseDto
}

data class UpdateInfo(
    val hasUpdate: Boolean,
    val isForceUpdate: Boolean = false,
    val currentVersion: String,
    val latestVersion: String,
    val releaseNotes: String?,
    val apkUrl: String?
)

class AppUpdateManager(private val context: Context) {

    private val firebaseApi: FirebaseUpdateApiService =
        NetworkClientFactory.createService("https://medusa-fd388.web.app/", FirebaseUpdateApiService::class.java)

    companion object {
        private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
        val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

        private val _downloadProgress = MutableStateFlow<Float?>(null)
        val downloadProgress: StateFlow<Float?> = _downloadProgress.asStateFlow()

        private val _isDownloading = MutableStateFlow(false)
        val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

        private var hasAutoChecked = false
    }

    suspend fun checkAutomatically(owner: String = "GitCorion", repo: String = "Medusa") {
        if (hasAutoChecked) return
        hasAutoChecked = true
        checkForUpdates(owner, repo)
    }

    suspend fun checkForUpdates(owner: String = "GitCorion", repo: String = "Medusa"): Result<UpdateInfo> {
        return runCatching {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVer = pInfo.versionName ?: "1.0"
            val currentVersionCode = androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(pInfo)

            val remoteVersion = firebaseApi.getLatestVersion()

            val isNewer = if (remoteVersion.versionCode > 0L) {
                remoteVersion.versionCode > currentVersionCode
            } else {
                val cleanRemote = remoteVersion.versionName.removePrefix("v").trim()
                val cleanCurrent = currentVer.removePrefix("v").trim()
                isVersionNewer(cleanCurrent, cleanRemote)
            }

            val isForceUpdate = (remoteVersion.minSupportedVersionCode > 0L && currentVersionCode < remoteVersion.minSupportedVersionCode) ||
                    (remoteVersion.forceUpdate && isNewer)

            val hasValidUrl = !remoteVersion.apkUrl.isNullOrBlank()

            val info = UpdateInfo(
                hasUpdate = (isNewer || isForceUpdate) && hasValidUrl,
                isForceUpdate = isForceUpdate,
                currentVersion = currentVer,
                latestVersion = remoteVersion.versionName.ifBlank { "v${remoteVersion.versionCode}" },
                releaseNotes = remoteVersion.releaseNotes,
                apkUrl = remoteVersion.apkUrl
            )
            _updateInfo.value = info
            info
        }
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

    suspend fun downloadAndInstall(apkUrl: String, onProgress: (Float) -> Unit = {}): Result<Unit> {
        _isDownloading.value = true
        _downloadProgress.value = 0f
        return withContext(Dispatchers.IO) {
            runCatching {
                val isDirectApk = apkUrl.endsWith(".apk", ignoreCase = true) ||
                        apkUrl.contains(".apk?", ignoreCase = true) ||
                        apkUrl.contains("/download", ignoreCase = true)

                if (!isDirectApk && (apkUrl.startsWith("http://") || apkUrl.startsWith("https://"))) {
                    // Si es enlace web de Firebase App Distribution u otro portal de testers, abrir en navegador/App Tester
                    val browserIntent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(apkUrl)).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(browserIntent)
                    return@runCatching
                }

                val client = OkHttpClient()
                val request = Request.Builder().url(apkUrl).build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) throw Exception("Error al descargar APK: HTTP ${response.code}")

                val body = response.body ?: throw Exception("Respuesta vacía al descargar APK")
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

                // Iniciar instalación nativa de Android
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
            }.also {
                _isDownloading.value = false
            }
        }
    }
}
