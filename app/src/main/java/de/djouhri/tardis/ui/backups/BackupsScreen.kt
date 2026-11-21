package de.djouhri.tardis.ui.backups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import de.djouhri.tardis.ui.components.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.tardis.data.model.tardis.BackupWirt
import de.djouhri.tardis.data.model.tardis.BackupsResponse
import de.djouhri.tardis.data.model.tardis.stundenText
import de.djouhri.tardis.data.repository.OpsRepository
import de.djouhri.tardis.ui.components.ErrorMessage
import de.djouhri.tardis.ui.components.StatusDot
import de.djouhri.tardis.ui.theme.StatusDown
import de.djouhri.tardis.ui.theme.StatusIdle
import de.djouhri.tardis.ui.theme.StatusUp
import de.djouhri.tardis.ui.theme.StatusWarn
import de.djouhri.tardis.ui.theme.TextLeise
import de.djouhri.tardis.util.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class BackupsUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val antwort: BackupsResponse? = null,
    /** Zeitpunkt (epoch ms), wenn [antwort] aus dem Speicher kommt. */
    val altStand: Long? = null,
)

@HiltViewModel
class BackupsViewModel @Inject constructor(
    private val opsRepository: OpsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BackupsUiState())
    val state = _state.asStateFlow()

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = refresh) }
            opsRepository.backups().fold(
                onSuccess = { s ->
                    _state.update { it.copy(loading = false, refreshing = false, error = null, antwort = s.daten, altStand = s.alt) }
                },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupsScreen(viewModel: BackupsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    // Der Pruefer laeuft alle 4 h; jede Minute nachsehen reicht weit.
    LaunchedEffect(Unit) {
        viewModel.load()
        while (true) {
            delay(60_000)
            viewModel.load()
        }
    }
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
            ) { Text("Lade Sicherung…") }

            a == null -> ErrorMessage(message = state.error ?: "Keine Antwort", onRetry = { viewModel.load() })

            !a.gemessen -> ErrorMessage(message = a.fehler ?: "Sicherungsstand nicht gemessen", onRetry = { viewModel.load() })

            else -> BackupsInhalt(a)
        }
    }
}

/** Ohne ViewModel, damit der Bildschirmfoto-Test sie mit Demo-Daten zeichnen kann. */
@Composable
fun BackupsInhalt(a: BackupsResponse) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Kopf(a) }
        if (a.probleme.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Befunde", style = MaterialTheme.typography.titleMedium, color = StatusWarn)
                        a.probleme.forEach { p ->
                            Text(p.bereich, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                            Text(p.grund, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        // Auffaellige zuerst: die Frage auf dieser Seite ist „wo fehlt etwas".
        items(a.wirte.sortedWith(compareBy({ !it.istAuffaellig }, { it.bekanntAus }, { it.name })), key = { it.name }) {
            WirtZeile(it, a.probenGemessen)
        }
    }
}

@Composable
private fun Kopf(a: BackupsResponse) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            val auffaellig = a.auffaellig.size
            Text(
                if (auffaellig == 0 && a.ok == true) "alles gesichert" else if (auffaellig == 0) "Wirte gesichert" else "$auffaellig auffällig",
                style = MaterialTheme.typography.headlineMedium,
                color = when {
                    auffaellig > 0 -> StatusDown
                    a.ok != true || a.veraltet -> StatusWarn
                    else -> StatusUp
                },
            )
            val zeilen = buildList {
                add("geprüft vor ${stundenText(a.geprueftVorH, dativ = true) ?: "?"}" + if (a.veraltet) ", Prüfer hängt" else "")
                a.geheimnisseOk?.let { add(if (it) "Schlüsselbund gesichert" else "Schlüsselbund fehlt") }
                a.ablageProzent?.let { p ->
                    add(String.format(Locale.GERMANY, "Off-Site %.0f %% belegt", p) +
                        (a.ablageFreiGb?.let { String.format(Locale.GERMANY, ", %.0f GB frei", it) } ?: ""))
                }
                if (!a.probenGemessen) add("Rückspielproben nicht gemessen")
            }
            zeilen.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun WirtZeile(w: BackupWirt, probenGemessen: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(color = farbe(w))
                Spacer(Modifier.width(8.dp))
                Text(w.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (w.bekanntAus) Text("bewusst aus", style = MaterialTheme.typography.bodySmall, color = TextLeise)
            }
            Spacer(Modifier.height(4.dp))
            Messzeile(
                "Sicherung",
                when {
                    w.alterH == null -> "keine Angabe"
                    else -> "vor ${stundenText(w.alterH, dativ = true)}" + (w.grenzeH?.let { " (Grenze ${stundenText(it)})" } ?: "")
                },
                when (w.ok) { true -> StatusUp; false -> StatusDown; null -> StatusIdle },
            )
            Messzeile(
                "Probe",
                when {
                    !probenGemessen -> "nicht gemessen"
                    w.probeOk == null -> "keine Probe"
                    w.probeOk -> "zurückgespielt vor ${stundenText(w.probeAlterH, dativ = true)}"
                    else -> "gescheitert oder zu alt (${stundenText(w.probeAlterH) ?: "?"})"
                },
                when (w.probeOk) { true -> StatusUp; false -> StatusDown; null -> StatusIdle },
            )
            w.snapshots?.let { Messzeile("Stände", "$it", MaterialTheme.colorScheme.onSurfaceVariant) }
            w.probleme.forEach {
                Text(it, style = MaterialTheme.typography.bodySmall, color = StatusDown, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun Messzeile(label: String, wert: String, farbe: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(84.dp))
        Text(wert, style = MaterialTheme.typography.bodySmall, color = farbe)
    }
}

/** Grau heisst „nicht gemessen", nie gruen: fehlende Information ist kein guter Zustand. */
@Composable
private fun farbe(w: BackupWirt): Color = when {
    w.bekanntAus -> StatusIdle
    w.istAuffaellig -> StatusDown
    w.ok == true && w.probeOk == true -> StatusUp
    else -> StatusWarn
}
