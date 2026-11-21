package de.djouhri.tardis.ui.overview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import de.djouhri.tardis.ui.components.Card
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.tardis.ui.tresore.NotknopfKarte
import de.djouhri.tardis.ui.tresore.NotknopfViewModel
import de.djouhri.tardis.data.model.tardis.BackupsResponse
import de.djouhri.tardis.data.model.tardis.OwnerSchritteResponse
import de.djouhri.tardis.data.model.tardis.stundenText
import de.djouhri.tardis.data.model.tardis.HostHealth
import de.djouhri.tardis.data.model.tardis.HostRollup
import de.djouhri.tardis.data.model.tardis.InboxCounts
import de.djouhri.tardis.data.model.tardis.ServiceSummary
import de.djouhri.tardis.data.model.tardis.WirteResponse
import de.djouhri.tardis.data.repository.DashboardRepository
import de.djouhri.tardis.data.repository.OpsRepository
import de.djouhri.tardis.data.model.tardis.gruppiert
import de.djouhri.tardis.ui.alarme.AlarmeUiState
import de.djouhri.tardis.ui.components.ErrorMessage
import de.djouhri.tardis.ui.components.StatusDot
import de.djouhri.tardis.ui.components.serviceStatusColor
import de.djouhri.tardis.ui.components.usageColor
import de.djouhri.tardis.ui.theme.TextLeise
import de.djouhri.tardis.ui.wirte.WirteKarte
import de.djouhri.tardis.ui.theme.StatusDown
import de.djouhri.tardis.ui.theme.StatusIdle
import de.djouhri.tardis.ui.theme.StatusUp
import de.djouhri.tardis.ui.theme.StatusWarn
import de.djouhri.tardis.util.formatBytes
import de.djouhri.tardis.util.formatPercent
import de.djouhri.tardis.util.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import de.djouhri.tardis.widget.WidgetSpeicher
import javax.inject.Inject

data class OverviewUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val hosts: List<HostRollup> = emptyList(),
    /** Nur die Container, die wirklich auffaellig sind. Nachgeladen, nicht geraten. */
    val probleme: List<ServiceSummary> = emptyList(),
    val health: HostHealth? = null,
    /** Alle Wirte mit Verlauf. Fehlt die Antwort, bleibt der alte host-Momentwert. */
    val wirte: WirteResponse? = null,
    val inbox: InboxCounts = InboxCounts(),
    /**
     * Wann diese Zahlen zuletzt vom Gateway kamen (epoch ms, null = noch nie).
     *
     * Eine Uebersicht ohne Alter ist eine Behauptung: sie sieht bei einer
     * stehenden Verbindung genauso aus wie bei einer frischen Messung.
     */
    val zuletzt: Long? = null,
    /** true: die Zahlen sind der letzte Stand aus dem Speicher, nicht live. */
    val ausSpeicher: Boolean = false,
    /** Sicherung je Wirt (2.0.25). null = noch nicht geladen. */
    val backups: BackupsResponse? = null,
    /** Handgriffe (2.0.25). null = noch nicht geladen oder nicht erreichbar. */
    val owner: OwnerSchritteResponse? = null,
) {
    val erreichbar: List<HostRollup> get() = hosts.filter { it.erreichbar }
    val nichtErreichbar: List<HostRollup> get() = hosts.filterNot { it.erreichbar }

    val gesamt: Int get() = erreichbar.sumOf { it.gesamt ?: 0 }
    val laufend: Int get() = erreichbar.sumOf { it.laufend ?: 0 }
    val bewusstAus: Int get() = erreichbar.sumOf { it.bewusstAus ?: 0 }
    val problemZahl: Int get() = erreichbar.sumOf { it.problemZahl }

    /**
     * Gestoppte Container, ueber die sich nichts sagen laesst (Remote-Hosts
     * fuehren keine control-map-Erwartung). Bewusst eine eigene Zahl statt
     * stillschweigend unter „laeuft" oder „Problem" verbucht.
     */
    val unbewertet: Int get() = erreichbar.sumOf { it.unbewertet ?: 0 }
}

@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val opsRepository: OpsRepository,
    private val dashboardRepository: DashboardRepository,
    private val widget: WidgetSpeicher,
) : ViewModel() {

    private val _state = MutableStateFlow(startStand())
    val state = _state.asStateFlow()

    /**
     * Erster Bildschirm aus dem Speicher (2.0.25): vorher stand bis zur ersten
     * Antwort des Gateways „Lade Status…", ueber WireGuard im Mobilnetz
     * mehrere Sekunden. Jetzt steht sofort der letzte Stand da, mit seiner
     * Uhrzeit und als gespeichert gekennzeichnet, bis die frische Antwort kommt.
     */
    private fun startStand(): OverviewUiState {
        val hosts = opsRepository.letzteHosts() ?: return OverviewUiState()
        return OverviewUiState(
            loading = false,
            hosts = hosts.daten,
            wirte = opsRepository.letzteWirte(),
            backups = opsRepository.letzteBackups(),
            zuletzt = hosts.alt,
            ausSpeicher = true,
        )
    }

    /**
     * @param refresh true beim Ziehen: dann dreht sich der Kringel.
     * @param still true fuer die selbsttaetige Auffrischung. Ohne das blinkt
     *   die Flaeche alle 30 s, und eine Oberflaeche, die von selbst zuckt,
     *   waehrend man sie liest, ist schlechter als eine, die stillsteht.
     */
    fun load(refresh: Boolean = false, still: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = !refresh && !still && it.hosts.isEmpty(),
                    refreshing = refresh,
                    error = if (still) it.error else null,
                )
            }
            val (hostsResult, summaryResult, wirteResult) = coroutineScope {
                val a = async { opsRepository.hostsStand() }
                val b = async { dashboardRepository.summary() }
                val c = async { opsRepository.wirte(60) }
                // Sicherung und Handgriffe haengen nicht am Containerstand:
                // sie kommen nach, wenn sie da sind, und halten sonst ihren Stand.
                launch {
                    opsRepository.backups().onSuccess { b2 -> _state.update { it.copy(backups = b2.daten) } }
                }
                launch {
                    opsRepository.ownerSchritte().onSuccess { o -> _state.update { it.copy(owner = o) } }
                }
                Triple(a.await(), b.await(), c.await())
            }

            hostsResult.fold(
                onSuccess = { stand ->
                    val hosts = stand.daten
                    val summary = summaryResult.getOrNull()
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = null,
                            hosts = hosts,
                            health = summary?.health,
                            // Ein gescheiterter Abruf haelt den letzten Stand, statt die Karte zu leeren.
                            wirte = wirteResult.getOrNull() ?: it.wirte,
                            inbox = summary?.inboxCounts ?: it.inbox,
                            // Aus dem Speicher: dessen Zeitpunkt, nicht „jetzt".
                            zuletzt = stand.alt ?: System.currentTimeMillis(),
                            ausSpeicher = stand.alt != null,
                        )
                    }
                    // Ohne Verbindung gibt es keine Containerliste nachzuladen.
                    if (stand.alt == null) ladeProbleme(hosts)
                    if (stand.alt == null && widget.hostsGesehen(hosts)) widget.neuZeichnen()
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            error = error.userMessage(),
                        )
                    }
                },
            )
        }
    }

    /**
     * Holt die Containerliste nur fuer die Hosts, die im Zaehlstand ueberhaupt
     * etwas melden. Im Normalfall (nichts auffaellig) faellt damit jeder
     * weitere Aufruf weg; vorher zog die Uebersicht bei jedem Aufruf alle
     * Container, um am Ende „0 Probleme" anzuzeigen.
     */
    private fun ladeProbleme(hosts: List<HostRollup>) {
        val betroffen = hosts.filter { it.erreichbar && it.problemZahl > 0 }
        if (betroffen.isEmpty()) {
            _state.update { it.copy(probleme = emptyList()) }
            return
        }
        viewModelScope.launch {
            val listen = coroutineScope {
                betroffen.map { h -> async { opsRepository.services(h.name).getOrNull().orEmpty() } }
                    .map { it.await() }
            }
            _state.update {
                it.copy(probleme = listen.flatten().filter { s -> s.istProblem }.sortedBy { s -> s.name })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    onOpenServices: () -> Unit,
    onOpenDetail: (host: String, name: String) -> Unit,
    alarme: AlarmeUiState = AlarmeUiState(),
    onOpenAlarme: () -> Unit = {},
    /** Beim Ziehen auch die Alarme neu holen, sonst bleibt deren Karte stehen. */
    onAlarmeNeu: () -> Unit = {},
    /** Unterseite Verfuegbarkeit (wie die Web-Flaeche Reliability, seit 2.0.14). */
    onOpenVerfuegbarkeit: () -> Unit = {},
    /** Unterseite Topologie (zoombare Karte, seit 2.0.16). */
    onOpenTopologie: () -> Unit = {},
    /** Unterseite Wirte (alle Wirte mit Verlauf, seit 2.0.17). */
    onOpenWirte: () -> Unit = {},
    /** Unterseite Sicherung (2.0.25). */
    onOpenBackups: () -> Unit = {},
    /** Unterseite „Wartet auf dich" (2.0.25). */
    onOpenOwner: () -> Unit = {},
    viewModel: OverviewViewModel = hiltViewModel(),
    notknopf: NotknopfViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    // Erst laden, dann alle 30 s still nachfassen, solange diese Flaeche
    // sichtbar ist. Vorher stand hier der Stand vom Oeffnen der App, auch nach
    // einer Stunde: ein Tardis, das nicht nachsieht, meldet den Ausfall nicht,
    // der nach dem Hinsehen passiert ist. Der Effekt endet mit der Flaeche,
    // im Hintergrund fragt die App nichts.
    LaunchedEffect(Unit) {
        viewModel.load()
        while (true) {
            delay(30_000)
            viewModel.load(still = true)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
    // Notknopf ueber allem, auch wenn der Status nicht laedt: er haengt nicht
    // an den Messwerten, und gerade im Stoerfall muss er erreichbar sein.
    NotknopfKarte(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), viewModel = notknopf)
    PullToRefreshBox(
        isRefreshing = state.refreshing,
        onRefresh = {
            viewModel.load(refresh = true)
            onAlarmeNeu()
        },
        modifier = Modifier.weight(1f).fillMaxWidth(),
    ) {
        when {
            state.loading -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { Text("Lade Status…") }
            }
            state.error != null && state.hosts.isEmpty() -> {
                ErrorMessage(message = state.error!!, onRetry = { viewModel.load() })
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Kritisches gehoert vor alles andere, der Rest unter den
                    // Containerstand: der bleibt die erste Antwort auf „brennt was?".
                    if (alarme.kritisch > 0) AlarmKurzKarte(alarme, onOpenAlarme)
                    ContainerRollupCard(state, onOpenServices)
                    if (alarme.kritisch == 0) AlarmKurzKarte(alarme, onOpenAlarme)
                    state.owner?.let { OwnerKurzKarte(it, onOpenOwner) }
                    state.backups?.let { BackupKurzKarte(it, onOpenBackups) }
                    if (state.probleme.isNotEmpty()) ProblemCard(state.probleme, onOpenServices, onOpenDetail)
                    // Vor 2.0.17 stand hier nur der Momentwert von host. Er
                    // bleibt als Rueckfall, falls die Wirte-Antwort fehlt.
                    val wirte = state.wirte
                    if (wirte != null && wirte.gemessen) {
                        WirteKarte(wirte, onOpenWirte)
                    } else {
                        state.health?.let { HostMetricsCard(it) }
                        wirte?.let { WirteKarte(it, onOpenWirte) }
                    }
                    InboxSummaryCard(state.inbox)
                    SprungKarte("Verfügbarkeit", "Uptime je Dienst, Vorfälle, Cluster-Verlauf", onOpenVerfuegbarkeit)
                    SprungKarte("Topologie", "Karte aller Wirte, Dienste und Verbindungen", onOpenTopologie)
                }
            }
        }
    }
    }
}

@Composable
private fun ContainerRollupCard(state: OverviewUiState, onOpenServices: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpenServices() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Container", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            // Die grosse Zahl ist der Befundstand, nicht die Laufzahl: „82 von
            // 93" beantwortet nicht, ob etwas zu tun ist. Genau daran hing der
            // alte Fehler, denn elf der elf gemeldeten „Probleme" waren Absicht.
            Text(
                if (state.problemZahl == 0) "nichts auffällig" else "${state.problemZahl} auffällig",
                style = MaterialTheme.typography.headlineMedium,
                color = if (state.problemZahl == 0) StatusUp else StatusDown,
            )
            // Die Nebenzahlen stehen bewusst getrennt: „bewusst aus" ist kein
            // Zwischenzustand von „laeuft", und „ohne Erwartung" ist kein
            // Zwischenzustand von „Problem".
            val nebenzahlen = buildList {
                add("${state.gesamt} Container")
                add("${state.laufend} laufen")
                if (state.bewusstAus > 0) add("${state.bewusstAus} bewusst aus")
                if (state.unbewertet > 0) add("${state.unbewertet} ohne Erwartung")
            }
            Text(
                nebenzahlen.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.zuletzt?.let { zeitpunkt ->
                Text(
                    if (state.ausSpeicher) "Gespeicherter Stand von ${uhrzeitOderTag(zeitpunkt)}, ohne Verbindung"
                    else "Stand ${uhrzeit(zeitpunkt)} Uhr",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLeise,
                )
            }

            Spacer(Modifier.height(10.dp))
            state.erreichbar.forEach { HostZeile(it) }
            state.nichtErreichbar.forEach { host ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(color = StatusIdle)
                    Spacer(Modifier.width(8.dp))
                    Text(host.name, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    // Kein „0/0": ueber einen unerreichbaren Host ist keine
                    // Aussage moeglich, und eine Null waere eine.
                    Text(
                        "nicht erreichbar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * Uhrzeit statt "vor 3 Minuten": eine relative Angabe muesste mitlaufen, sonst
 * altert genau die Zeile, die das Alter nennen soll.
 */
private fun uhrzeit(zeitpunkt: Long): String =
    SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(zeitpunkt))

/** „14:32 Uhr" heute, sonst „16.11. 14:32 Uhr": ein gespeicherter Stand kann Tage alt sein. */
internal fun uhrzeitOderTag(zeitpunkt: Long, jetzt: Long = System.currentTimeMillis()): String {
    val tag = SimpleDateFormat("yyyyMMdd", Locale.GERMANY)
    val format = if (tag.format(Date(zeitpunkt)) == tag.format(Date(jetzt))) "HH:mm" else "d.M. HH:mm"
    return SimpleDateFormat(format, Locale.GERMANY).format(Date(zeitpunkt)) + " Uhr"
}

/**
 * Was nur der Owner tun kann (2.0.25). Steht unter den Alarmen: es brennt
 * nicht, aber es wartet. Gezeigt wird nur, was heute dran ist; Geplantes und
 * Gemeldetes steht auf der Unterseite.
 */
@Composable
private fun OwnerKurzKarte(owner: OwnerSchritteResponse, onOpen: () -> Unit) {
    if (!owner.gemessen) return
    val dran = owner.dran()
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Wartet auf dich", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    dran.isEmpty() -> "heute nichts"
                    dran.size == 1 -> "1 Schritt"
                    else -> "${dran.size} Schritte"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = if (dran.isEmpty()) StatusUp else StatusWarn,
            )
            // 2.0.29: verpasste Termine nennen, sonst sieht ein Stapel von gestern aus wie heute.
            val verpasst = owner.gruppen().verpasst.size
            if (verpasst > 0) {
                Text(
                    if (verpasst == 1) "1 mit verpasstem Termin" else "$verpasst mit verpasstem Termin",
                    style = MaterialTheme.typography.bodySmall,
                    color = StatusWarn,
                )
            }
            dran.take(3).forEach { s ->
                Text(
                    "· ${s.titel}",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            val geplant = owner.schritte.size - dran.size
            if (geplant > 0) {
                Text("$geplant geplant oder gemeldet", style = MaterialTheme.typography.bodySmall, color = TextLeise,
                    modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** Sicherung in einer Zeile: alles gesichert, oder welche Wirte nicht (2.0.25). */
@Composable
private fun BackupKurzKarte(b: BackupsResponse, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sicherung", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(6.dp))
            if (!b.gemessen) {
                Text("nicht gemessen", style = MaterialTheme.typography.headlineSmall, color = StatusWarn)
                b.fehler?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextLeise) }
                return@Column
            }
            val auffaellig = b.auffaellig
            Text(
                when {
                    auffaellig.isNotEmpty() -> "${auffaellig.size} von ${b.wirte.count { !it.bekanntAus }} Wirten auffällig"
                    b.probleme.isNotEmpty() -> "${b.probleme.size} Befund" + if (b.probleme.size > 1) "e" else ""
                    else -> "alles gesichert"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = when {
                    auffaellig.isNotEmpty() -> StatusDown
                    b.probleme.isNotEmpty() || b.veraltet -> StatusWarn
                    else -> StatusUp
                },
            )
            if (auffaellig.isNotEmpty()) {
                Text(auffaellig.joinToString(" · ") { it.name }, style = MaterialTheme.typography.bodySmall)
            }
            if (b.veraltet) {
                Text("Prüfer seit ${stundenText(b.geprueftVorH, dativ = true) ?: "?"} still", style = MaterialTheme.typography.bodySmall,
                    color = StatusWarn)
            }
        }
    }
}

@Composable
private fun HostZeile(host: HostRollup) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(color = if (host.problemZahl == 0) StatusUp else StatusDown)
        Spacer(Modifier.width(8.dp))
        Text(host.name, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        // Kein „82/93": der Bruch las sich als „elf laufen nicht", obwohl alle
        // elf bewusst aus sind. Jede Zahl bekommt hier ihr eigenes Wort.
        val teile = buildList {
            add("${host.laufend ?: 0} laufen")
            host.bewusstAus?.takeIf { it > 0 }?.let { add("$it aus") }
            host.unbewertet?.takeIf { it > 0 }?.let { add("$it offen") }
            if (host.problemZahl > 0) add("${host.problemZahl} auffällig")
        }
        Text(
            teile.joinToString(" · "),
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = if (host.problemZahl > 0) StatusDown else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProblemCard(
    probleme: List<ServiceSummary>,
    onOpenServices: () -> Unit,
    onOpenDetail: (host: String, name: String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Auffällig (${probleme.size})",
                style = MaterialTheme.typography.titleMedium,
                color = StatusDown,
            )
            Spacer(Modifier.height(6.dp))
            // Jede Zeile fuehrt zu IHREM Dienst, nicht die Karte zur Liste.
            // Vorher war der Weg vom Befund zu seinen Logs: Karte antippen,
            // Liste durchsuchen, Dienst finden, oeffnen. Der Befund steht schon
            // da, also ist er auch der Knopf.
            probleme.take(8).forEach { svc ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenDetail(svc.host, svc.name) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(color = serviceStatusColor(svc))
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(svc.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${svc.host} · ${svc.zustandsText()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (probleme.size > 8) {
                Text(
                    "… und ${probleme.size - 8} weitere",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onOpenServices() }
                        .padding(top = 4.dp),
                )
            }
        }
    }
}

/** Eine Zeile, die auf eine Unterseite fuehrt. */
@Composable
private fun SprungKarte(titel: String, untertitel: String, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(titel, style = MaterialTheme.typography.titleMedium)
                Text(untertitel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AlarmKurzKarte(alarme: AlarmeUiState, onOpenAlarme: () -> Unit) {
    val antwort = alarme.antwort ?: return
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth().clickable { onOpenAlarme() }) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Alarme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(6.dp))
            if (!antwort.erreichbar) {
                Text("nicht gemessen", style = MaterialTheme.typography.headlineSmall, color = StatusWarn)
                Text(
                    "Alarmquelle nicht erreichbar",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            val gruppen = antwort.alarme.gruppiert()
            Text(
                when {
                    antwort.alarme.isEmpty() -> "keine aktiven"
                    antwort.kritisch > 0 -> "${antwort.kritisch} kritisch, ${antwort.alarme.size} gesamt"
                    else -> "${antwort.alarme.size} aktiv"
                },
                style = MaterialTheme.typography.headlineSmall,
                color = when {
                    antwort.alarme.isEmpty() -> StatusUp
                    antwort.kritisch > 0 -> StatusDown
                    else -> StatusWarn
                },
            )
            // Die drei obersten Befunde mit Namen: eine blosse Zahl sagt nicht,
            // ob es der bekannte Dauerbrenner ist oder etwas Neues.
            gruppen.take(3).forEach { g ->
                // Tippen oeffnet die Alarme, gedrueckt halten kopiert die ID (wie in der Alarm-Liste).
                val id = g.alarme.first().kurzId
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .combinedClickable(
                            onClick = onOpenAlarme,
                            onLongClick = {
                                if (id != null) {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("alarm-id", id))
                                    Toast.makeText(context, "Alarm-ID $id kopiert", Toast.LENGTH_SHORT).show()
                                }
                            },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusDot(color = if (g.istKritisch) StatusDown else StatusWarn)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        g.alarme.first().zusammenfassung,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (g.alarme.size > 1) {
                        Text(" ×${g.alarme.size}", style = MaterialTheme.typography.bodySmall)
                    }
                    id?.let {
                        Text(
                            " $it",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (gruppen.size > 3) {
                Text(
                    "… und ${gruppen.size - 3} weitere Arten",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun HostMetricsCard(health: HostHealth) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("host jetzt", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            MetricRow("CPU", health.cpuPercent.formatPercent(), usageColor(health.cpuPercent))
            val memLabel = if (health.memoryUsed != null && health.memoryTotal != null) {
                "${health.memoryUsed.formatBytes()} / ${health.memoryTotal.formatBytes()}"
            } else {
                health.memoryPercent.formatPercent()
            }
            MetricRow("RAM", memLabel, usageColor(health.memoryPercent))
            MetricRow("Disk", health.diskPercent.formatPercent(), usageColor(health.diskPercent))
            health.cpuTemp?.let { MetricRow("Temp", "%.0f °C".format(it), usageColor(it)) }
            val load = listOfNotNull(health.load1m, health.load5m, health.load15m)
            if (load.isNotEmpty()) {
                MetricRow(
                    "Load",
                    load.joinToString(" ") { "%.2f".format(it) },
                    MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = valueColor,
        )
    }
}

@Composable
private fun InboxSummaryCard(counts: InboxCounts) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Inbox", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                Text("Offen: ${counts.open}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Text(
                    "Zurückgestellt: ${counts.snoozed}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
