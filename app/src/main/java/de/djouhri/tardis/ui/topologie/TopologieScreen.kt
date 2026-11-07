package de.djouhri.tardis.ui.topologie

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.tardis.data.model.tardis.KartenLayout
import de.djouhri.tardis.data.model.tardis.TopoDienst
import de.djouhri.tardis.data.model.tardis.TopologieResponse
import de.djouhri.tardis.data.model.tardis.kartenLayout
import de.djouhri.tardis.data.repository.OpsRepository
import de.djouhri.tardis.ui.components.ErrorMessage
import de.djouhri.tardis.ui.theme.StatusDown
import de.djouhri.tardis.ui.theme.StatusUp
import de.djouhri.tardis.ui.theme.StatusWarn
import de.djouhri.tardis.ui.theme.TextLeise
import de.djouhri.tardis.util.seitText
import de.djouhri.tardis.util.userMessage
import javax.inject.Inject
import kotlin.math.hypot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TopologieUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val antwort: TopologieResponse? = null,
    val layout: KartenLayout? = null,
)

@HiltViewModel
class TopologieViewModel @Inject constructor(
    private val opsRepository: OpsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TopologieUiState())
    val state = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            opsRepository.topologie().fold(
                onSuccess = { t -> _state.update { TopologieUiState(loading = false, antwort = t, layout = kartenLayout(t)) } },
                onFailure = { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } },
            )
        }
    }
}

/** Wirte, deren Container die App steuern kann (wie die Dienste-Liste). */
private val STEUERBAR = setOf("host", "node1", "node2", "prodesk-ai")

@Composable
fun TopologieScreen(
    onOpenDetail: (host: String, name: String) -> Unit = { _, _ -> },
    viewModel: TopologieViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    val a = state.antwort
    val layout = state.layout
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Lade Topologie…") }
        a == null || layout == null -> ErrorMessage(message = state.error ?: "Keine Antwort", onRetry = { viewModel.load() })
        a.fehler != null -> ErrorMessage(message = "Nicht gemessen: ${a.fehler}", onRetry = { viewModel.load() })
        else -> Karte(a, layout, onOpenDetail)
    }
}

private fun farbe(zustand: String, up: Color, warn: Color, down: Color, leise: Color): Color = when (zustand) {
    "laeuft" -> up
    "aus" -> down
    "unbekannt" -> warn
    else -> leise
}

@Composable
private fun Karte(a: TopologieResponse, layout: KartenLayout, onOpenDetail: (String, String) -> Unit) {
    var nurProbleme by rememberSaveable { mutableStateOf(false) }
    var gewaehlt by rememberSaveable { mutableStateOf<String?>(null) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var versatz by remember { mutableStateOf(Offset.Zero) }
    val dichte = LocalDensity.current.density
    val messer = rememberTextMeasurer()
    val up = StatusUp
    val warn = StatusWarn
    val down = StatusDown
    val leise = TextLeise
    val linie = MaterialTheme.colorScheme.outlineVariant
    val hervor = MaterialTheme.colorScheme.primary
    val flaeche = MaterialTheme.colorScheme.surfaceVariant
    val schrift = MaterialTheme.colorScheme.onSurface
    val dienstNach = remember(a) { a.dienste.associateBy { it.id } }
    val nachbarn = remember(a, gewaehlt) {
        gewaehlt?.let { id -> a.verbindungen.filter { it.von == id || it.nach == id }.map { if (it.von == id) it.nach else it.von }.toSet() }
            ?: emptySet()
    }
    val probleme = a.dienste.count { it.istProblem }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "${a.dienste.count { it.zustand == "laeuft" }} laufen · ${a.dienste.count { it.zustand == "bewusst_aus" }} bewusst aus · $probleme auffällig" +
                    (a.gesundheit?.let { " · Gesundheit $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = leise,
            )
            seitText(a.stand)?.let { Text("Stand $it", style = MaterialTheme.typography.bodySmall, color = leise) }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(selected = nurProbleme, onClick = { nurProbleme = !nurProbleme }, label = { Text("Nur Auffälliges $probleme") })
                FilterChip(selected = false, onClick = { zoom = 1f; versatz = Offset.Zero }, label = { Text("Ganz zeigen") })
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Karte der Wirte und Dienste, mit zwei Fingern zoomen" }
                    .pointerInput(layout) {
                        detectTransformGestures { mitte, schieben, faktor, _ ->
                            val neu = (zoom * faktor).coerceIn(0.6f, 6f)
                            // Um den Fingermittelpunkt zoomen, nicht um die Ecke.
                            versatz = mitte - (mitte - versatz) * (neu / zoom) + schieben
                            zoom = neu
                        }
                    }
                    .pointerInput(layout) {
                        detectTapGestures { tipp ->
                            // Derselbe Massstab wie beim Zeichnen.
                            val s = size.width / layout.breite * zoom
                            val welt = (tipp - versatz) / s
                            val treffer = layout.dienste.minByOrNull { (_, p) -> hypot(p.x - welt.x, p.y - welt.y) }
                            gewaehlt = treffer?.takeIf { (_, p) -> hypot(p.x - welt.x, p.y - welt.y) * s < 24.dp.toPx() }?.key
                        }
                    },
            ) {
                val s = size.width / layout.breite * zoom
                // Schrift in Kartenkoordinaten: sie waechst mit dem Zoom wie alles andere.
                fun welt(groesse: Float) = (groesse / dichte).sp
                withTransform({
                    translate(versatz.x, versatz.y)
                    scale(s, s, Offset.Zero)
                }) {
                    // Wirte
                    layout.wirte.forEach { (id, r) ->
                        drawRoundRect(flaeche, Offset(r.x, r.y), Size(r.b, r.h), CornerRadius(6f, 6f))
                        drawText(
                            messer, id, Offset(r.x + 6f, r.y + 5f),
                            style = TextStyle(color = schrift, fontSize = welt(11f)),
                        )
                    }
                    // Verbindungen: gewaehlte hervor, sonst duenn
                    a.verbindungen.forEach { v ->
                        val p = layout.mitte(v.von) ?: return@forEach
                        val q = layout.mitte(v.nach) ?: return@forEach
                        val aktiv = gewaehlt != null && (v.von == gewaehlt || v.nach == gewaehlt)
                        if (gewaehlt != null && !aktiv) return@forEach
                        drawLine(
                            if (aktiv) hervor else linie.copy(alpha = 0.5f),
                            Offset(p.x, p.y), Offset(q.x, q.y),
                            strokeWidth = if (aktiv) 2f else 0.8f,
                        )
                    }
                    // Gruppen-Ueberschriften erst ab etwas Zoom, sonst Gekritzel
                    if (zoom >= 1.4f) {
                        layout.gruppen.forEach { g ->
                            drawText(messer, g.text, Offset(g.x, g.y - 11f), style = TextStyle(color = leise, fontSize = welt(8f)))
                        }
                    }
                    // Dienste
                    layout.dienste.forEach { (id, p) ->
                        val d = dienstNach[id] ?: return@forEach
                        val gedimmt = (nurProbleme && !d.istProblem) ||
                            (gewaehlt != null && id != gewaehlt && id !in nachbarn)
                        val c = farbe(d.zustand, up, warn, down, leise).copy(alpha = if (gedimmt) 0.2f else 1f)
                        drawCircle(c, radius = 8f, center = Offset(p.x, p.y))
                        if (id == gewaehlt) drawCircle(hervor, radius = 11f, center = Offset(p.x, p.y), style = Stroke(2f))
                        if (zoom >= 2.5f && !gedimmt) {
                            drawText(
                                messer, d.label.take(14), Offset(p.x - 13f, p.y + 9f),
                                style = TextStyle(color = schrift, fontSize = welt(5.5f)),
                            )
                        }
                    }
                }
            }
            gewaehlt?.let { dienstNach[it] }?.let { d ->
                DienstKarte(
                    d,
                    nachbarn.mapNotNull { dienstNach[it]?.label ?: it }.sorted(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onSchliessen = { gewaehlt = null },
                    onOeffnen = d.container?.takeIf { d.wirt in STEUERBAR }?.let { c -> { onOpenDetail(d.wirt, c) } },
                )
            }
        }
    }
}

private val ZUSTAND_TEXT = mapOf(
    "laeuft" to "läuft",
    "bewusst_aus" to "bewusst aus",
    "aus" to "aus, sollte laufen",
    "unbekannt" to "Zustand unbekannt",
)

@Composable
private fun DienstKarte(
    d: TopoDienst,
    nachbarn: List<String>,
    modifier: Modifier,
    onSchliessen: () -> Unit,
    onOeffnen: (() -> Unit)?,
) {
    Card(modifier = modifier.fillMaxWidth().padding(12.dp).heightIn(max = 260.dp)) {
        Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState())) {
            Text(d.label, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(d.wirt, d.gruppe, ZUSTAND_TEXT[d.zustand] ?: d.zustand, d.erwartung?.let { "Erwartung $it" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = when (d.zustand) { "laeuft" -> StatusUp; "aus" -> StatusDown; "unbekannt" -> StatusWarn; else -> TextLeise },
            )
            if (nachbarn.isNotEmpty()) {
                Text("Verbunden mit: ${nachbarn.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
            }
            Row {
                onOeffnen?.let { TextButton(onClick = it) { Text("Dienst öffnen") } }
                TextButton(onClick = onSchliessen) { Text("Schließen") }
            }
        }
    }
}
