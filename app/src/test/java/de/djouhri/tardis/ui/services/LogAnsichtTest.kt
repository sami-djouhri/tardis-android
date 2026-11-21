package de.djouhri.tardis.ui.services

import org.junit.Assert.assertEquals
import org.junit.Test

class LogAnsichtTest {

    @Test
    fun `stufen werden erkannt`() {
        assertEquals(LogStufe.FEHLER, stufeVon("2026-11-21 ERROR db weg"))
        assertEquals(LogStufe.FEHLER, stufeVon("""{"level": "error", "msg": "x"}"""))
        assertEquals(LogStufe.WARNUNG, stufeVon("level=warn msg=langsam"))
        assertEquals(LogStufe.LEISE, stufeVon("DEBUG cache hit"))
        assertEquals(LogStufe.INFO, stufeVon("INFO: GET /health 200"))
        // „errors=0" ist kein Fehler.
        assertEquals(LogStufe.INFO, stufeVon("INFO sync done errors=0"))
    }

    @Test
    fun `farbcodes raus, wiederholungen gebuendelt, stacktrace erbt die stufe`() {
        val roh = "\u001B[32mINFO\u001B[0m start\nINFO ping\nINFO ping\nINFO ping\n" +
            "Traceback (most recent call last):\n  File \"a.py\", line 1\nValueError: kaputt\nINFO weiter\n"
        val z = logZeilen(roh)
        assertEquals("INFO start", z[0].text)
        assertEquals(3, z[1].anzahl)
        assertEquals(LogStufe.FEHLER, z[2].stufe)
        assertEquals(LogStufe.FEHLER, z[3].stufe)
        assertEquals(LogStufe.FEHLER, z[4].stufe)
        assertEquals(LogStufe.INFO, z[5].stufe)
    }

    @Test
    fun `filter und suche`() {
        val z = logZeilen("INFO start\nWARN langsam\nERROR weg\nINFO Datenbank offen")
        assertEquals(4, z.gefiltert(LogFilter.ALLE, "").size)
        assertEquals(listOf("WARN langsam", "ERROR weg"), z.gefiltert(LogFilter.WARNUNG, "").map { it.text })
        assertEquals(listOf("ERROR weg"), z.gefiltert(LogFilter.FEHLER, "").map { it.text })
        assertEquals(listOf("INFO Datenbank offen"), z.gefiltert(LogFilter.ALLE, " datenbank ").map { it.text })
    }
}
