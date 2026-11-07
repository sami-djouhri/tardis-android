package de.djouhri.tardis.bildschirm

import android.os.Looper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import de.djouhri.tardis.data.api.DashboardApi
import de.djouhri.tardis.data.api.InboxApi
import de.djouhri.tardis.data.api.OpsApi
import de.djouhri.tardis.data.api.VersionApi
import de.djouhri.tardis.data.demo.DemoData
import de.djouhri.tardis.data.demo.DemoModeManager
import de.djouhri.tardis.data.repository.DashboardRepository
import de.djouhri.tardis.data.repository.InboxRepository
import de.djouhri.tardis.data.repository.OpsRepository
import de.djouhri.tardis.navigation.AlarmeRoute
import de.djouhri.tardis.navigation.TardisLeiste
import de.djouhri.tardis.ui.reliability.ReliabilityScreen
import de.djouhri.tardis.ui.topologie.TopologieScreen
import de.djouhri.tardis.ui.topologie.TopologieViewModel
import de.djouhri.tardis.ui.reliability.ReliabilityViewModel
import de.djouhri.tardis.ui.abruf.AbrufScreen
import de.djouhri.tardis.ui.abruf.AbrufViewModel
import de.djouhri.tardis.ui.alarme.AlarmeScreen
import de.djouhri.tardis.ui.alarme.AlarmeUiState
import de.djouhri.tardis.ui.alarme.AlarmeViewModel
import de.djouhri.tardis.ui.inbox.InboxScreen
import de.djouhri.tardis.ui.inbox.InboxViewModel
import de.djouhri.tardis.ui.overview.OverviewScreen
import de.djouhri.tardis.ui.overview.OverviewViewModel
import de.djouhri.tardis.ui.services.ServiceDetailScreen
import de.djouhri.tardis.ui.services.ServiceDetailViewModel
import de.djouhri.tardis.ui.services.ServicesScreen
import de.djouhri.tardis.ui.services.ServicesViewModel
import de.djouhri.tardis.ui.theme.HomelabTheme
import de.djouhri.tardis.widget.WidgetSpeicher
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.lang.reflect.Proxy

/**
 * Jede Flaeche einmal gezeichnet, ausschliesslich mit den Demo-Daten.
 *
 * Warum es das gibt: bis 2.0.11 hat niemand die Bildschirme der App je
 * angesehen, ausser auf dem Telefon des Owners. Ein Blick auf das echte Geraet
 * zeigt echte Daten, und die gehoeren nicht in ein Bild, das ich lese
 * (Owner 2026-11-02). Die Demo-Daten sind erfunden
 * und decken jeden Zustand ab, den die App unterscheidet.
 *
 * Die Repositories bekommen eine API, die bei JEDEM Aufruf scheitert: kommt ein
 * Bild trotzdem mit Inhalt heraus, stammt er nachweislich aus der Demo.
 *
 * Erzeugen: `./gradlew :app:recordRoborazziDebug`, Bilder unter
 * `app/build/outputs/roborazzi/`. Ein normaler `testDebugUnitTest` zeichnet
 * dieselben Flaechen ohne Bild, faengt also Abstuerze beim Aufbau.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// 360 dp ist ein schmales, haeufiges Telefon; die Hoehe reicht fuer lange Flaechen.
@Config(sdk = [35], qualifiers = "w360dp-h1600dp-xxhdpi", application = android.app.Application::class)
class BildschirmeTest {

    @get:Rule
    val compose = createComposeRule()

    private val demo = DemoModeManager().apply { enable() }

    @Suppress("UNCHECKED_CAST")
    private fun <T> nieGerufen(art: Class<T>): T =
        Proxy.newProxyInstance(art.classLoader, arrayOf(art)) { _, methode, _ ->
            throw IllegalStateException("Im Demo-Modus darf keine API gerufen werden: ${methode.name}")
        } as T

    private val ops = OpsRepository(nieGerufen(OpsApi::class.java), demo)
    private val widget = WidgetSpeicher(ApplicationProvider.getApplicationContext(), demo)

    private fun bild(name: String, schrift: Float = 1f, inhalt: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, schrift)) {
                HomelabTheme {
                    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                        inhalt()
                    }
                }
            }
        }
        // Laden laeuft im viewModelScope (Hauptschleife), Animationen ueber
        // die Testuhr. Beides von Hand vorspulen: die Uebersicht frischt alle
        // 30 s auf, mit automatischer Uhr wuerde der Test nie ruhig.
        repeat(4) {
            compose.mainClock.advanceTimeBy(500)
            shadowOf(Looper.getMainLooper()).idle()
        }
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")
    }

    private val alarmeGeladen = AlarmeUiState(loading = false, antwort = DemoData.alarme)

    @Test
    fun uebersicht() = bild("01-uebersicht") {
        OverviewScreen(
            onOpenServices = {},
            onOpenDetail = { _, _ -> },
            alarme = alarmeGeladen,
            viewModel = OverviewViewModel(
                ops,
                DashboardRepository(nieGerufen(DashboardApi::class.java), nieGerufen(VersionApi::class.java), demo),
                widget,
            ),
        )
    }

    @Test
    fun dienste() = bild("02-dienste") {
        ServicesScreen(onOpenDetail = { _, _ -> }, viewModel = ServicesViewModel(ops))
    }

    @Test
    fun dienstDetailBeobachtet() = bild("03-detail-beobachtet") {
        ServiceDetailScreen("edge", "reverse-proxy", ServiceDetailViewModel(ops, flowOf(true)))
    }

    @Test
    fun dienstDetailUnbeobachtet() = bild("04-detail-unbeobachtet") {
        ServiceDetailScreen("app-node", "web-portal", ServiceDetailViewModel(ops, flowOf(true)))
    }

    @Test
    fun dienstDetailAusgefallen() = bild("05-detail-ausgefallen") {
        ServiceDetailScreen("app-node", "mail-relay", ServiceDetailViewModel(ops, flowOf(true)))
    }

    @Test
    fun alarme() = bild("06-alarme") {
        AlarmeScreen(AlarmeViewModel(ops, widget).apply { load() })
    }

    @Test
    fun abruf() = bild("07-abruf") {
        AbrufScreen(AbrufViewModel(ops, flowOf(true)))
    }

    @Test
    fun inbox() = bild("08-inbox") {
        InboxScreen(InboxViewModel(InboxRepository(nieGerufen(InboxApi::class.java), demo)))
    }

    @Test
    fun topologie() = bild("13-topologie") {
        TopologieScreen(viewModel = TopologieViewModel(ops))
    }

    @Test
    fun verlauf() = bild("12-verlauf") {
        InboxScreen(InboxViewModel(InboxRepository(nieGerufen(InboxApi::class.java), demo)).apply { zeigeVerlauf(true) })
    }

    @Test
    fun verfuegbarkeit() = bild("11-verfuegbarkeit") {
        ReliabilityScreen(ReliabilityViewModel(ops))
    }

    /** Fuenf Eintraege auf 360 dp: steht „Übersicht" noch ganz da? */
    @Test
    fun leiste() = bild("09-leiste") { Leiste() }

    /** Dasselbe mit grosser Systemschrift (130 %), wie sie viele einstellen. */
    @Test
    fun leisteGrosseSchrift() = bild("10-leiste-schrift-130", schrift = 1.3f) { Leiste() }

    @Composable
    private fun Leiste() {
        Column {
            TardisLeiste(aktiv = { it == AlarmeRoute }, alarme = alarmeGeladen, onWahl = {})
        }
    }
}
