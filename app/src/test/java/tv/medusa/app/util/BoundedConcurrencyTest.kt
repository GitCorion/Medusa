package tv.medusa.app.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class BoundedConcurrencyTest {

    @Test
    fun `forEachBounded ejecuta todos los elementos respetando el limite`() = runTest {
        val items = (1..20).toList()
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)
        val done = AtomicInteger(0)

        items.forEachBounded(concurrency = 4) {
            val current = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { maxOf(it, current) }
            delay(10)
            done.incrementAndGet()
            inFlight.decrementAndGet()
        }

        assertEquals(20, done.get())
        assertTrue("No debe superar el límite (max=$maxInFlight)", maxInFlight.get() <= 4)
        assertTrue("Debería paralelizar (max=$maxInFlight)", maxInFlight.get() >= 2)
    }

    @Test
    fun `forEachBounded con concurrencia 1 ejecuta en orden y sin solapamiento`() = runTest {
        val items = (1..5).toList()
        val order = mutableListOf<Int>()
        val inFlight = AtomicInteger(0)
        val maxInFlight = AtomicInteger(0)

        items.forEachBounded(concurrency = 1) {
            val current = inFlight.incrementAndGet()
            maxInFlight.updateAndGet { maxOf(it, current) }
            order.add(it)
            delay(1)
            inFlight.decrementAndGet()
        }

        assertEquals(listOf(1, 2, 3, 4, 5), order)
        assertEquals(1, maxInFlight.get())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `forEachBounded rechaza concurrencia invalida`() = runTest {
        listOf(1).forEachBounded(concurrency = 0) { }
    }
}
