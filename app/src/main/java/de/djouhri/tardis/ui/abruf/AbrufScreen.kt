package de.djouhri.tardis.ui.abruf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.tardis.data.local.SettingsStore
import de.djouhri.tardis.data.model.tardis.AbrufDienst
import de.djouhri.tardis.data.model.tardis.AbrufResponse
import de.djouhri.tardis.data.model.tardis.Lab
import de.djouhri.tardis.data.repository.OpsRepository
import de.djouhri.tardis.ui.components.ErrorMessage
import de.djouhri.tardis.ui.components.StatusDot
import de.djouhri.tardis.ui.security.rememberDeviceAuth
import de.djouhri.tardis.ui.theme.TextLeise
import de.djouhri.tardis.ui.theme.StatusDown
import de.djouhri.tardis.ui.theme.StatusIdle
import de.djouhri.tardis.ui.theme.StatusUp
import de.djouhri.tardis.ui.theme.StatusWarn
import de.djouhri.tardis.util.userMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AbrufUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val antwort: AbrufResponse? = null,
    /** Name des Dienstes bzw. "lab", an dem gerade eine Aktion laeuft. */
    val beschaeftigt: String? = null,
    val meldung: String? = null,
    val meldungIstFehler: Boolean = false,
)

@HiltViewModel
class AbrufViewModel(
    private val opsRepository: OpsRepository,
    bestaetigungNoetig: Flow<Boolean>,
) : ViewModel() {

    // Der Flow statt des ganzen SettingsStore: so laesst sich die Flaeche im
    // Bildschirmfoto-Test ohne Schluesselspeicher bauen (der fehlt in Robolectric).
    @Inject constructor(opsRepository: OpsRepository, settingsStore: SettingsStore) :
        this(opsRepository, settingsStore.requireActionConfirm)


    private val _state = MutableStateFlow(AbrufUiState())
    val state = _state.asStateFlow()

    val requireConfirm: StateFlow<Boolean> = bestaetigungNoetig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun load(refresh: Boolean = false) {
        viewModelScope.launch { holen(refresh) }
    }

    private suspend fun holen(refresh: Boolean = false) {
        _state.update { it.copy(refreshing = refresh) }
        opsRepository.abruf().fold(
            onSuccess = { a -> _state.update { it.copy(loading = false, refreshing = false, error = null, antwort = a) } },
            onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
        )
    }

    private fun melde(text: String, fehler: Boolean = false) =
        _state.update { it.copy(beschaeftigt = null, meldung = text, meldungIstFehler = fehler) }

    /**
     * Weckt und sieht dann nach, bis der Dienst bereit ist (hoechstens 90 s).
     *
     * Die Antwort des Weckers sagt nur „startet". Ob der Dienst danach wirklich
     * antwortet, weiss erst sein Healthcheck; ohne das Nachsehen stuende
     * „startet" so lange da, bis jemand von Hand zieht.
     */
    fun wecken(dienst: AbrufDienst) {
        viewModelScope.launch {
            _state.update { it.copy(beschaeftigt = dienst.name, meldung = null) }
            val ergebnis = opsRepository.wecken(dienst.name).getOrElse { e ->
                melde("${dienst.titel}: ${e.userMessage()}", fehler = true)
                return@launch
            }
            if (!ergebnis.ok) {
                melde(ergebnis.meldung(dienst.titel), fehler = true)
                holen()
                return@launch
            }
            repeat(30) {
                holen()
                val jetzt = _state.value.antwort?.wecker?.dienste?.firstOrNull { it.name == dienst.name }
                if (jetzt?.bereit == true) {
                    melde("${dienst.titel} ist wach.")
                    return@launch
                }
                delay(3_000)
            }
            melde("${dienst.titel} läuft, ist nach 90 s aber noch nicht bereit. Healthcheck prüfen.", fehler = true)
        }
    }

    fun wartung(dienst: AbrufDienst, an: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(beschaeftigt = dienst.name, meldung = null) }
            opsRepository.wartung(dienst.name, an).fold(
                onSuccess = { r ->
                    when {
                        r.warnung != null -> melde(r.warnung, fehler = true)
                        r.wartung -> melde("${dienst.titel} wird wach gehalten, bis du es wieder ausschaltest.")
                        else -> melde("${dienst.titel} schläft wieder nach Leerlauf ein.")
                    }
                },
                onFailure = { e -> melde("${dienst.titel}: ${e.userMessage()}", fehler = true) },
            )
            holen()
        }
    }

    fun lab(aktion: String, label: String) {
        viewModelScope.launch {
            _state.update { it.copy(beschaeftigt = "lab", meldung = null) }
            opsRepository.lab(aktion).fold(
                onSuccess = { r ->
                    when {
                        r.ok -> melde("$label: erledigt.")
                        // Keine Stoerung: eine belegte Rolle hat Vorrang, und
                        // Erzwingen gibt es absichtlich nicht.
                        r.conflict -> melde("$label abgelehnt: ${r.log.lastOrNull() ?: "eine belegte oder reservierte Rolle hat Vorrang"}", fehler = true)
                        else -> melde("$label gescheitert: ${r.error ?: r.log.lastOrNull() ?: "rc=${r.rc}"}", fehler = true)
                    }
                },
                onFailure = { e -> melde("$label: ${e.userMessage()}", fehler = true) },
            )
            holen()
        }
    }
}

/** Eine bestaetigungspflichtige Aktion, bis sie bestaetigt ist. */
private data class Vorhaben(val titel: String, val text: String, val ausfuehren: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AbrufScreen(viewModel: AbrufViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val requireConfirm by viewModel.requireConfirm.collectAsState()
    val deviceAuth = rememberDeviceAuth()
    var offen by remember { mutableStateOf<Vorhaben?>(null) }

    // Wie bei den Diensten: bevorzugt Biometrie/PIN, sonst ein Dialog.
    val bestaetigen: (Vorhaben) -> Unit = { v ->
        if (!requireConfirm) {
            v.ausfuehren()
        } else if (deviceAuth?.canAuthenticate() == true) {
            deviceAuth.authenticate(title = v.titel, subtitle = v.text, onSuccess = v.ausfuehren)
        } else {
            offen = v
        }
    }

    LaunchedEffect(Unit) {
        viewModel.load()
        while (true) {
            delay(30_000)
            if (viewModel.state.value.beschaeftigt == null) viewModel.load()
        }
    }

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
            ) { Text("Lade…") }

            antwort == null -> ErrorMessage(message = state.error ?: "Keine Antwort", onRetry = { viewModel.load() })

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.meldung?.let {
                    Text(it, color = if (state.meldungIstFehler) StatusWarn else StatusUp,
                        style = MaterialTheme.typography.bodyMedium)
                }
                state.error?.let {
                    Text("Aktualisieren gescheitert: $it", color = StatusWarn, style = MaterialTheme.typography.bodySmall)
                }
                WeckerKarte(antwort, state, viewModel, bestaetigen)
                LabKarte(antwort.lab, state.beschaeftigt == "lab", viewModel, bestaetigen)
                Text(
                    "Dienste auf weiteren Wirten sind hier noch nicht angebunden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLeise,
                )
            }
        }
    }

    offen?.let { v ->
        AlertDialog(
            onDismissRequest = { offen = null },
            title = { Text(v.titel) },
            text = { Text(v.text) },
            confirmButton = { TextButton(onClick = { offen = null; v.ausfuehren() }) { Text("Ja") } },
            dismissButton = { TextButton(onClick = { offen = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun WeckerKarte(
    antwort: AbrufResponse,
    state: AbrufUiState,
    viewModel: AbrufViewModel,
    bestaetigen: (Vorhaben) -> Unit,
) {
    val wecker = antwort.wecker
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Dienste auf Abruf", style = MaterialTheme.typography.titleMedium)
            if (!wecker.erreichbar) {
                Spacer(Modifier.height(6.dp))
                Text("Wecker nicht erreichbar, Zustand unbekannt", color = StatusWarn)
                wecker.fehler?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                return@Column
            }
            val wach = wecker.dienste.count { it.laeuft }
            val speicher = wecker.freiMb?.let { " · ${"%.1f".format(it / 1024.0)} GB frei" } ?: ""
            Text(
                "$wach von ${wecker.dienste.size} wach$speicher",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            wecker.dienste.forEachIndexed { i, d ->
                if (i > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                DienstZeile(
                    dienst = d,
                    beschaeftigt = state.beschaeftigt == d.name,
                    sperre = state.beschaeftigt != null,
                    onWecken = { viewModel.wecken(d) },
                    onWartung = { an ->
                        bestaetigen(
                            Vorhaben(
                                titel = if (an) "Wach halten?" else "Wieder einschlafen lassen?",
                                text = if (an) "${d.titel} wird nicht mehr automatisch schlafen gelegt, bis du es ausschaltest."
                                else "${d.titel} schläft wieder nach ${d.idleTimeoutS / 60} min ohne Zugriff ein.",
                            ) { viewModel.wartung(d, an) },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun DienstZeile(
    dienst: AbrufDienst,
    beschaeftigt: Boolean,
    sperre: Boolean,
    onWecken: () -> Unit,
    onWartung: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(
            color = when {
                dienst.bereit -> StatusUp
                dienst.laeuft -> StatusWarn
                !dienst.startbar -> StatusDown
                else -> StatusIdle
            },
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(dienst.titel, style = MaterialTheme.typography.bodyLarge)
            Text(
                dienst.zustandsText(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            beschaeftigt -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            !dienst.laeuft -> OutlinedButton(
                onClick = onWecken,
                enabled = !sperre && dienst.startbar,
            ) { Text("Wecken") }
            else -> Switch(
                checked = dienst.wartung,
                onCheckedChange = onWartung,
                enabled = !sperre,
                // Der Schalter allein sagt nicht, WAS er schaltet.
                modifier = Modifier.semantics { contentDescription = "${dienst.titel} wach halten" },
            )
        }
    }
}

@Composable
private fun LabKarte(
    lab: Lab,
    beschaeftigt: Boolean,
    viewModel: AbrufViewModel,
    bestaetigen: (Vorhaben) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Windows-AD-Lab", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (!lab.verfuegbar) {
                Text("Kein Zustand vom Knoten, weder an noch aus belegt", color = StatusWarn)
                lab.fehler?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(color = if (lab.laeuft) StatusUp else StatusIdle)
                Spacer(Modifier.width(8.dp))
                Text(
                    when {
                        lab.laeuft && lab.reserviert -> "läuft (VM ${lab.vms.joinToString("/")}) · Wartungsmodus"
                        lab.laeuft -> "läuft (VM ${lab.vms.joinToString("/")}) · aus nach 45 min Leerlauf"
                        lab.reserviert -> "aus · Wartungsmodus gesetzt"
                        else -> "aus"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            val neben = buildList {
                lab.freiMb?.let { add("${"%.1f".format(it / 1024.0)} GB frei auf dem Knoten") }
                if (lab.veraltet) add("Stand ${(lab.alterS ?: 0) / 60} min alt")
            }
            if (neben.isNotEmpty()) {
                Text(
                    neben.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lab.veraltet) StatusWarn else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            if (beschaeftigt) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    // Ein VM-Start dauert, und ohne diesen Satz sieht das Warten wie ein Haenger aus.
                    Text("Arbeitet… ein Start kann bis zu drei Minuten dauern", style = MaterialTheme.typography.bodySmall)
                }
                return@Column
            }
            val frage: (String, String, String) -> Unit = { aktion, label, text ->
                bestaetigen(Vorhaben("$label?", text) { viewModel.lab(aktion, label) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (lab.laeuft) {
                    OutlinedButton(onClick = { frage("stop", "Lab stoppen", "Die Windows-VMs werden heruntergefahren.") }) {
                        Text("Stoppen")
                    }
                } else {
                    Button(onClick = { frage("start", "Lab starten", "Die Windows-VMs starten. Belegte Rollen haben Vorrang, es wird nichts verdrängt, auf dem jemand ist.") }) {
                        Text("Starten")
                    }
                }
                if (lab.reserviert) {
                    OutlinedButton(onClick = { frage("wartung-aus", "Wartungsmodus beenden", "Das Lab geht nach 45 min Leerlauf wieder aus.") }) {
                        Text("Wartung aus")
                    }
                } else {
                    OutlinedButton(onClick = {
                        val (aktion, text) = if (lab.laeuft) {
                            "wartung-an" to "Das Lab bleibt an und wird nicht verdrängt, bis du den Wartungsmodus beendest."
                        } else {
                            "start-wartung" to "Das Lab startet und bleibt an, bis du den Wartungsmodus beendest."
                        }
                        frage(aktion, "Wartungsmodus", text)
                    }) { Text(if (lab.laeuft) "Wartung an" else "Starten & halten") }
                }
            }
        }
    }
}
