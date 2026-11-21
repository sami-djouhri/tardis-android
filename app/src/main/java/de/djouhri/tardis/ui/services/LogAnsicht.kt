package de.djouhri.tardis.ui.services

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.djouhri.tardis.ui.theme.StatusDown
import de.djouhri.tardis.ui.theme.StatusWarn
import de.djouhri.tardis.ui.theme.TextLeise

/*
 * Logs als Zeilen statt als ein Textblock (seit 2.0.31). Vorher stand das Log
 * als ein einziger Text in einer Box: Fehler sahen aus wie alles andere, und
 * eine Zeile, die 80-mal hintereinander kam, schob den Rest aus dem Bild.
 */

enum class LogStufe { FEHLER, WARNUNG, INFO, LEISE }

data class LogZeile(val text: String, val stufe: LogStufe, val anzahl: Int = 1)

enum class LogFilter(val titel: String) { ALLE("Alle"), WARNUNG("Warnungen"), FEHLER("Fehler") }

private val ANSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")
private val FEHLER_WORT = Regex(
    "\\b(ERROR|ERR|FATAL|CRITICAL|CRIT|PANIC|EMERG|ALERT|Traceback|Exception|panic:)\\b|level=(error|fatal|crit)|\"level\":\\s*\"(error|fatal|critical)\"",
    RegexOption.IGNORE_CASE,
)
private val WARN_WORT = Regex(
    "\\b(WARN|WARNING)\\b|level=warn|\"level\":\\s*\"warn(ing)?\"",
    RegexOption.IGNORE_CASE,
)
private val LEISE_WORT = Regex("\\b(DEBUG|TRACE)\\b|level=(debug|trace)", RegexOption.IGNORE_CASE)

/** Fortsetzung eines Stacktraces: eingerueckt, „at …", „File …", „Caused by", „…Error:". */
private val FORTSETZUNG = Regex("^(\\s+|\\tat |Caused by|\\.\\.\\. \\d+ more|[A-Za-z_.]+(Error|Exception)(:|$))")

internal fun stufeVon(zeile: String): LogStufe = when {
    FEHLER_WORT.containsMatchIn(zeile) -> LogStufe.FEHLER
    WARN_WORT.containsMatchIn(zeile) -> LogStufe.WARNUNG
    LEISE_WORT.containsMatchIn(zeile) -> LogStufe.LEISE
    else -> LogStufe.INFO
}

/**
 * Zerlegt ein Log in Zeilen: Farbcodes raus, Stufe je Zeile, Fortsetzungen
 * eines Stacktraces erben die Stufe der Zeile davor, gleiche Zeilen direkt
 * hintereinander werden zu einer mit Zaehler.
 */
internal fun logZeilen(roh: String): List<LogZeile> {
    val aus = mutableListOf<LogZeile>()
    roh.lineSequence().map { it.replace(ANSI, "").trimEnd('\r') }.filter { it.isNotBlank() }.forEach { z ->
        val vorher = aus.lastOrNull()
        var stufe = stufeVon(z)
        if (stufe == LogStufe.INFO && vorher?.stufe == LogStufe.FEHLER && FORTSETZUNG.containsMatchIn(z)) {
            stufe = LogStufe.FEHLER
        }
        if (vorher != null && vorher.text == z) {
            aus[aus.lastIndex] = vorher.copy(anzahl = vorher.anzahl + 1)
        } else {
            aus += LogZeile(z, stufe)
        }
    }
    return aus
}

internal fun List<LogZeile>.gefiltert(filter: LogFilter, suche: String): List<LogZeile> = filter { z ->
    val stufeOk = when (filter) {
        LogFilter.ALLE -> true
        LogFilter.WARNUNG -> z.stufe == LogStufe.FEHLER || z.stufe == LogStufe.WARNUNG
        LogFilter.FEHLER -> z.stufe == LogStufe.FEHLER
    }
    stufeOk && (suche.isBlank() || z.text.contains(suche.trim(), ignoreCase = true))
}

/** Filter, Suche und Zeilen. Zustand haelt der Aufrufer, damit Vollbild und Karte dasselbe zeigen. */
@Composable
internal fun LogAnsicht(
    zeilen: List<LogZeile>,
    filter: LogFilter,
    onFilter: (LogFilter) -> Unit,
    suche: String,
    onSuche: (String) -> Unit,
    umbruch: Boolean,
    onUmbruch: (Boolean) -> Unit,
    hoehe: Dp?,
) {
    val fehler = remember(zeilen) { zeilen.count { it.stufe == LogStufe.FEHLER } }
    val warn = remember(zeilen) { zeilen.count { it.stufe == LogStufe.WARNUNG } }
    val sichtbar = remember(zeilen, filter, suche) { zeilen.gefiltert(filter, suche) }
    Column {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LogFilter.entries.forEach { f ->
                val zahl = when (f) {
                    LogFilter.ALLE -> null
                    LogFilter.WARNUNG -> fehler + warn
                    LogFilter.FEHLER -> fehler
                }
                FilterChip(
                    selected = filter == f,
                    onClick = { onFilter(f) },
                    label = { Text(if (zahl != null) "${f.titel} $zahl" else f.titel) },
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            FilterChip(selected = umbruch, onClick = { onUmbruch(!umbruch) }, label = { Text("Umbruch") })
        }
        OutlinedTextField(
            value = suche,
            onValueChange = onSuche,
            singleLine = true,
            placeholder = { Text("In den Logs suchen") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            textStyle = MaterialTheme.typography.bodySmall,
        )
        if (sichtbar.isEmpty()) {
            Text(
                if (zeilen.isEmpty()) "(keine Logs)" else "Keine Zeile passt.",
                style = MaterialTheme.typography.bodySmall,
                color = TextLeise,
            )
            return@Column
        }
        val liste = rememberLazyListState()
        // Ans Ende springen, sobald neue Zeilen da sind: die interessante Zeile eines Logs ist die letzte.
        LaunchedEffect(sichtbar.size, sichtbar.lastOrNull()) { liste.scrollToItem(sichtbar.lastIndex) }
        val quer = rememberScrollState()
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (hoehe != null) Modifier.height(hoehe) else Modifier.fillMaxHeight())
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(if (umbruch) Modifier else Modifier.horizontalScroll(quer)),
        ) {
            LazyColumn(state = liste, modifier = if (umbruch) Modifier.fillMaxSize() else Modifier.width(2000.dp).fillMaxHeight()) {
                itemsIndexed(sichtbar) { _, z -> LogZeileAnsicht(z, suche, umbruch) }
            }
        }
    }
}

@Composable
private fun LogZeileAnsicht(z: LogZeile, suche: String, umbruch: Boolean) {
    val farbe = when (z.stufe) {
        LogStufe.FEHLER -> StatusDown
        LogStufe.WARNUNG -> StatusWarn
        LogStufe.INFO -> MaterialTheme.colorScheme.onSurface
        LogStufe.LEISE -> TextLeise
    }
    val markierung = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .background(if (z.stufe == LogStufe.FEHLER || z.stufe == LogStufe.WARNUNG) farbe else Color.Transparent),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            buildAnnotatedString {
                if (z.anzahl > 1) withStyle(SpanStyle(color = TextLeise, fontWeight = FontWeight.Bold)) { append("${z.anzahl}× ") }
                val t = z.text
                val s = suche.trim()
                if (s.isEmpty()) {
                    append(t)
                } else {
                    var i = 0
                    while (i < t.length) {
                        val f = t.indexOf(s, i, ignoreCase = true)
                        if (f < 0) { append(t.substring(i)); break }
                        append(t.substring(i, f))
                        withStyle(SpanStyle(background = markierung)) { append(t.substring(f, f + s.length)) }
                        i = f + s.length
                    }
                }
            },
            color = farbe,
            softWrap = umbruch,
            maxLines = if (umbruch) Int.MAX_VALUE else 1,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
        )
    }
}
