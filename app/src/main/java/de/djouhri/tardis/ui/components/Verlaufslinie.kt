package de.djouhri.tardis.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.djouhri.tardis.ui.theme.TextLeise
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun Verlaufslinie(
    werte: List<Double?>,
    beschreibung: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    farbe: Color? = null,
    bereich: ClosedFloatingPointRange<Double>? = null,
    hoehe: Dp = 36.dp,
    zeiten: List<Long>? = null,
    wertText: (Double) -> String = { String.format(Locale.GERMANY, "%.1f", it) },
) {
    val gemessen = werte.filterNotNull()
    if (gemessen.size < 2) return
    val strich = farbe ?: MaterialTheme.colorScheme.primary
    val grund = MaterialTheme.colorScheme.outlineVariant
    val achse = zeiten?.takeIf { it.size == werte.size && it.last() > it.first() }
    val marken = remember(achse) { achse?.let { zeitMarken(it.first(), it.last()) } ?: emptyList() }
    var gewaehlt by remember(werte) { mutableStateOf<Int?>(null) }

    Column(modifier) {
        gewaehlt?.let { i ->
            val w = werte.getOrNull(i)
            Text(
                listOfNotNull(achse?.let { uhrzeit(it[i], it.last() - it.first()) }, w?.let(wertText) ?: "keine Messung")
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(hoehe)
                .semantics { contentDescription = beschreibung }
                .then(
                    if (achse == null) Modifier else Modifier
                        .pointerInput(werte) {
                            fun index(x: Float) = ((x / size.width) * (werte.size - 1)).toInt().coerceIn(0, werte.size - 1)
                            detectTapGestures { p -> gewaehlt = if (gewaehlt == index(p.x)) null else index(p.x) }
                        }
                        .pointerInput(werte) {
                            fun index(x: Float) = ((x / size.width) * (werte.size - 1)).toInt().coerceIn(0, werte.size - 1)
                            detectDragGestures(
                                onDragStart = { p -> gewaehlt = index(p.x) },
                                onDrag = { change, _ -> gewaehlt = index(change.position.x) },
                            )
                        },
                ),
        ) {
            val min = bereich?.start ?: gemessen.min()
            val max = bereich?.endInclusive ?: gemessen.max()
            val spanne = (max - min).takeIf { it > 0 } ?: 1.0
            val schritt = size.width / (werte.size - 1).coerceAtLeast(1)
            drawLine(grund, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
            if (achse != null) {
                val von = achse.first().toFloat()
                val dauer = (achse.last() - achse.first()).toFloat()
                val gestrichelt = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
                marken.forEach { m ->
                    val x = (m - von) / dauer * size.width
                    drawLine(grund, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f, pathEffect = gestrichelt)
                }
            }
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
            gewaehlt?.let { i ->
                val x = i * schritt
                drawLine(strich.copy(alpha = 0.6f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                werte.getOrNull(i)?.let { w ->
                    val y = (size.height - 2f) - ((w - min) / spanne).coerceIn(0.0, 1.0).toFloat() * (size.height - 4f)
                    drawCircle(strich, radius = 3.5.dp.toPx(), center = Offset(x, y))
                }
            }
        }
        if (achse != null && marken.isNotEmpty()) {
            val dauer = achse.last() - achse.first()
            BoxWithConstraints(Modifier.fillMaxWidth().height(14.dp)) {
                val breite = maxWidth
                marken.forEach { m ->
                    val anteil = (m - achse.first()).toFloat() / dauer
                    // Beschriftung mittig unter der Hilfslinie, am Rand nach innen geschoben.
                    val x = (breite * anteil - 16.dp).coerceIn(0.dp, breite - 32.dp)
                    Box(Modifier.offset(x = x)) {
                        Text(uhrzeit(m, dauer), style = MaterialTheme.typography.labelSmall, color = TextLeise)
                    }
                }
            }
        }
    }
}

private val UHR = DateTimeFormatter.ofPattern("HH:mm")
private val TAGE = listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")

/** Uhrzeit fuer Achse und Anzeige; ueber mehr als einen Tag mit Wochentag. */
internal fun uhrzeit(unixS: Long, spanneS: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochSecond(unixS).atZone(zone)
    return if (spanneS > 26 * 3600) "${TAGE[t.dayOfWeek.value - 1]} ${t.hour} Uhr" else UHR.format(t)
}

/**
 * Runde Zeitpunkte zwischen [von] und [bis] (Unix-Sekunden) fuer die Achse:
 * der kleinste Abstand aus 5 min bis 1 Tag, mit dem hoechstens [hoechstens]
 * Marken entstehen, ausgerichtet an der Ortszeit (also 14:00, nicht 13:07).
 */
internal fun zeitMarken(
    von: Long,
    bis: Long,
    hoechstens: Int = 4,
    zone: ZoneId = ZoneId.systemDefault(),
): List<Long> {
    if (bis <= von) return emptyList()
    val abstaende = listOf(5, 10, 15, 30, 60, 120, 180, 240, 360, 720, 1440).map { it * 60L }
    val versatz = zone.rules.getOffset(Instant.ofEpochSecond(von)).totalSeconds.toLong()
    for (a in abstaende) {
        val erste = Math.floorDiv(von + versatz + a - 1, a) * a - versatz
        val marken = generateSequence(erste) { it + a }.takeWhile { it <= bis }.toList()
        if (marken.size <= hoechstens) return marken
    }
    return emptyList()
}
