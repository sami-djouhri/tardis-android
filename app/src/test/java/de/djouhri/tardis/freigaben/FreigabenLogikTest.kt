package de.djouhri.tardis.freigaben

import de.djouhri.tardis.data.model.tardis.Befehl
import de.djouhri.tardis.data.model.tardis.BefehlAblehnung
import de.djouhri.tardis.data.model.tardis.BefehlErgebnis
import de.djouhri.tardis.data.model.tardis.HausbusAntwort
import de.djouhri.tardis.data.model.tardis.VerlaufEintrag
import de.djouhri.tardis.data.repository.FreigabenRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class FreigabenLogikTest {
    private fun b(id: String, e: BefehlErgebnis? = null, sha: String = "0".repeat(64)) =
        Befehl(id = id, titel = id, sha256 = sha, inhalt = "echo", ergebnis = e)

    @Test
    fun zaehlerAusDenDemoDaten() {
        val z = FreigabenLogik.zaehler(FreigabenRepository.DEMO, FreigabenRepository.DEMO_BEFEHLE)
        assertEquals(2, z.deploys)
        assertEquals(1, z.laufen)      // Beispiel-Timer laeuft
        assertEquals(1, z.gescheitert) // Beispiel-Regel
        assertEquals(2, z.befehleOffen) // Regel (gescheitert) und Neustart (nie gelaufen)
        assertTrue(FreigabenLogik.zaehler(null, null).leer)
    }

    @Test
    fun abgelehntZaehltNichtAlsGescheitert() {
        val x = b("a", BefehlErgebnis("fehler", rc = 1)).copy(abgelehnt = BefehlAblehnung("nein"))
        val z = FreigabenLogik.zaehler(null, de.djouhri.tardis.data.model.tardis.BefehleAntwort(befehle = listOf(x)))
        assertEquals(0, z.gescheitert)
        assertEquals(0, z.befehleOffen)
    }

    @Test
    fun wartetLangeAbVierundzwanzigStunden() {
        val jetzt = Instant.parse("2026-11-21T12:00:00Z")
        assertFalse(FreigabenLogik.wartetLange("2026-11-20T13:00:00Z", jetzt))
        assertTrue(FreigabenLogik.wartetLange("2026-11-20T12:00:00+00:00", jetzt))
        assertFalse(FreigabenLogik.wartetLange(null, jetzt))
        assertFalse(FreigabenLogik.wartetLange("kaputt", jetzt))
        assertNull(FreigabenLogik.wartezeit("2026-11-22T12:00:00Z", jetzt))
    }

    @Test
    fun verlaufstexte() {
        fun e(aktion: String, zustand: String? = null, modus: String? = null, rc: Int? = null, grund: String? = null) =
            VerlaufEintrag(0, "befehl", aktion, "T", zustand = zustand, modus = modus, rc = rc, grund = grund)
        assertEquals("Lauf gelungen, Exit 0", FreigabenLogik.verlaufText(e("ergebnis", "ok", "lauf", 0)))
        assertEquals("Probelauf gescheitert, Exit 2", FreigabenLogik.verlaufText(e("ergebnis", "fehler", "probe", 2)))
        assertEquals("abgelehnt: erst Backup", FreigabenLogik.verlaufText(e("abgelehnt", grund = "erst Backup")))
        assertEquals("rot", FreigabenLogik.verlaufStufe(e("ergebnis", "fehler")))
        assertEquals("gelb", FreigabenLogik.verlaufStufe(e("ergebnis", "abgebrochen")))
        assertEquals("neutral", FreigabenLogik.verlaufStufe(e("freigegeben")))
        assertEquals("21.11. 17:00", FreigabenLogik.zeitText(1_795_276_800, ZoneId.of("Europe/Berlin")))
    }

    @Test
    fun ersterLaufMeldetNichtsDanachNurNeues() {
        val alt = b("alt", BefehlErgebnis("ok", rc = 0, ende = 5))
        val neu = b("neu")
        val erst = FreigabenLogik.neuZuMelden(listOf(alt, neu), null, null)
        assertTrue(erst.neuOffen.isEmpty() && erst.neuFertig.isEmpty())
        val gemerktOffen = setOf(FreigabenLogik.offenSchluessel(neu))
        val gemerktFertig = setOf(FreigabenLogik.ergebnisSchluessel(alt)!!)
        val geaendert = neu.copy(sha256 = "1".repeat(64))
        val fertig = alt.copy(ergebnis = BefehlErgebnis("fehler", rc = 1, ende = 9))
        val m = FreigabenLogik.neuZuMelden(listOf(fertig, geaendert), gemerktOffen, gemerktFertig)
        assertEquals(listOf("neu"), m.neuOffen.map { it.id })   // neue Fassung meldet neu
        assertEquals(listOf("alt"), m.neuFertig.map { it.id })  // neues Ergebnis meldet
        // Laufend ist kein Ergebnis
        assertNull(FreigabenLogik.ergebnisSchluessel(b("x", BefehlErgebnis("laeuft"))))
    }

    @Test
    fun hausbusZaehltImKopf() {
        val z = FreigabenLogik.zaehler(null, null, FreigabenRepository.DEMO_HAUSBUS)
        assertEquals(1, z.hausbus)
        assertFalse(z.leer)
        assertTrue(FreigabenLogik.zaehler(null, null, HausbusAntwort(erreichbar = false)).leer)
    }

    @Test
    fun hausbusVerfallsrest() {
        val jetzt = Instant.parse("2026-01-16T07:20:00Z")
        val rest = FreigabenLogik.verfaelltIn("2026-01-16T08:00:00Z", jetzt)!!
        assertEquals("noch 40 min", FreigabenLogik.restText(rest))
        assertEquals("noch 5 h", FreigabenLogik.restText(FreigabenLogik.verfaelltIn("2026-01-16T12:30:00Z", jetzt)!!))
        assertNull(FreigabenLogik.verfaelltIn("2026-01-16T07:00:00Z", jetzt))
        assertNull(FreigabenLogik.verfaelltIn("", jetzt))
    }

    @Test
    fun hausbusVerlaufstext() {
        val e = VerlaufEintrag(1, "hausbus", "freigegeben", "Container neu starten: x", zustand = "ok")
        assertEquals("freigegeben, Hausbus hat es ausgeführt", FreigabenLogik.verlaufText(e))
        assertEquals("freigegeben", FreigabenLogik.verlaufText(e.copy(zustand = null)))
    }
}
