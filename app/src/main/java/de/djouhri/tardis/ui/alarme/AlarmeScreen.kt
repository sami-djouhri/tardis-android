package de.djouhri.tardis.ui.alarme

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.tardis.data.model.tardis.AlarmGruppe
import de.djouhri.tardis.data.model.tardis.AlarmeResponse
import de.djouhri.tardis.data.model.tardis.gruppiert
import de.djouhri.tardis.data.repository.OpsRepository
import de.djouhri.tardis.ui.components.ErrorMessage
import de.djouhri.tardis.ui.components.StatusDot
import de.djouhri.tardis.ui.theme.TextLeise
import de.djouhri.tardis.ui.theme.StatusDown
import de.djouhri.tardis.ui.theme.StatusUp
import de.djouhri.tardis.ui.theme.StatusWarn
import de.djouhri.tardis.util.seitText
import de.djouhri.tardis.util.uhrzeitAus
import de.djouhri.tardis.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import de.djouhri.tardis.widget.WidgetSpeicher
import javax.inject.Inject

data class AlarmeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    /** Der Abruf selbst scheiterte (Netz, Token). Etwas anderes als `antwort.erreichbar == false`. */
    val error: String? = null,
    val antwort: AlarmeResponse? = null,
) {
    /** Nur mit einer erreichbaren Quelle ist eine Zahl eine Aussage. */
    val anzahl: Int? get() = antwort?.takeIf { it.erreichbar }?.alarme?.size
    val kritisch: Int get() = antwort?.takeIf { it.erreichbar }?.kritisch ?: 0
}

/**
 * Gehoert der Activity, nicht dem Tab: die Uebersicht und der Zaehler an der
 * Navigationsleiste lesen denselben Stand. Sonst fragte jede Flaeche fuer sich,
 * und der Zaehler zeigte eine andere Zahl als die Liste darunter.
 */
@HiltViewModel
class AlarmeViewModel @Inject constructor(
    private val opsRepository: OpsRepository,
    private val widget: WidgetSpeicher,
) : ViewModel() {

    private val _state = MutableStateFlow(AlarmeUiState())
    val state = _state.asStateFlow()

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = refresh) }
            opsRepository.alarme().fold(
                onSuccess = { a ->
                    _state.update { AlarmeUiState(loading = false, antwort = a) }
                    // Das Widget bekommt denselben Stand, den die Leiste zeigt.
                    if (widget.alarmeGesehen(a)) widget.neuZeichnen()
                },
                onFailure = { e ->
                    // Die letzte gute Liste bleibt stehen, der Fehler kommt dazu:
                    // ein Funkloch soll die Liste nicht leeren.
                    _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmeScreen(viewModel: AlarmeViewModel) {
    val state by viewModel.state.collectAsState()
    // Owner 2026-11-06: Filter nach Bereich statt „nur kritische“, die stehen
    // ohnehin oben. null = alle Bereiche.
    var bereich by rememberSaveable { mutableStateOf<String?>(null) }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { viewModel.load(refresh = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        val antwort = state.antwort
        when {
            state.loading -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text("Lade Alarme…") }

            antwort == null -> ErrorMessage(message = state.error ?: "Keine Antwort", onRetry = { viewModel.load() })

            else -> {
                // Ein Bereich, der nach dem Aktualisieren leer ist, filtert nicht ins Nichts.
                val aktiv = bereich?.takeIf { b -> antwort.alarme.any { it.bereich == b } }
                val gruppen = antwort.alarme
                    .filter { aktiv == null || it.bereich == aktiv }
                    .gruppiert()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { Kopf(state, antwort, aktiv) { bereich = it } }
                    items(gruppen, key = { it.name }) { AlarmKarte(it) }
                }
            }
        }
    }
}

@Composable
private fun Kopf(
    state: AlarmeUiState,
    antwort: AlarmeResponse,
    bereich: String?,
    setzeBereich: (String?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!antwort.erreichbar) {
            // Der wichtigste Satz dieser Flaeche: ohne Quelle gibt es keine
            // Aussage, auch nicht „alles ruhig".
            Text(
                "Nicht gemessen: Alarmquelle nicht erreichbar",
                style = MaterialTheme.typography.headlineSmall,
                color = StatusWarn,
            )
            antwort.fehler?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            return@Column
        }
        Text(
            when {
                antwort.alarme.isEmpty() -> "keine aktiven Alarme"
                antwort.kritisch > 0 -> "${antwort.alarme.size} aktiv, ${antwort.kritisch} kritisch"
                else -> "${antwort.alarme.size} aktiv"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = when {
                antwort.alarme.isEmpty() -> StatusUp
                antwort.kritisch > 0 -> StatusDown
                else -> StatusWarn
            },
        )
        // Prometheus zuletzt nicht erreicht: die Liste ist der letzte gute Stand.
        antwort.fehler?.let {
            Text("Letzter Abruf von Prometheus gescheitert, Liste kann veraltet sein", color = StatusWarn,
                style = MaterialTheme.typography.bodySmall)
        }
        state.error?.let {
            Text("Aktualisieren gescheitert: $it", color = StatusWarn, style = MaterialTheme.typography.bodySmall)
        }
        uhrzeitAus(antwort.stand)?.let {
            Text("Stand $it Uhr", style = MaterialTheme.typography.bodySmall, color = TextLeise)
        }
        val bereiche = antwort.alarme.groupingBy { it.bereich }.eachCount()
        if (bereiche.size > 1) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = bereich == null,
                    onClick = { setzeBereich(null) },
                    label = { Text("Alle") },
                )
                bereiche.entries.sortedByDescending { it.value }.forEach { (name, anzahl) ->
                    FilterChip(
                        selected = bereich == name,
                        onClick = { setzeBereich(if (bereich == name) null else name) },
                        label = { Text("$name $anzahl") },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlarmKarte(gruppe: AlarmGruppe) {
    val farbe = if (gruppe.istKritisch) StatusDown else StatusWarn
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(color = farbe)
                Spacer(Modifier.width(8.dp))
                Text(
                    gruppe.name,
                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f),
                )
                if (gruppe.alarme.size > 1) {
                    Text("×${gruppe.alarme.size}", style = MaterialTheme.typography.labelMedium, color = farbe)
                }
            }
            gruppe.alarme.forEach { a ->
                Spacer(Modifier.height(6.dp))
                Text(a.zusammenfassung, style = MaterialTheme.typography.bodyMedium)
                a.beschreibung?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val neben = listOfNotNull(a.instanz, seitText(a.seit))
                if (neben.isNotEmpty()) {
                    Text(
                        neben.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLeise,
                    )
                }
            }
        }
    }
}
