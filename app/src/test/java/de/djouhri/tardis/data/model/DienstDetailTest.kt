package de.djouhri.tardis.data.model

import de.djouhri.tardis.data.model.tardis.DienstDetail
import de.djouhri.tardis.data.model.tardis.Wechsel
import de.djouhri.tardis.data.model.tardis.text
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `/v1/ops/services/{host}/{name}/detail`, Rohform nach `mobile-api/app/routers/ops.py`. */
class DienstDetailTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    @Test
    fun `liest die vier teile`() {
        val d = json.decodeFromString<DienstDetail>(
            """{"host": "a", "name": "b",
                "container": {"gefunden": true, "status": "running", "gestartet": "2026-11-01T16:44:08Z",
                              "beendet": null, "exit_code": null, "neustarts": 0, "erstellt": "2026-11-01T16:44:05Z"},
                "verbrauch": {"gemessen": true, "cpu_kerne": 0.0077, "ram_bytes": 92766208.0,
                              "ram_grenze_bytes": 268435456.0, "verlauf_cpu": [[1793600000, 0.01]],
                              "verlauf_ram": [[1793600000, 9.2e7], [1793601800, 9.3e7]], "schritt_s": 1800},
                "wirt": {"gemessen": true, "cpu_prozent": 73.2, "ram_prozent": 40.1, "platte_prozent": 52.5},
                "verfuegbarkeit": {"erfasst": true, "uptime_24h": 100.0, "zuletzt_beendet": null,
                                   "abtastung_s": 120, "ereignisse": [{"alt": "running", "neu": "exited", "zeit": "2026-11-02T22:00:00Z"}]},
                "alarme_gemessen": true, "alarme": []}""",
        )
        assertEquals(0, d.container.neustarts)
        assertEquals(2, d.verbrauch.verlaufRam.size)
        assertEquals(0.3456, d.verbrauch.ramAnteil!!, 0.001)
        assertEquals(73.2, d.wirt.cpuProzent!!, 0.0)
        assertEquals("beendet", d.verfuegbarkeit.ereignisse.single().text())
        assertTrue(d.alarmeGemessen)
    }

    @Test
    fun `ohne grenze kein anteil`() {
        val d = json.decodeFromString<DienstDetail>(
            """{"verbrauch": {"gemessen": true, "ram_bytes": 1.0, "ram_grenze_bytes": null}}""",
        )
        assertNull(d.verbrauch.ramAnteil)
    }

    @Test
    fun `fehlende teile heissen nicht gemessen`() {
        val d = json.decodeFromString<DienstDetail>("""{"host": "a", "name": "b"}""")
        assertFalse(d.verbrauch.gemessen)
        assertFalse(d.verfuegbarkeit.erfasst)
        assertFalse(d.alarmeGemessen)
    }

    @Test
    fun `wechsel in worten`() {
        assertEquals("entfernt (Neuaufbau)", Wechsel("running", "removed").text())
        assertEquals("wieder hochgekommen", Wechsel("restarting", "running").text())
        assertEquals("gestartet", Wechsel("exited", "running").text())
    }
}
