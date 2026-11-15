package de.djouhri.tardis.ui.freigaben

import android.Manifest
import android.os.Build
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.djouhri.tardis.data.model.tardis.FreigabeStand
import de.djouhri.tardis.data.model.tardis.FreigabenAntwort
import de.djouhri.tardis.data.model.tardis.Unterschrift
import de.djouhri.tardis.data.model.tardis.WartendeFreigabe
import de.djouhri.tardis.data.repository.FreigabenRepository
import de.djouhri.tardis.freigaben.FreigabeWaechter
import de.djouhri.tardis.security.FreigabeSchluessel
import de.djouhri.tardis.ui.components.ErrorMessage
import de.djouhri.tardis.ui.security.rememberDeviceAuth
import de.djouhri.tardis.ui.theme.StatusDown
import de.djouhri.tardis.ui.theme.StatusUp
import de.djouhri.tardis.ui.theme.StatusWarn
import de.djouhri.tardis.ui.theme.TextLeise
import de.djouhri.tardis.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.Signature
import javax.inject.Inject

data class FreigabenUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val antwort: FreigabenAntwort? = null,
    /** Fingerabdruck des eigenen Freigabeschluessels, `null` = noch keiner erzeugt. */
    val fingerabdruck: String? = null,
    val beschaeftigt: String? = null,
    val meldung: String? = null,
    val meldungIstFehler: Boolean = false,
) {
    val schluesselGemeldet: Boolean
        get() = fingerabdruck != null && antwort?.schluessel?.contains(fingerabdruck) == true
}

@HiltViewModel
class FreigabenViewModel @Inject constructor(
    private val repository: FreigabenRepository,
    private val schluessel: FreigabeSchluessel,
    @ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    private val _state = MutableStateFlow(FreigabenUiState())
    val state = _state.asStateFlow()

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(refreshing = refresh, fingerabdruck = schluessel.fingerabdruck()) }
            repository.liste().fold(
                onSuccess = { a ->
                    _state.update { it.copy(loading = false, refreshing = false, error = null, antwort = a) }
                    FreigabeWaechter.melden(context, a.wartend)
                },
                onFailure = { e -> _state.update { it.copy(loading = false, refreshing = false, error = e.userMessage()) } },
            )
        }
    }

    private fun melde(text: String, fehler: Boolean = false) =
        _state.update { it.copy(beschaeftigt = null, meldung = text, meldungIstFehler = fehler) }

    /** Erzeugt (falls noetig) den Freigabeschluessel und meldet den oeffentlichen Teil ans Gateway. */
    fun schluesselEinrichten(neu: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(beschaeftigt = "schluessel", meldung = null) }
            runCatching {
                if (neu || !schluessel.vorhanden()) schluessel.erzeugen()
                schluessel.pem()
            }.fold(
                onSuccess = { pem ->
                    repository.schluesselMelden(pem).fold(
                        onSuccess = { r ->
                            _state.update { it.copy(fingerabdruck = r.fingerabdruck) }
                            melde("Schlüssel gemeldet. Wirksam erst, wenn er auf dem Zielwirt per root-Befehl hinterlegt ist.")
                            load()
                        },
                        onFailure = { e -> melde("Melden gescheitert: ${e.userMessage()}", fehler = true) },
                    )
                },
                onFailure = { e -> melde("Schlüssel nicht erzeugt: ${e.message}", fehler = true) },
            )
        }
    }

    /**
     * Schritt 1: die Signature vorbereiten. Liefert null samt Meldung, wenn der
     * Schluessel fehlt oder seit einem neuen Fingerabdruck ungueltig ist.
     */
    fun vorbereiten(): Signature? = try {
        schluessel.vorbereiten()
    } catch (e: KeyPermanentlyInvalidatedException) {
        melde("Der Freigabeschlüssel ist ungültig (neuer Fingerabdruck?). Neu erzeugen und auf dem Zielwirt neu hinterlegen.", fehler = true)
        null
    } catch (e: Exception) {
        melde("Freigabeschlüssel nicht nutzbar: ${e.message}", fehler = true)
        null
    }

    /** Schritt 2: mit der per Fingerabdruck bestaetigten Signature unterschreiben und einreichen. */
    fun freigeben(w: WartendeFreigabe, bestaetigt: Signature) {
        val fp = _state.value.fingerabdruck ?: return
        viewModelScope.launch {
            _state.update { it.copy(beschaeftigt = w.schluessel, meldung = null) }
            val ausgestellt = System.currentTimeMillis() / 1000
            val sig = runCatching {
                schluessel.unterschreiben(bestaetigt, FreigabeSchluessel.nachricht(w.wirt, w.projekt, w.repo, w.commit, ausgestellt))
            }.getOrElse { e ->
                melde("Unterschrift gescheitert: ${e.message}", fehler = true)
                return@launch
            }
            repository.freigeben(w, Unterschrift(ausgestellt, fp, sig)).fold(
                onSuccess = { melde("${w.projekt} freigegeben. ${w.wirt} holt den Stand binnen einer Minute.") },
                onFailure = { e -> melde("${w.projekt}: ${e.userMessage()}", fehler = true) },
            )
            load()
        }
    }

    fun meldungWeg() = _state.update { it.copy(meldung = null) }
}

private fun kurz(sha: String?) = sha?.take(10) ?: "–"

@Composable
private fun ampelFarbe(ampel: String): Color = when (ampel) {
    "gruen" -> StatusUp
    "gelb" -> StatusWarn
    else -> StatusDown
}

private fun ampelText(ampel: String) = when (ampel) {
    "gruen" -> "Grün: nur Inhalt und Code"
    "gelb" -> "Gelb: genauer hinsehen"
    else -> "Rot: Rechte, Netz, Image oder erster Stand"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FreigabenScreen(viewModel: FreigabenViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val deviceAuth = rememberDeviceAuth()
    val context = LocalContext.current
    val erlaubnis = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        viewModel.load()
        FreigabeWaechter.planen(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            erlaubnis.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val freigeben: (WartendeFreigabe) -> Unit = freigeben@{ w ->
        if (deviceAuth == null || !deviceAuth.canAuthenticate()) {
            viewModel.meldungWeg()
            return@freigeben
        }
        val sig = viewModel.vorbereiten() ?: return@freigeben
        deviceAuth.unterschreiben(
            title = "${w.projekt} auf ${w.wirt} freigeben",
            subtitle = "${kurz(w.commit)} ${w.betreff}",
            signature = sig,
            onSuccess = { viewModel.freigeben(w, it) },
            onFailed = { },
        )
    }

    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = { viewModel.load(refresh = true) },
        modifier = Modifier.fillMaxSize(),
    ) {
        FreigabenInhalt(
            state = state,
            geraeteschutz = deviceAuth?.canAuthenticate() == true,
            onFreigeben = freigeben,
            onSchluessel = { neu -> viewModel.schluesselEinrichten(neu) },
            onNeu = { viewModel.load() },
        )
    }
}

/** Ohne ViewModel und Hilt, damit der Bildschirmfoto-Test die Flaeche mit erfundenen Daten zeichnen kann. */
@Composable
internal fun FreigabenInhalt(
    state: FreigabenUiState,
    geraeteschutz: Boolean,
    onFreigeben: (WartendeFreigabe) -> Unit,
    onSchluessel: (Boolean) -> Unit,
    onNeu: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        state.meldung?.let {
            Text(it, color = if (state.meldungIstFehler) MaterialTheme.colorScheme.error else StatusUp)
        }
        when {
            state.loading -> Box { CircularProgressIndicator() }
            state.error != null && state.antwort == null -> ErrorMessage(message = state.error, onRetry = onNeu)
        }
        val a = state.antwort
        if (a != null && !a.konfiguriert) {
            Text("Auf dem Gateway noch nicht eingerichtet: ${a.fehler.joinToString()}", color = TextLeise)
        }
        if (!geraeteschutz) {
            Text(
                "Ohne Fingerabdruck oder Geräte-PIN kann diese App nicht freigeben. Geräteschutz in Android einrichten.",
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (a != null && a.konfiguriert) {
            Text(
                if (a.wartend.isEmpty()) "Nichts wartet auf Freigabe." else "Wartet auf deine Freigabe",
                style = MaterialTheme.typography.titleMedium,
            )
            a.wartend.forEach { w ->
                WartendKarte(
                    w = w,
                    aktiv = state.schluesselGemeldet && geraeteschutz && state.beschaeftigt == null,
                    beschaeftigt = state.beschaeftigt == w.schluessel,
                    onFreigeben = { onFreigeben(w) },
                )
            }
            if (a.stand.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Stand je Ziel", style = MaterialTheme.typography.titleMedium)
                a.stand.forEach { StandZeile(it) }
            }
            a.fehler.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        Spacer(Modifier.height(4.dp))
        SchluesselKarte(state, onSchluessel)
    }
}

@Composable
private fun Box(content: @Composable () -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { content() }

@Composable
private fun WartendKarte(w: WartendeFreigabe, aktiv: Boolean, beschaeftigt: Boolean, onFreigeben: () -> Unit) {
    var diffOffen by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(
                    Modifier
                        .size(12.dp)
                        .background(ampelFarbe(w.ampel), RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(8.dp))
                Text("${w.projekt} auf ${w.wirt}", fontWeight = FontWeight.SemiBold)
            }
            Text(ampelText(w.ampel), color = ampelFarbe(w.ampel), style = MaterialTheme.typography.bodySmall)
            w.gruende.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
            Text("${kurz(w.commit)}  ${w.betreff}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
            Text(
                if (w.basis == null) "Erste Freigabe: der ganze Stand geht live."
                else "${w.commits.size} Commit(s) seit ${kurz(w.basis)}",
                color = TextLeise, style = MaterialTheme.typography.bodySmall,
            )
            if (w.commits.size > 1) {
                w.commits.forEach {
                    Text("${kurz(it.sha)}  ${it.betreff}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = TextLeise)
                }
            }
            if (diffOffen) {
                Text(
                    w.diff + if (w.diffGekuerzt) "\n… gekürzt" else "",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { diffOffen = !diffOffen }) { Text(if (diffOffen) "Diff zu" else "Diff ansehen") }
                Button(onClick = onFreigeben, enabled = aktiv) {
                    if (beschaeftigt) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Freigeben")
                }
            }
        }
    }
}

@Composable
private fun StandZeile(s: FreigabeStand) {
    Column {
        Text("${s.projekt} auf ${s.wirt}", fontWeight = FontWeight.Medium)
        val ausgerollt = when {
            !s.gemessen -> "nicht gemessen"
            s.ausgerollt == null -> "noch nichts ausgerollt"
            s.ausgerollt == s.freigegeben -> "läuft ${kurz(s.ausgerollt)} (zuletzt freigegeben)"
            else -> "läuft ${kurz(s.ausgerollt)}, freigegeben ${kurz(s.freigegeben)}"
        }
        Text(ausgerollt, color = TextLeise, style = MaterialTheme.typography.bodySmall)
        s.gescheitert?.let {
            Text("${kurz(it)} gescheitert, auf den letzten guten Stand zurückgerollt", color = StatusWarn,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SchluesselKarte(state: FreigabenUiState, onSchluessel: (Boolean) -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Freigabeschlüssel", fontWeight = FontWeight.SemiBold)
            val fp = state.fingerabdruck
            if (fp == null) {
                Text("Noch keiner erzeugt. Er bleibt im Telefon und unterschreibt nur nach Fingerabdruck oder PIN.",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = { onSchluessel(false) }, enabled = state.beschaeftigt == null) { Text("Erzeugen und melden") }
            } else {
                Text("Fingerabdruck (am Zielwirt beim Einrichten abtippen):", style = MaterialTheme.typography.bodySmall)
                Text(FreigabeSchluessel.gruppiert(fp), fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                Text(
                    if (state.schluesselGemeldet) "Beim Gateway gemeldet." else "Beim Gateway noch nicht gemeldet.",
                    color = if (state.schluesselGemeldet) StatusUp else StatusWarn,
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!state.schluesselGemeldet) {
                        Button(onClick = { onSchluessel(false) }, enabled = state.beschaeftigt == null) { Text("Melden") }
                    }
                    TextButton(onClick = { onSchluessel(true) }, enabled = state.beschaeftigt == null) { Text("Neu erzeugen") }
                }
            }
        }
    }
}
