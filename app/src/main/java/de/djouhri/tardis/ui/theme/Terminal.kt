package de.djouhri.tardis.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.djouhri.tardis.R
import kotlin.random.Random
import kotlinx.coroutines.delay

/*
 * Terminal-Stil (2.0.23, Entscheid 2026-11-15, R16-R18 und R53-R55):
 * Dunkelrot, JetBrains Mono, Scanlines, Flimmern. Dieselbe Umsetzung wie
 * `Stil.kt` in saganta-apps/core; die Tardis-App ist ein eigenes Projekt und
 * haengt nicht an core, deshalb steht sie hier ein zweites Mal. Wer dort etwas
 * aendert, zieht es hier nach.
 */

private val JetBrainsMono = FontFamily(
    Font(R.font.jetbrains_mono_400, FontWeight.Normal),
    Font(R.font.jetbrains_mono_500, FontWeight.Medium),
    Font(R.font.jetbrains_mono_600, FontWeight.SemiBold),
    Font(R.font.jetbrains_mono_700, FontWeight.Bold),
)

private fun TextStyle.mono() = copy(fontFamily = JetBrainsMono, fontFeatureSettings = null,
    letterSpacing = if (letterSpacing.value < 0f) 0.sp else letterSpacing)

/**
 * Dieselben Stufen wie [Typography], alles in der Monoschrift, Titel mit
 * Phosphor-Schein in der Farbe des gewaehlten Tons (seit der Farbwahl).
 */
fun terminalTypography(akzent: Color): Typography {
    val leuchten = Shadow(color = akzent.copy(alpha = 0.55f), blurRadius = 10f)
    fun TextStyle.titel() = mono().copy(shadow = leuchten)
    return Typography(
    displayLarge = Typography.displayLarge.titel(),
    displayMedium = Typography.displayMedium.titel(),
    displaySmall = Typography.displaySmall.titel(),
    headlineLarge = Typography.headlineLarge.titel(),
    headlineMedium = Typography.headlineMedium.titel(),
    headlineSmall = Typography.headlineSmall.titel(),
    titleLarge = Typography.titleLarge.titel(),
    titleMedium = Typography.titleMedium.mono(),
    titleSmall = Typography.titleSmall.mono(),
    bodyLarge = Typography.bodyLarge.mono().copy(fontSize = 15.sp),
    bodyMedium = Typography.bodyMedium.mono().copy(fontSize = 13.sp),
    bodySmall = Typography.bodySmall.mono().copy(fontSize = 11.5.sp),
    labelLarge = Typography.labelLarge.mono(),
    labelMedium = Typography.labelMedium.mono(),
    labelSmall = Typography.labelSmall.mono(),
    )
}

val TerminalShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp),
)

/** Jede dritte Bildzeile etwas dunkler, dazu ein leichtes Abdunkeln zum Rand. Statisch. */
fun Modifier.scanlines(abstand: Dp = 3.dp, staerke: Float = 0.14f): Modifier = drawWithContent {
    drawContent()
    val schritt = abstand.toPx().coerceAtLeast(2f)
    val linie = (schritt / 3f).coerceAtLeast(1f)
    val farbe = Color.Black.copy(alpha = staerke)
    var y = 0f
    while (y < size.height) {
        drawRect(farbe, topLeft = Offset(0f, y), size = Size(size.width, linie))
        y += schritt
    }
    drawRect(
        Brush.radialGradient(
            0.6f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.35f),
            center = center,
            radius = size.maxDimension * 0.75f,
        ),
    )
}

/**
 * Flimmern als kurzer Stoss (unter 300 ms, hoechstens 7 Prozent dunkler) alle
 * 6 bis 14 Sekunden, dazwischen steht das Bild: keine Endlos-Animation, die den
 * Akku leert, weit unter der Schwelle fuer Lichtempfindliche (WCAG 2.3.1).
 * Aus, wenn am Telefon die Animationen abgeschaltet sind.
 */
@Composable
fun Modifier.flimmern(): Modifier {
    if (!effekteAn()) return this
    val schleier = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(6_000, 14_000))
            repeat(Random.nextInt(1, 3)) {
                schleier.animateTo(Random.nextDouble(0.03, 0.07).toFloat(), tween(60, easing = LinearEasing))
                schleier.animateTo(0f, tween(90, easing = LinearEasing))
            }
        }
    }
    return drawWithContent {
        drawContent()
        val a = schleier.value
        if (a > 0f) drawRect(Color.Black.copy(alpha = a))
    }
}
