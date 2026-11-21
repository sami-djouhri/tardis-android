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
import de.djouhri.tardis.ui.components.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.platform.LocalUriHandler
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
import de.djouhri.tardis.ui.theme.TextLeise
import de.djouhri.tardis.util.seitText
import de.djouhri.tardis.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import java.time.Duration
import java.time.LocalDateTime

data class InboxUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val items: List<InboxItem> = emptyList(),
    val counts: InboxCounts = InboxCounts(),
    val message: String? = null,
    /** Letzte Triage fuer „Rückgängig": externe ID und was getan wurde. */
    val letzte: Pair<String, String>? = null,
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
                    val text = triageText(action, snoozeMinutes)
                    _state.update { s ->
                        s.copy(
                            items = s.items.filterNot { it.externalId == externalId },
                            letzte = if (action == "reopen") null else externalId to text,
                            message = if (action == "reopen") "Wieder offen" else null,
                        )
                    }
                    load(refresh = true)
                },
                onFailure = { error -> _state.update { it.copy(error = error.userMessage()) } },
            )
        }
    }

    /** Holt den zuletzt triagierten Eintrag zurueck (Aktion `reopen`). */
    fun rueckgaengig() {
        val (id, _) = _state.value.letzte ?: return
        action(id, "reopen")
    }

    fun meldungGesehen() = _state.update { it.copy(message = null, letzte = null) }

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
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.letzte, state.message) {
        val letzte = state.letzte
        val text = letzte?.second ?: state.message ?: return@LaunchedEffect
        val r = snackbar.showSnackbar(
            text,
            actionLabel = if (letzte != null) "Rückgängig" else null,
            duration = SnackbarDuration.Short,
        )
        if (r == SnackbarResult.ActionPerformed) viewModel.rueckgaengig() else viewModel.meldungGesehen()
    }

    Box(Modifier.fillMaxSize()) {
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
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { viewModel.zeigeVerlauf(true) }) { Text("Verlauf", maxLines = 1) }
                        IconButton(onClick = { showNoteDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Neue Notiz", tint = MaterialTheme.colorScheme.primary)
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
                        onSnooze = { min -> viewModel.action(item.externalId, "snooze", snoozeMinutes = min) },
                        onArchive = { viewModel.action(item.externalId, "archive") },
                    )
                }
            }
        }
    }

    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(8.dp))
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
    onSnooze: (Int) -> Unit,
    onArchive: () -> Unit,
) {
    val uri = LocalUriHandler.current
    var spaeter by remember { mutableStateOf(false) }
    val links = oeffenbareLinks(item)
    var auswahl by remember { mutableStateOf(false) }
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
            quellenZeile(item)?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = TextLeise)
            }
            item.bezug?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (bezugTrifft(it)) StatusWarn else MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
            // Seit 2.0.31: Erledigt als Hauptknopf, Später mit Wahl der Dauer,
            // Archiv und Quelle als Symbole rechts. Vorher vier gleich blasse
            // Textknöpfe, und Später stellte immer fest 3 h zurück.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = onDone, contentPadding = KNOPF_INNEN) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Erledigt")
                }
                Box {
                    OutlinedButton(onClick = { spaeter = true }, contentPadding = KNOPF_INNEN) {
                        Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Später")
                    }
                    DropdownMenu(expanded = spaeter, onDismissRequest = { spaeter = false }) {
                        spaeterWahl().forEach { (text, min) ->
                            DropdownMenuItem(text = { Text(text) }, onClick = { spaeter = false; onSnooze(min) })
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (links.isNotEmpty()) {
                    Box {
                        IconButton(onClick = {
                            if (links.size == 1) runCatching { uri.openUri(links.first().url) } else auswahl = true
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = if (links.size > 1) "Quellen öffnen (${links.size})" else "Quelle öffnen",
                            )
                        }
                        DropdownMenu(expanded = auswahl, onDismissRequest = { auswahl = false }) {
                            links.forEach { l ->
                                DropdownMenuItem(
                                    text = { Text(l.quelle ?: l.url) },
                                    onClick = { auswahl = false; runCatching { uri.openUri(l.url) } },
                                )
                            }
                        }
                    }
                }
                IconButton(onClick = onArchive) {
                    Icon(Icons.Filled.Archive, contentDescription = "Archivieren", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private val KNOPF_INNEN = PaddingValues(horizontal = 14.dp, vertical = 6.dp)

/** Die Wahl unter „Später": Text und Minuten, „morgen früh" ist 8 Uhr am naechsten Tag. */
internal fun spaeterWahl(jetzt: LocalDateTime = LocalDateTime.now()): List<Pair<String, Int>> = listOf(
    "In 1 Stunde" to 60,
    "In 3 Stunden" to 180,
    "Morgen früh" to minutenBisMorgenFrueh(jetzt),
    "In einer Woche" to 7 * 24 * 60,
)

internal fun minutenBisMorgenFrueh(jetzt: LocalDateTime): Int {
    val ziel = jetzt.toLocalDate().plusDays(1).atTime(8, 0)
    return Duration.between(jetzt, ziel).toMinutes().toInt().coerceIn(1, 10080)
}

/** Text der Rueckmeldung nach einer Triage. */
internal fun triageText(action: String, snoozeMinutes: Int?): String = when (action) {
    "done" -> "Erledigt"
    "archive" -> "Archiviert"
    "snooze" -> when {
        snoozeMinutes == null -> "Zurückgestellt"
        snoozeMinutes < 120 -> "Zurückgestellt für $snoozeMinutes min"
        snoozeMinutes < 24 * 60 -> "Zurückgestellt für ${snoozeMinutes / 60} h"
        else -> ((snoozeMinutes + 720) / (24 * 60)).let { "Zurückgestellt für $it ${if (it == 1) "Tag" else "Tage"}" }
    }
    else -> "Gespeichert"
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
