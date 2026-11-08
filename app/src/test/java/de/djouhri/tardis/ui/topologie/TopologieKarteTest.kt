package de.djouhri.tardis.ui.topologie

import android.os.Looper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.geometry.Offset
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import de.djouhri.tardis.data.model.tardis.TopoAenderungen
import de.djouhri.tardis.data.model.tardis.TopoDienst
import de.djouhri.tardis.data.model.tardis.TopoVerbindung
import de.djouhri.tardis.data.model.tardis.TopoWirt
import de.djouhri.tardis.data.model.tardis.TopologieResponse
import de.djouhri.tardis.data.model.tardis.kartenLayout
import de.djouhri.tardis.ui.theme.HomelabTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Die Karte in echter Groesse auf einem echten Telefon zeichnen.
 *
 * Warum es das gibt: 2.0.16/2.0.17 stuerzten beim Oeffnen der Topologie ab.
 * `drawText` ohne Groesse leitet seine Grenzen aus der Canvas in Pixeln minus
 * Startpunkt ab, der Startpunkt steht aber in Kartenkoordinaten. Live ist die
 * Karte rund 2500 Einheiten hoch, die Canvas rund 1900 px: jede Beschriftung
 * darunter ergab eine negative Hoehe, und `Constraints` wirft. Der
 * Bildschirmtest sah das nicht (27 Demo-Dienste, 1600 dp hohe Flaeche).
 * Deshalb hier: Groesse wie live (erfunden), Telefonhoehe, dazu Zoom und
 * Verschieben, damit auch die Beschriftungen ab Zoom 1,4 und 2,5 gezeichnet
 * werden.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h740dp-xxhdpi", application = android.app.Application::class)
class TopologieKarteTest {

    @get:Rule
    val compose = createComposeRule()

    private val echteGroesse = run {
        fun viele(wirt: String, n: Int, gruppen: Int) =
            (1..n).map { TopoDienst("$wirt-$it", "$wirt-dienst-mit-langem-namen-$it", wirt, gruppe = "Gruppe ${it % gruppen}", zustand = if (it % 7 == 0) "aus" else "laeuft",
                klasse = listOf("CORE", "OPS", "INTERNAL", "PUBLIC")[it % 4], erreichbarkeit = listOf("lokal", "geraete", "oeffentlich")[it % 3]) }
        val dienste = viele("alpha", 114, 15) + viele("beta", 110, 12) + viele("gamma", 12, 1) +
            viele("delta", 5, 1) + viele("epsilon", 4, 1) + viele("zeta", 4, 1) + viele("eta", 4, 1) +
            viele("theta", 2, 1) + viele("iota", 1, 1) + viele("kappa", 1, 1)
        val wirte = listOf("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta", "iota", "kappa").map { TopoWirt(it, online = it != "kappa", cpu = 12.0, ram = 48.0) } +
            (1..11).map { TopoWirt("leer$it") }
        // gamma-1 -> gamma-2 -> gamma-7 (aus): gamma-2 und gamma-1 sind mitbetroffen.
        val linien = listOf(
            TopoVerbindung("gamma-1", "gamma-2", "depends"),
            TopoVerbindung("gamma-2", "gamma-7", "depends"),
            TopoVerbindung("alpha-1", "beta-1", "lan"),
            TopoVerbindung("alpha-3", "leer1", "backup"),
        )
        TopologieResponse(wirte = wirte, dienste = dienste, verbindungen = linien)
    }

    private fun zeichne(aenderungen: TopoAenderungen? = null) {
        val layout = kartenLayout(echteGroesse)
        assertTrue("Karte muss hoeher sein als die Canvas, sonst prueft der Test nichts", layout.hoehe > 2000f)
        compose.setContent {
            HomelabTheme {
                Surface(modifier = Modifier.fillMaxSize()) { Karte(echteGroesse, layout, { _, _ -> }, aenderungen = aenderungen) }
            }
        }
        compose.waitForIdle()
        shadowOf(Looper.getMainLooper()).idle()
        // Ohne Bildaufnahme laeuft unter Robolectric die Zeichenphase nicht,
        // der Test waere dann gruen, ohne die Canvas je gezeichnet zu haben
        // (so am 2026-11-08 passiert). Laufen mit `recordRoborazziDebug`.
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/topologie-gross-1.png")
    }

    @Test
    fun echteGroesseZeichnetOhneAbsturz() = zeichne()

    @Test
    fun gezoomtUndVerschobenZeichnetOhneAbsturz() {
        zeichne()
        val karte = compose.onNodeWithContentDescription("Karte der Wirte und Dienste", substring = true)
        // Zweimal auseinanderziehen: ueber 2,5 kommen die Dienstnamen dazu.
        repeat(2) {
            karte.performTouchInput {
                pinch(center - Offset(20f, 0f), center - Offset(300f, 0f), center + Offset(20f, 0f), center + Offset(300f, 0f))
            }
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/topologie-gross-2.png")
        }
        repeat(3) {
            karte.performTouchInput { swipeUp() }
            compose.waitForIdle()
            compose.onRoot().captureRoboImage("build/outputs/roborazzi/topologie-gross-3.png")
        }
    }

    @Test
    fun sucheZeigtTrefferUndAntippenOeffnetDenDienst() {
        zeichne()
        compose.onNodeWithText("Dienst, Wirt oder Gruppe suchen").performTextInput("gamma-12")
        compose.waitForIdle()
        compose.onNodeWithText("1 Treffer").assertIsDisplayed()
        compose.onNodeWithText("gamma-dienst-mit-langem-namen-12").performClick()
        compose.waitForIdle()
        // Liste weicht, Dienstkarte mit Wirt und Zustand erscheint.
        compose.onNodeWithText("Schließen").assertIsDisplayed()
        compose.onNodeWithText("gamma · Gruppe 0 · läuft").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/topologie-gross-suche.png")
    }

    @Test
    fun nurAuffaelligesZeigtDieListe() {
        zeichne()
        val zahl = echteGroesse.dienste.count { it.istProblem }
        compose.onNodeWithText("Nur Auffälliges $zahl").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("$zahl auffällig").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/topologie-gross-auffaellig.png")
    }

    @Test
    fun wirtKopfOeffnetDasBlattUndZeigtAufDerKarte() {
        zeichne()
        val karte = compose.onNodeWithContentDescription("Karte der Wirte und Dienste", substring = true)
        // Kopf des ersten Kastens: links oben, knapp unter der Kante.
        karte.performTouchInput { click(Offset(width * 0.3f, 12f * density)) }
        compose.waitForIdle()
        compose.onNodeWithText("Auf Karte zeigen").assertIsDisplayed()
        compose.onNodeWithText("CPU 12 % · RAM 48 %").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/topologie-gross-wirt.png")
        compose.onNodeWithText("Auf Karte zeigen").performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/topologie-gross-wirt-gezeigt.png")
    }

    @Test
    fun linseErreichbarkeitFaerbtUmUndZeigtLegende() {
        zeichne()
        compose.onNodeWithText("Farbe: Zustand").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Erreichbarkeit").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Farbe: Erreichbarkeit").assertIsDisplayed()
        val oeffentlich = echteGroesse.dienste.count { it.erreichbarkeit == "oeffentlich" }
        compose.onNodeWithText(" öffentlich $oeffentlich").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/topologie-gross-linse.png")
    }

    @Test
    fun dienstZeigtSeineAusfallKette() {
        zeichne()
        compose.onNodeWithText("Dienst, Wirt oder Gruppe suchen").performTextInput("gamma-2")
        compose.waitForIdle()
        compose.onNodeWithText("gamma-dienst-mit-langem-namen-2").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Braucht: gamma-dienst-mit-langem-namen-7").assertIsDisplayed()
        compose.onNodeWithText("Fällt mit aus: gamma-dienst-mit-langem-namen-1").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/topologie-gross-kette.png")
    }

    @Test
    fun mitbetroffeneStehenInDerZusammenfassung() {
        zeichne()
        compose.onNodeWithText("mitbetroffen", substring = true).assertIsDisplayed()
    }

    @Test
    fun aenderungenAlsBannerUndListe() {
        zeichne(
            TopoAenderungen(
                seitMs = System.currentTimeMillis() - 3_600_000,
                neuAuffaellig = listOf("beta-7"),
                wiederGut = listOf("beta-1"),
                neu = emptyList(),
                weg = listOf("alter-dienst"),
            ),
        )
        compose.onNodeWithText("1 neu auffällig · 1 wieder gut · 1 weg", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Antippen für die Liste").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("beta-dienst-mit-langem-namen-7").assertIsDisplayed()
        compose.onNodeWithText("Nicht mehr da: alter-dienst").assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/outputs/roborazzi/topologie-gross-aenderungen.png")
    }
}
