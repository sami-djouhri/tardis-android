package de.djouhri.tardis.ui.wirte

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.tardis.data.model.tardis.WirtMessung
import de.djouhri.tardis.data.model.tardis.WirteResponse
import de.djouhri.tardis.data.repository.OpsRepository
import de.djouhri.tardis.ui.components.ErrorMessage
import de.djouhri.tardis.ui.components.StatusDot
import de.djouhri.tardis.ui.components.Verlaufslinie
import de.djouhri.tardis.ui.components.usageColor
import de.djouhri.tardis.ui.theme.StatusIdle
import de.djouhri.tardis.ui.theme.StatusWarn
import de.djouhri.tardis.ui.theme.TextLeise
import de.djouhri.tardis.util.userMessage
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class WirteUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val fenster: Int = 60,
    val antwort: WirteResponse? = null,
)

@HiltViewModel
class WirteViewModel @Inject constructor(
    private val opsRepository: OpsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(WirteUiState())
    val state = _state.asStateFlow()

    fun load(refresh: Boolean = false, fenster: Int = _state.value.fenster) {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = refresh, fenster = fenster) }
            opsRepository.wirte(fenster).fold(
                onSuccess = { a -> _state.update { it.copy(loading = false, refreshing = false, error = null, antwort = a) } },
                // Der letzte gute Stand bleibt stehen, der Fehler kommt dazu.
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }
}

private val FENSTER = listOf(60 to "1 h", 360 to "6 h", 1440 to "24 h")
private val PROZENT = 0.0..100.0

private fun prozent(p: Double?): String =
    p?.let { String.format(Locale.GERMANY, "%.0f %%", it) } ?: "–"

private fun fensterText(minuten: Int): String =
    FENSTER.firstOrNull { it.first == minuten }?.second ?: "$minuten min"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WirteScreen(viewModel: WirteViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    // Wie die Startseite: still nachfassen, solange die Flaeche offen ist.
    LaunchedEffect(Unit) {
        viewModel.load()
        while (true) {
            delay(30_000)
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
            ) { Text("Lade Wirte…") }

            a == null -> ErrorMessage(message = state.error ?: "Keine Antwort", onRetry = { viewModel.load() })

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FENSTER.forEach { (min, text) ->
                            FilterChip(
                                selected = state.fenster == min,
                                onClick = { viewModel.load(fenster = min) },
                                label = { Text(text) },
                            )
                        }
                    }
                    Text(
                        "Linien von vor ${fensterText(a.fensterMinuten)} bis jetzt, Prozent immer auf 0 bis 100.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextLeise,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                state.error?.let { item { Text("Aktualisieren gescheitert: $it", color = StatusWarn, style = MaterialTheme.typography.bodySmall) } }
                if (!a.gemessen) {
                    item { Text("Nicht gemessen: ${a.fehler ?: "Prometheus nicht erreichbar"}", color = StatusWarn) }
                } else {
                    items(a.wirte, key = { it.name }) { WirtKarte(it) }
                }
            }
        }
    }
}

@Composable
private fun WirtKarte(w: WirtMessung) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(color = if (w.erreichbar) usageColor(w.engpass) else StatusIdle)
                Spacer(Modifier.width(8.dp))
                Text(w.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                val neben = listOfNotNull(
                    w.kerne?.let { "$it Kerne" },
                    w.temp?.let { String.format(Locale.GERMANY, "%.0f °C", it) },
                ).joinToString(" · ")
                if (neben.isNotEmpty()) Text(neben, style = MaterialTheme.typography.bodySmall, color = TextLeise)
            }
            if (!w.erreichbar) {
                Text("gerade nicht erreichbar, der Verlauf zeigt die letzten Messungen", style = MaterialTheme.typography.bodySmall, color = StatusWarn)
            }
            w.node?.let {
                Text("Gast auf $it: CPU, Load und Temperatur sind die des Nodes", style = MaterialTheme.typography.bodySmall, color = TextLeise)
            }
            MesswertZeile("CPU", prozent(w.cpu), usageColor(w.cpu), w.erreichbar)
            Verlaufslinie(w.verlauf.cpu, "CPU von ${w.name}", farbe = linienFarbe(w.erreichbar), bereich = PROZENT)
            MesswertZeile("Speicher", prozent(w.ram), usageColor(w.ram), w.erreichbar)
            Verlaufslinie(w.verlauf.ram, "Speicher von ${w.name}", farbe = linienFarbe(w.erreichbar), bereich = PROZENT)
            MesswertZeile("Platte /", prozent(w.platte), usageColor(w.platte), w.erreichbar)
            Verlaufslinie(w.verlauf.platte, "Platte von ${w.name}", farbe = linienFarbe(w.erreichbar), bereich = PROZENT)
            // Load ist keine Prozentzahl: die Skala reicht bis zur Kernzahl,
            // darueber wartet Arbeit. Ohne Kernzahl streckt sie sich selbst.
            val loadText = w.load?.let { String.format(Locale.GERMANY, "%.2f", it) + (w.kerne?.let { k -> " / $k" } ?: "") } ?: "–"
            MesswertZeile("Load (1 min)", loadText, MaterialTheme.colorScheme.onSurface, w.erreichbar)
            val loadMax = maxOf(w.kerne?.toDouble() ?: 0.0, w.verlauf.load.filterNotNull().maxOrNull() ?: 0.0)
            Verlaufslinie(
                w.verlauf.load,
                "Load von ${w.name}",
                farbe = linienFarbe(w.erreichbar),
                bereich = if (loadMax > 0) 0.0..loadMax else null,
            )
        }
    }
}

@Composable
private fun linienFarbe(erreichbar: Boolean): Color? = if (erreichbar) null else StatusIdle

@Composable
private fun MesswertZeile(titel: String, wert: String, farbe: Color, erreichbar: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Text(titel, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            if (erreichbar) wert else "nicht gemessen",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = if (erreichbar) farbe else StatusWarn,
        )
    }
}

/**
 * Karte der Startseite: jeder Wirt eine Zeile mit CPU-Verlauf der letzten Stunde
 * und den drei Prozentwerten. Antippen oeffnet die Unterseite mit allen Linien.
 */
@Composable
fun WirteKarte(antwort: WirteResponse, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Wirte", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!antwort.gemessen) {
                Text("nicht gemessen", style = MaterialTheme.typography.headlineSmall, color = StatusWarn)
                Text(antwort.fehler ?: "Prometheus nicht erreichbar", style = MaterialTheme.typography.bodySmall, color = TextLeise)
                return@Column
            }
            val eng = antwort.wirte.count { it.erreichbar && (it.engpass ?: 0.0) >= 90 }
            val stumm = antwort.wirte.count { !it.erreichbar }
            Text(
                listOfNotNull(
                    "${antwort.wirte.size} Wirte",
                    if (eng > 0) "$eng über 90 %" else null,
                    if (stumm > 0) "$stumm nicht erreichbar" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.padding(top = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                SpaltenKopf("CPU 1 h", 56)
                SpaltenKopf("CPU", 40)
                SpaltenKopf("RAM", 40)
                SpaltenKopf("Platte", 44)
            }
            antwort.wirte.forEach { KurzZeile(it) }
        }
    }
}

@Composable
private fun SpaltenKopf(text: String, breite: Int) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = TextLeise,
        textAlign = TextAlign.End,
        modifier = Modifier.width(breite.dp),
    )
}

@Composable
private fun KurzZeile(w: WirtMessung) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = if (w.erreichbar) usageColor(w.engpass) else StatusIdle)
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(w.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            w.node?.let {
                Text("Gast auf $it", style = MaterialTheme.typography.labelSmall, color = TextLeise, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Verlaufslinie(
            w.verlauf.cpu,
            "CPU von ${w.name}, letzte Stunde",
            modifier = Modifier.width(48.dp),
            farbe = if (w.erreichbar) null else StatusIdle,
            bereich = PROZENT,
            hoehe = 20.dp,
        )
        Spacer(Modifier.width(8.dp))
        if (w.erreichbar) {
            Wert(prozent(w.cpu), usageColor(w.cpu), 40)
            Wert(prozent(w.ram), usageColor(w.ram), 40)
            Wert(prozent(w.platte), usageColor(w.platte), 44)
        } else {
            Text(
                "nicht erreichbar",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(124.dp),
            )
        }
    }
}

@Composable
private fun Wert(text: String, farbe: Color, breite: Int) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        color = farbe,
        textAlign = TextAlign.End,
        maxLines = 1,
        modifier = Modifier.width(breite.dp),
    )
}
