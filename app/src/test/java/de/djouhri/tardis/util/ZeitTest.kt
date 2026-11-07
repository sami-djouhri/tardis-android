package de.djouhri.tardis.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ZeitTest {

    private val jetzt = Instant.parse("2026-10-31T12:00:00Z")
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun `kurze Dauern`() {
        assertEquals("seit eben", seitText("2026-10-31T11:59:40Z", jetzt, berlin))
        assertEquals("seit 12 min", seitText("2026-10-31T11:48:00Z", jetzt, berlin))
        assertEquals("seit 5 h", seitText("2026-10-31T06:30:00Z", jetzt, berlin))
    }

    @Test
    fun `lange Dauern tragen das Datum`() {
        assertEquals("seit 1 Tag (30.10.)", seitText("2026-10-30T10:00:00Z", jetzt, berlin))
        // 22:34 UTC ist in Berlin schon der naechste Tag: das Datum steht in der
        // Zone des Telefons, sonst passt es nicht zur Uhrzeit, die man kennt.
        assertEquals("seit 14 Tagen (17.10.)", seitText("2026-10-16T22:34:57.123456789Z", jetzt, berlin))
    }

    @Test
    fun `Offset statt Z wird gelesen`() {
        // So schreibt der alerts-Dienst `last_poll` (Python isoformat).
        assertEquals("seit 12 min", seitText("2026-10-31T11:48:00+00:00", jetzt, berlin))
        // Am 31.10. gilt schon MEZ (+1), die Sommerzeit endete am 25.10.
        assertEquals("12:48", uhrzeitAus("2026-10-31T11:48:00.123456+00:00", berlin))
    }

    @Test
    fun `nichts Erfundenes`() {
        assertNull(seitText(null, jetzt, berlin))
        assertNull(seitText("gestern", jetzt, berlin))
        assertNull(seitText("2026-11-01T00:00:00Z", jetzt, berlin))
        assertNull(uhrzeitAus("kaputt", berlin))
    }
}
