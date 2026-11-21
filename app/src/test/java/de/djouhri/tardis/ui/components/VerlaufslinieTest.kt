package de.djouhri.tardis.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class VerlaufslinieTest {
    private val berlin = ZoneId.of("Europe/Berlin")
    private fun s(t: String) = LocalDateTime.parse(t).atZone(berlin).toEpochSecond()

    @Test
    fun `eine stunde bekommt runde viertelstunden`() {
        val m = zeitMarken(s("2026-11-21T13:07"), s("2026-11-21T14:07"), zone = berlin)
        assertEquals(listOf("13:15", "13:30", "13:45", "14:00"), m.map { uhrzeit(it, 3600, berlin) })
    }

    @Test
    fun `ein tag bekommt marken an der ortszeit`() {
        val m = zeitMarken(s("2026-11-20T14:10"), s("2026-11-21T14:10"), zone = berlin)
        assertTrue(m.size in 2..4)
        m.forEach { assertEquals(0, LocalDateTime.ofEpochSecond(it, 0, berlin.rules.getOffset(java.time.Instant.ofEpochSecond(it))).minute) }
    }

    @Test
    fun `leere oder verkehrte spanne gibt keine marken`() {
        assertEquals(emptyList<Long>(), zeitMarken(100, 100))
        assertEquals(emptyList<Long>(), zeitMarken(200, 100))
    }

    @Test
    fun `ueber einen tag mit wochentag`() {
        assertEquals("Sa 14 Uhr", uhrzeit(s("2026-11-21T14:00"), 3 * 86400, berlin))
    }
}
