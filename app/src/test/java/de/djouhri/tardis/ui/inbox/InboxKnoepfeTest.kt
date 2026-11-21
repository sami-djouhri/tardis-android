package de.djouhri.tardis.ui.inbox

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class InboxKnoepfeTest {

    @Test
    fun `morgen frueh ist acht uhr am naechsten tag`() {
        assertEquals(10 * 60, minutenBisMorgenFrueh(LocalDateTime.parse("2026-11-21T22:00")))
        assertEquals(24 * 60 + 7 * 60 + 30, minutenBisMorgenFrueh(LocalDateTime.parse("2026-11-21T00:30")))
    }

    @Test
    fun `spaeter bietet vier dauern im erlaubten bereich`() {
        val w = spaeterWahl(LocalDateTime.parse("2026-11-21T10:00"))
        assertEquals(listOf("In 1 Stunde", "In 3 Stunden", "Morgen früh", "In einer Woche"), w.map { it.first })
        w.forEach { (_, min) -> assert(min in 1..10080) }
    }

    @Test
    fun `rueckmeldung nennt was geschah`() {
        assertEquals("Erledigt", triageText("done", null))
        assertEquals("Archiviert", triageText("archive", null))
        assertEquals("Zurückgestellt für 60 min", triageText("snooze", 60))
        assertEquals("Zurückgestellt für 3 h", triageText("snooze", 180))
        assertEquals("Zurückgestellt für 7 Tage", triageText("snooze", 10080))
        assertEquals("Zurückgestellt für 22 h", triageText("snooze", 1320))
        assertEquals("Zurückgestellt für 1 Tag", triageText("snooze", 1440))
    }
}
