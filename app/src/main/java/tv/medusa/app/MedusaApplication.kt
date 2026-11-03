package tv.medusa.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import tv.medusa.app.data.worker.CollectionIndexScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MedusaApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // Índice de descubrimiento: refresco diario + pasada al arrancar (con TTL interno).
        CollectionIndexScheduler.scheduleDaily(this)
        CollectionIndexScheduler.enqueueNow(this)
    }
}
