package de.djouhri.tardis.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Eine Linie ohne Achsen: auf dem Telefon zaehlt die Form, der Wert steht daneben.
 *
 * @param bereich feste Skala, etwa 0..100 fuer Prozent. Ohne sie wird die Reihe
 *   auf ihr eigenes Min/Max gestreckt, und ein Wirt mit 2 bis 4 % CPU sieht dann
 *   genauso unruhig aus wie einer mit 20 bis 95 %. Zum Vergleich mehrerer Wirte
 *   taugt nur die feste Skala.
 * @param werte `null` ist eine Messluecke: die Linie setzt dort ab, statt eine
 *   Null zu zeichnen, die nie gemessen wurde.
 */
@Composable
fun Verlaufslinie(
    werte: List<Double?>,
    beschreibung: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    farbe: Color? = null,
    bereich: ClosedFloatingPointRange<Double>? = null,
    hoehe: Dp = 36.dp,
) {
    val gemessen = werte.filterNotNull()
    if (gemessen.size < 2) return
    val strich = farbe ?: MaterialTheme.colorScheme.primary
    val grund = MaterialTheme.colorScheme.outlineVariant
    Canvas(
        modifier = modifier
            .height(hoehe)
            .semantics { contentDescription = beschreibung },
    ) {
        val min = bereich?.start ?: gemessen.min()
        val max = bereich?.endInclusive ?: gemessen.max()
        val spanne = (max - min).takeIf { it > 0 } ?: 1.0
        val schritt = size.width / (werte.size - 1).coerceAtLeast(1)
        drawLine(grund, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
        val pfad = Path()
        var abgesetzt = true
        werte.forEachIndexed { i, w ->
            if (w == null) {
                abgesetzt = true
                return@forEachIndexed
            }
            val anteil = ((w - min) / spanne).coerceIn(0.0, 1.0).toFloat()
            val x = i * schritt
            val y = (size.height - 2f) - anteil * (size.height - 4f)
            if (abgesetzt) pfad.moveTo(x, y) else pfad.lineTo(x, y)
            abgesetzt = false
        }
        drawPath(pfad, strich, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}
