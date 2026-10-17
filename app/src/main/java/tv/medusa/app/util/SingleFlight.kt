package tv.medusa.app.util

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Agrupa llamadas **simultáneas** con la misma [key]: la primera ejecuta [block] y las demás esperan
 * su resultado en vez de repetir el trabajo (normalmente una consulta al servidor).
 *
 * Las cachés con TTL ya evitan repetir consultas *consecutivas*, pero no cubren el caso *concurrente*
 * —el que se daba al arrancar la Home—: el feed pedía "Continuar Viendo" y, unos milisegundos después,
 * el refresco al volver a la pantalla pedía lo mismo; el segundo no veía la caché porque aún estaba
 * vacía y salían dos consultas (Resume + NextUp) contra el servidor.
 *
 * No cachea nada: en cuanto [block] termina, la siguiente llamada vuelve a ejecutarlo (para eso están
 * las cachés con TTL). Es deliberado que sea así: aquí solo se evita el trabajo *duplicado en vuelo*.
 */
class SingleFlight {

    private val mutex = Mutex()
    private val inFlight = HashMap<String, CompletableDeferred<Any?>>()

    @Suppress("UNCHECKED_CAST")
    suspend fun <T : Any> run(key: String, block: suspend () -> T): T {
        val deferred: CompletableDeferred<Any?>
        val isOwner: Boolean

        mutex.withLock {
            val existing = inFlight[key]
            if (existing != null) {
                deferred = existing
                isOwner = false
            } else {
                deferred = CompletableDeferred()
                inFlight[key] = deferred
                isOwner = true
            }
        }

        if (!isOwner) {
            return try {
                deferred.await() as T
            } catch (e: CancellationException) {
                // Si el que espera sigue activo, la cancelación vino de quien lo pidió primero
                // (por ejemplo, la pantalla que lo lanzó se cerró): lo hacemos nosotros.
                if (currentCoroutineContext().isActive) block() else throw e
            } catch (_: Throwable) {
                // El primer intento falló: cada espera obtiene su propio resultado.
                block()
            }
        }

        return try {
            block().also { deferred.complete(it) }
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            mutex.withLock { inFlight.remove(key) }
        }
    }
}
