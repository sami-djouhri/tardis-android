package de.djouhri.tardis.data.model

import de.djouhri.tardis.data.model.tardis.Befehl
import de.djouhri.tardis.data.model.tardis.BefehlErgebnis
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
    }
}
