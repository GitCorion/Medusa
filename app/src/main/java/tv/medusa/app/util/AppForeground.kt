package tv.medusa.app.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Visibilidad de la app, para que el trabajo de fondo ceda el paso a lo que el usuario espera.
 *
 * `MainActivity` la actualiza en `onStart`/`onStop`. El enriquecido de metadatos la consulta para
 * bajar su ritmo mientras la app está abierta: el servidor doméstico suele ir justo y, si no, las
 * olas de metadatos compiten con las consultas que el usuario está mirando en ese momento.
 *
 * Por defecto es `false` (ritmo alto): si el worker corre sin actividad delante no hay nada con lo
 * que competir.
 */
object AppForeground {
    private val _isForegroundFlow = MutableStateFlow(false)
    val isForegroundFlow: StateFlow<Boolean> = _isForegroundFlow.asStateFlow()

    var isVisible: Boolean
        get() = _isForegroundFlow.value
        set(value) {
            _isForegroundFlow.value = value
        }
}
