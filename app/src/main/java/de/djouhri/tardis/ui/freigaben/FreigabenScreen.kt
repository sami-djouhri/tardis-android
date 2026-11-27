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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import de.djouhri.tardis.ui.components.Card
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
import de.djouhri.tardis.util.uhrzeitAus
import de.djouhri.tardis.util.userMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.Signature
import javax.inject.Inject
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import de.djouhri.tardis.data.model.tardis.Befehl
import de.djouhri.tardis.data.model.tardis.BefehlUnterschrift
import de.djouhri.tardis.data.model.tardis.BefehleAntwort
import de.djouhri.tardis.data.model.tardis.VerlaufEintrag
import de.djouhri.tardis.data.model.tardis.HausbusAntwort
import de.djouhri.tardis.data.model.tardis.HausbusFreigabe
import de.djouhri.tardis.freigaben.FreigabenLogik
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import kotlinx.coroutines.delay

data class FreigabenUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val antwort: FreigabenAntwort? = null,
    /** Befehls-Freigabe (seit 2.0.31); `null` = nicht geladen oder Gateway kennt sie nicht. */
    val befehle: BefehleAntwort? = null,
    /** Letzte Entscheidungen und Laeufe, neueste zuerst (seit 2.0.32). */
    val verlauf: List<VerlaufEintrag> = emptyList(),
    /** Hausbus-Freigaben von brain-bus (seit 2.0.33); `null` = nicht geladen oder Gateway kennt sie nicht. */
    val hausbus: HausbusAntwort? = null,
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
            repository.befehle().onSuccess { b ->
                _state.update { it.copy(befehle = b) }
                FreigabeWaechter.meldenBefehle(context, b.befehle)
            }
            repository.hausbus().onSuccess { h ->
                _state.update { it.copy(hausbus = h) }
                if (h.erreichbar) FreigabeWaechter.meldenHausbus(context, h.freigaben)
            }
            repository.verlauf().onSuccess { v -> _state.update { it.copy(verlauf = v) } }
        }
    }

    /** Still nachladen, solange ein Befehl wartet oder laeuft: das Ergebnis kommt nach Sekunden. */
    fun befehleNachladen() {
        viewModelScope.launch {
            repository.befehle().onSuccess { b -> _state.update { it.copy(befehle = b) } }
            repository.verlauf().onSuccess { v -> _state.update { it.copy(verlauf = v) } }
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

    /**
     * Befehl: mit der bestaetigten Signature den sha256 des gezeigten Skripttexts unterschreiben.
     * `modus`: lauf, probe oder abbruch; jeder Modus hat seinen eigenen unterschriebenen Text.
     */
    fun befehlUnterschreiben(b: Befehl, bestaetigt: Signature, modus: String) {
        val fp = _state.value.fingerabdruck ?: return
        viewModelScope.launch {
            _state.update { it.copy(beschaeftigt = "befehl:${b.id}", meldung = null) }
            val ausgestellt = System.currentTimeMillis() / 1000
            val sig = runCatching {
                schluessel.unterschreiben(
                    bestaetigt, FreigabeSchluessel.befehlNachricht(b.id, b.sha256, ausgestellt, b.wirt, modus),
                )
            }.getOrElse { e ->
                melde("Unterschrift gescheitert: ${e.message}", fehler = true)
                return@launch
            }
            val u = BefehlUnterschrift(b.sha256, ausgestellt, fp, sig, modus)
            val erg = if (modus == "abbruch") repository.befehlAbbrechen(b, u) else repository.befehlFreigeben(b, u)
            erg.fold(
                onSuccess = {
                    melde(when (modus) {
                        "probe" -> "${b.titel}: Probelauf freigegeben, ${b.wirt} zeigt gleich, was er täte."
                        "abbruch" -> "${b.titel}: Abbruch unterschrieben, ${b.wirt} beendet das Skript."
                        else -> "${b.titel}: freigegeben, ${b.wirt} führt es gleich aus."
                    })
                },
                onFailure = { e -> melde("${b.titel}: ${e.userMessage()}", fehler = true) },
            )
            befehleNachladen()
        }
    }

    fun befehlAblehnen(b: Befehl, grund: String) {
        viewModelScope.launch {
            repository.befehlAblehnen(b, grund).fold(
                onSuccess = { melde("${b.titel}: abgelehnt, der Grund steht jetzt als Aufgabe an.") },
                onFailure = { e -> melde("${b.titel}: ${e.userMessage()}", fehler = true) },
            )
            load()
        }
    }

    fun deployAblehnen(w: WartendeFreigabe, grund: String) {
        viewModelScope.launch {
            repository.deployAblehnen(w, grund).fold(
                onSuccess = { melde("${w.projekt}: abgelehnt, wartet erst nach einem neuen Push wieder.") },
                onFailure = { e -> melde("${w.projekt}: ${e.userMessage()}", fehler = true) },
            )
            load()
        }
    }

    /** Freigeben erst nach Fingerabdruck oder PIN (fragt der Bildschirm), Ablehnen ohne. */
    fun hausbusEntscheiden(h: HausbusFreigabe, freigeben: Boolean, grund: String = "") {
        viewModelScope.launch {
            _state.update { it.copy(beschaeftigt = "hausbus:${h.id}", meldung = null) }
            repository.hausbusEntscheiden(h, freigeben, grund).fold(
                onSuccess = { r -> melde("${h.titel}: ${r.meldung}", fehler = !r.ok) },
                onFailure = { e -> melde("${h.titel}: ${e.userMessage()}", fehler = true) },
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

    val befehlUnterschreiben: (Befehl, String) -> Unit = freigeben@{ b, modus ->
        if (deviceAuth == null || !deviceAuth.canAuthenticate()) return@freigeben
        val sig = viewModel.vorbereiten() ?: return@freigeben
        deviceAuth.unterschreiben(
            title = when (modus) {
                "probe" -> "Probelauf auf ${b.wirt} (ändert nichts)"
                "abbruch" -> "Lauf auf ${b.wirt} abbrechen"
                else -> "Als root auf ${b.wirt} ausführen"
            },
            subtitle = "${b.titel} · ${b.sha256.take(12)}",
            signature = sig,
            onSuccess = { viewModel.befehlUnterschreiben(b, it, modus) },
            onFailed = { },
        )
    }

    val hausbusFreigeben: (HausbusFreigabe) -> Unit = freigeben@{ h ->
        if (deviceAuth == null || !deviceAuth.canAuthenticate()) return@freigeben
        deviceAuth.authenticate(
            title = "Hausbus-Vorschlag ausführen",
            subtitle = h.titel,
            onSuccess = { viewModel.hausbusEntscheiden(h, freigeben = true) },
        )
    }

    val laeuftNoch = state.befehle?.befehle?.any { it.wartet || it.laeuft } == true
    LaunchedEffect(laeuftNoch) {
        while (laeuftNoch) {
            delay(4_000)
            viewModel.befehleNachladen()
        }
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
            onBefehl = befehlUnterschreiben,
            onSchluessel = { neu -> viewModel.schluesselEinrichten(neu) },
            onNeu = { viewModel.load() },
            onBefehlAblehnen = { b, grund -> viewModel.befehlAblehnen(b, grund) },
            onDeployAblehnen = { w, grund -> viewModel.deployAblehnen(w, grund) },
            onHausbus = hausbusFreigeben,
            onHausbusAblehnen = { h, grund -> viewModel.hausbusEntscheiden(h, freigeben = false, grund = grund) },
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
    onBefehl: (Befehl, String) -> Unit = { _, _ -> },
    onBefehlAblehnen: (Befehl, String) -> Unit = { _, _ -> },
    onDeployAblehnen: (WartendeFreigabe, String) -> Unit = { _, _ -> },
    onHausbus: (HausbusFreigabe) -> Unit = {},
    onHausbusAblehnen: (HausbusFreigabe, String) -> Unit = { _, _ -> },
) {
    // Ablehnen fragt nach dem Grund; bei Deploys und Befehlen wird er zur Aufgabe.
    var ablehnen by remember { mutableStateOf<Pair<String, (String) -> Unit>?>(null) }
    var ablehnenHinweis by remember { mutableStateOf(HINWEIS_AUFGABE) }
    ablehnen?.let { (was, weiter) ->
        AblehnenDialog(was, onAbbrechen = { ablehnen = null }, onAblehnen = { grund -> weiter(grund); ablehnen = null },
            hinweis = ablehnenHinweis)
    }
    // Ohne eigene Flaeche erbten Ueberschriften die Vorgabe-Schriftfarbe (schwarz auf dunklem Grund).
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val zaehler = FreigabenLogik.zaehler(state.antwort, state.befehle, state.hausbus)
        if (!state.loading) KopfZaehler(zaehler)
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
                    onAblehnen = {
                        ablehnenHinweis = HINWEIS_AUFGABE
                        ablehnen = "${w.projekt} auf ${w.wirt}" to { g -> onDeployAblehnen(w, g) }
                    },
                )
            }
            a.abgelehnt.forEach { ab ->
                Text(
                    "Abgelehnt: ${ab.projekt} auf ${ab.wirt} (${kurz(ab.commit)}): ${ab.grund}",
                    color = StatusWarn, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        state.hausbus?.let { h ->
            HausbusAbschnitt(
                h,
                aktiv = geraeteschutz && state.beschaeftigt == null,
                beschaeftigt = state.beschaeftigt?.removePrefix("hausbus:"),
                onFreigeben = onHausbus,
                onAblehnen = { x ->
                    ablehnenHinweis = HINWEIS_HAUSBUS
                    ablehnen = x.titel to { g -> onHausbusAblehnen(x, g) }
                },
            )
        }
        state.befehle?.let { b ->
            BefehleAbschnitt(
                b,
                aktiv = state.schluesselGemeldet && geraeteschutz && state.beschaeftigt == null,
                beschaeftigt = state.beschaeftigt?.removePrefix("befehl:"),
                onBefehl = onBefehl,
                onAblehnen = { befehl ->
                    ablehnenHinweis = HINWEIS_AUFGABE
                    ablehnen = befehl.titel to { g -> onBefehlAblehnen(befehl, g) }
                },
            )
        }
        if (a != null && a.konfiguriert) {
            if (a.stand.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Stand je Ziel", style = MaterialTheme.typography.titleMedium)
                a.stand.forEach { StandZeile(it) }
            }
            a.fehler.forEach { Text(it, color = MaterialTheme.colorScheme.error) }
        }
        if (state.verlauf.isNotEmpty()) VerlaufAbschnitt(state.verlauf)
        Spacer(Modifier.height(4.dp))
        SchluesselKarte(state, onSchluessel)
    }
    }
}

@Composable
private fun Box(content: @Composable () -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { content() }

@Composable
private fun KopfZaehler(z: FreigabenLogik.Zaehler) {
    if (z.leer) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        if (z.deploys > 0) Marke(if (z.deploys == 1) "1 Deploy wartet" else "${z.deploys} Deploys warten", StatusWarn)
        if (z.befehleOffen > 0) Marke("${z.befehleOffen} Befehl${if (z.befehleOffen == 1) "" else "e"} offen", StatusWarn)
        if (z.hausbus > 0) Marke("Hausbus fragt ${z.hausbus}×", StatusWarn)
        if (z.laufen > 0) Marke("${z.laufen} läuft", MaterialTheme.colorScheme.primary)
        if (z.gescheitert > 0) Marke("${z.gescheitert} gescheitert", StatusDown)
    }
}

@Composable
private fun Marke(text: String, farbe: Color) {
    Text(
        text,
        color = farbe,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        modifier = Modifier
            .background(farbe.copy(alpha = 0.14f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private const val HINWEIS_AUFGABE = "Nichts läuft. Der Grund wird zur Aufgabe; ein neuer Stand wartet wieder."
private const val HINWEIS_HAUSBUS = "brain-bus führt nichts aus. Der Grund steht im Verlauf."

@Composable
private fun AblehnenDialog(
    was: String,
    onAbbrechen: () -> Unit,
    onAblehnen: (String) -> Unit,
    hinweis: String = HINWEIS_AUFGABE,
) {
    var grund by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onAbbrechen,
        title = { Text("$was ablehnen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hinweis,
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = grund, onValueChange = { grund = it.take(1000) }, label = { Text("Grund") },
                    minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onAblehnen(grund.trim()) }, enabled = grund.isNotBlank()) { Text("Ablehnen") }
        },
        dismissButton = { TextButton(onClick = onAbbrechen) { Text("Zurück") } },
    )
}

@Composable
private fun WartendKarte(
    w: WartendeFreigabe, aktiv: Boolean, beschaeftigt: Boolean, onFreigeben: () -> Unit, onAblehnen: () -> Unit = {},
) {
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
            de.djouhri.tardis.util.seitText(w.seit)?.let { seit ->
                val lange = FreigabenLogik.wartetLange(w.seit)
                Text("Wartet $seit", color = if (lange) StatusWarn else TextLeise, style = MaterialTheme.typography.bodySmall)
            }
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { diffOffen = !diffOffen }) { Text(if (diffOffen) "Diff zu" else "Diff ansehen", maxLines = 1) }
                Button(onClick = onFreigeben, enabled = aktiv) {
                    if (beschaeftigt) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Freigeben", maxLines = 1)
                }
            }
            TextButton(onClick = onAblehnen) { Text("Ablehnen …", color = StatusDown, maxLines = 1) }
        }
    }
}

@Composable
private fun HausbusAbschnitt(
    h: HausbusAntwort,
    aktiv: Boolean,
    beschaeftigt: String?,
    onFreigeben: (HausbusFreigabe) -> Unit,
    onAblehnen: (HausbusFreigabe) -> Unit,
) {
    // Leer und erreichbar: nichts zeigen, die Flaeche bleibt ruhig.
    if (h.erreichbar && h.freigaben.isEmpty()) return
    Spacer(Modifier.height(4.dp))
    Text("Hausbus fragt", style = MaterialTheme.typography.titleMedium)
    if (!h.erreichbar) {
        Text("brain-bus antwortet nicht${h.fehler?.let { " ($it)" } ?: ""}; offene Vorschläge unbekannt.",
            color = StatusWarn, style = MaterialTheme.typography.bodySmall)
        return
    }
    h.freigaben.forEach { x -> HausbusKarte(x, aktiv, beschaeftigt == x.id, { onFreigeben(x) }, { onAblehnen(x) }) }
}

@Composable
private fun HausbusKarte(
    h: HausbusFreigabe,
    aktiv: Boolean,
    beschaeftigt: Boolean,
    onFreigeben: () -> Unit,
    onAblehnen: () -> Unit,
) {
    var offen by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(h.titel, fontWeight = FontWeight.SemiBold)
            val rest = FreigabenLogik.verfaelltIn(h.verfaellt)
            val bald = rest != null && rest.toHours() < 1
            Text(
                listOfNotNull("Regel ${h.regel}", h.risiko.takeIf { it.isNotBlank() }?.let { "Risiko $it" },
                    rest?.let { "verfällt ${FreigabenLogik.restText(it)}" }).joinToString(" · "),
                color = if (bald) StatusWarn else TextLeise, style = MaterialTheme.typography.bodySmall,
            )
            if (h.begruendung.isNotBlank()) {
                Text(
                    h.begruendung,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (offen) Int.MAX_VALUE else 3,
                    modifier = Modifier.clickable { offen = !offen },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onFreigeben, enabled = aktiv) {
                    if (beschaeftigt) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("Ausführen", maxLines = 1)
                }
                TextButton(onClick = onAblehnen, enabled = aktiv) { Text("Ablehnen …", color = StatusDown, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun BefehleAbschnitt(
    b: BefehleAntwort, aktiv: Boolean, beschaeftigt: String?, onBefehl: (Befehl, String) -> Unit,
    onAblehnen: (Befehl) -> Unit = {},
) {
    if (b.befehle.isEmpty() && b.eingerichtet) return
    Spacer(Modifier.height(4.dp))
    val offen = b.befehle.count { it.offen }
    Text(if (offen > 0) "Befehle ($offen offen)" else "Befehle", style = MaterialTheme.typography.titleMedium)
    Text(
        "Fertige Handgriffe als Skript. Nach deiner Unterschrift führt der Wirt es als root aus, ohne Terminal, höchstens 30 min.",
        style = MaterialTheme.typography.bodySmall, color = TextLeise,
    )
    if (!b.eingerichtet && b.laeufer.isEmpty()) {
        Text(
            "Der Läufer auf host ist noch nicht eingerichtet. Einmal im Terminal: sudo bash interne Notiz",
            color = StatusWarn, style = MaterialTheme.typography.bodySmall,
        )
    }
    val wirte = b.befehle.map { it.wirt }.toSet()
    b.laeufer.filter { it.wirt in wirte || it.wirt == "host" }.forEach { lf ->
        val (text, farbe) = when {
            !lf.eingerichtet -> "Läufer ${lf.wirt}: nicht eingerichtet" to StatusWarn
            lf.still -> "Läufer ${lf.wirt}: still seit ${lf.zeit?.let { FreigabenLogik.zeitText(it) } ?: "?"}" to StatusDown
            else -> "Läufer ${lf.wirt}: v${lf.version}, zuletzt ${lf.zeit?.let { FreigabenLogik.zeitText(it) } ?: "?"}" to TextLeise
        }
        Text(text, color = farbe, style = MaterialTheme.typography.bodySmall)
    }
    val reihenfolge = compareByDescending<Befehl> { it.laeuft || it.wartet }.thenByDescending { it.offen }
        .thenBy { it.abgelehnt != null }
    b.befehle.sortedWith(reihenfolge).forEach { befehl ->
        val lf = b.laeufer(befehl.wirt)
        val bereit = lf?.eingerichtet ?: (befehl.wirt == "host" && b.eingerichtet)
        BefehlKarte(
            befehl, aktiv = aktiv && bereit, laeuferBereit = bereit, kannAbbrechen = (lf?.version ?: 1) >= 2,
            beschaeftigt = beschaeftigt == befehl.id,
            onUnterschreiben = { modus -> onBefehl(befehl, modus) },
            onAblehnen = { onAblehnen(befehl) },
        )
    }
}

@Composable
private fun Ausgabe(text: String, laeuft: Boolean) {
    val scroll = rememberScrollState()
    // Waehrend des Laufs ans Ende rollen, damit die neueste Zeile sichtbar ist.
    LaunchedEffect(text, laeuft) { if (laeuft) scroll.scrollTo(scroll.maxValue) }
    Text(
        text.ifBlank { "(noch keine Ausgabe)" },
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        softWrap = false,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = if (laeuft) 180.dp else 240.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(8.dp)
            .verticalScroll(scroll)
            .horizontalScroll(rememberScrollState()),
    )
}

@Composable
private fun BefehlKarte(
    b: Befehl, aktiv: Boolean, laeuferBereit: Boolean, kannAbbrechen: Boolean, beschaeftigt: Boolean,
    onUnterschreiben: (String) -> Unit, onAblehnen: () -> Unit,
) {
    var skriptOffen by remember { mutableStateOf(false) }
    var gesehen by remember(b.sha256) { mutableStateOf(false) }
    // Gescheitert: Ausgabe gleich offen, das ist der erste Blick, den man braucht.
    var ausgabeOffen by remember(b.ergebnis?.ende, b.ergebnis?.zustand) { mutableStateOf(b.gescheitert) }
    val e = b.ergebnis
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.titel, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (b.wirt != "host") Marke(b.wirt, MaterialTheme.colorScheme.primary)
            }
            b.warum?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            b.rueckweg?.let { Text("Rückweg: $it", style = MaterialTheme.typography.bodySmall, color = TextLeise) }
            val (zustand, farbe) = befehlZustand(b)
            Text(zustand, color = farbe, style = MaterialTheme.typography.bodySmall)
            e?.grund?.takeIf { !b.laeuft }?.let { Text(it, color = StatusDown, style = MaterialTheme.typography.bodySmall) }
            b.abgelehnt?.let {
                Text("Abgelehnt: ${it.grund}", color = StatusWarn, style = MaterialTheme.typography.bodySmall)
            }
            if (!laeuferBereit && b.offen) {
                Text("Der Läufer auf ${b.wirt} ist noch nicht eingerichtet.", color = StatusWarn,
                    style = MaterialTheme.typography.bodySmall)
            }
            Text("sha256 ${b.sha256.take(16)}…", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = TextLeise)
            if (skriptOffen) {
                Text(
                    b.inhalt,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    softWrap = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(8.dp)
                        .horizontalScroll(rememberScrollState()),
                )
            }
            if (e != null && (b.laeuft || (ausgabeOffen && e.ausgabe.isNotBlank()))) Ausgabe(e.ausgabe, b.laeuft)
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                OutlinedButton(onClick = { skriptOffen = !skriptOffen; gesehen = true }) {
                    Text(if (skriptOffen) "Skript zu" else "Skript (${b.inhalt.lines().size} Z.)", maxLines = 1)
                }
                if (e != null && e.ausgabe.isNotBlank() && !b.laeuft) {
                    OutlinedButton(onClick = { ausgabeOffen = !ausgabeOffen }) {
                        Text(if (ausgabeOffen) "Ausgabe zu" else "Ausgabe", maxLines = 1)
                    }
                }
                if (b.probe && b.offen) {
                    OutlinedButton(onClick = { onUnterschreiben("probe") }, enabled = aktiv && gesehen) {
                        Text("Probelauf", maxLines = 1)
                    }
                }
            }
            if (b.laeuft && kannAbbrechen) {
                OutlinedButton(
                    onClick = { onUnterschreiben("abbruch") },
                    enabled = aktiv,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = StatusDown),
                ) { Text("Abbrechen", maxLines = 1) }
            }
            if (b.offen) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { onUnterschreiben("lauf") }, enabled = aktiv && gesehen) {
                        if (beschaeftigt) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Text(if (gesehen) "Als root ausführen" else "Erst das Skript ansehen", maxLines = 1)
                    }
                    TextButton(onClick = onAblehnen) { Text("Ablehnen …", color = StatusDown, maxLines = 1) }
                }
            }
        }
    }
}

@Composable
private fun befehlZustand(b: Befehl): Pair<String, Color> {
    val e = b.ergebnis
    val wie = if (e?.modus == "probe") "Probelauf" else "Lauf"
    val um = e?.ende?.let { " (${FreigabenLogik.zeitText(it)})" } ?: ""
    return when {
        b.wartet -> "Freigegeben, wartet auf den Läufer" to StatusWarn
        e == null -> "Noch nicht gelaufen" to TextLeise
        b.laeuft -> "$wie läuft gerade${e.angenommen?.let { " seit ${FreigabenLogik.zeitText(it)}" } ?: ""}" to StatusWarn
        e.veraltet -> "Skript seit dem letzten Lauf geändert" to StatusWarn
        e.zustand == "ok" && e.modus == "probe" -> "Probelauf gelungen$um, echter Lauf steht aus" to StatusWarn
        e.zustand == "ok" -> "Gelaufen$um, Exit 0" to StatusUp
        e.zustand == "abgebrochen" -> "$wie abgebrochen$um" to StatusWarn
        e.zustand == "abgelehnt" -> "Vom Läufer abgewiesen$um" to StatusDown
        else -> "$wie gescheitert$um, Exit ${e.rc ?: "?"}" to StatusDown
    }
}

@Composable
private fun VerlaufAbschnitt(verlauf: List<VerlaufEintrag>) {
    var offen by remember { mutableStateOf(false) }
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth().clickable { offen = !offen }, verticalAlignment = Alignment.CenterVertically) {
        Text("Verlauf", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(if (offen) "zu" else "${verlauf.size} Einträge", color = TextLeise, style = MaterialTheme.typography.bodySmall)
    }
    if (!offen) return
    verlauf.take(30).forEach { v -> VerlaufZeile(v) }
}

@Composable
private fun VerlaufZeile(v: VerlaufEintrag) {
    var ausgabe by remember { mutableStateOf(false) }
    val farbe = when (FreigabenLogik.verlaufStufe(v)) {
        "gruen" -> StatusUp
        "gelb" -> StatusWarn
        "rot" -> StatusDown
        else -> TextLeise
    }
    Column(Modifier.fillMaxWidth().clickable(enabled = !v.ausgabe.isNullOrBlank()) { ausgabe = !ausgabe }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(8.dp).background(farbe, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(8.dp))
            Text(v.titel, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
            Text(FreigabenLogik.zeitText(v.zeit), color = TextLeise, style = MaterialTheme.typography.bodySmall)
        }
        Text(
            FreigabenLogik.verlaufText(v) + (v.wirt?.let { " · $it" } ?: ""),
            color = farbe, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp),
        )
        if (ausgabe) v.ausgabe?.let { Ausgabe(it, laeuft = false) }
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
