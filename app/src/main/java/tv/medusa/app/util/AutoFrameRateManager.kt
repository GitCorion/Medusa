package tv.medusa.app.util

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.Display
import kotlin.math.abs

/**
 * Gestor de Auto Frame Rate (AFR) para Android TV.
 * Conmuta automáticamente la tasa de refresco del televisor (ej. 24 Hz, 50 Hz, 60 Hz)
 * según los fotogramas por segundo (fps) del vídeo para eliminar el judder 3:2.
 */
object AutoFrameRateManager {

    private const val TAG = "AutoFrameRateManager"
    private var originalModeId: Int = 0

    fun matchFrameRate(activity: Activity?, videoFps: Float) {
        if (activity == null || videoFps <= 15f || videoFps > 120f) return
        val window = activity.window ?: return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    activity.display
                } else {
                    @Suppress("DEPRECATION")
                    activity.windowManager.defaultDisplay
                } ?: return

                val supportedModes = display.supportedModes ?: return
                if (supportedModes.isEmpty()) return

                if (originalModeId == 0) {
                    originalModeId = window.attributes.preferredDisplayModeId
                }

                val currentMode = display.mode ?: return
                val targetRefreshRates = when {
                    // Cine (23.976 / 24 fps) -> 24Hz o múltiplos enteros (48Hz, 120Hz)
                    videoFps in 23.0f..24.5f -> listOf(24.0f, 23.976f, 48.0f, 120.0f)
                    // PAL / Europa (25 / 50 fps) -> 50Hz o 100Hz
                    videoFps in 24.8f..25.2f || videoFps in 49.5f..50.5f -> listOf(50.0f, 100.0f)
                    // NTSC / 30 / 60 fps -> 60Hz o 59.94Hz
                    else -> listOf(60.0f, 59.94f)
                }

                // Buscar modo en la misma resolución que tenga la tasa de refresco deseada
                val bestMode = supportedModes
                    .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
                    .firstOrNull { mode ->
                        targetRefreshRates.any { target -> abs(mode.refreshRate - target) < 0.15f }
                    }

                if (bestMode != null && bestMode.modeId != currentMode.modeId) {
                    Log.i(TAG, "Conmutando tasa de refresco para ${videoFps}fps -> Modo ${bestMode.modeId} (${bestMode.refreshRate}Hz)")
                    val params = window.attributes
                    params.preferredDisplayModeId = bestMode.modeId
                    window.attributes = params
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "No se pudo cambiar la tasa de refresco: ${t.message}")
        }
    }

    fun restoreOriginal(activity: Activity?) {
        if (activity == null) return
        val window = activity.window ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val params = window.attributes
                if (params.preferredDisplayModeId != originalModeId) {
                    params.preferredDisplayModeId = originalModeId
                    window.attributes = params
                    Log.i(TAG, "Tasa de refresco restaurada a modo original: $originalModeId")
                }
                // Olvidar el modo: si el usuario cambia la tasa desde los ajustes de la TV,
                // la próxima sesión debe tomar el nuevo valor como original.
                originalModeId = 0
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error restaurando tasa de refresco: ${t.message}")
        }
    }
}
