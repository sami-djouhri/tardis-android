package de.djouhri.tardis.ui.services

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import de.djouhri.tardis.ui.components.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.tardis.data.model.tardis.HostRollup
import de.djouhri.tardis.data.model.tardis.ServiceSummary
import de.djouhri.tardis.data.model.tardis.ServiceZustand
import de.djouhri.tardis.data.repository.OpsRepository
import de.djouhri.tardis.ui.components.ErrorMessage
import de.djouhri.tardis.ui.components.StatusDot
import de.djouhri.tardis.ui.components.serviceStatusColor
import de.djouhri.tardis.util.userMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Womit die Liste eingeschraenkt wird. */
enum class ServiceFilter(val titel: String) {
    ALLE("Alle"),
    AUFFAELLIG("Auffällig"),
    LAEUFT("Läuft"),
    AUS("Aus"),
}

data class ServicesUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val error: String? = null,
    val services: List<ServiceSummary> = emptyList(),
    val hosts: List<HostRollup> = emptyList(),
    /** `null` = alle erreichbaren Hosts zusammen. */
    val gewaehlterHost: String? = null,
    val suche: String = "",
    val filter: ServiceFilter = ServiceFilter.ALLE,
) {
    /** Die Liste nach Suchbegriff und Filter, sonst unveraendert. */
    val sichtbar: List<ServiceSummary>
        get() {
            val begriff = suche.trim()
            return services.asSequence()
                .filter { svc ->
                    begriff.isEmpty() ||
                        svc.name.contains(begriff, ignoreCase = true) ||
                        svc.image.orEmpty().contains(begriff, ignoreCase = true)
                }
                .filter { svc ->
                    when (filter) {
                        ServiceFilter.ALLE -> true
                        ServiceFilter.AUFFAELLIG -> svc.istProblem
                        ServiceFilter.LAEUFT -> svc.isRunning
                        ServiceFilter.AUS -> !svc.isRunning
                    }
                }
                .sortedWith(compareBy({ it.host }, { it.name }))
                .toList()
        }
}

/** Ein flaches Anzeige-Element: entweder eine Host-Ueberschrift oder ein Container. */
sealed interface ServiceRow {
    data class Header(val host: String, val laufend: Int, val gesamt: Int) : ServiceRow
    data class Item(val service: ServiceSummary) : ServiceRow
}

private fun List<ServiceSummary>.rows(): List<ServiceRow> =
    groupBy { it.host }.flatMap { (host, list) ->
        buildList {
            add(ServiceRow.Header(host, list.count { it.isRunning }, list.size))
            list.forEach { add(ServiceRow.Item(it)) }
        }
    }

@HiltViewModel
class ServicesViewModel @Inject constructor(
    private val opsRepository: OpsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ServicesUiState())
    val state = _state.asStateFlow()

    fun load(refresh: Boolean = false) {
        viewModelScope.launch {
            _state.update {
                it.copy(loading = !refresh && it.services.isEmpty(), refreshing = refresh, error = null)
            }
            // Erst die Hostliste: sie sagt, welche Hosts es gibt und welche
            // gerade antworten. Ein unerreichbarer Host wird nicht abgefragt,
            // sonst wartet die Liste auf einen Zeitablauf, den niemand braucht.
            val hosts = opsRepository.hosts().getOrNull().orEmpty()
            val ziel = _state.value.gewaehlterHost
            val abzufragen = when {
                ziel != null -> listOf(ziel)
                hosts.isEmpty() -> listOf<String?>(null)
                else -> hosts.filter { it.erreichbar }.map { it.name }
            }

            // Parallel, nicht nacheinander: vier Hosts hintereinander bedeuten
            // vier Wartezeiten hintereinander, und der langsamste ist der ueber
            // den Socket-Proxy am anderen Ende des LAN.
            val ergebnisse = coroutineScope {
                abzufragen.map { host -> async { opsRepository.services(host) } }.map { it.await() }
            }
            val gelungen = ergebnisse.mapNotNull { it.getOrNull() }.flatten()
            val fehler = ergebnisse.firstNotNullOfOrNull { it.exceptionOrNull() }

            _state.update {
                it.copy(
                    loading = false,
                    refreshing = false,
                    hosts = hosts,
                    services = gelungen,
                    // Ein Fehler wird auch dann gezeigt, wenn andere Hosts
                    // geantwortet haben: sonst sieht eine unvollstaendige
                    // Liste wie eine vollstaendige aus.
                    error = if (gelungen.isEmpty() || fehler != null) fehler?.userMessage() else null,
                )
            }
        }
    }

    fun waehleHost(host: String?) {
        if (host == _state.value.gewaehlterHost) return
        _state.update { it.copy(gewaehlterHost = host, services = emptyList()) }
        load()
    }

    fun setzeSuche(text: String) = _state.update { it.copy(suche = text) }

    fun setzeFilter(filter: ServiceFilter) = _state.update { it.copy(filter = filter) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicesScreen(
    onOpenDetail: (host: String, name: String) -> Unit,
    viewModel: ServicesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.load() }

    Column(modifier = Modifier.fillMaxSize()) {
        Filterleiste(state, viewModel)

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.load(refresh = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                state.loading -> Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) { Text("Lade Dienste…") }

                state.error != null && state.services.isEmpty() ->
                    ErrorMessage(message = state.error!!, onRetry = { viewModel.load() })

                else -> {
                    val sichtbar = state.sichtbar
                    if (sichtbar.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            // Sagt, warum nichts da ist. „Keine Dienste" allein
                            // liest sich wie ein Ausfall.
                            Text(
                                "Kein Treffer bei ${state.services.size} Diensten.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(sichtbar.rows()) { row ->
                                when (row) {
                                    is ServiceRow.Header -> Text(
                                        "${row.host} · ${row.laufend}/${row.gesamt} laufen",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                                    )
                                    is ServiceRow.Item -> ServiceListItem(row.service, onOpenDetail)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Filterleiste(state: ServicesUiState, viewModel: ServicesViewModel) {
    Column(modifier = Modifier.padding(horizontal = 12.dp)) {
        OutlinedTextField(
            value = state.suche,
            onValueChange = { viewModel.setzeSuche(it) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Name oder Image") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.suche.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setzeSuche("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Suche löschen")
                    }
                }
            },
        )

        // Host-Auswahl. Steht ueber dem Zustandsfilter, weil sie bestimmt, was
        // ueberhaupt geladen wird, nicht nur was davon zu sehen ist.
        if (state.hosts.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FilterChip(
                    selected = state.gewaehlterHost == null,
                    onClick = { viewModel.waehleHost(null) },
                    label = { Text("Alle Hosts") },
                )
                state.hosts.forEach { host ->
                    FilterChip(
                        selected = state.gewaehlterHost == host.name,
                        onClick = { viewModel.waehleHost(host.name) },
                        // Ein Host ohne Socket-Proxy laesst sich nicht abfragen.
                        // Ausgrauen statt verstecken: sonst sieht ein fehlender
                        // Host wie ein nicht existierender aus.
                        enabled = host.erreichbar,
                        label = { Text(host.name) },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ServiceFilter.entries.forEach { f ->
                val anzahl = when (f) {
                    ServiceFilter.ALLE -> state.services.size
                    ServiceFilter.AUFFAELLIG -> state.services.count { it.istProblem }
                    ServiceFilter.LAEUFT -> state.services.count { it.isRunning }
                    ServiceFilter.AUS -> state.services.count { !it.isRunning }
                }
                FilterChip(
                    selected = state.filter == f,
                    onClick = { viewModel.setzeFilter(f) },
                    label = { Text("${f.titel} ($anzahl)") },
                )
            }
        }
    }
}

@Composable
private fun ServiceListItem(
    service: ServiceSummary,
    onOpenDetail: (String, String) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenDetail(service.host, service.name) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusDot(color = serviceStatusColor(service))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    service.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    service.zustandsText(),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (service.zustand == ServiceZustand.AUFFAELLIG ||
                        service.zustand == ServiceZustand.AUSGEFALLEN
                    ) {
                        serviceStatusColor(service)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}
