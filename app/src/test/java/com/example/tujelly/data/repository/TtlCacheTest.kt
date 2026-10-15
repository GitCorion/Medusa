package com.example.tujelly.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtlCacheTest {

    @Test
    fun `devuelve el valor antes de expirar y null despues`() {
        var now = 0L
        val cache = TtlCache<String, Int>(defaultTtlMillis = 1000, timeSource = { now })

        cache.put("a", 1)
        assertEquals(1, cache.get("a"))

        now = 1000
        assertEquals(1, cache.get("a")) // todavía vigente (empate exacto)

        now = 1001
        assertNull(cache.get("a"))
    }

    @Test
    fun `respeta el tamano maximo y desaloja la entrada mas antigua`() {
        var now = 0L
        val cache = TtlCache<Int, Int>(defaultTtlMillis = 10_000, maxSize = 3, timeSource = { now })

        cache.put(1, 1)
        now = 10
        cache.put(2, 2)
        now = 20
        cache.put(3, 3)
        now = 30
        // Supera la cota: purga (nada caducado) y desaloja la más antigua (clave 1).
        cache.put(4, 4)

        assertTrue("size=${cache.size}", cache.size <= 3)
        assertNull(cache.get(1))
        assertEquals(4, cache.get(4))
    }

    @Test
    fun `purgeExpired elimina solo las caducadas`() {
        var now = 0L
        val cache = TtlCache<Int, Int>(defaultTtlMillis = 1000, maxSize = 100, timeSource = { now })

        cache.put(1, 1)
        now = 2000
        cache.put(2, 2)

        cache.purgeExpired()

        assertNull(cache.get(1))
        assertEquals(2, cache.get(2))
        assertEquals(1, cache.size)
    }

    @Test
    fun `reemplazar una clave existente no consume cupo extra`() {
        val cache = TtlCache<String, Int>(defaultTtlMillis = 10_000, maxSize = 2)

        cache.put("a", 1)
        cache.put("b", 2)
        cache.put("a", 3) // reemplazo, no crecimiento

        assertEquals(2, cache.size)
        assertEquals(3, cache.get("a"))
        assertEquals(2, cache.get("b"))
    }
}
