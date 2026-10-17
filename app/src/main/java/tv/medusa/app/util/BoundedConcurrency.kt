package tv.medusa.app.util

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Ejecuta [block] sobre cada elemento de la colección con, como máximo, [concurrency]
 * ejecuciones simultáneas.
 *
 * Pensado para trabajo de I/O de fondo (p. ej. enriquecido de metadatos) donde interesa
 * paralelizar sin saturar al servidor: se limita el número de peticiones en vuelo en vez de
 * intercalar esperas fijas (`delay`) entre elementos.
 *
 * Suspende hasta que TODO el trabajo haya terminado (envuelve en `coroutineScope`). Si
 * [concurrency] <= 1, se ejecuta estrictamente en secuencia.
 */
suspend fun <T> Iterable<T>.forEachBounded(concurrency: Int, block: suspend (T) -> Unit) {
    require(concurrency >= 1) { "concurrency debe ser >= 1" }
    val items = this
    if (concurrency == 1) {
        for (item in items) block(item)
        return
    }
    coroutineScope {
        val semaphore = Semaphore(concurrency)
        for (item in items) {
            launch { semaphore.withPermit { block(item) } }
        }
    }
}
