package com.example.tujelly.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.tujelly.data.local.UserPreferencesRepository
import com.example.tujelly.data.local.db.AppDatabase
import com.example.tujelly.data.repository.MediaRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class JellyfinSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val database: AppDatabase,
    private val mediaRepository: MediaRepository
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "JellyfinCatalogSyncWork"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_USER_ID = "user_id"
        const val KEY_TOKEN = "token"
        const val KEY_FORCE_FULL_SYNC = "force_full_sync"
    }

    override suspend fun doWork(): Result {
        val prefs = userPreferencesRepository.userPreferencesFlow.first()

        val serverUrl = inputData.getString(KEY_SERVER_URL)?.ifBlank { null }
            ?: prefs.jellyfinServerUrl
        val userId = inputData.getString(KEY_USER_ID)?.ifBlank { null }
            ?: prefs.jellyfinUserId
        val token = inputData.getString(KEY_TOKEN)?.ifBlank { null }
            ?: prefs.jellyfinAccessToken
        val forceFullSync = inputData.getBoolean(KEY_FORCE_FULL_SYNC, false)

        if (serverUrl.isBlank() || userId.isBlank() || token.isBlank()) {
            return Result.failure(workDataOf("error" to "Missing Jellyfin credentials"))
        }

        return try {
            val syncResult = mediaRepository.syncJellyfinLibrary(
                serverUrl = serverUrl,
                userId = userId,
                token = token,
                lastSyncTimestamp = if (forceFullSync) null else prefs.jellyfinLastSync,
                forceFullSync = forceFullSync
            )

            if (syncResult.isSuccess) {
                // Estadísticas del planificador SQLite: bootstrapea con ANALYZE la primera vez
                // (para que se usen los índices compuestos) y luego se mantiene con PRAGMA optimize.
                runCatching {
                    val sqliteDb = database.openHelper.writableDatabase
                    val hasStats = sqliteDb
                        .query("SELECT 1 FROM sqlite_master WHERE type='table' AND name='sqlite_stat1' LIMIT 1")
                        .use { it.moveToFirst() }
                    sqliteDb.execSQL(if (hasStats) "PRAGMA optimize" else "ANALYZE")
                }
                Result.success()
            } else {
                val exception = syncResult.exceptionOrNull()
                android.util.Log.e("JellyfinSyncWorker", "Sync failed: ${exception?.message}", exception)
                if (runAttemptCount < 3) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf("error" to (exception?.message ?: "Unknown sync error")))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("JellyfinSyncWorker", "Exception in JellyfinSyncWorker: ${e.message}", e)
            if (isStopped) {
                Result.retry()
            } else if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure(workDataOf("error" to (e.message ?: "Sync error")))
            }
        }
    }
}
