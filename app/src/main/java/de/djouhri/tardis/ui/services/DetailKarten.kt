package de.djouhri.tardis.ui.services

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import de.djouhri.tardis.data.model.tardis.Alarm
import de.djouhri.tardis.data.model.tardis.DienstDetail
import de.djouhri.tardis.data.model.tardis.Verbrauch
import de.djouhri.tardis.data.model.tardis.text
import de.djouhri.tardis.ui.components.StatusDot
import de.djouhri.tardis.ui.theme.TextLeise
import de.djouhri.tardis.ui.theme.StatusDown
import de.djouhri.tardis.ui.theme.StatusUp
import de.djouhri.tardis.ui.theme.StatusWarn
import de.djouhri.tardis.util.formatBytes
import de.djouhri.tardis.util.seitText
import de.djouhri.tardis.util.zeitpunktText

/**
 * Die Alarme genau dieses Containers. Steht nur da, wenn es welche gibt oder
 * die Quelle nicht zu erreichen war: eine Karte „keine Alarme" auf jedem
 * Dienst waere Rauschen, ein stilles Fehlen bei ausgefallener Quelle aber
 * eine Behauptung.
 */
@Composable
internal fun DetailAlarmeKarte(detail: DienstDetail) {
    if (detail.alarmeGemessen && detail.alarme.isEmpty()) return
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Alarme", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (!detail.alarmeGemessen) {
                Hinweis("Alarmquelle nicht erreicht, also nicht gemessen.")
            }
            detail.alarme.forEach { AlarmZeile(it) }
        }
    }
}

@Composable
private fun AlarmZeile(a: Alarm) {
    Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        StatusDot(color = if (a.istKritisch) StatusDown else StatusWarn, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(a.zusammenfassung, style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(a.name, seitText(a.seit)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = TextLeise,
            )
        }
    }
}

/** Seit wann er laeuft, wie oft er neu gestartet ist, und was der Beobachter gesehen hat. */
@Composable
internal fun LaufKarte(detail: DienstDetail) {
    val lauf = detail.container
    val verf = detail.verfuegbarkeit
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Laufzeit und Verlauf", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (!lauf.gefunden) {
                Hinweis("Container auf diesem Rechner nicht gefunden.")
            } else {
                if (lauf.beendet != null) {
                    Wert("Beendet", listOfNotNull(seitText(lauf.beendet), lauf.exitCode?.let { "Exit-Code $it" }).joinToString(" · "))
                } else {
                    Wert("Läuft", seitText(lauf.gestartet) ?: "–")
                }
                lauf.neustarts?.let {
                    Wert(
                        "Neustarts",
                        if (it == 0) "keine" else "$it durch Docker",
                        farbe = if (it >= 5) StatusWarn else null,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (!verf.erfasst) {
                // Wortlaut mit Absicht: „nicht aufgezeichnet", nicht „100 %".
                Hinweis("${verf.grund ?: "Verlauf nicht aufgezeichnet"}.")
                return@Column
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Kennzahl("24 h", verf.uptime24h)
                Kennzahl("7 Tage", verf.uptime7d)
                Kennzahl("30 Tage", verf.uptime30d)
            }
            verf.zuletztBeendet?.let {
                Spacer(Modifier.height(4.dp))
                Wert("Zuletzt beendet", zeitpunktText(it) ?: it)
            }
            if (verf.ereignisse.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                verf.ereignisse.forEach { e ->
                    Row(modifier = Modifier.padding(vertical = 2.dp)) {
                        Text(
                            zeitpunktText(e.zeit) ?: "–",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(96.dp),
                        )
                        Text(e.text(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            verf.abtastungS?.let {
                Spacer(Modifier.height(6.dp))
                Hinweis("Abgetastet alle ${it / 60} min: ein kürzerer Neuaufbau fehlt hier, die Startzeit oben stimmt trotzdem.")
            }
        }
    }
}

@Composable
private fun Kennzahl(titel: String, prozent: Double?) {
    Column {
        Text(titel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            prozent?.let { "%.1f\u00A0%%".format(it) } ?: "–",
            style = MaterialTheme.typography.titleMedium,
            color = when {
                prozent == null -> MaterialTheme.colorScheme.onSurfaceVariant
                prozent < 99.0 -> StatusDown
                prozent < 99.5 -> StatusWarn
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** CPU und Arbeitsspeicher jetzt, darunter die letzten 24 Stunden. */
@Composable
internal fun VerbrauchKarte(v: Verbrauch) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Verbrauch", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (!v.gemessen) {
                Hinweis(
                    if (v.grund == "keine Messreihe") "Keine Messreihe: der Container läuft nicht oder der Rechner wird nicht gemessen."
                    else "Nicht gemessen (${v.grund ?: "unbekannt"})."
                )
                return@Column
            }
            Wert("CPU", cpuText(v.cpuKerne))
            Verlaufslinie(v.verlaufCpu, "CPU, letzte 24 Stunden")
            Spacer(Modifier.height(10.dp))
            Wert(
                "RAM",
                buildString {
                    append(v.ramBytes?.toLong()?.formatBytes() ?: "–")
                    v.ramGrenzeBytes?.let { append(" von ${it.toLong().formatBytes()}") }
                    v.ramAnteil?.let { append(" (%.0f\u00A0%%)".format(it * 100)) }
                    if (v.ramGrenzeBytes == null) append(" · keine Grenze")
                },
                farbe = v.ramAnteil?.let { if (it >= 0.9) StatusDown else if (it >= 0.75) StatusWarn else null },
            )
            Verlaufslinie(v.verlaufRam, "Arbeitsspeicher, letzte 24 Stunden")
        }
    }
}

private fun cpuText(kerne: Double?): String = when {
    kerne == null -> "–"
    kerne < 1.0 -> "%.0f\u00A0%% eines Kerns".format(kerne * 100)
    else -> "%.1f Kerne".format(kerne)
}

/**
 * Eine Linie ueber 24 h, ohne Achsen: auf dem Telefon geht es um die Form
 * (Spitze heute Nacht? waechst er stetig?), nicht um den Wert einer Stunde.
 * Der Wert steht darueber.
 */
@Composable
private fun Verlaufslinie(punkte: List<List<Double>>, beschreibung: String) {
    val werte = punkte.mapNotNull { it.getOrNull(1) }
    if (werte.size < 2) return
    val farbe = MaterialTheme.colorScheme.primary
    val grund = MaterialTheme.colorScheme.outlineVariant
    Spacer(Modifier.height(4.dp))
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .semantics { contentDescription = beschreibung },
    ) {
        val min = werte.min()
        val max = werte.max()
        val spanne = (max - min).takeIf { it > 0 } ?: 1.0
        val schritt = size.width / (werte.size - 1)
        drawLine(grund, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1f)
        val pfad = Path()
        werte.forEachIndexed { i, w ->
            val x = i * schritt
            val y = (size.height - 2f) - ((w - min) / spanne).toFloat() * (size.height - 4f)
            if (i == 0) pfad.moveTo(x, y) else pfad.lineTo(x, y)
        }
        drawPath(pfad, farbe, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
    }
}

/** Wie voll der Rechner ist, auf dem der Dienst laeuft. */
@Composable
internal fun WirtKarte(detail: DienstDetail) {
    val w = detail.wirt
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Rechner ${detail.host}", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (!w.gemessen) {
                Hinweis("Nicht gemessen (${w.grund ?: "unbekannt"}).")
                return@Column
            }
            Auslastung("CPU", w.cpuProzent)
            Auslastung("RAM", w.ramProzent)
            Auslastung("Platte", w.platteProzent)
        }
    }
}

@Composable
private fun Auslastung(titel: String, prozent: Double?) {
    val farbe = when {
        prozent == null -> TextLeise
        prozent >= 90 -> StatusDown
        prozent >= 75 -> StatusWarn
        else -> StatusUp
    }
    Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(titel, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(56.dp))
        LinearProgressIndicator(
            progress = { ((prozent ?: 0.0) / 100).toFloat().coerceIn(0f, 1f) },
            color = farbe,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            // Ohne Luecke und Endpunkt: Material zeichnet sonst einen Punkt ans
            // Ende, der wie ein zweiter Messwert aussieht.
            gapSize = 0.dp,
            drawStopIndicator = {},
            modifier = Modifier.weight(1f).height(6.dp),
        )
        Text(
            prozent?.let { "%.0f\u00A0%%".format(it) } ?: "–",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(48.dp).padding(start = 8.dp),
        )
    }
}

@Composable
private fun Wert(titel: String, wert: String, farbe: Color? = null) {
    Row(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            titel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(96.dp),
        )
        Text(wert, style = MaterialTheme.typography.bodyMedium, color = farbe ?: MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Hinweis(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
