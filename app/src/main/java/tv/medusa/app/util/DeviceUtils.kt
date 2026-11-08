package tv.medusa.app.util

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build

object DeviceUtils {

    private const val PREFS_DEVICE = "medusa_device_identity"
    private const val KEY_STABLE_DEVICE_ID = "stable_device_id"

    @Volatile
    var cachedDeviceId: String? = null
        private set

    /**
     * Retorna un ID de dispositivo único y estable por instalación (persiste entre reinicios y sesiones).
     */
    fun getStableDeviceId(context: Context): String {
        cachedDeviceId?.let { return it }
        synchronized(this) {
            cachedDeviceId?.let { return it }
            val prefs = context.applicationContext.getSharedPreferences(PREFS_DEVICE, Context.MODE_PRIVATE)
            var id = prefs.getString(KEY_STABLE_DEVICE_ID, null)
            if (id.isNullOrBlank()) {
                id = java.util.UUID.randomUUID().toString()
                prefs.edit().putString(KEY_STABLE_DEVICE_ID, id).apply()
            }
            cachedDeviceId = id
            return id
        }
    }

    fun isEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT?.lowercase() ?: ""
        val model = Build.MODEL?.lowercase() ?: ""
        val brand = Build.BRAND?.lowercase() ?: ""
        val device = Build.DEVICE?.lowercase() ?: ""
        val product = Build.PRODUCT?.lowercase() ?: ""
        val hardware = Build.HARDWARE?.lowercase() ?: ""
        val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""

        return (brand.startsWith("generic") && device.startsWith("generic"))
                || fingerprint.startsWith("generic")
                || fingerprint.startsWith("unknown")
                || hardware.contains("goldfish")
                || hardware.contains("ranchu")
                || model.contains("google_sdk")
                || model.contains("emulator")
                || model.contains("android sdk built for")
                || manufacturer.contains("genymotion")
                || product.contains("sdk_google")
                || product.contains("google_sdk")
                || product.contains("sdk")
                || product.contains("vbox86p")
                || product.contains("simulator")
    }

    /**
     * Detecta automáticamente si el dispositivo es de bajos recursos (RAM baja, POCOs núcleos o bandera isLowRamDevice).
     */
    fun isLowResourceDevice(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        if (activityManager?.isLowRamDevice == true) {
            return true
        }

        val memInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memInfo)
        val totalMemMb = memInfo.totalMem / (1024 * 1024)

        val cpuCores = Runtime.getRuntime().availableProcessors()

        // Dispositivos TV con menos de 2.5GB de RAM o <= 2 núcleos se consideran de recursos limitados
        return totalMemMb < 2560 || cpuCores <= 2
    }

    /**
     * Retorna un factor de multiplicación (0.25f a 1.0f) para escalar la densidad de partículas y efectos visuales.
     */
    fun getRecommendedParticleScale(context: Context): Float {
        return if (isLowResourceDevice(context)) 0.3f else 1.0f
    }

    /**
     * Determina si el dispositivo actual es una televisión (Android TV / Google TV / Leanback).
     */
    fun isTvDevice(context: Context): Boolean {
        val pm = context.packageManager
        val isLeanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val isTelevision = pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTvUiMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        val hasTouch = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

        return isLeanback || isTelevision || isTvUiMode || !hasTouch
    }

    /**
     * Determina si el dispositivo admite entrada táctil activa (tablets, teléfonos) y no es una TV pura.
     */
    fun isTouchDevice(context: Context): Boolean {
        val pm = context.packageManager
        val hasTouch = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
        val isLeanbackOnly = pm.hasSystemFeature("android.software.leanback_only")
        val isLeanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        val isTvUiMode = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION

        return hasTouch && !isTvUiMode && !isLeanbackOnly && !isLeanback
    }
}

