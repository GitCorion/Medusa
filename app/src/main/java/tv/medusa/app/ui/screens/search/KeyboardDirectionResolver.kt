package tv.medusa.app.ui.screens.search
import kotlin.math.abs

object KeyboardDirectionResolver {
    /**
     * Teclado español de 37 teclas. La Ñ ocupa una tecla propia; las filas no tienen por qué
     * medir lo mismo (7, 7, 6, 6, 6 y 5 teclas), y el resolver de foco lo tolera.
     */
    val KEYBOARD_ROWS = listOf(
        listOf('A', 'B', 'C', 'D', 'E', 'F', 'G'),
        listOf('H', 'I', 'J', 'K', 'L', 'M', 'Ñ'),
        listOf('N', 'O', 'P', 'Q', 'R', 'S'),
        listOf('T', 'U', 'V', 'W', 'X', 'Y'),
        listOf('Z', '1', '2', '3', '4', '5'),
        listOf('6', '7', '8', '9', '0')
    )

    val ALL_CHARS: List<Char> = ('A'..'Z') + 'Ñ' + ('0'..'9')

    val KEY_POSITIONS: Map<Char, Pair<Int, Int>> = buildMap {
        for (r in KEYBOARD_ROWS.indices) {
            for (c in KEYBOARD_ROWS[r].indices) {
                put(KEYBOARD_ROWS[r][c], r to c)
            }
        }
    }

    enum class Direction { LEFT, RIGHT, UP, DOWN }

    enum class ActionKey(val col: Float) {
        SPACE(1.0f),
        BACKSPACE(3.0f),
        CLEAR(5.0f)
    }

    /**
     * Tabla de direcciones estáticas precalculadas para el teclado completo 6x6.
     * Permite navegación O(1) instantánea sin filtros ni cálculos en tiempo de renderizado.
     */
    val STATIC_FULL_GRID_DIRECTIONS: Map<Pair<Char, Direction>, Char> = buildMap {
        for (r in KEYBOARD_ROWS.indices) {
            for (c in KEYBOARD_ROWS[r].indices) {
                val ch = KEYBOARD_ROWS[r][c]
                if (c < KEYBOARD_ROWS[r].size - 1) put(ch to Direction.RIGHT, KEYBOARD_ROWS[r][c + 1])
                if (c > 0) put(ch to Direction.LEFT, KEYBOARD_ROWS[r][c - 1])
                KEYBOARD_ROWS.getOrNull(r - 1)?.getOrNull(c)?.let { put(ch to Direction.UP, it) }
                KEYBOARD_ROWS.getOrNull(r + 1)?.getOrNull(c)?.let { put(ch to Direction.DOWN, it) }
            }
        }
    }

    /**
     * Resuelve el carácter activo más cercano en la dirección dada respecto a [currentChar].
     * Retorna null si no hay caracteres activos en esa dirección (indicando salida del teclado).
     */
    fun findNextKey(
        currentChar: Char,
        direction: Direction,
        activeChars: Set<Char>
    ): Char? {
        // Optimización O(1) para teclado completo o modo Libre
        if (activeChars.size >= ALL_CHARS.size) {
            return STATIC_FULL_GRID_DIRECTIONS[currentChar to direction]
        }

        val (r, c) = KEY_POSITIONS[currentChar] ?: return null
        return when (direction) {
            Direction.RIGHT -> {
                activeChars
                    .filter { cand -> (KEY_POSITIONS[cand]?.second ?: -1) > c }
                    .minByOrNull { cand ->
                        val (cr, cc) = KEY_POSITIONS[cand]!!
                        val dc = cc - c
                        val dr = abs(cr - r)
                        dc + dr * 1.2f
                    }
            }
            Direction.LEFT -> {
                activeChars
                    .filter { cand -> (KEY_POSITIONS[cand]?.second ?: 99) < c }
                    .minByOrNull { cand ->
                        val (cr, cc) = KEY_POSITIONS[cand]!!
                        val dc = c - cc
                        val dr = abs(cr - r)
                        dc + dr * 1.2f
                    }
            }
            Direction.UP -> {
                activeChars
                    .filter { cand -> (KEY_POSITIONS[cand]?.first ?: 99) < r }
                    .minByOrNull { cand ->
                        val (cr, cc) = KEY_POSITIONS[cand]!!
                        val dr = r - cr
                        val dc = abs(cc - c)
                        dr + dc * 1.2f
                    }
            }
            Direction.DOWN -> {
                activeChars
                    .filter { cand -> (KEY_POSITIONS[cand]?.first ?: -1) > r }
                    .minByOrNull { cand ->
                        val (cr, cc) = KEY_POSITIONS[cand]!!
                        val dr = cr - r
                        val dc = abs(cc - c)
                        dr + dc * 1.2f
                    }
            }
        }
    }

    /**
     * Resuelve la tecla de acción activa más cercana al pulsar ABAJO desde una letra [currentChar].
     */
    fun findNextActionKey(
        currentChar: Char,
        activeActions: Set<ActionKey>
    ): ActionKey? {
        if (activeActions.isEmpty()) return null
        val (r, c) = KEY_POSITIONS[currentChar] ?: return null
        return activeActions.minByOrNull { action ->
            val dr = KEYBOARD_ROWS.size - r
            val dc = abs(action.col - c.toFloat())
            dr + dc * 1.2f
        }
    }

    /**
     * Resuelve la letra activa más cercana al pulsar ARRIBA desde una tecla de acción en [actionCol].
     */
    fun findNextLetterFromAction(
        actionCol: Float,
        activeChars: Set<Char>
    ): Char? {
        if (activeChars.isEmpty()) return null
        return activeChars.minByOrNull { cand ->
            val (cr, cc) = KEY_POSITIONS[cand] ?: (0 to 0)
            val dr = KEYBOARD_ROWS.size - cr
            val dc = abs(cc.toFloat() - actionCol)
            dr + dc * 1.2f
        }
    }
}
