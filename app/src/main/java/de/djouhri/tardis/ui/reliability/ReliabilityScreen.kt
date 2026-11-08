package de.djouhri.tardis.ui.reliability

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.tardis.data.model.tardis.DienstVerfuegbarkeit
import de.djouhri.tardis.data.model.tardis.ReliabilityResponse
import de.djouhri.tardis.data.model.tardis.SystemMetrik
import de.djouhri.tardis.data.model.tardis.VerlaufReihe
import de.djouhri.tardis.data.model.tardis.Vorfall
import de.djouhri.tardis.data.repository.OpsRepository
import de.djouhri.tardis.ui.components.ErrorMessage
import de.djouhri.tardis.ui.components.Verlaufslinie
import de.djouhri.tardis.ui.theme.StatusDown
import de.djouhri.tardis.ui.theme.StatusUp
import de.djouhri.tardis.ui.theme.StatusWarn
import de.djouhri.tardis.ui.theme.TextLeise
import de.djouhri.tardis.util.seitText
import de.djouhri.tardis.util.userMessage
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ReliabilityUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val fenster: Int = 60,
    val antwort: ReliabilityResponse? = null,
)

@HiltViewModel
class ReliabilityViewModel @Inject constructor(
    private val opsRepository: OpsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ReliabilityUiState())
    val state = _state.asStateFlow()

    fun load(refresh: Boolean = false, fenster: Int = _state.value.fenster) {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = refresh, fenster = fenster) }
            opsRepository.reliability(fenster).fold(
                onSuccess = { a -> _state.update { it.copy(loading = false, refreshing = false, error = null, antwort = a) } },
                // Der letzte gute Stand bleibt stehen, der Fehler kommt dazu.
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }
}

private val FENSTER = listOf(60 to "1 h", 360 to "6 h", 1440 to "24 h")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReliabilityScreen(viewModel: ReliabilityViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { viewModel.load(refresh = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        val a = state.antwort
        when {
            state.loading && a == null -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text("Lade Verfügbarkeit…") }

            a == null -> ErrorMessage(message = state.error ?: "Keine Antwort", onRetry = { viewModel.load() })

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.error?.let { item { Text("Aktualisieren gescheitert: $it", color = StatusWarn, style = MaterialTheme.typography.bodySmall) } }
                item { KennzahlenKarte(a) }
                item { DiensteKarte(a) }
                item { VerlaufKarte(a, state.fenster) { viewModel.load(fenster = it) } }
                item { SystemKarte(a) }
                item { VorfaelleKarte(a) }
            }
        }
    }
}

@Composable
private fun Karte(titel: String, inhalt: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(titel, style = MaterialTheme.typography.titleMedium)
            inhalt()
        }
    }
}

@Composable
private fun NichtGemessen(grund: String?) {
    Text("Nicht gemessen", style = MaterialTheme.typography.titleSmall, color = StatusWarn)
    grund?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextLeise) }
}

@Composable
private fun KennzahlenKarte(a: ReliabilityResponse) {
    val k = a.kennzahlen
    Karte("Verfügbarkeit") {
        if ("dienste" in a.fehler) {
            NichtGemessen(a.fehler["dienste"])
            return@Karte
        }
        Text(
            if (k.unter99 == 0) "alle gewerteten Dienste ≥ 99 %" else "${k.unter99} unter 99 % (24 h)",
            style = MaterialTheme.typography.headlineSmall,
            color = if (k.unter99 == 0) StatusUp else StatusDown,
        )
        Text(
            "${k.unauffaellig} voll verfügbar · ${k.bewusstAus} bewusst aus · ${k.nichtGefuehrt} nicht geführt · ${k.incidents24h} Vorfälle (24 h)",
            style = MaterialTheme.typography.bodySmall,
            color = TextLeise,
        )
    }
}

private fun prozent(p: Double?): String = when {
    p == null -> "–"
    p >= 99.95 -> String.format(Locale.GERMANY, "%.0f %%", p)
    else -> String.format(Locale.GERMANY, "%.2f %%", p)
}

@Composable
private fun prozentFarbe(p: Double?, ohneWertung: Boolean): Color = when {
    ohneWertung || p == null -> TextLeise
    p >= 99.0 -> StatusUp
    p >= 95.0 -> StatusWarn
    else -> StatusDown
}

@Composable
private fun DiensteKarte(a: ReliabilityResponse) {
    // Wie im Web: die meisten Zeilen sind 100 / 100 / 100. Erst das
    // Auffaellige, der Rest auf Wunsch.
    var alle by rememberSaveable { mutableStateOf(false) }
    Karte("Dienste (24 h / 7 T / 30 T)") {
        if ("dienste" in a.fehler) {
            NichtGemessen(a.fehler["dienste"])
            return@Karte
        }
        val sichtbar = if (alle) a.dienste else a.dienste.filter { it.auffaellig }
        if (sichtbar.isEmpty()) {
            Text("Alle ${a.dienste.size} Dienste waren voll verfügbar.", style = MaterialTheme.typography.bodyMedium)
        }
        sichtbar.forEach { DienstZeile(it) }
        val rest = a.dienste.size - a.dienste.count { it.auffaellig }
        if (rest > 0) {
            TextButton(onClick = { alle = !alle }) {
                Text(if (alle) "Nur Auffälliges zeigen" else "$rest unauffällige zeigen")
            }
        }
    }
}

@Composable
private fun DienstZeile(d: DienstVerfuegbarkeit) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                d.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f),
            )
            Text(prozent(d.uptime24h), color = prozentFarbe(d.uptime24h, d.ohneWertung), style = MaterialTheme.typography.bodyMedium)
        }
        val neben = listOfNotNull(
            "7 T ${prozent(d.uptime7d)} · 30 T ${prozent(d.uptime30d)}",
            d.erwartung,
            if (d.nichtGefuehrt) "nicht in der control-map" else null,
        )
        Text(neben.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = TextLeise)
    }
}

@Composable
private fun VerlaufKarte(a: ReliabilityResponse, fenster: Int, setzeFenster: (Int) -> Unit) {
    Karte("Cluster-Verlauf") {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FENSTER.forEach { (min, text) ->
                FilterChip(selected = fenster == min, onClick = { setzeFenster(min) }, label = { Text(text) })
            }
        }
        if (a.verlauf.none { it.erreichbar }) {
            NichtGemessen("Prometheus nicht erreichbar")
            return@Karte
        }
        a.verlauf.forEach { VerlaufZeile(it) }
    }
}

@Composable
private fun VerlaufZeile(r: VerlaufReihe) {
    val werte = r.punkte.map { it.v }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(r.titel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        val letzter = werte.lastOrNull()
        Text(
            if (!r.erreichbar || letzter == null) "nicht gemessen"
            else String.format(Locale.GERMANY, if (r.einheit == "%") "%.0f %%" else "%.2f", letzter),
            style = MaterialTheme.typography.bodyMedium,
            color = if (r.erreichbar) MaterialTheme.colorScheme.onSurface else StatusWarn,
        )
    }
    Verlaufslinie(werte, "${r.titel} im gewählten Zeitraum")
}

@Composable
private fun SystemKarte(a: ReliabilityResponse) {
    Karte("System-Metriken") {
        when {
            "system" in a.fehler -> NichtGemessen(a.fehler["system"])
            a.system.isEmpty() -> Text("Noch keine Metriken erfasst.", style = MaterialTheme.typography.bodyMedium)
            else -> a.system.forEach { SystemZeile(it) }
        }
    }
}

@Composable
private fun SystemZeile(m: SystemMetrik) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(m.metrik, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        val schwelle = m.schwelle?.let { String.format(Locale.GERMANY, " / Schwelle %.0f", it) } ?: ""
        Text(
            (m.letzter?.let { String.format(Locale.GERMANY, "%.1f", it) } ?: "–") + schwelle,
            style = MaterialTheme.typography.bodyMedium,
            color = if (m.ueberSchwelle) StatusDown else MaterialTheme.colorScheme.onSurface,
        )
    }
    Verlaufslinie(m.werte, "Verlauf ${m.metrik}", farbe = if (m.ueberSchwelle) StatusDown else null)
}

@Composable
private fun VorfaelleKarte(a: ReliabilityResponse) {
    Karte("Letzte Vorfälle") {
        when {
            "incidents" in a.fehler -> NichtGemessen(a.fehler["incidents"])
            a.incidents.isEmpty() -> Text("Keine Zustandswechsel im Zeitraum.", style = MaterialTheme.typography.bodyMedium)
            else -> a.incidents.take(20).forEach { VorfallZeile(it) }
        }
        if (a.incidents.size > 20) {
            Text("und ${a.incidents.size - 20} ältere", style = MaterialTheme.typography.bodySmall, color = TextLeise)
        }
    }
}

@Composable
private fun VorfallZeile(v: Vorfall) {
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                v.container,
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f),
            )
            seitText(v.zeit)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextLeise) }
        }
        val wechsel = listOfNotNull(v.alt, v.neu).joinToString(" → ")
        val zeile = listOf(wechsel, v.details.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
        if (zeile.isNotBlank()) {
            Text(zeile, style = MaterialTheme.typography.bodySmall, color = if (v.neu == "running") StatusUp else StatusWarn)
        }
    }
}
