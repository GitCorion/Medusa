package tv.medusa.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión del bug de paginación: con un servidor que devuelve páginas **más cortas** que el
 * `Limit` pedido (p. ej. 110 de 200) el sync daba la vista por terminada y truncaba el catálogo
 * a las primeras centenas de títulos (síntoma "solo me aparecen ~110").
 */
class SyncPaginationTest {

    @Test
    fun `pagina corta con total conocido sigue pidiendo paginas`() {
        // El servidor devolvió 110 items de los 200 pedidos, pero quedan 29.890 por llegar.
        assertTrue(hasMorePages(itemsOnPage = 110, startIndex = 110, pageSize = 200, totalRecordCount = 30_000))
    }

    @Test
    fun `ultima pagina incompleta con total conocido termina`() {
        // Ya hemos recorrido los 30.000 del total.
        assertFalse(hasMorePages(itemsOnPage = 90, startIndex = 30_000, pageSize = 200, totalRecordCount = 30_000))
    }

    @Test
    fun `pagina vacia termina aunque el total diga que queda`() {
        assertFalse(hasMorePages(itemsOnPage = 0, startIndex = 500, pageSize = 200, totalRecordCount = 30_000))
    }

    @Test
    fun `sin total, pagina llena continua`() {
        assertTrue(hasMorePages(itemsOnPage = 200, startIndex = 200, pageSize = 200, totalRecordCount = 0))
    }

    @Test
    fun `sin total, pagina corta tambien continua (se sondea una mas)`() {
        // No nos fiamos de la página corta: el servidor puede estar capando el tamaño.
        assertTrue(hasMorePages(itemsOnPage = 110, startIndex = 110, pageSize = 200, totalRecordCount = 0))
    }

    @Test
    fun `sin total, pagina vacia termina`() {
        assertFalse(hasMorePages(itemsOnPage = 0, startIndex = 110, pageSize = 200, totalRecordCount = 0))
    }

    @Test
    fun `pagina llena con total conocido continua`() {
        assertTrue(hasMorePages(itemsOnPage = 200, startIndex = 200, pageSize = 200, totalRecordCount = 5_000))
    }

    @Test
    fun `delta con total conocido y pagina corta continua`() {
        assertTrue(hasMorePages(itemsOnPage = 40, startIndex = 40, pageSize = 200, totalRecordCount = 500))
    }
}
