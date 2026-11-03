package tv.medusa.app.data.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Programación del indexador de descubrimiento: una pasada diaria en background y una
 * pasada al arrancar (que se salta si el índice sigue fresco).
 */
object CollectionIndexScheduler {

    private const val PERIODIC_NAME = "CollectionIndexPeriodic"

    fun scheduleDaily(context: Context) {
        val request = PeriodicWorkRequestBuilder<CollectionIndexWorker>(1, TimeUnit.DAYS)
            .setConstraints(networkConstraints())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun enqueueNow(context: Context, force: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<CollectionIndexWorker>()
            .setConstraints(networkConstraints())
            .setInputData(workDataOf(CollectionIndexWorker.KEY_FORCE to force))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            CollectionIndexWorker.WORK_NAME,
            if (force) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Reconstruye solo una plataforma. Se usa al abrir una plataforma cuyo índice aún no
     * existe, para que no haya que esperar al ciclo diario.
     */
    fun enqueuePlatformNow(context: Context, platformId: String) {
        val request = OneTimeWorkRequestBuilder<CollectionIndexWorker>()
            .setConstraints(networkConstraints())
            .setInputData(
                workDataOf(
                    CollectionIndexWorker.KEY_FORCE to false,
                    CollectionIndexWorker.KEY_PLATFORM_ID to platformId
                )
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${CollectionIndexWorker.WORK_NAME}_$platformId",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    private fun networkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
}
