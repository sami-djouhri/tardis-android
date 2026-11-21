package de.djouhri.tardis.ui.owner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.AlertDialog
import de.djouhri.tardis.ui.components.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.tardis.data.model.tardis.OwnerSchritt
import de.djouhri.tardis.data.model.tardis.OwnerSchritteResponse
import de.djouhri.tardis.data.model.tardis.gegenprobeText
import de.djouhri.tardis.data.model.tardis.verpasstText
import de.djouhri.tardis.ui.theme.StatusDown
import de.djouhri.tardis.data.repository.OpsRepository
import de.djouhri.tardis.ui.components.ErrorMessage
import de.djouhri.tardis.ui.theme.StatusUp
import de.djouhri.tardis.ui.theme.StatusWarn
import de.djouhri.tardis.ui.theme.TextLeise
import de.djouhri.tardis.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject
import de.djouhri.tardis.data.repository.FreigabenRepository

data class OwnerUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val antwort: OwnerSchritteResponse? = null,
    /** Meldung nach „Gemacht", gut oder schlecht. */
    val hinweis: String? = null,
    /** Schritt-IDs mit einem offenen Skript unter Freigaben > Befehle (seit 2.0.31). */
    val alsBefehl: Set<String> = emptySet(),
)

@HiltViewModel
class OwnerViewModel @Inject constructor(
    private val opsRepository: OpsRepository,
    private val freigaben: FreigabenRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(OwnerUiState())
    val state = _state.asStateFlow()

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = refresh) }
            opsRepository.ownerSchritte().fold(
                onSuccess = { a -> _state.update { it.copy(loading = false, refreshing = false, error = null, antwort = a) } },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
            freigaben.befehle().onSuccess { b ->
                _state.update { it.copy(alsBefehl = b.befehle.filter { x -> x.offen }.mapNotNull { x -> x.schritt }.toSet()) }
            }
        }
    }

    fun gemacht(schritt: OwnerSchritt) {
        viewModelScope.launch {
            opsRepository.gemacht(schritt.id).fold(
                onSuccess = {
                    // Sofort als gemeldet zeigen; der naechste Abruf bestaetigt es vom Server.
                    _state.update { s ->
                        s.copy(
                            hinweis = "„${schritt.titel}“ gemeldet. Abgehakt wird nach der Gegenprobe.",
                            antwort = s.antwort?.let { a ->
                                a.copy(schritte = a.schritte.map { if (it.id == schritt.id) it.copy(gemeldet = true) else it })
                            },
                        )
                    }
                },
                onFailure = { e -> _state.update { it.copy(hinweis = "Nicht gemeldet: ${e.userMessage()}") } },
            )
        }
    }

    fun hinweisGelesen() = _state.update { it.copy(hinweis = null) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnerScreen(onOpenFreigaben: () -> Unit = {}, viewModel: OwnerViewModel = hiltViewModel()) {
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
            ) { Text("Lade…") }

            a == null -> ErrorMessage(message = state.error ?: "Keine Antwort", onRetry = { viewModel.load() })

            !a.gemessen -> ErrorMessage(message = a.fehler ?: "Nicht gemessen", onRetry = { viewModel.load() })

            else -> OwnerInhalt(
                a, state.hinweis, onGemacht = viewModel::gemacht, onHinweisGelesen = viewModel::hinweisGelesen,
                alsBefehl = state.alsBefehl, onOpenFreigaben = onOpenFreigaben,
            )
        }
    }
}

private val TAG = DateTimeFormatter.ofPattern("EE d.M.", Locale.GERMANY)

/** Ohne ViewModel, damit der Bildschirmfoto-Test sie mit Demo-Daten zeichnen kann. */
@Composable
fun OwnerInhalt(
    a: OwnerSchritteResponse,
    hinweis: String?,
    onGemacht: (OwnerSchritt) -> Unit,
    onHinweisGelesen: () -> Unit,
    heute: LocalDate = LocalDate.now(),
    alsBefehl: Set<String> = emptySet(),
    onOpenFreigaben: () -> Unit = {},
) {
    var frage by remember { mutableStateOf<OwnerSchritt?>(null) }
    val jetzt = LocalDateTime.now()
    val g = a.gruppen(heute)
    val dranZahl = g.verpasst.size + g.heute.size + g.ohneTermin.size
    // Reihenfolge (2.0.29): Verpasstes zuerst, dann heute, dann ohne Termin, dann
    // Geplantes nach Tag, zuletzt Gemeldetes und Wartendes. Vorher lag Verpasstes
    // unmarkiert im selben Stapel wie alles ohne Termin.
    val abschnitte = listOf(
        Triple("Verpasst", g.verpasst, StatusWarn),
        Triple("Heute", g.heute, null),
        Triple("Ohne Termin", g.ohneTermin, null),
        Triple("Geplant", g.geplant, null),
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    if (dranZahl == 0) "Heute nichts" else "$dranZahl jetzt dran",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (dranZahl == 0) StatusUp else StatusWarn,
                )
                if (g.verpasst.isNotEmpty()) {
                    Text(
                        "davon ${g.verpasst.size} mit verpasstem Termin",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StatusWarn,
                    )
                }
                Text(
                    "Aus dem Aufgaben-Dienst, Stand vor ${a.standVorMin ?: "?"} min" +
                        if (a.veraltet) ". Abgleich hängt, Liste kann alt sein." else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (a.veraltet) StatusWarn else TextLeise,
                )
            }
        }
        hinweis?.let { h ->
            item {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth().clickable { onHinweisGelesen() }) {
                    Text(h, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        abschnitte.forEach { (titel, liste, farbe) ->
            if (liste.isNotEmpty()) {
                item(key = "kopf-$titel") {
                    Text(
                        "$titel (${liste.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = farbe ?: MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                items(liste, key = { it.id }) {
                    SchrittKarte(it, heute, jetzt, onGemacht = { frage = it }, alsBefehl = it.id in alsBefehl, onOpenFreigaben = onOpenFreigaben)
                }
            }
        }
        if (g.zurueck.isNotEmpty()) {
            item(key = "kopf-zurueck") {
                Text("Gemeldet oder wartet (${g.zurueck.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            items(g.zurueck, key = { it.id }) { SchrittKarte(it, heute, jetzt, onGemacht = null) }
        }
    }

    frage?.let { s ->
        AlertDialog(
            onDismissRequest = { frage = null },
            title = { Text("Als gemacht melden?") },
            text = { Text("„${s.titel}“. Abgehakt wird erst, wenn die Gegenprobe grün ist.") },
            confirmButton = {
                TextButton(onClick = { onGemacht(s); frage = null }) { Text("Gemacht") }
            },
            dismissButton = { TextButton(onClick = { frage = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun SchrittKarte(
    s: OwnerSchritt,
    heute: LocalDate,
    jetzt: LocalDateTime,
    onGemacht: (() -> Unit)?,
    alsBefehl: Boolean = false,
    onOpenFreigaben: () -> Unit = {},
) {
    val context = LocalContext.current
    var offen by rememberSaveable(s.id) { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.titel, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                s.tagDatum?.let { Text(TAG.format(it), style = MaterialTheme.typography.bodySmall, color = TextLeise) }
            }
            val probe = gegenprobeText(s, jetzt)
            when {
                s.gemeldet -> Text("gemeldet, wird übertragen", style = MaterialTheme.typography.bodySmall, color = StatusUp)
                s.gegenprobeRot && probe != null -> Text(probe, style = MaterialTheme.typography.bodySmall, color = StatusDown)
                probe != null -> Text(probe, style = MaterialTheme.typography.bodySmall, color = StatusWarn)
                s.wartet != null -> Text(s.wartet, style = MaterialTheme.typography.bodySmall, color = StatusWarn)
            }
            verpasstText(s, heute)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = StatusWarn) }
            if (s.text.isNotBlank()) {
                Text(
                    s.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (offen) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp).clickable { offen = !offen },
                )
            }
            if (alsBefehl) {
                // Seit 2.0.31: liegt ein fertiges Skript unter Freigaben, ist das der kurze Weg.
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).clickable { onOpenFreigaben() },
                ) {
                    Text(
                        "Fertiges Skript unter Freigaben: ansehen und per Fingerabdruck ausführen, kein Terminal nötig.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(10.dp),
                    )
                }
            }
            // Befehle zum Kopieren: der Weg vom Telefon in ein Terminal ist kopieren, nicht abtippen.
            s.befehle.forEach { befehl ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        befehl,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("befehl", befehl))
                        Toast.makeText(context, "Befehl kopiert", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Befehl kopieren")
                    }
                }
            }
            if (onGemacht != null) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onGemacht) { Text("Gemacht") }
            }
        }
    }
}
