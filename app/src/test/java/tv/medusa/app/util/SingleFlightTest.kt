package tv.medusa.app.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SingleFlightTest {

    @Test
    fun `agrupa llamadas simultaneas con la misma clave`() = runTest {
        val singleFlight = SingleFlight()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val executions = AtomicInteger(0)

        val first = async {
            singleFlight.run("clave") {
                executions.incrementAndGet()
                entered.complete(Unit)
                release.await()
                "único"
            }
        }
        entered.await()

        val second = async {
            singleFlight.run("clave") {
                executions.incrementAndGet()
                "otro"
            }
        }
        yield()
        release.complete(Unit)

        assertEquals("único", first.await())
        assertEquals("único", second.await())
        assertEquals(1, executions.get())
    }

    @Test
    fun `no cachea nada, la siguiente llamada se ejecuta de nuevo`() = runTest {
        val singleFlight = SingleFlight()
        val executions = AtomicInteger(0)

        val first = singleFlight.run("clave") { executions.incrementAndGet(); "primera" }
        val second = singleFlight.run("clave") { executions.incrementAndGet(); "segunda" }

        assertEquals("primera", first)
        assertEquals("segunda", second)
        assertEquals(2, executions.get())
    }

    @Test
    fun `claves distintas no se agrupan`() = runTest {
        val singleFlight = SingleFlight()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val executions = AtomicInteger(0)

        val first = async {
            singleFlight.run("a") {
                executions.incrementAndGet()
                entered.complete(Unit)
                release.await()
                "a"
            }
        }
        entered.await()

        val second = async { singleFlight.run("b") { executions.incrementAndGet(); "b" } }
        yield()
        release.complete(Unit)

        assertEquals("a", first.await())
        assertEquals("b", second.await())
        assertEquals(2, executions.get())
    }

    @Test
    fun `si el primero se cancela, el que esperaba hace el trabajo`() = runTest {
        val singleFlight = SingleFlight()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val executions = AtomicInteger(0)

        val owner = launch {
            singleFlight.run("clave") {
                executions.incrementAndGet()
                entered.complete(Unit)
                release.await()
                "del primero"
            }
        }
        entered.await()

        val waiter = async {
            singleFlight.run("clave") {
                executions.incrementAndGet()
                "del segundo"
            }
        }
        yield()
        owner.cancel()
        advanceUntilIdle()

        assertEquals("del segundo", waiter.await())
        assertEquals(2, executions.get())
    }

    @Test
    fun `si el primero falla, el que esperaba obtiene su propio resultado`() = runTest {
        val singleFlight = SingleFlight()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val executions = AtomicInteger(0)

        val owner = launch {
            runCatching {
                singleFlight.run("clave") {
                    executions.incrementAndGet()
                    entered.complete(Unit)
                    release.await()
                    error("fallo del primero")
                }
            }
        }
        entered.await()

        val waiter = async {
            singleFlight.run("clave") { executions.incrementAndGet(); "del segundo" }
        }
        yield()
        release.complete(Unit)
        advanceUntilIdle()

        assertEquals("del segundo", waiter.await())
        assertEquals(2, executions.get())
    }

    @Test
    fun `la excepcion del primero llega a quien la pidio`() = runTest {
        val singleFlight = SingleFlight()

        val result = runCatching {
            singleFlight.run<Unit>("clave") { error("boom") }
        }

        assertEquals("boom", result.exceptionOrNull()?.message)
    }
}
