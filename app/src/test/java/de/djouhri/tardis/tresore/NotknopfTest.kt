package de.djouhri.tardis.tresore

import de.djouhri.tardis.data.model.tardis.AlarmeResponse
import de.djouhri.tardis.data.model.tardis.AllesZuResponse
import de.djouhri.tardis.data.model.tardis.kurzfassung
import de.djouhri.tardis.security.AppSperre
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Notknopf, Biometrie-Sperre und kurze Alarm-ID (2.0.24, ZIELBILD 100 (b) und 99 (5)). */
class NotknopfTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    @Test
    fun `gescheiterter Tresor steht namentlich in der Kurzfassung`() {
        val r = json.decodeFromString<AllesZuResponse>(
            """{"ok":false,"probe":false,"schritte":[
              {"id":"medien","name":"Medien","wirt":"host","ok":true,"zustand":"zu"},
              {"id":"node1-tresore","name":"Filme","wirt":"node1","ok":false,"zustand":"nicht erreichbar","meldung":"timeout"}]}""",
        )
        assertEquals("NICHT zu: Filme (nicht erreichbar)", r.kurzfassung())
    }

    @Test
    fun `alle zu nur wenn jeder Schritt zu ist`() {
        val r = json.decodeFromString<AllesZuResponse>(
            """{"ok":true,"schritte":[{"id":"a","name":"A","wirt":"w","ok":true,"zustand":"zu"},
              {"id":"b","name":"B","wirt":"w","ok":true,"zustand":"nicht eingerichtet"}]}""",
        )
        assertEquals("Alle Tresore zu", r.kurzfassung())
    }

    @Test
    fun `stummes tardis ist nicht zu`() {
        val r = json.decodeFromString<AllesZuResponse>("""{"ok":false,"schritte":[],"fehler":"tardis: ConnectError"}""")
        assertEquals("Nicht ausgeführt: tardis: ConnectError", r.kurzfassung())
        assertEquals("Keine Antwort über die Tresore", AllesZuResponse(ok = true).kurzfassung())
    }

    @Test
    fun `kurze Alarm-ID kommt aus dem JSON`() {
        val a = json.decodeFromString<AlarmeResponse>(
            """{"erreichbar":true,"alarme":[{"name":"X","schwere":"warning","kurz_id":"A-7K2Q"},{"name":"Y"}]}""",
        )
        assertEquals("A-7K2Q", a.alarme[0].kurzId)
        assertNull(a.alarme[1].kurzId)
    }

    @Test
    fun `gesperrt beim Start, nach Entsperren offen`() {
        val s = AppSperre { 0L }
        assertTrue(s.gesperrt)
        assertTrue(s.inDenVordergrund())
        s.entsperren()
        assertFalse(s.gesperrt)
    }

    @Test
    fun `unter 5 Minuten im Hintergrund bleibt offen, ab 5 Minuten gesperrt`() {
        var jetzt = 0L
        val s = AppSperre { jetzt }
        s.entsperren()
        s.inDenHintergrund()
        jetzt = AppSperre.FRIST_MS - 1
        assertFalse(s.inDenVordergrund())
        s.inDenHintergrund()
        jetzt += AppSperre.FRIST_MS
        assertTrue(s.inDenVordergrund())
    }

    @Test
    fun `wiederholtes onStop verschiebt die Frist nicht`() {
        var jetzt = 0L
        val s = AppSperre { jetzt }
        s.entsperren()
        s.inDenHintergrund()
        jetzt = 4 * 60 * 1000L
        s.inDenHintergrund()
        jetzt = AppSperre.FRIST_MS
        assertTrue(s.inDenVordergrund())
    }
}
