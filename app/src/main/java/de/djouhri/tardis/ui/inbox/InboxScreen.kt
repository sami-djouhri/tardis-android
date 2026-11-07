package de.djouhri.tardis.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import de.djouhri.tardis.data.model.tardis.VerlaufZeile
import de.djouhri.tardis.data.model.tardis.gebuendelt
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.tardis.data.model.tardis.InboxCounts
import de.djouhri.tardis.data.model.tardis.InboxItem
import de.djouhri.tardis.data.model.tardis.StreamItem
import de.djouhri.tardis.data.repository.InboxRepository
import de.djouhri.tardis.ui.components.ErrorMessage
import de.djouhri.tardis.ui.components.StatusPill
import de.djouhri.tardis.ui.theme.StatusDown
import de.djouhri.tardis.ui.theme.StatusIdle
import de.djouhri.tardis.ui.theme.StatusUp
import de.djouhri.tardis.ui.theme.StatusWarn
import de.djouhri.tardis.util.seitText
import de.djouhri.tardis.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class InboxUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val items: List<InboxItem> = emptyList(),
    val counts: InboxCounts = InboxCounts(),
    val message: String? = null,
    /**
     * „Verlauf“ zeigt statt der offenen Eintraege die Zeitleiste aller
     * Ereignisse (`/v1/stream`). Ein Umschalter in diesem Bereich statt eines
     * sechsten Reiters: die Leiste traegt schon fuenf.
     */
    val zeigeVerlauf: Boolean = false,
    val verlauf: List<StreamItem> = emptyList(),
    val verlaufLaedt: Boolean = false,
    val verlaufFehler: String? = null,
    /** Filter wie auf der Web-Flaeche Events (seit 2.0.15); leer = alle. */
    val verlaufTypen: Set<String> = emptySet(),
    val verlaufQuellen: Set<String> = emptySet(),
    /**
     * Typen aus dem zuletzt UNGEFILTERTEN Fenster. Unter einem Typfilter enthaelt
     * die Antwort per Konstruktion nur diesen Typ; daraus „die anderen sind leer"
     * zu schliessen waere ein Zirkelschluss (wie im Web).
     */
    val bekannteTypen: Set<String> = emptySet(),
    /** Je gesehene Quellen, gesammelt: die Chips bleiben stabil, auch gefiltert. */
    val bekannteQuellen: Set<String> = emptySet(),
    val verdichten: Boolean = true,
)

@HiltViewModel
class InboxViewModel @Inject constructor(
    private val inboxRepository: InboxRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(InboxUiState())
    val state = _state.asStateFlow()

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(loading = !refresh && it.items.isEmpty(), refreshing = refresh, error = null)
            }
            inboxRepository.list().fold(
                onSuccess = { list ->
                    _state.update {
                        it.copy(loading = false, refreshing = false, error = null, items = list.items, counts = list.counts)
                    }
                },
                onFailure = { error ->
                    _state.update { it.copy(loading = false, refreshing = false, error = error.userMessage()) }
                },
            )
        }
    }

    fun zeigeVerlauf(an: Boolean) {
        _state.update { it.copy(zeigeVerlauf = an) }
        if (an) ladeVerlauf()
    }

    fun ladeVerlauf() {
        viewModelScope.launch {
            val jetzt = _state.value
            _state.update { it.copy(verlaufLaedt = true, verlaufFehler = null) }
            inboxRepository.stream(typen = jetzt.verlaufTypen, quellen = jetzt.verlaufQuellen).fold(
                onSuccess = { liste ->
                    _state.update {
                        it.copy(
                            verlaufLaedt = false,
                            verlauf = liste,
                            bekannteQuellen = it.bekannteQuellen + liste.map { e -> e.source }.filter { q -> q.isNotBlank() } + it.verlaufQuellen,
                            bekannteTypen = if (it.verlaufTypen.isEmpty()) liste.map { e -> e.kind }.toSet() else it.bekannteTypen,
                        )
                    }
                },
                onFailure = { error -> _state.update { it.copy(verlaufLaedt = false, verlaufFehler = error.userMessage()) } },
            )
        }
    }

    fun schalteTyp(typ: String) {
        _state.update { it.copy(verlaufTypen = it.verlaufTypen.umschalten(typ)) }
        ladeVerlauf()
    }

    fun schalteQuelle(quelle: String) {
        _state.update { it.copy(verlaufQuellen = it.verlaufQuellen.umschalten(quelle)) }
        ladeVerlauf()
    }

    fun setzeVerdichten(an: Boolean) = _state.update { it.copy(verdichten = an) }

    private fun Set<String>.umschalten(wert: String) = if (wert in this) this - wert else this + wert

    fun action(externalId: String, action: String, snoozeMinutes: Int? = null) {
        viewModelScope.launch {
            inboxRepository.action(externalId, action, snoozeMinutes).fold(
                onSuccess = {
                    // Optimistisch aus der Liste nehmen, dann neu laden.
                    _state.update { s -> s.copy(items = s.items.filterNot { it.externalId == externalId }) }
                    load(refresh = true)
                },
                onFailure = { error -> _state.update { it.copy(error = error.userMessage()) } },
            )
        }
    }

    fun createNote(title: String, content: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            inboxRepository.createNote(title.trim(), content.trim()).fold(
                onSuccess = { _state.update { it.copy(message = "Notiz gespeichert") } },
                onFailure = { error -> _state.update { it.copy(error = error.userMessage()) } },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(viewModel: InboxViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }

    var showNoteDialog by remember { mutableStateOf(false) }

    PullToRefreshBox(
        isRefreshing = if (state.zeigeVerlauf) state.verlaufLaedt else state.refreshing,
        onRefresh = { if (state.zeigeVerlauf) viewModel.ladeVerlauf() else viewModel.load(refresh = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            state.zeigeVerlauf -> VerlaufListe(
                state,
                onZurueck = { viewModel.zeigeVerlauf(false) },
                onTyp = viewModel::schalteTyp,
                onQuelle = viewModel::schalteQuelle,
                onVerdichten = viewModel::setzeVerdichten,
            )

            state.loading -> Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { Text("Lade Inbox…") }

            state.error != null && state.items.isEmpty() ->
                ErrorMessage(message = state.error!!, onRetry = { viewModel.load() })

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${state.counts.open} offen · ${state.counts.snoozed} zurückgestellt",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { viewModel.zeigeVerlauf(true) }) { Text("Verlauf") }
                        TextButton(onClick = { showNoteDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Text(" Notiz")
                        }
                    }
                }
                if (state.items.isEmpty()) {
                    item {
                        Text(
                            "Nichts Offenes. Alles erledigt.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 24.dp),
                        )
                    }
                }
                items(state.items, key = { it.externalId }) { item ->
                    InboxCard(
                        item = item,
                        onDone = { viewModel.action(item.externalId, "done") },
                        onSnooze = { viewModel.action(item.externalId, "snooze", snoozeMinutes = 180) },
                        onArchive = { viewModel.action(item.externalId, "archive") },
                    )
                }
            }
        }
    }

    if (showNoteDialog) {
        NoteDialog(
            onDismiss = { showNoteDialog = false },
            onSave = { title, content ->
                viewModel.createNote(title, content)
                showNoteDialog = false
            },
        )
    }
}

@Composable
private fun severityColor(severity: String) = when (severity.lowercase()) {
    "critical", "error", "high" -> StatusDown
    "warning", "warn", "medium" -> StatusWarn
    "info" -> StatusUp
    else -> StatusIdle
}

@Composable
private fun InboxCard(
    item: InboxItem,
    onDone: () -> Unit,
    onSnooze: () -> Unit,
    onArchive: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                StatusPill(text = item.source, color = severityColor(item.severity))
            }
            item.detail?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                )
            }
            item.ageHours?.let { age ->
                Spacer(Modifier.height(4.dp))
                Text(
                    if (age < 1.0) "gerade eben" else "vor ${age.toInt()} h",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDone) { Text("Erledigt") }
                TextButton(onClick = onSnooze) { Text("Später") }
                TextButton(onClick = onArchive) { Text("Archiv") }
            }
        }
    }
}

@Composable
private fun NoteDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Notiz") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titel") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Inhalt (optional)") },
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, content) }, enabled = title.isNotBlank()) {
                Text("Speichern")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Abbrechen") } },
    )
}

private val ART = mapOf(
    "mail" to "Post",
    "news" to "Nachricht",
    "inbox" to "Inbox",
    "deal" to "Angebot",
    "alert" to "Alarm",
    "document" to "Dokument",
    "service" to "Dienst",
)

/** Die Zeitleiste aller Ereignisse, juengste zuerst. Nur lesen, keine Aktionen. */
@Composable
private fun VerlaufListe(
    state: InboxUiState,
    onZurueck: () -> Unit,
    onTyp: (String) -> Unit = {},
    onQuelle: (String) -> Unit = {},
    onVerdichten: (Boolean) -> Unit = {},
) {
    val zeilen = if (state.verdichten) state.verlauf.gebuendelt() else state.verlauf.map { VerlaufZeile(it) }
    val jeQuelle = state.verlauf.groupingBy { it.source }.eachCount()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Verlauf · ${state.verlauf.size} Ereignisse",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onZurueck) { Text("Offen") }
            }
        }
        item {
            // Typen: was im ungefilterten Fenster vorkam, plus die aktiven.
            ChipZeile((state.bekannteTypen + state.verlaufTypen).sorted()) { typ ->
                FilterChip(
                    selected = typ in state.verlaufTypen,
                    onClick = { onTyp(typ) },
                    label = { Text(ART[typ] ?: typ) },
                )
            }
        }
        if (state.bekannteQuellen.isNotEmpty()) {
            item {
                ChipZeile(state.bekannteQuellen.sorted()) { quelle ->
                    FilterChip(
                        selected = quelle in state.verlaufQuellen,
                        onClick = { onQuelle(quelle) },
                        label = { Text(jeQuelle[quelle]?.let { "$quelle $it" } ?: quelle) },
                    )
                }
            }
        }
        item {
            FilterChip(
                selected = state.verdichten,
                onClick = { onVerdichten(!state.verdichten) },
                label = { Text("Wiederholungen bündeln") },
            )
        }
        state.verlaufFehler?.let { fehler ->
            item { Text(fehler, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
        }
        if (!state.verlaufLaedt && state.verlaufFehler == null && state.verlauf.isEmpty()) {
            item { Text("Keine Ereignisse.", style = MaterialTheme.typography.bodyMedium) }
        }
        items(zeilen, key = { z -> z.item.externalId + z.item.eventType + z.item.occurredAt }) { z ->
            val e = z.item
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            e.title?.takeIf { it.isNotBlank() } ?: e.eventType,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                        )
                        Spacer(Modifier.width(8.dp))
                        StatusPill(text = ART[e.kind] ?: e.source, color = severityColor(e.severity))
                    }
                    e.body?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 3,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        listOfNotNull(
                            seitText(e.zeitpunktUtc),
                            if (z.anzahl > 1) "${z.anzahl}× seit ${seitText(z.aeltestUtc) ?: "früher"}" else null,
                            e.source.takeIf { it.isNotBlank() },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Waagrecht wischbare Reihe von Chips. */
@Composable
private fun ChipZeile(werte: List<String>, chip: @Composable (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) { werte.forEach { chip(it) } }
}
