package de.djouhri.tardis.data.model

import de.djouhri.tardis.data.model.tardis.AbrufDienst
import de.djouhri.tardis.data.model.tardis.AbrufResponse
import de.djouhri.tardis.data.model.tardis.Alarm
import de.djouhri.tardis.data.model.tardis.AlarmeResponse
import de.djouhri.tardis.data.model.tardis.WeckErgebnis
import de.djouhri.tardis.data.model.tardis.gruppiert
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AbrufTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    @Test
    fun `gleichnamige Alarme landen in einer Gruppe, Reihenfolge bleibt`() {
        val gruppen = listOf(
            Alarm("Kritisch", "critical"),
            Alarm("Neustart", "warning", instanz = "knoten-a"),
            Alarm("Zigbee", "warning"),
            Alarm("Neustart", "warning", instanz = "knoten-b"),
        ).gruppiert()
        assertEquals(listOf("Kritisch", "Neustart", "Zigbee"), gruppen.map { it.name })
        assertEquals(listOf("knoten-a", "knoten-b"), gruppen[1].alarme.map { it.instanz })
        assertTrue(gruppen[0].istKritisch)
        assertFalse(gruppen[1].istKritisch)
    }

    @Test
    fun `unerreichbare Quelle ist keine Ruhe`() {
        val a = json.decodeFromString<AlarmeResponse>("""{"erreichbar":false,"fehler":"tardis: ConnectError","alarme":[]}""")
        assertFalse(a.erreichbar)
        assertEquals(0, a.kritisch)
    }

    @Test
    fun `Antwort des Gateways wird gelesen`() {
        // Form wie live am 2026-10-31 von /v1/ops/abruf.
        val roh = """
            {"wecker":{"erreichbar":true,"fehler":null,"frei_mb":5669,"reserve_mb":900,"dienste":[
              {"name":"schach","titel":"Schach","laeuft":false,"bereit":false,"leerlauf_s":null,
               "idle_timeout_s":1800,"startbar":true,"wartung":false}]},
             "lab":{"verfuegbar":true,"veraltet":false,"alter_s":40,"fehler":null,"laeuft":false,
               "vms":[],"reserviert":false,"frei_mb":10278}}
        """.trimIndent()
        val r = json.decodeFromString<AbrufResponse>(roh)
        assertEquals(5669, r.wecker.freiMb)
        assertEquals("Schach", r.wecker.dienste.single().titel)
        assertEquals(1800, r.wecker.dienste.single().idleTimeoutS)
        assertEquals(10278, r.lab.freiMb)
    }

    @Test
    fun `Zustandstexte des Weckers`() {
        assertEquals("schläft", AbrufDienst("a").zustandsText())
        assertEquals("schläft · zu wenig Speicher zum Starten", AbrufDienst("a", startbar = false).zustandsText())
        assertEquals("startet…", AbrufDienst("a", laeuft = true).zustandsText())
        assertEquals(
            "wach · schläft in 20 min",
            AbrufDienst("a", laeuft = true, bereit = true, leerlaufS = 600, idleTimeoutS = 1800).zustandsText(),
        )
        assertEquals(
            "wach · schläft gleich ein",
            AbrufDienst("a", laeuft = true, bereit = true, leerlaufS = 1790, idleTimeoutS = 1800).zustandsText(),
        )
        // Ohne Zugriffsspur keine erfundene Restzeit.
        assertEquals("wach", AbrufDienst("a", laeuft = true, bereit = true, idleTimeoutS = 1800).zustandsText())
        assertEquals(
            "wach · wird wach gehalten",
            AbrufDienst("a", laeuft = true, bereit = true, leerlaufS = 5000, idleTimeoutS = 1800, wartung = true).zustandsText(),
        )
    }

    @Test
    fun `Weckergebnis nennt den Grund einer Absage`() {
        assertTrue(WeckErgebnis("x", zustand = "lowmem").meldung("Schach").contains("Speicher"))
        assertTrue(WeckErgebnis("x", zustand = "throttled").meldung("Schach").contains("zu viele Starts"))
        assertEquals("Schach startet.", WeckErgebnis("x", ok = true, zustand = "starting").meldung("Schach"))
    }
}
