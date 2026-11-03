package tv.medusa.app.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import tv.medusa.app.domain.usecase.CollectionIndexUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Construye/refresca el índice de descubrimiento (plataformas, tendencias, popular regional)
 * en segundo plano. Se programa a diario y también al arrancar; el caso de uso decide si
 * realmente hay que reconstruir (TTL + versión del catálogo).
 */
@HiltWorker
class CollectionIndexWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val collectionIndexUseCase: CollectionIndexUseCase
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "CollectionIndexWork"
        const val KEY_FORCE = "force"
        const val KEY_PLATFORM_ID = "platform_id"
    }

    override suspend fun doWork(): Result {
        return try {
            val force = inputData.getBoolean(KEY_FORCE, false)
            val platformId = inputData.getString(KEY_PLATFORM_ID)
            val built = collectionIndexUseCase.ensureIndex(
                force = force,
                onlyPlatformId = platformId
            )
            Log.d("CollectionIndex", "Índice actualizado: $built elementos")
            Result.success()
        } catch (e: Exception) {
            Log.e("CollectionIndex", "Error construyendo índice", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
