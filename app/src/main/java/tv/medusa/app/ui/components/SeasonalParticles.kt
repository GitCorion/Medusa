package tv.medusa.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlinx.coroutines.delay
import tv.medusa.app.domain.model.ParticleType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import tv.medusa.app.ui.theme.TextPrimary

private enum class LeafShape {
    MAPLE,
    OAK,
    BIRCH
}

private class SeasonalParticle(
    var x: Float,
    var y: Float,
    val size: Float,
    val speedY: Float,
    val speedX: Float,
    val swayFrequency: Float,
    val swayAmplitude: Float,
    var rotation: Float,
    val rotationSpeed: Float,
    val flutterFrequency: Float,
    val leafShape: LeafShape,
    val alpha: Float,
    val color: Color
)

private class FlyingBatShadow(
    var x: Float,
    var y: Float,
    val size: Float,
    val speedX: Float,
    val flapSpeed: Float,
    val waveFreq: Float,
    val flapPhase: Float,
    val direction: Float, // -1f = izquierda, 1f = derecha
    val alpha: Float,
    val path: Path = Path()
)

private class HolidayStar(
    val x: Float,
    val y: Float,
    val size: Float,
    val twinkleSpeed: Float,
    val phase: Float,
    val color: Color
)

private class FloatingHeart(
    var x: Float,
    var y: Float,
    val size: Float,
    val speedY: Float,
    val swayFreq: Float,
    val swayAmp: Float,
    val color: Color,
    val alpha: Float
)

/**
 * Capa de ambientación estacional para toda la pantalla (1920x1080 / 4K).
 *
 * Diseño elegante y cinematográfico sin saturación artificial:
 * - HALLOWEEN:
 *   • Siluetas puras de sombras de murciélagos volando en el plano superior, con aleteo suave y aerodinámico.
 *   • Hojas botánicas de otoño (arce, roble y abedul) con volteo 3D y balanceo por viento.
 * - NAVIDAD:
 *   • Estrellas mágicas festivas titilantes en oro y plata.
 *   • Copos de nieve cristalinos con brazos dendríticos.
 * - SAN VALENTÍN:
 *   • Corazones flotantes etéreos y ascuas cálidas ascendentes.
 */
@Composable
fun SeasonalParticles(
    particleType: ParticleType,
    modifier: Modifier = Modifier,
    particleCount: Int = 10,
    particleScale: Float = 1.0f
) {
    if (particleType == ParticleType.NONE || particleScale <= 0.05f) return

    val effectiveCount = remember(particleCount, particleScale) {
        (particleCount * particleScale).toInt().coerceAtLeast(2)
    }

    // 1. Partículas base (hojas, copos o ascuas)
    val particles = remember(particleType, effectiveCount) {
        val rand = Random(4242)
        List(effectiveCount) {
            val leafShape = LeafShape.entries[rand.nextInt(LeafShape.entries.size)]
            val (color, size, speed) = when (particleType) {
                ParticleType.LEAVES -> {
                    val leafColors = listOf(
                        Color(0xFFEA580C), // Naranja calabaza vivo
                        Color(0xFFF59E0B), // Ámbar dorado
                        Color(0xFFDC2626), // Carmesí arce
                        Color(0xFFB45309), // Naranja herrumbre
                        Color(0xFF9A3412), // Castaño oscuro
                        Color(0xFFD97706)  // Ocre brillante
                    )
                    Triple(
                        leafColors[rand.nextInt(leafColors.size)],
                        rand.nextFloat() * 10f + 14f, // 14dp a 24dp (tamaño discreto y elegante)
                        rand.nextFloat() * 0.9f + 0.6f
                    )
                }
                ParticleType.SNOW -> {
                    val snowColors = listOf(
                        Color(0xEEFFFFFF),
                        Color(0xCCFFFFFF),
                        Color(0x99E2E8F0)
                    )
                    Triple(
                        snowColors[rand.nextInt(snowColors.size)],
                        rand.nextFloat() * 5f + 3f,
                        rand.nextFloat() * 0.9f + 0.5f
                    )
                }
                ParticleType.EMBERS -> {
                    val emberColors = listOf(
                        Color(0xCCF43F5E),
                        Color(0xAAFB7185),
                        Color(0x88FDA4AF)
                    )
                    Triple(
                        emberColors[rand.nextInt(emberColors.size)],
                        rand.nextFloat() * 6f + 3f,
                        -(rand.nextFloat() * 0.7f + 0.4f)
                    )
                }
                ParticleType.NONE -> Triple(Color.Transparent, 0f, 0f)
            }

            SeasonalParticle(
                x = rand.nextFloat(),
                y = rand.nextFloat(),
                size = size,
                speedY = speed,
                speedX = (rand.nextFloat() - 0.5f) * 0.4f,
                swayFrequency = rand.nextFloat() * 1.5f + 0.8f,
                swayAmplitude = rand.nextFloat() * 0.8f + 0.6f,
                rotation = rand.nextFloat() * 360f,
                rotationSpeed = (rand.nextFloat() - 0.5f) * 1.8f,
                flutterFrequency = rand.nextFloat() * 1.8f + 1.2f,
                leafShape = leafShape,
                alpha = rand.nextFloat() * 0.35f + 0.50f,
                color = color
            )
        }
    }

    // 2. Sombras puras de murciélagos: elegantes, aerodinámicas y sin colores chillones
    val batShadows = remember(particleType, particleScale) {
        if (particleType == ParticleType.LEAVES && particleScale > 0.15f) {
            val slots = listOf(
                // (x, y, size, direction, speedX, alpha)
                Triple(0.20f, 0.14f, 48f) to Triple(1f, 1.3f, 0.58f),  // Grande, primer plano
                Triple(0.80f, 0.22f, 34f) to Triple(-1f, 1.1f, 0.42f), // Mediano, segundo plano
                Triple(0.50f, 0.10f, 42f) to Triple(1f, 1.2f, 0.50f),  // Centro superior
                Triple(0.35f, 0.36f, 30f) to Triple(-1f, 1.0f, 0.36f)  // Sombra suave más lejana
            )
            val selectedSlots = if (particleScale <= 0.35f) slots.take(1) else if (particleScale < 0.8f) slots.take(2) else slots

            selectedSlots.mapIndexed { idx, (posAndSize, flight) ->
                val (x, y, size) = posAndSize
                val (dir, spdX, alpha) = flight
                FlyingBatShadow(
                    x = x,
                    y = y,
                    size = size,
                    speedX = spdX,
                    flapSpeed = 10f,
                    waveFreq = 1.1f,
                    flapPhase = idx * 1.4f,
                    direction = dir,
                    alpha = alpha
                )
            }
        } else {
            emptyList()
        }
    }

    // 3. Estrellas mágicas navideñas
    val holidayStars = remember(particleType, particleScale) {
        if (particleType == ParticleType.SNOW && particleScale > 0.15f) {
            val count = (14 * particleScale).toInt().coerceAtLeast(3)
            val rand = Random(888)
            List(count) {
                val starColors = listOf(
                    Color(0xFFFDE047),
                    Color(0xFFFEF08A),
                    tv.medusa.app.ui.theme.TvPalette.TextSoft
                )
                HolidayStar(
                    x = rand.nextFloat(),
                    y = rand.nextFloat() * 0.85f,
                    size = rand.nextFloat() * 6f + 8f,
                    twinkleSpeed = rand.nextFloat() * 2f + 1.5f,
                    phase = rand.nextFloat() * 6.28f,
                    color = starColors[rand.nextInt(starColors.size)]
                )
            }
        } else {
            emptyList()
        }
    }

    // 4. Corazones flotantes de San Valentín
    val floatingHearts = remember(particleType, particleScale) {
        if (particleType == ParticleType.EMBERS && particleScale > 0.15f) {
            val count = (7 * particleScale).toInt().coerceAtLeast(2)
            val rand = Random(999)
            List(count) { idx ->
                val heartColors = listOf(
                    Color(0xFFFB7185),
                    Color(0xFFF43F5E),
                    Color(0xFFFDA4AF)
                )
                FloatingHeart(
                    x = rand.nextFloat(),
                    y = rand.nextFloat(),
                    size = rand.nextFloat() * 8f + 16f,
                    speedY = -(rand.nextFloat() * 0.5f + 0.4f),
                    swayFreq = rand.nextFloat() * 1.2f + 0.9f,
                    swayAmp = rand.nextFloat() * 0.5f + 0.4f,
                    color = heartColors[idx % heartColors.size],
                    alpha = rand.nextFloat() * 0.3f + 0.55f
                )
            }
        } else {
            emptyList()
        }
    }

    var timeSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastTime = withFrameMillis { it }
        while (true) {
            val currentTime = withFrameMillis { it }
            val delta = (currentTime - lastTime) / 1000f
            lastTime = currentTime
            timeSeconds += delta
            delay(33L)
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return@Canvas

        // A. Sombras cinematográficas de murciélagos (Halloween)
        if (particleType == ParticleType.LEAVES) {
            batShadows.forEach { bat ->
                bat.x += bat.speedX * bat.direction * 0.0014f
                bat.y += sin((timeSeconds * bat.waveFreq) + bat.flapPhase) * 0.00035f

                // Reaparición fluida en bucle
                if (bat.direction < 0 && bat.x < -0.12f) {
                    bat.x = 1.12f
                    bat.y = Random.nextFloat() * 0.55f + 0.08f
                } else if (bat.direction > 0 && bat.x > 1.12f) {
                    bat.x = -0.12f
                    bat.y = Random.nextFloat() * 0.55f + 0.08f
                }

                val flap = sin((timeSeconds * bat.flapSpeed) + bat.flapPhase)
                drawBatShadow(
                    center = Offset(bat.x * width, bat.y * height),
                    size = bat.size,
                    flap = flap,
                    facingRight = bat.direction > 0,
                    alpha = bat.alpha,
                    batPath = bat.path
                )
            }
        }

        // B. Estrellas mágicas titilantes de Navidad
        if (particleType == ParticleType.SNOW) {
            holidayStars.forEach { star ->
                drawHolidayStar(
                    center = Offset(star.x * width, star.y * height),
                    size = star.size,
                    time = timeSeconds,
                    twinkleSpeed = star.twinkleSpeed,
                    phase = star.phase,
                    color = star.color
                )
            }
        }

        // C. Corazones flotantes de San Valentín
        if (particleType == ParticleType.EMBERS) {
            floatingHearts.forEach { heart ->
                val sway = sin((timeSeconds * heart.swayFreq) + (heart.x * 10f)) * heart.swayAmp
                heart.x += sway * 0.0006f
                heart.y += heart.speedY * 0.0007f
                if (heart.y < -0.06f) {
                    heart.y = 1.06f
                    heart.x = Random.nextFloat()
                }
                drawFloatingHeart(
                    center = Offset(heart.x * width, heart.y * height),
                    size = heart.size,
                    color = heart.color,
                    alpha = heart.alpha
                )
            }
        }

        // D. Partículas ambientales (Hojas otoñales, Nieve o Ascuas)
        particles.forEach { p ->
            val sway = sin((timeSeconds * p.swayFrequency) + (p.x * 12f)) * p.swayAmplitude
            p.x += (p.speedX + sway) * 0.0008f
            p.y += p.speedY * 0.0008f
            p.rotation += p.rotationSpeed

            if (p.y > 1.06f) {
                p.y = -0.06f
                p.x = Random.nextFloat()
            } else if (p.y < -0.06f && particleType == ParticleType.EMBERS) {
                p.y = 1.06f
                p.x = Random.nextFloat()
            }
            if (p.x > 1.06f) p.x = -0.06f
            if (p.x < -0.06f) p.x = 1.06f

            val screenX = p.x * width
            val screenY = p.y * height

            when (particleType) {
                ParticleType.LEAVES -> {
                    val flutter = cos((timeSeconds * p.flutterFrequency) + (p.x * 20f))
                    rotate(p.rotation, pivot = Offset(screenX, screenY)) {
                        scale(scaleX = flutter, scaleY = 1f, pivot = Offset(screenX, screenY)) {
                            when (p.leafShape) {
                                LeafShape.MAPLE -> drawMapleLeaf(
                                    center = Offset(screenX, screenY),
                                    size = p.size,
                                    color = p.color,
                                    alpha = p.alpha
                                )
                                LeafShape.OAK -> drawOakLeaf(
                                    center = Offset(screenX, screenY),
                                    size = p.size,
                                    color = p.color,
                                    alpha = p.alpha
                                )
                                LeafShape.BIRCH -> drawBirchLeaf(
                                    center = Offset(screenX, screenY),
                                    size = p.size,
                                    color = p.color,
                                    alpha = p.alpha
                                )
                            }
                        }
                    }
                }
                ParticleType.SNOW -> {
                    drawSnowflake(
                        center = Offset(screenX, screenY),
                        radius = p.size,
                        color = p.color.copy(alpha = p.alpha)
                    )
                }
                ParticleType.EMBERS -> {
                    drawGlowingEmber(
                        center = Offset(screenX, screenY),
                        radius = p.size,
                        color = p.color.copy(alpha = p.alpha * 0.9f)
                    )
                }
                ParticleType.NONE -> Unit
            }
        }
    }
}

/**
 * Dibuja una silueta estilizada y limpia de sombra de murciélago (sin colores artificiales ni bordes chillones).
 * Geometría vectorial aerodinámica continua con aleteo suave y orgánico.
 */
private fun DrawScope.drawBatShadow(
    center: Offset,
    size: Float,
    flap: Float, // -1f .. 1f
    facingRight: Boolean,
    alpha: Float,
    batPath: Path
) {
    val scaleX = if (facingRight) 1f else -1f
    scale(scaleX = scaleX, scaleY = 1f, pivot = center) {
        val s = size * 0.5f
        val flapOffset = flap * (s * 0.45f)
        val shadowColor = Color(0xFF000000).copy(alpha = alpha)

        batPath.reset()
        // Oreja izquierda
        batPath.moveTo(center.x - s * 0.12f, center.y - s * 0.45f)
        // Coronilla
        batPath.lineTo(center.x, center.y - s * 0.28f)
        // Oreja derecha
        batPath.lineTo(center.x + s * 0.12f, center.y - s * 0.45f)
        // Hombro derecho
        batPath.lineTo(center.x + s * 0.18f, center.y - s * 0.18f)

        // Borde superior curvado del ala derecha hacia la punta
        batPath.cubicTo(
            center.x + s * 0.52f, center.y - s * 0.45f - flapOffset * 0.6f,
            center.x + s * 0.95f, center.y - s * 0.52f - flapOffset * 0.9f,
            center.x + s * 1.50f, center.y - s * 0.22f - flapOffset
        )

        // 3 festones inferiores del ala derecha
        // Festón 1 (exterior)
        batPath.quadraticTo(
            center.x + s * 1.15f, center.y + s * 0.08f - flapOffset * 0.5f,
            center.x + s * 0.92f, center.y + s * 0.28f - flapOffset * 0.3f
        )
        // Festón 2 (medio)
        batPath.quadraticTo(
            center.x + s * 0.72f, center.y + s * 0.18f - flapOffset * 0.2f,
            center.x + s * 0.52f, center.y + s * 0.34f - flapOffset * 0.1f
        )
        // Festón 3 (interior hacia la cola)
        batPath.quadraticTo(
            center.x + s * 0.32f, center.y + s * 0.24f,
            center.x + s * 0.08f, center.y + s * 0.44f
        )

        // Punta estilizada de la cola
        batPath.lineTo(center.x, center.y + s * 0.58f)

        // Festón 3 ala izquierda
        batPath.lineTo(center.x - s * 0.08f, center.y + s * 0.44f)
        batPath.quadraticTo(
            center.x - s * 0.32f, center.y + s * 0.24f,
            center.x - s * 0.52f, center.y + s * 0.34f - flapOffset * 0.1f
        )
        // Festón 2
        batPath.quadraticTo(
            center.x - s * 0.72f, center.y + s * 0.18f - flapOffset * 0.2f,
            center.x - s * 0.92f, center.y + s * 0.28f - flapOffset * 0.3f
        )
        // Festón 1 hacia la punta izquierda
        batPath.quadraticTo(
            center.x - s * 1.15f, center.y + s * 0.08f - flapOffset * 0.5f,
            center.x - s * 1.50f, center.y - s * 0.22f - flapOffset
        )

        // Borde superior ala izquierda hacia el hombro
        batPath.cubicTo(
            center.x - s * 0.95f, center.y - s * 0.52f - flapOffset * 0.9f,
            center.x - s * 0.52f, center.y - s * 0.45f - flapOffset * 0.6f,
            center.x - s * 0.18f, center.y - s * 0.18f
        )

        batPath.close()

        drawPath(path = batPath, color = shadowColor)
    }
}

private val UnitHolidayStarPath by lazy {
    Path().apply {
        moveTo(0f, -1f)
        lineTo(0.22f, 0f)
        lineTo(0f, 1f)
        lineTo(-0.22f, 0f)
        close()
        moveTo(-1f, 0f)
        lineTo(0f, 0.22f)
        lineTo(1f, 0f)
        lineTo(0f, -0.22f)
        close()
    }
}

private val UnitFloatingHeartPath by lazy {
    Path().apply {
        moveTo(0f, 0.8f)
        cubicTo(-1.15f, 0.2f, -1.15f, -0.75f, 0f, -0.35f)
        cubicTo(1.15f, -0.75f, 1.15f, 0.2f, 0f, 0.8f)
        close()
    }
}

private val UnitMapleLeafPath by lazy {
    Path().apply {
        moveTo(0f, 1.4f)
        lineTo(0f, 0.85f)
        cubicTo(-0.45f, 0.55f, -0.85f, 0.3f, -1.15f, -0.05f)
        lineTo(-0.6f, -0.25f)
        cubicTo(-0.95f, -0.65f, -0.65f, -1.05f, -0.3f, -1.25f)
        lineTo(0f, -1.7f)
        lineTo(0.3f, -1.25f)
        cubicTo(0.65f, -1.05f, 0.95f, -0.65f, 0.6f, -0.25f)
        lineTo(1.15f, -0.05f)
        cubicTo(0.85f, 0.3f, 0.45f, 0.55f, 0f, 0.85f)
        close()
    }
}

private val UnitOakLeafPath by lazy {
    Path().apply {
        moveTo(0f, 1.4f)
        lineTo(0f, 0.9f)
        cubicTo(-0.5f, 0.7f, -0.7f, 0.4f, -0.4f, 0.2f)
        cubicTo(-0.8f, 0.0f, -0.85f, -0.4f, -0.45f, -0.55f)
        cubicTo(-0.7f, -0.75f, -0.6f, -1.2f, -0.2f, -1.35f)
        cubicTo(-0.1f, -1.55f, 0.1f, -1.55f, 0.2f, -1.35f)
        cubicTo(0.6f, -1.2f, 0.7f, -0.75f, 0.45f, -0.55f)
        cubicTo(0.85f, -0.4f, 0.8f, 0.0f, 0.4f, 0.2f)
        cubicTo(0.7f, 0.4f, 0.5f, 0.7f, 0f, 0.9f)
        close()
    }
}

private val UnitBirchLeafPath by lazy {
    Path().apply {
        moveTo(0f, 1.3f)
        lineTo(0f, 0.8f)
        cubicTo(-0.75f, 0.6f, -0.9f, -0.1f, -0.45f, -0.8f)
        lineTo(0f, -1.5f)
        lineTo(0.45f, -0.8f)
        cubicTo(0.9f, -0.1f, 0.75f, 0.6f, 0f, 0.8f)
        close()
    }
}

/**
 * Dibuja una estrella mágica titilante navideña.
 */
private fun DrawScope.drawHolidayStar(
    center: Offset,
    size: Float,
    time: Float,
    twinkleSpeed: Float,
    phase: Float,
    color: Color
) {
    val twinkle = (sin(time * twinkleSpeed + phase) * 0.5f + 0.5f)
    if (twinkle < 0.12f) return
    val alpha = twinkle * 0.85f
    val r = size * (0.65f + twinkle * 0.35f)

    translate(left = center.x, top = center.y) {
        scale(scaleX = r, scaleY = r, pivot = Offset.Zero) {
            drawPath(UnitHolidayStarPath, color.copy(alpha = alpha))
        }
    }
    drawCircle(
        color = TextPrimary.copy(alpha = alpha),
        radius = r * 0.18f,
        center = center
    )
}

/**
 * Dibuja un corazón suave flotante para San Valentín.
 */
private fun DrawScope.drawFloatingHeart(
    center: Offset,
    size: Float,
    color: Color,
    alpha: Float
) {
    val s = size * 0.5f
    translate(left = center.x, top = center.y) {
        scale(scaleX = s, scaleY = s, pivot = Offset.Zero) {
            drawPath(path = UnitFloatingHeartPath, color = color.copy(alpha = alpha))
        }
    }
}

/**
 * Dibuja una hoja de arce (Maple) con lóbulos puntiagudos y hendiduras profundas.
 */
private fun DrawScope.drawMapleLeaf(
    center: Offset,
    size: Float,
    color: Color,
    alpha: Float
) {
    val s = size * 0.75f
    translate(left = center.x, top = center.y) {
        scale(scaleX = s, scaleY = s, pivot = Offset.Zero) {
            drawPath(path = UnitMapleLeafPath, color = color.copy(alpha = alpha))
        }
    }
    drawLine(
        color = Color(0xFFFEF3C7).copy(alpha = alpha * 0.45f),
        start = Offset(center.x, center.y + s * 0.85f),
        end = Offset(center.x, center.y - s * 1.4f),
        strokeWidth = 1.2f
    )
}

/**
 * Dibuja una hoja de roble (Oak) con lóbulos redondeados característicos.
 */
private fun DrawScope.drawOakLeaf(
    center: Offset,
    size: Float,
    color: Color,
    alpha: Float
) {
    val s = size * 0.7f
    translate(left = center.x, top = center.y) {
        scale(scaleX = s, scaleY = s, pivot = Offset.Zero) {
            drawPath(path = UnitOakLeafPath, color = color.copy(alpha = alpha))
        }
    }
    drawLine(
        color = Color(0xFFFEF3C7).copy(alpha = alpha * 0.4f),
        start = Offset(center.x, center.y + s * 0.9f),
        end = Offset(center.x, center.y - s * 1.35f),
        strokeWidth = 1.2f
    )
}

/**
 * Dibuja una hoja de abedul (Birch) con forma acorazonada y punta afilada.
 */
private fun DrawScope.drawBirchLeaf(
    center: Offset,
    size: Float,
    color: Color,
    alpha: Float
) {
    val s = size * 0.72f
    translate(left = center.x, top = center.y) {
        scale(scaleX = s, scaleY = s, pivot = Offset.Zero) {
            drawPath(path = UnitBirchLeafPath, color = color.copy(alpha = alpha))
        }
    }
    drawLine(
        color = Color(0xFFFEF3C7).copy(alpha = alpha * 0.4f),
        start = Offset(center.x, center.y + s * 0.8f),
        end = Offset(center.x, center.y - s * 1.2f),
        strokeWidth = 1.1f
    )
}

private fun DrawScope.drawSnowflake(
    center: Offset,
    radius: Float,
    color: Color
) {
    drawCircle(
        color = color,
        radius = radius,
        center = center
    )
    if (radius > 3.0f) {
        val arm = radius * 1.6f
        drawLine(color.copy(alpha = color.alpha * 0.7f), Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), strokeWidth = 1.0f)
        drawLine(color.copy(alpha = color.alpha * 0.7f), Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), strokeWidth = 1.0f)
    }
}

private fun DrawScope.drawGlowingEmber(
    center: Offset,
    radius: Float,
    color: Color
) {
    drawCircle(
        color = color.copy(alpha = color.alpha * 0.25f),
        radius = radius * 2.2f,
        center = center
    )
    drawCircle(
        color = color,
        radius = radius,
        center = center
    )
}
