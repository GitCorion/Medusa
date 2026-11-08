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
        tv.medusa.app.util.DeviceUtils.getStableDeviceId(this)
        tv.medusa.app.data.remote.NetworkClientFactory.initCache(this)
        // Índice de descubrimiento: refresco diario + pasada al arrancar (con TTL interno).
        CollectionIndexScheduler.scheduleDaily(this)
        CollectionIndexScheduler.enqueueNow(this)
    }
}
