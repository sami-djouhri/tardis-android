package de.djouhri.tardis.data.model

import de.djouhri.tardis.data.model.tardis.BackupWirt
import de.djouhri.tardis.data.model.tardis.Gegenprobe
import de.djouhri.tardis.data.model.tardis.gegenprobeText
import de.djouhri.tardis.data.model.tardis.verpasstText
import de.djouhri.tardis.data.model.tardis.OwnerSchritt
import de.djouhri.tardis.data.model.tardis.OwnerSchritteResponse
import de.djouhri.tardis.data.model.tardis.stundenText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class OwnerTest {
    private val heute = LocalDate.of(2026, 11, 17)

    @Test
    fun `dran ist heute, frueher oder ohne tag, nie wartend oder gemeldet`() {
        val a = OwnerSchritteResponse(
            gemessen = true,
            schritte = listOf(
                OwnerSchritt("ohne", "ohne Tag"),
                OwnerSchritt("heute", "heute", tag = "2026-11-17"),
                OwnerSchritt("gestern", "gestern", tag = "2026-11-16"),
                OwnerSchritt("morgen", "morgen", tag = "2026-11-18"),
                OwnerSchritt("wartet", "wartet", wartet = "Gegenprobe steht aus"),
                OwnerSchritt("gemeldet", "gemeldet", gemeldet = true),
                OwnerSchritt("kaputt", "kaputter Tag", tag = "18.11."),
            ),
        )
        assertEquals(listOf("ohne", "heute", "gestern", "kaputt"), a.dran(heute).map { it.id })
    }

    @Test
    fun `gruppen trennen verpasst, heute, ohne termin, geplant und zurueck`() {
        val a = OwnerSchritteResponse(
            gemessen = true,
            schritte = listOf(
                OwnerSchritt("ohne", "ohne Tag"),
                OwnerSchritt("heute", "heute", tag = "2026-11-17"),
                OwnerSchritt("vorgestern", "vorgestern", tag = "2026-11-15"),
                OwnerSchritt("gestern", "gestern", tag = "2026-11-16"),
                OwnerSchritt("morgen", "morgen", tag = "2026-11-18"),
                OwnerSchritt("wartet", "wartet", tag = "2026-11-10", wartet = "Gegenprobe steht aus"),
                OwnerSchritt("gemeldet", "gemeldet", gemeldet = true),
                OwnerSchritt("kaputt", "kaputter Tag", tag = "18.11."),
            ),
        )
        val g = a.gruppen(heute)
        assertEquals(listOf("vorgestern", "gestern"), g.verpasst.map { it.id })
        assertEquals(listOf("heute"), g.heute.map { it.id })
        assertEquals(listOf("ohne", "kaputt"), g.ohneTermin.map { it.id })
        assertEquals(listOf("morgen"), g.geplant.map { it.id })
        assertEquals(listOf("wartet", "gemeldet"), g.zurueck.map { it.id })
        // dran bleibt die Summe der drei ersten Abschnitte (Kurzkarte, Widget).
        assertEquals(g.verpasst.size + g.heute.size + g.ohneTermin.size, a.dran(heute).size)
    }

    @Test
    fun `verpasst nennt tag und abstand, nur in der vergangenheit`() {
        // Wochentag je nach JDK „Mo" oder „Mo.", deshalb nur Anfang und Ende pruefen.
        val gestern = verpasstText(OwnerSchritt("a", "a", tag = "2026-11-16"), heute)!!
        assertTrue(gestern, gestern.startsWith("verpasst: Mo") && gestern.endsWith(" 16.11., seit 1 Tag"))
        val drei = verpasstText(OwnerSchritt("a", "a", tag = "2026-11-14"), heute)!!
        assertTrue(drei, drei.endsWith(" 14.11., seit 3 Tagen"))
        assertEquals(null, verpasstText(OwnerSchritt("a", "a", tag = "2026-11-17"), heute))
        assertEquals(null, verpasstText(OwnerSchritt("a", "a"), heute))
    }

    @Test
    fun `gegenprobe sagt wer prueft, seit wann und warum ein schritt zurueck ist`() {
        val jetzt = LocalDateTime.of(2026, 11, 17, 12, 0)
        fun iso(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString()
        val gemeldet = "Owner meldet gemacht (15.11. 20:10), Gegenprobe steht aus"
        val hand = OwnerSchritt("h", "h", wartet = gemeldet, gemeldetAm = iso(jetzt.minusDays(2)),
            gegenprobe = Gegenprobe(auto = false))
        assertEquals("Gegenprobe von Hand, gemeldet vor 2 Tagen.", gegenprobeText(hand, jetzt))
        val auto = OwnerSchritt("a", "a", wartet = gemeldet, gemeldetAm = iso(jetzt.minusHours(1)),
            gegenprobe = Gegenprobe(auto = true, zustand = "rot", befund = "Container briefkasten: exited",
                zeit = iso(LocalDateTime.of(2026, 11, 17, 11, 30))))
        assertEquals("Gegenprobe läuft automatisch, gemeldet heute. Zuletzt 17.11. 11:30 rot: Container briefkasten: exited.",
            gegenprobeText(auto, jetzt))
        val neu = OwnerSchritt("n", "n", wartet = gemeldet, gegenprobe = Gegenprobe(auto = true))
        assertEquals("Gegenprobe läuft automatisch. Noch nicht geprüft.", gegenprobeText(neu, jetzt))
        val zurueck = OwnerSchritt("z", "z", gegenprobe = Gegenprobe(auto = true, zustand = "rot", befund = "829 h alt",
            zeit = iso(LocalDateTime.of(2026, 11, 17, 11, 30))))
        assertTrue(zurueck.gegenprobeRot)
        assertEquals("Gegenprobe rot 17.11. 11:30: 829 h alt. Bitte noch einmal ansehen.", gegenprobeText(zurueck, jetzt))
        // Ohne Meldung und ohne roten Befund gibt es nichts zu sagen; Vorbedingung ist keine Meldung.
        assertEquals(null, gegenprobeText(OwnerSchritt("o", "o"), jetzt))
        assertEquals(null, gegenprobeText(OwnerSchritt("v", "v", wartet = "wartet auf eine Vorbedingung"), jetzt))
    }

    @Test
    fun `backup-wirt auffaellig nur bei befund, nie bei bewusst aus oder ungemessen`() {
        assertTrue(BackupWirt("a", ok = false).istAuffaellig)
        assertTrue(BackupWirt("a", ok = true, probeOk = false).istAuffaellig)
        assertFalse(BackupWirt("a", ok = true, probeOk = null).istAuffaellig)
        assertFalse(BackupWirt("a", ok = false, bekanntAus = true).istAuffaellig)
    }

    @Test
    fun `stunden in lesbarer einheit`() {
        assertEquals("16 h", stundenText(16.4))
        assertEquals("31 Tage", stundenText(752.9))
        assertEquals("31 Tagen", stundenText(752.9, dativ = true))
        assertEquals(null, stundenText(null))
    }
}
