package de.djouhri.tardis.ui.services

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.tardis.data.local.SettingsStore
import de.djouhri.tardis.data.model.tardis.DienstDetail
import de.djouhri.tardis.data.model.tardis.ServiceSummary
import de.djouhri.tardis.data.repository.OpsRepository
import de.djouhri.tardis.ui.components.StatusDot
import de.djouhri.tardis.ui.components.serviceStatusColor
import de.djouhri.tardis.ui.security.rememberDeviceAuth
import de.djouhri.tardis.ui.theme.StatusDown
import de.djouhri.tardis.ui.theme.StatusUp
import de.djouhri.tardis.util.userMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ServiceDetailUiState(
    val host: String = "",
    val name: String = "",
    val loading: Boolean = true,
    val actionInProgress: String? = null,
    /** Der ganze Datensatz statt einzelner Felder: nur so stimmt die Einordnung. */
    val service: ServiceSummary? = null,
    val logs: String = "",
    /** Laufzeit, Verbrauch, Wirt, Verlauf. `null`, solange (oder wenn) es nicht kam. */
    val detail: DienstDetail? = null,
    val detailFehler: String? = null,
    val lines: Int = 200,
    val error: String? = null,
    val message: String? = null,
) {
    /** Anzeige-Datensatz, auch bevor der erste Abruf durch ist. */
    val anzeige: ServiceSummary
        get() = service ?: ServiceSummary(host = host, name = name, status = "unknown")
}

@HiltViewModel
class ServiceDetailViewModel(
    private val opsRepository: OpsRepository,
    bestaetigungNoetig: Flow<Boolean>,
) : ViewModel() {

    // Der Flow statt des ganzen SettingsStore: so laesst sich die Flaeche im
    // Bildschirmfoto-Test ohne Schluesselspeicher bauen (der fehlt in Robolectric).
    @Inject constructor(opsRepository: OpsRepository, settingsStore: SettingsStore) :
        this(opsRepository, settingsStore.requireActionConfirm)


    private val _state = MutableStateFlow(ServiceDetailUiState())
    val state = _state.asStateFlow()

    /** Ob schreibende Aktionen zusaetzlich lokal bestaetigt werden muessen. */
    val requireConfirm: StateFlow<Boolean> = bestaetigungNoetig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private var initialized = false

    fun init(host: String, name: String) {
        if (initialized) return
        initialized = true
        _state.update { it.copy(host = host, name = name) }
        refresh()
    }

    fun refresh() {
        val host = _state.value.host
        val name = _state.value.name
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            // Nebeneinander statt nacheinander: ueber das VPN kostet jeder
            // Aufruf eine Rundreise, und das Detail fragt Prometheus mehrfach.
            val detailAuftrag = async { opsRepository.detail(host, name) }
            val svc = opsRepository.services(host).getOrNull()?.firstOrNull { it.name == name }
            val logsResult = opsRepository.logs(host, name, _state.value.lines)
            val detailResult = detailAuftrag.await()
            _state.update {
                it.copy(
                    loading = false,
                    service = svc ?: it.service,
                    logs = logsResult.getOrNull()?.logs ?: it.logs,
                    error = logsResult.exceptionOrNull()?.userMessage(),
                    detail = detailResult.getOrNull() ?: it.detail,
                    detailFehler = detailResult.exceptionOrNull()?.userMessage(),
                )
            }
        }
    }

    fun setLines(lines: Int) {
        if (lines == _state.value.lines) return
        _state.update { it.copy(lines = lines) }
        refresh()
    }

    fun performAction(action: String) {
        val host = _state.value.host
        val name = _state.value.name
        viewModelScope.launch {
            _state.update { it.copy(actionInProgress = action, message = null, error = null) }
            opsRepository.action(host, name, action).fold(
                onSuccess = {
                    _state.update {
                        it.copy(
                            actionInProgress = null,
                            message = "Aktion '$action' ausgeführt",
                        )
                    }
                    // Der Zustand kommt aus dem naechsten Abruf, nicht aus der
                    // Antwort der Aktion: `docker start` meldet `running`,
                    // sobald der Prozess laeuft, der Healthcheck braucht danach
                    // noch Zeit. Der Rueckgabewert bestaetigt die Absicht, nicht
                    // die Wirkung.
                    refresh()
                },
                onFailure = { error ->
                    _state.update { it.copy(actionInProgress = null, error = error.userMessage()) }
                },
            )
        }
    }
}

@Composable
fun ServiceDetailScreen(
    host: String,
    name: String,
    viewModel: ServiceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val requireConfirm by viewModel.requireConfirm.collectAsState()
    val deviceAuth = rememberDeviceAuth()
    val context = LocalContext.current
    LaunchedEffect(host, name) { viewModel.init(host, name) }

    var confirm by remember { mutableStateOf<String?>(null) }

    // Einheitlicher Einstieg fuer start/stop/restart: bevorzugt die lokale
    // Geraetebestaetigung (Biometrie/PIN); ist keine verfuegbar, faellt es auf
    // den textuellen Bestaetigungsdialog zurueck.
    val requestAction: (String) -> Unit = { action ->
        if (requireConfirm && deviceAuth?.canAuthenticate() == true) {
            deviceAuth.authenticate(
                title = "Aktion bestätigen",
                subtitle = "'$action' für '${state.name}'",
                onSuccess = { viewModel.performAction(action) },
            )
        } else {
            confirm = action
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatusKarte(state)
        state.detail?.let { DetailAlarmeKarte(it) }
        AktionsKarte(state, requestAction)
        state.detail?.let { d ->
            VerbrauchKarte(d.verbrauch)
            LaufKarte(d)
            WirtKarte(d)
        }
        state.detailFehler?.let {
            // Ein aelteres Gateway kennt die Route nicht: dann fehlt nur das
            // Detail, Aktionen und Logs gehen weiter.
            Text("Details nicht geladen: $it", color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall)
        }
        LogKarte(state, viewModel, context)

        state.error?.let {
            Text(it, color = StatusDown, style = MaterialTheme.typography.bodyMedium)
        }
    }

    confirm?.let { action ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("${action.replaceFirstChar { it.uppercase() }}?") },
            text = { Text("Container '${state.name}' auf '${state.host}' wirklich $action?") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    viewModel.performAction(action)
                }) { Text("Ja") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("Abbrechen") }
            },
        )
    }
}

@Composable
private fun StatusKarte(state: ServiceDetailUiState) {
    val svc = state.anzeige
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(color = serviceStatusColor(svc))
                Spacer(Modifier.width(10.dp))
                Text(state.name, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                svc.zustandsText(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Host: ${state.host}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Was die control-map erwartet, gehoert sichtbar an den Dienst.
            // Ohne diesen Satz sieht ein bewusster Rueckfall wie ein Ausfall
            // aus, und man startet ihn versehentlich wieder.
            erwartungsText(svc)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            svc.image?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun erwartungsText(svc: ServiceSummary): String? = when {
    !svc.expectedState.isNullOrBlank() -> "control-map: soll '${svc.expectedState}' sein"
    svc.inControlMap == true -> "control-map: soll laufen"
    svc.inControlMap == false -> "steht nicht in der control-map"
    else -> null
}

@Composable
private fun AktionsKarte(state: ServiceDetailUiState, requestAction: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Aktionen", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            if (state.actionInProgress != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(18.dp).height(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Führe '${state.actionInProgress}' aus…")
                }
            } else {
                // Nur anbieten, was in der aktuellen Lage etwas bewirkt: ein
                // „Start" auf einem laufenden Container ist ein Klick, der
                // nichts tut und trotzdem eine Bestaetigung verlangt.
                val laeuft = state.anzeige.isRunning
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { requestAction("start") }, enabled = !laeuft) { Text("Start") }
                    OutlinedButton(onClick = { requestAction("restart") }) { Text("Restart") }
                    OutlinedButton(onClick = { requestAction("stop") }, enabled = laeuft) { Text("Stop") }
                }
            }
            state.message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = StatusUp, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun LogKarte(
    state: ServiceDetailUiState,
    viewModel: ServiceDetailViewModel,
    context: Context,
) {
    val logScroll = rememberScrollState()
    // Ans Ende springen, sobald neue Zeilen da sind: die interessante Zeile
    // eines Logs ist die letzte. Vorher stand man am Anfang und scrollte bei
    // 500 Zeilen von Hand nach unten.
    //
    // Der Schluessel enthaelt `maxValue` mit Absicht. Beim ersten Durchlauf ist
    // der Text noch nicht vermessen, `maxValue` also 0; ein Sprung dorthin
    // waere ein Sprung an den Anfang und saehe genauso aus wie gar kein Sprung.
    LaunchedEffect(state.logs, logScroll.maxValue) {
        if (state.logs.isNotEmpty() && logScroll.maxValue > 0) {
            logScroll.scrollTo(logScroll.maxValue)
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Logs", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { viewModel.refresh() }) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Logs neu laden")
                }
                IconButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("${state.name}-logs", state.logs))
                        Toast.makeText(context, "Logs kopiert", Toast.LENGTH_SHORT).show()
                    },
                    enabled = state.logs.isNotBlank(),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Logs kopieren")
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                listOf(100, 200, 500).forEach { n ->
                    FilterChip(
                        selected = state.lines == n,
                        onClick = { viewModel.setLines(n) },
                        label = { Text("$n") },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.loading && state.logs.isBlank()) {
                Text("Lade Logs…", style = MaterialTheme.typography.bodySmall)
            } else {
                Text(
                    state.logs.ifBlank { "(keine Logs)" },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(logScroll)
                        .horizontalScroll(rememberScrollState()),
                )
            }
        }
    }
}
