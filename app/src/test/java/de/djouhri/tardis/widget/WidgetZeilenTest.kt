package de.djouhri.tardis.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Das Widget sagt nie „0", wenn niemand gemessen hat: frisch installiert steht
 * dort „nicht gemessen", nicht „Keine Alarme".
 */
class WidgetZeilenTest {

    @Test
    fun `ohne messung keine ruhe`() {
        val z = widgetZeilen(WidgetStand())
        assertEquals("Alarme nicht gemessen", z.alarmZeile)
        assertEquals("Dienste nicht gemessen", z.diensteZeile)
        assertEquals(Ton.LEISE, z.alarmTon)
        assertEquals("noch kein Stand", z.fuss)
    }

    @Test
    fun `kritisch faerbt rot`() {
        val z = widgetZeilen(WidgetStand(alarme = 4, kritisch = 1, alarmeStandMs = 0, probleme = 2, diensteStandMs = 0))
        assertEquals("4 Alarme · 1 kritisch", z.alarmZeile)
        assertEquals(Ton.KRITISCH, z.alarmTon)
        assertEquals("2 Dienste brauchen dich", z.diensteZeile)
    }

    @Test
    fun `alles ruhig`() {
        val z = widgetZeilen(WidgetStand(alarme = 0, alarmeStandMs = 0, probleme = 0, diensteStandMs = 0))
        assertEquals("Keine Alarme", z.alarmZeile)
        assertEquals(Ton.GUT, z.diensteTon)
    }

    @Test
    fun `stummer rechner ist nicht in ordnung`() {
        val z = widgetZeilen(WidgetStand(probleme = 0, stumm = 1, diensteStandMs = 0))
        assertEquals("Dienste in Ordnung · 1 Rechner stumm", z.diensteZeile)
        assertEquals(Ton.LEISE, z.diensteTon)
    }

    @Test
    fun `fehlschlag behaelt den alten stand`() {
        val z = widgetZeilen(WidgetStand(alarme = 0, alarmeStandMs = 0, zuletztFehler = true))
        assertTrue(z.fuss.endsWith("· nicht erreichbar"))
        assertTrue(z.fuss.startsWith("Stand "))
    }
}
