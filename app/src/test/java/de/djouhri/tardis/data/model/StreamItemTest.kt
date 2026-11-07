package de.djouhri.tardis.data.model

import de.djouhri.tardis.data.model.tardis.gebuendelt
import de.djouhri.tardis.data.model.tardis.StreamItem
import de.djouhri.tardis.util.seitText
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * `/v1/stream`. Rohdaten nach `life-ops-api/app/api/routes_stream.py`
 * (`_row_to_stream_item`); `occurred_at` ist dort eine naive Spalte.
 */
class StreamItemTest {

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }

    @Test
    fun `liest die felder des dienstes`() {
        val liste = json.decodeFromString<List<StreamItem>>(
            """[{"external_id": "x1", "source": "briefkasten", "kind": "mail", "event_type": "mail.received",
                 "title": "Brief", "body": null, "severity": "info", "occurred_at": "2026-10-31T18:00:00",
                 "action_url": null, "refs": {"event_id": 4, "entity_ref": null, "parent_external_id": null}}]""",
        )
        val e = liste.single()
        assertEquals("x1", e.externalId)
        assertEquals("mail", e.kind)
        assertEquals("mail.received", e.eventType)
        assertNull(e.body)
    }

    @Test
    fun `naiver zeitpunkt gilt als utc`() {
        val e = StreamItem(occurredAt = "2026-10-31T18:00:00")
        assertEquals("2026-10-31T18:00:00Z", e.zeitpunktUtc)
        assertEquals("seit 30 min", seitText(e.zeitpunktUtc, jetzt = Instant.parse("2026-10-31T18:30:00Z")))
    }

    @Test
    fun `vorhandener offset bleibt`() {
        assertEquals("2026-10-31T19:00:00+01:00", StreamItem(occurredAt = "2026-10-31T19:00:00+01:00").zeitpunktUtc)
        assertEquals("2026-10-31T18:00:00Z", StreamItem(occurredAt = "2026-10-31T18:00:00Z").zeitpunktUtc)
        assertEquals("2026-10-31T18:00:00Z", StreamItem(occurredAt = "2026-10-31 18:00:00").zeitpunktUtc)
        assertNull(StreamItem(occurredAt = null).zeitpunktUtc)
    }

    @Test
    fun gleicheMeldungWirdGebuendeltMitAeltestemZeitpunkt() {
        val liste = listOf(
            StreamItem(externalId = "a", source = "kg", eventType = "suche", title = "Suche", occurredAt = "2026-11-07T10:00:00Z"),
            StreamItem(externalId = "b", source = "news", eventType = "neu", title = "Artikel", occurredAt = "2026-11-07T09:00:00Z"),
            StreamItem(externalId = "c", source = "kg", eventType = "suche", title = "Suche", occurredAt = "2026-11-07T08:00:00Z"),
            StreamItem(externalId = "d", source = "andere", eventType = "suche", title = "Suche", occurredAt = "2026-11-07T07:00:00Z"),
        )
        val zeilen = liste.gebuendelt()
        assertEquals(listOf("a", "b", "d"), zeilen.map { it.item.externalId })
        assertEquals(2, zeilen[0].anzahl)
        assertEquals("2026-11-07T08:00:00Z", zeilen[0].aeltestUtc)
        assertEquals(null, zeilen[1].aeltestUtc)
    }
}
