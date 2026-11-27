package de.djouhri.tardis.data.model

import de.djouhri.tardis.data.model.tardis.Befehl
import de.djouhri.tardis.data.model.tardis.BefehlAblehnung
import de.djouhri.tardis.data.model.tardis.BefehlErgebnis
import de.djouhri.tardis.data.model.tardis.BefehleAntwort
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BefehlTest {
    private fun b(e: BefehlErgebnis? = null, wartet: Boolean = false) =
        Befehl(id = "x", titel = "x", sha256 = "0".repeat(64), inhalt = "echo", wartet = wartet, ergebnis = e)

    @Test
    fun offenIstNurWasEineFreigabeBraucht() {
        assertTrue(b().offen)
        assertTrue(b(BefehlErgebnis("fehler", rc = 1)).offen)
        assertTrue(b(BefehlErgebnis("abgelehnt")).offen)
        assertTrue(b(BefehlErgebnis("ok", rc = 0, veraltet = true)).offen)
        assertFalse(b(BefehlErgebnis("ok", rc = 0)).offen)
        assertFalse(b(BefehlErgebnis("laeuft")).offen)
        assertFalse(b(wartet = true).offen)
        // Ein Probelauf ist kein Erledigt, eine veraltete laufende Fassung blockiert nicht.
        assertTrue(b(BefehlErgebnis("ok", rc = 0, modus = "probe")).offen)
        assertTrue(b(BefehlErgebnis("laeuft", veraltet = true)).offen)
        assertTrue(b(BefehlErgebnis("abgebrochen", rc = 130)).offen)
    }

    @Test
    fun abgelehntIstNichtOffen() {
        val x = b().copy(abgelehnt = BefehlAblehnung("erst Backup", 5))
        assertFalse(x.offen)
    }

    @Test
    fun gescheitertNurAktuelleFassung() {
        assertTrue(b(BefehlErgebnis("fehler", rc = 2)).gescheitert)
        assertFalse(b(BefehlErgebnis("fehler", rc = 2, veraltet = true)).gescheitert)
        assertFalse(b(BefehlErgebnis("ok", rc = 0)).gescheitert)
    }

    @Test
    fun antwortDesGatewaysWirdGelesen() {
        val roh = """{"eingerichtet":true,"laeufer":[{"wirt":"host","eingerichtet":true,"version":2,"zeit":5,"still":false},
            {"wirt":"node1","eingerichtet":false}],"befehle":[{"id":"a","titel":"A","wirt":"node1","probe":true,
            "sha256":"${"0".repeat(64)}","inhalt":"echo","ergebnis":{"zustand":"laeuft","ausgabe":"bis hier","modus":"lauf"},
            "abgelehnt":null,"unbekannt":1}]}"""
        val a = Json { ignoreUnknownKeys = true }.decodeFromString(BefehleAntwort.serializer(), roh)
        assertEquals(2, a.laeufer("host")?.version)
        assertFalse(a.laeufer("node1")!!.eingerichtet)
        assertTrue(a.befehle[0].laeuft && a.befehle[0].probe)
        assertEquals("node1", a.befehle[0].wirt)
    }
}
