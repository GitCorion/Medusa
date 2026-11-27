package tv.medusa.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import tv.medusa.app.data.local.UserPreferencesRepository
import tv.medusa.app.data.repository.MedusaServerSync
import tv.medusa.app.data.worker.CollectionIndexScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MedusaApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var userPreferencesRepository: UserPreferencesRepository

    private val bootstrapScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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
        // El servidor puede aportar la clave TMDB y las credenciales Trakt: se aplican antes
        // de que el usuario abra Ajustes, sin pedirle que repita el QR en esta TV/tablet.
        bootstrapScope.launch {
            MedusaServerSync.apply(userPreferencesRepository)
        }
    }
}
