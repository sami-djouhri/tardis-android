package de.djouhri.tardis.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import de.djouhri.tardis.ui.theme.Aussehen
import de.djouhri.tardis.ui.theme.LocalTardisEffekte
import de.djouhri.tardis.ui.theme.LocalTardisTon
import de.djouhri.tardis.ui.theme.TardisTon
import de.djouhri.tardis.ui.theme.glut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import de.djouhri.tardis.ui.components.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.tardis.data.local.SettingsStore
import de.djouhri.tardis.data.model.tardis.Geraet
import de.djouhri.tardis.data.repository.PairingRepository
import de.djouhri.tardis.data.repository.widerrufHinweis
import de.djouhri.tardis.ui.security.rememberDeviceAuth
import de.djouhri.tardis.ui.theme.StatusDown
import de.djouhri.tardis.ui.theme.StatusUp
import de.djouhri.tardis.ui.update.UpdateViewModel
import de.djouhri.tardis.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val deviceId: String? = null,
    val connectionResult: String? = null,
    val connectionOk: Boolean = false,
    val checkingConnection: Boolean = false,
    val error: String? = null,
    // ── Geraeteverwaltung ────────────────────────────────────────────────────
    val geraete: List<Geraet> = emptyList(),
    val geraeteLaden: Boolean = false,
    val geraeteGeladen: Boolean = false,
    val geraeteFehler: String? = null,
    /** Ergebnis des letzten Widerrufs im Klartext, inklusive der Anzahl. */
    val widerrufHinweis: String? = null,
    // ── Entkoppeln ───────────────────────────────────────────────────────────
    val entkoppelnLaeuft: Boolean = false,
    /**
     * Gesetzt, wenn der serverseitige Widerruf gescheitert ist und lokal noch
     * NICHT geloescht wurde. Solange das steht, wartet das Entkoppeln auf eine
     * zweite Bestaetigung: danach waere die Warnung nicht mehr zu sehen, weil
     * die App auf den Pairing-Screen wechselt.
     */
    val widerrufGescheitert: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val pairingRepository: PairingRepository,
    private val settingsStore: SettingsStore,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()

    val requireActionConfirm: StateFlow<Boolean> = settingsStore.requireActionConfirm
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    init {
        viewModelScope.launch {
            _state.update { it.copy(deviceId = settingsStore.getDeviceId()) }
        }
    }

    fun setRequireActionConfirm(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setRequireActionConfirm(enabled) }
    }

    fun testConnection() {
        viewModelScope.launch {
            _state.update { it.copy(checkingConnection = true, connectionResult = null, error = null) }
            pairingRepository.testConnection().fold(
                onSuccess = { status ->
                    _state.update { it.copy(checkingConnection = false, connectionOk = true, connectionResult = "Erreichbar ($status)") }
                },
                onFailure = { error ->
                    _state.update { it.copy(checkingConnection = false, connectionOk = false, connectionResult = error.userMessage()) }
                },
            )
        }
    }

    /**
     * Entkoppeln in zwei Schritten, mit einer Rueckfrage dazwischen.
     *
     * Schritt 1 ist der serverseitige Widerruf. Gelingt er, folgt Schritt 2
     * sofort und die App landet auf dem Pairing-Screen. Gelingt er NICHT, wird
     * lokal noch nichts geloescht, sondern [SettingsUiState.widerrufGescheitert]
     * gesetzt: nach dem lokalen Loeschen kippt `isPaired` und diese Flaeche ist
     * weg, die Warnung waere also nie zu sehen. Ein stilles Scheitern erzeugt
     * genau die Annahme, die ein Widerruf ausschliessen soll.
     *
     * `trotzdem = true` ueberspringt den Widerruf und raeumt nur lokal auf.
     */
    fun unpair(trotzdem: Boolean = false) {
        viewModelScope.launch {
            if (trotzdem) {
                pairingRepository.entkoppleLokal()
                return@launch
            }
            _state.update { it.copy(entkoppelnLaeuft = true, widerrufGescheitert = null) }
            pairingRepository.widerrufeDiesesGeraet().fold(
                onSuccess = {
                    pairingRepository.entkoppleLokal()
                    _state.update { s -> s.copy(entkoppelnLaeuft = false) }
                },
                onFailure = { fehler ->
                    _state.update { s ->
                        s.copy(entkoppelnLaeuft = false, widerrufGescheitert = fehler.userMessage())
                    }
                },
            )
        }
    }

    fun widerrufVerwerfen() {
        _state.update { it.copy(widerrufGescheitert = null) }
    }

    fun ladeGeraete() {
        viewModelScope.launch {
            _state.update { it.copy(geraeteLaden = true, geraeteFehler = null) }
            pairingRepository.geraete().fold(
                onSuccess = { liste ->
                    _state.update {
                        it.copy(geraeteLaden = false, geraeteGeladen = true, geraete = liste)
                    }
                },
                onFailure = { fehler ->
                    _state.update {
                        it.copy(geraeteLaden = false, geraeteFehler = fehler.userMessage())
                    }
                },
            )
        }
    }

    /**
     * Widerruft ein ANDERES Geraet und nennt die Anzahl.
     *
     * `0` ist kein Fehler, sondern „kenne ich nicht oder war schon
     * widerrufen", und wird als solches gemeldet statt als Erfolg. Danach die
     * Liste neu holen, damit `gueltig` nicht veraltet daneben steht.
     */
    fun widerrufeGeraet(geraetId: String) {
        viewModelScope.launch {
            _state.update { it.copy(widerrufHinweis = null, geraeteFehler = null) }
            pairingRepository.widerrufeGeraet(geraetId).fold(
                onSuccess = { anzahl ->
                    _state.update { it.copy(widerrufHinweis = widerrufHinweis(anzahl)) }
                    ladeGeraete()
                },
                onFailure = { fehler ->
                    _state.update { it.copy(geraeteFehler = fehler.userMessage()) }
                },
            )
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    updateViewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val updateState by updateViewModel.state.collectAsState()
    val requireActionConfirm by viewModel.requireActionConfirm.collectAsState()
    var confirmUnpair by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Gerät", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Gekoppelt · ID ${state.deviceId?.take(12) ?: "–"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { viewModel.testConnection() },
                    enabled = !state.checkingConnection,
                ) { Text(if (state.checkingConnection) "Teste…" else "Verbindung testen") }
                state.connectionResult?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.connectionOk) StatusUp else StatusDown,
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Version", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    updateState.installierteVersion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Die App sieht alle sechs Stunden selbst nach und meldet sich oben, " +
                        "wenn etwas bereitliegt. Hier kann man es erzwingen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { updateViewModel.pruefe() },
                    enabled = !updateState.prueftGerade,
                ) { Text(if (updateState.prueftGerade) "Prüfe…" else "Jetzt nachsehen") }

                val update = updateState.angeboten
                when {
                    update != null -> {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Neu: ${update.versionName} (${update.versionCode})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StatusUp,
                        )
                        update.changelog.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall)
                        }
                        update.sha256.takeIf { it.isNotBlank() }?.let {
                            Text(
                                "SHA-256: $it",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        // Die Knoepfe sind dieselben wie im Banner oben, und sie
                        // teilen sich dessen Zustand: was hier geladen wird, gilt
                        // dort als geladen.
                        when {
                            updateState.laedtGerade ->
                                Text("Wird geladen…", style = MaterialTheme.typography.bodySmall)
                            updateState.bereit != null ->
                                Button(onClick = { updateViewModel.installieren() }) { Text("Installieren") }
                            else ->
                                Button(onClick = { updateViewModel.holen() }) { Text("Herunterladen") }
                        }
                        updateState.fehler?.let {
                            Spacer(Modifier.height(4.dp))
                            Text(it, color = StatusDown, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    updateState.geprueft -> {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            updateState.fehler ?: "Aktuell.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (updateState.fehler != null) StatusDown else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sicherheit", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Schreibende Aktionen (Start/Stop/Restart) zusätzlich per " +
                                "Biometrie oder Geräte-PIN bestätigen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = requireActionConfirm,
                        onCheckedChange = { viewModel.setRequireActionConfirm(it) },
                    )
                }
            }
        }

        AussehenKarte()

        GeraeteKarte(state, viewModel)

        state.error?.let {
            Text(it, color = StatusDown, style = MaterialTheme.typography.bodyMedium)
        }

        OutlinedButton(
            onClick = { confirmUnpair = true },
            enabled = !state.entkoppelnLaeuft,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (state.entkoppelnLaeuft) "Melde ab…" else "Gerät entkoppeln") }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text("Entkoppeln?") },
            text = {
                Text(
                    "Das Gateway widerruft die Token dieses Geräts, danach werden " +
                        "JWT und Zertifikate lokal gelöscht. Ein erneutes Pairing " +
                        "ist nötig.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    viewModel.unpair()
                }) { Text("Entkoppeln") }
            },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text("Abbrechen") } },
        )
    }

    // Zweite Rueckfrage, und sie ist der Grund fuer die Aufteilung in zwei
    // Schritte: lokal ist hier noch NICHTS geloescht. Waere es das, haette
    // `isPaired` schon gekippt, diese Flaeche waere weg und die Warnung nie zu
    // sehen gewesen. Ein Widerruf, der nichts widerruft, ist schlimmer als
    // keiner, weil er die Annahme erzeugt, das Geraet sei draussen.
    state.widerrufGescheitert?.let { fehler ->
        AlertDialog(
            onDismissRequest = { viewModel.widerrufVerwerfen() },
            title = { Text("Nicht widerrufen") },
            text = {
                Text(
                    "Das Gateway war nicht erreichbar: $fehler\n\n" +
                        "Das Token dieses Geräts bleibt damit bis zu 30 Tage gültig. " +
                        "Besser zuerst das VPN einschalten und es erneut versuchen.\n\n" +
                        "Trotzdem lokal entkoppeln? Das Gerät lässt sich danach von " +
                        "einem anderen Gerät aus unter „Gekoppelte Geräte\" aussperren.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.widerrufVerwerfen()
                    viewModel.unpair(trotzdem = true)
                }) { Text("Trotzdem entkoppeln") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.widerrufVerwerfen() }) { Text("Abbrechen") }
            },
        )
    }
}

/**
 * Gekoppelte Geraete, bewusst als Abschnitt der Einstellungen und nicht als
 * eigene Flaeche: es ist Geraete-Verwaltung und gehoert dorthin, wo auch
 * „Entkoppeln" steht.
 *
 * Die Liste wird erst auf Tippen geholt. Beim Oeffnen der Einstellungen waere
 * es eine Anfrage, die niemand angefordert hat, und ueber WireGuard kostet jede
 * eine Verbindung.
 */
@Composable
private fun GeraeteKarte(state: SettingsUiState, viewModel: SettingsViewModel) {
    val deviceAuth = rememberDeviceAuth()
    var confirmWiderruf by remember { mutableStateOf<Geraet?>(null) }

    // Derselbe Einstieg wie bei start/stop/restart: bevorzugt die lokale
    // Geraetebestaetigung, sonst der textuelle Dialog. Bestaetigt wird in
    // jedem Fall, nur die Art unterscheidet sich.
    //
    // Bewusst OHNE die Einstellung `requireActionConfirm` zu pruefen, anders
    // als im Dienst-Detail. Zwei Gruende: die Einstellung nennt ausdruecklich
    // „Start/Stop/Restart", und ein Widerruf ist nicht umkehrbar (das Geraet
    // muss neu gekoppelt werden, im Zweifel das eigene). Wer die Schranke fuer
    // Container abschaltet, hat nicht entschieden, auch Zugaenge ohne
    // Rueckfrage zu entziehen.
    val widerrufAnfordern: (Geraet) -> Unit = { g ->
        if (deviceAuth?.canAuthenticate() == true) {
            deviceAuth.authenticate(
                title = "Widerruf bestätigen",
                subtitle = g.etikett ?: g.geraetId.take(12),
                onSuccess = { viewModel.widerrufeGeraet(g.geraetId) },
            )
        } else {
            confirmWiderruf = g
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Gekoppelte Geräte", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Ein verlorenes Telefon lässt sich hier aussperren. Der Widerruf " +
                    "gilt sofort für alle Token des Geräts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { viewModel.ladeGeraete() },
                enabled = !state.geraeteLaden,
            ) {
                Text(
                    when {
                        state.geraeteLaden -> "Lade…"
                        state.geraeteGeladen -> "Neu laden"
                        else -> "Geräte anzeigen"
                    },
                )
            }

            state.geraeteFehler?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = StatusDown, style = MaterialTheme.typography.bodySmall)
            }
            state.widerrufHinweis?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Eine leere Liste nach gelungenem Laden heisst etwas anderes als
            // „noch nicht geladen" und wird deshalb benannt.
            if (state.geraeteGeladen && state.geraete.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Keine Geräte gemeldet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            for (g in state.geraete) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            g.etikett?.takeIf { it.isNotBlank() } ?: g.geraetId.take(12),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            // `jti_kurz` haelt zwei Kopplungen desselben Geraets
                            // auseinander; der eigene Eintrag wird benannt, damit
                            // niemand sich selbst fuer ein fremdes Geraet haelt.
                            buildString {
                                append(if (g.gueltig) "gültig" else "widerrufen oder abgelaufen")
                                append(" · ")
                                append(g.jtiKurz)
                                if (g.geraetId == state.deviceId) append(" · dieses Gerät")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (g.gueltig) StatusUp else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    if (g.gueltig) {
                        TextButton(onClick = { widerrufAnfordern(g) }) { Text("Aussperren") }
                    }
                }
            }
        }
    }

    confirmWiderruf?.let { g ->
        val eigenes = g.geraetId == state.deviceId
        AlertDialog(
            onDismissRequest = { confirmWiderruf = null },
            title = { Text("Aussperren?") },
            text = {
                Text(
                    if (eigenes) {
                        "Das ist DIESES Gerät. Alle seine Token werden ungültig, " +
                            "die App braucht danach ein neues Pairing."
                    } else {
                        "Alle Token von „${g.etikett ?: g.geraetId.take(12)}\" werden " +
                            "ungültig. Das Gerät muss danach neu gekoppelt werden."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWiderruf = null
                    viewModel.widerrufeGeraet(g.geraetId)
                }) { Text("Aussperren") }
            },
            dismissButton = { TextButton(onClick = { confirmWiderruf = null }) { Text("Abbrechen") } },
        )
    }
}

/**
 * Effekte an oder aus. Wirkt sofort und ohne ViewModel: der Zustand liegt in
 * [Aussehen], das Theme liest ihn direkt. Die Farbwahl ist seit 2.0.31 weg,
 * die App ist fest blau.
 */
@Composable
internal fun AussehenKarte() {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Aussehen", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Effekte", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Nebel, Flimmern, Glitch bei neuem Alarm, Glut um Ausfälle. " +
                            "Aus heißt: nichts bewegt sich.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = LocalTardisEffekte.current,
                    onCheckedChange = { Aussehen.setzeEffekte(context, it) },
                )
            }
        }
    }
}
