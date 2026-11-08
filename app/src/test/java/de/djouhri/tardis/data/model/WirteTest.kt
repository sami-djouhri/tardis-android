package de.djouhri.tardis.data.model

import de.djouhri.tardis.data.model.tardis.WirteResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `/v1/ops/wirte`, Rohform nach `mobile-api/app/routers/wirte.py`. */
class WirteTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    @Test
    fun `liest wirte mit luecken und gast`() {
        val a = json.decodeFromString<WirteResponse>(
            """{"gemessen": true, "fehler": null, "fenster_minuten": 60, "start": 1793300000, "schritt": 40,
                "wirte": [
                  {"name": "host", "node": null, "erreichbar": true, "cpu": 51.0, "ram": 75.4, "platte": 70.4,
                   "load": 3.68, "kerne": 4, "temp": 56.8,
                   "verlauf": {"cpu": [40.0, null, 51.0], "ram": [75.0], "platte": [], "load": [3.1]}},
                  {"name": "xtts", "node": "hypervisor3", "erreichbar": false, "cpu": null, "ram": null,
                   "platte": null, "load": null, "kerne": null, "temp": null,
                   "verlauf": {"cpu": [], "ram": [], "platte": [], "load": []}}
                ]}""",
        )
        assertTrue(a.gemessen)
        assertEquals(40, a.schritt)
        val j = a.wirte[0]
        assertEquals(listOf(40.0, null, 51.0), j.verlauf.cpu)
        assertEquals(75.4, j.engpass!!, 0.0)
        assertFalse(j.istGast)
        val x = a.wirte[1]
        assertTrue(x.istGast)
        assertFalse(x.erreichbar)
        assertNull(x.engpass)
    }

    @Test
    fun `nicht gemessen ist keine leere flotte`() {
        val a = json.decodeFromString<WirteResponse>("""{"gemessen": false, "fehler": "weg", "wirte": []}""")
        assertFalse(a.gemessen)
        assertEquals("weg", a.fehler)
    }
}
