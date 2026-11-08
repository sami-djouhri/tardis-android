package de.djouhri.tardis.data.model

import de.djouhri.tardis.data.model.tardis.Linse
import de.djouhri.tardis.data.model.tardis.TopoDienst
import de.djouhri.tardis.data.model.tardis.TopoSchnappschuss
import de.djouhri.tardis.data.model.tardis.TopoVerbindung
import de.djouhri.tardis.data.model.tardis.TopologieResponse
import de.djouhri.tardis.data.model.tardis.abhaengigkeitsKette
import de.djouhri.tardis.data.model.tardis.aenderungen
import de.djouhri.tardis.data.model.tardis.linsenWert
import de.djouhri.tardis.data.model.tardis.mitBetroffen
import de.djouhri.tardis.data.model.tardis.zeitpunktText
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ausfall-Kette, Mitbetroffene, Aenderungen seit dem letzten Blick, Linsen (seit 2.0.20). */
class TopologieKetteTest {

    private fun dep(von: String, nach: String) = TopoVerbindung(von, nach, "depends")

    // web -> api -> db, cron -> api, api -> cache; dazu eine LAN-Linie, die nicht zaehlt.
    private val linien = listOf(
        dep("web", "api"), dep("api", "db"), dep("cron", "api"), dep("api", "cache"),
        TopoVerbindung("web", "proxy", "lan"),
    )

    @Test
    fun ketteKenntBeideRichtungenDirektUndDahinter() {
        val k = abhaengigkeitsKette("api", linien)
        assertEquals(setOf("db", "cache"), k.braucht)
        assertEquals(emptySet<String>(), k.brauchtWeiter)
        assertEquals(setOf("web", "cron"), k.betroffen)
        val vonDb = abhaengigkeitsKette("db", linien)
        assertEquals(setOf("api"), vonDb.betroffen)
        assertEquals(setOf("web", "cron"), vonDb.betroffenWeiter)
        val vonWeb = abhaengigkeitsKette("web", linien)
        assertEquals(setOf("api"), vonWeb.braucht)
        assertEquals(setOf("db", "cache"), vonWeb.brauchtWeiter)
        // Die LAN-Linie ist keine Abhaengigkeit.
        assertTrue("proxy" !in vonWeb.alle)
    }

    @Test
    fun zyklusHaengtNichtUndEnthaeltDenDienstNichtSelbst() {
        val k = abhaengigkeitsKette("a", listOf(dep("a", "b"), dep("b", "c"), dep("c", "a")))
        assertEquals(setOf("b"), k.braucht)
        assertEquals(setOf("c"), k.brauchtWeiter)
        assertTrue("a" !in k.alle)
    }

    @Test
    fun mitbetroffenSindNurLaufendeHinterEinemAusgefallenen() {
        val dienste = listOf(
            TopoDienst("web", "web", "h", zustand = "laeuft"),
            TopoDienst("api", "api", "h", zustand = "laeuft"),
            TopoDienst("db", "db", "h", zustand = "aus"),
            TopoDienst("cron", "cron", "h", zustand = "bewusst_aus"),
            TopoDienst("cache", "cache", "h", zustand = "unbekannt"),
        )
        // db aus -> api und web betroffen; cron ist bewusst aus, zaehlt nicht;
        // cache ist nur unbekannt und macht niemanden mitbetroffen.
        assertEquals(setOf("api", "web"), mitBetroffen(dienste, linien))
    }

    @Test
    fun aenderungenSeitDemLetztenBlick() {
        val alt = TopoSchnappschuss(1000L, mapOf("a" to "laeuft", "b" to "aus", "c" to "laeuft", "weg" to "laeuft", "d" to "bewusst_aus"))
        val jetzt = TopologieResponse(
            dienste = listOf(
                TopoDienst("a", "a", "h", zustand = "aus"),
                TopoDienst("b", "b", "h", zustand = "laeuft"),
                TopoDienst("c", "c", "h", zustand = "laeuft"),
                TopoDienst("d", "d", "h", zustand = "unbekannt"),
                TopoDienst("neu", "neu", "h", zustand = "laeuft"),
            ),
        )
        val ae = aenderungen(alt, jetzt)
        assertEquals(listOf("a", "d"), ae.neuAuffaellig)
        assertEquals(listOf("b"), ae.wiederGut)
        assertEquals(listOf("neu"), ae.neu)
        assertEquals(listOf("weg"), ae.weg)
        assertEquals(setOf("a", "b", "d", "neu"), ae.sichtbar)
        assertEquals(1000L, ae.seitMs)
        assertTrue(aenderungen(TopoSchnappschuss(0, mapOf("c" to "laeuft")), TopologieResponse(dienste = listOf(TopoDienst("c", "c", "h", zustand = "laeuft")))).leer)
    }

    @Test
    fun zeitpunktHeuteGesternSonstDatum() {
        val zone = ZoneId.of("Europe/Berlin")
        val heute = LocalDate.of(2026, 11, 8)
        fun ms(tag: Int, h: Int, m: Int) = ZonedDateTime.of(2026, 11, tag, h, m, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals("heute 14:02", zeitpunktText(ms(8, 14, 2), heute, zone))
        assertEquals("gestern 09:30", zeitpunktText(ms(7, 9, 30), heute, zone))
        assertEquals("31.10. 23:59", zeitpunktText(ZonedDateTime.of(2026, 10, 31, 23, 59, 0, 0, zone).toInstant().toEpochMilli(), heute, zone))
    }

    @Test
    fun linsenOrdnenEinUndFangenUnbekanntes() {
        val d = TopoDienst("x", "x", "h", zustand = "aus", klasse = "core", erreichbarkeit = "geraete")
        assertEquals("aus", linsenWert(d, Linse.ZUSTAND))
        assertEquals("geraete", linsenWert(d, Linse.ERREICHBARKEIT))
        assertEquals("CORE", linsenWert(d, Linse.KLASSE))
        val leer = TopoDienst("y", "y", "h", klasse = "ghost")
        assertEquals("unbekannt", linsenWert(leer, Linse.ERREICHBARKEIT))
        assertEquals("sonst", linsenWert(leer, Linse.KLASSE))
    }

    /** Dieselbe Json-Einstellung wie AppModule: neue Felder kommen an, alte Antworten bleiben lesbar. */
    @Test
    fun neueFelderKommenAnUndAlteAntwortBleibtLesbar() {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; isLenient = true }
        val neu = json.decodeFromString(
            TopologieResponse.serializer(),
            """{"wirte":[{"id":"h","ip":"192.0.2.1","temp":61.1,"load":2.5,"backup_alter_h":30.0,"backup_alt":true,
               "drift_geister":1,"drift_fehlend":0,"container":91}],
               "dienste":[{"id":"a","label":"A","wirt":"h","zustand":"laeuft","erreichbarkeit":"oeffentlich","port":8150,"url":"https://a.example.invalid/"}],
               "verbindungen":[{"von":"a","nach":"h","art":"lan","label":"HTTPS"}]}""",
        )
        val w = neu.wirte.single()
        assertEquals(30.0, w.backupAlterH!!, 0.0)
        assertEquals(true, w.backupAlt)
        assertEquals(1, w.driftGeister)
        assertEquals(91, w.container)
        assertEquals("oeffentlich", neu.dienste.single().erreichbarkeit)
        assertEquals(8150, neu.dienste.single().port)
        assertEquals("HTTPS", neu.verbindungen.single().label)
        val alt = json.decodeFromString(
            TopologieResponse.serializer(),
            """{"wirte":[{"id":"h"}],"dienste":[{"id":"a","label":"A","wirt":"h","zustand":"laeuft"}],"verbindungen":[{"von":"a","nach":"h","art":"lan"}]}""",
        )
        assertNull(alt.wirte.single().backupAlterH)
        assertNull(alt.dienste.single().url)
    }
}
