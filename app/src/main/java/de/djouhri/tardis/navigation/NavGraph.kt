package de.djouhri.tardis.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import de.djouhri.tardis.ui.abruf.AbrufScreen
import de.djouhri.tardis.ui.alarme.AlarmeScreen
import de.djouhri.tardis.ui.alarme.AlarmeViewModel
import de.djouhri.tardis.ui.inbox.InboxScreen
import kotlinx.coroutines.delay
import de.djouhri.tardis.ui.overview.OverviewScreen
import de.djouhri.tardis.ui.reliability.ReliabilityScreen
import de.djouhri.tardis.ui.wirte.WirteScreen
import de.djouhri.tardis.ui.topologie.TopologieScreen
import de.djouhri.tardis.ui.services.ServiceDetailScreen
import de.djouhri.tardis.ui.services.ServicesScreen
import de.djouhri.tardis.ui.settings.SettingsScreen
import de.djouhri.tardis.ui.update.UpdateBanner
import de.djouhri.tardis.ui.update.UpdateViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.serialization.Serializable

@Serializable object OverviewRoute
@Serializable object ServicesRoute
@Serializable object AlarmeRoute
@Serializable object AbrufRoute
@Serializable object InboxRoute
@Serializable object SettingsRoute
@Serializable object ReliabilityRoute
@Serializable object TopologieRoute
@Serializable object WirteRoute
@Serializable data class ServiceDetailRoute(val host: String, val name: String)

private data class TopLevelDest(
    val label: String,
    val route: Any,
    val icon: ImageVector,
)

private val topLevelDests = listOf(
    TopLevelDest("Start", OverviewRoute, Icons.Filled.Dashboard),
    TopLevelDest("Dienste", ServicesRoute, Icons.Filled.Storage),
    TopLevelDest("Alarme", AlarmeRoute, Icons.Filled.NotificationsActive),
    TopLevelDest("Abruf", AbrufRoute, Icons.Filled.Bedtime),
    TopLevelDest("Inbox", InboxRoute, Icons.Filled.Inbox),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TardisMainScreen(
    demoActive: Boolean = false,
    sessionExpired: Boolean = false,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Wie das UpdateViewModel: der Activity zugeordnet, damit Zaehler an der
    // Leiste, Karte auf der Uebersicht und Liste denselben Stand zeigen.
    val alarmeViewModel: AlarmeViewModel = hiltViewModel()
    val alarme by alarmeViewModel.state.collectAsState()
    // Jede Minute nachsehen, solange die App im Vordergrund ist. Im
    // Hintergrund fragt sie nichts: dafuer gibt es ntfy.
    LaunchedEffect(demoActive) {
        while (true) {
            alarmeViewModel.load()
            delay(60_000)
        }
    }

    val isTopLevel = topLevelDests.any { dest ->
        currentDestination?.hierarchy?.any { it.hasRoute(dest.route::class) } == true
    }

    val title = when {
        currentDestination?.hasRoute(SettingsRoute::class) == true -> "Einstellungen"
        currentDestination?.hasRoute(ReliabilityRoute::class) == true -> "Verfügbarkeit"
        currentDestination?.hasRoute(TopologieRoute::class) == true -> "Topologie"
        currentDestination?.hasRoute(WirteRoute::class) == true -> "Wirte"
        currentDestination?.hasRoute(ServiceDetailRoute::class) == true ->
            backStackEntry?.toRoute<ServiceDetailRoute>()?.name ?: "Dienst"
        currentDestination?.hierarchy?.any { it.hasRoute(ServicesRoute::class) } == true -> "Dienste"
        currentDestination?.hierarchy?.any { it.hasRoute(InboxRoute::class) } == true -> "Inbox"
        currentDestination?.hierarchy?.any { it.hasRoute(AlarmeRoute::class) } == true -> "Alarme"
        currentDestination?.hierarchy?.any { it.hasRoute(AbrufRoute::class) } == true -> "Auf Abruf"
        else -> "Tardis"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (!isTopLevel) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                        }
                    }
                },
                actions = {
                    if (isTopLevel) {
                        IconButton(onClick = {
                            navController.navigate(SettingsRoute) { launchSingleTop = true }
                        }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Einstellungen")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (isTopLevel) {
                TardisLeiste(
                    aktiv = { route ->
                        currentDestination?.hierarchy?.any { it.hasRoute(route::class) } == true
                    },
                    alarme = alarme,
                    onWahl = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Hier erzeugt, nicht in den Zielen: so teilen Banner und
            // Einstellungen denselben Stand (der Besitzer ist die Activity,
            // nicht ein einzelner Navigationseintrag). Sonst prueft jede
            // Flaeche fuer sich, und die eine zeigt ein Update an, das die
            // andere schon geladen hat.
            val updateViewModel: UpdateViewModel = hiltViewModel()
            if (!demoActive) {
                UpdateBanner(updateViewModel)
            }
            if (demoActive) {
                TardisBanner(
                    text = "DEMO - Beispieldaten, keine Verbindung zum Gateway",
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    onContainer = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            if (sessionExpired && !demoActive) {
                TardisBanner(
                    text = "Sitzung abgelaufen - bitte in den Einstellungen neu koppeln",
                    container = MaterialTheme.colorScheme.errorContainer,
                    onContainer = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            NavHost(
                navController = navController,
                startDestination = OverviewRoute,
            ) {
                composable<OverviewRoute> {
                    OverviewScreen(
                        onOpenServices = {
                            navController.navigate(ServicesRoute) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onOpenDetail = { host, name ->
                            navController.navigate(ServiceDetailRoute(host, name))
                        },
                        alarme = alarme,
                        onAlarmeNeu = { alarmeViewModel.load() },
                        onOpenVerfuegbarkeit = { navController.navigate(ReliabilityRoute) { launchSingleTop = true } },
                        onOpenTopologie = { navController.navigate(TopologieRoute) { launchSingleTop = true } },
                        onOpenWirte = { navController.navigate(WirteRoute) { launchSingleTop = true } },
                        onOpenAlarme = {
                            navController.navigate(AlarmeRoute) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
                composable<AlarmeRoute> {
                    AlarmeScreen(alarmeViewModel)
                }
                composable<AbrufRoute> {
                    AbrufScreen()
                }
                composable<ServicesRoute> {
                    ServicesScreen(
                        onOpenDetail = { host, name ->
                            navController.navigate(ServiceDetailRoute(host, name))
                        },
                    )
                }
                composable<ServiceDetailRoute> { entry ->
                    val route = entry.toRoute<ServiceDetailRoute>()
                    ServiceDetailScreen(host = route.host, name = route.name)
                }
                composable<InboxRoute> {
                    InboxScreen()
                }
                composable<ReliabilityRoute> {
                    ReliabilityScreen()
                }
                composable<TopologieRoute> {
                    TopologieScreen(
                        onOpenDetail = { host, name -> navController.navigate(ServiceDetailRoute(host, name)) },
                        onOpenWirte = { navController.navigate(WirteRoute) { launchSingleTop = true } },
                    )
                }
                composable<WirteRoute> {
                    WirteScreen()
                }
                composable<SettingsRoute> {
                    SettingsScreen(updateViewModel = updateViewModel)
                }
            }
        }
    }
}

/**
 * Die untere Leiste, eigenstaendig, damit der Bildschirmfoto-Test sie ohne
 * Navigation und Hilt zeichnen kann: bei fuenf Eintraegen ist die Frage, ob
 * „Übersicht" auf einem schmalen Telefon noch ganz dasteht.
 */
@Composable
internal fun TardisLeiste(
    aktiv: (Any) -> Boolean,
    alarme: de.djouhri.tardis.ui.alarme.AlarmeUiState,
    onWahl: (Any) -> Unit,
) {
    NavigationBar {
        topLevelDests.forEach { dest ->
            NavigationBarItem(
                icon = {
                    val zahl = if (dest.route == AlarmeRoute) alarme.anzahl ?: 0 else 0
                    if (zahl > 0) {
                        BadgedBox(badge = {
                            Badge(
                                containerColor = if (alarme.kritisch > 0) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.tertiary,
                            ) { Text(if (zahl > 99) "99+" else "$zahl") }
                        }) { Icon(dest.icon, contentDescription = "${dest.label}, $zahl aktiv") }
                    } else {
                        Icon(dest.icon, contentDescription = dest.label)
                    }
                },
                label = { Text(dest.label, maxLines = 1) },
                selected = aktiv(dest.route),
                onClick = { onWahl(dest.route) },
            )
        }
    }
}

@Composable
private fun TardisBanner(
    text: String,
    container: androidx.compose.ui.graphics.Color,
    onContainer: androidx.compose.ui.graphics.Color,
) {
    Surface(color = container, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = text,
            color = onContainer,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
