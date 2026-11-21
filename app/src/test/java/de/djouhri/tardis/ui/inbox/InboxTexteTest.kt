package de.djouhri.tardis.ui.inbox

import de.djouhri.tardis.data.model.tardis.InboxItem
import de.djouhri.tardis.data.model.tardis.InboxLink
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InboxTexteTest {

    private fun item(vararg quellen: String, thema: String? = "security_vuln", anzahl: Int? = null) = InboxItem(
        externalId = "x",
        thema = thema,
        sourceCount = anzahl,
        links = quellen.map { InboxLink(quelle = it, url = "https://$it.example.invalid/a") },
    )

    @Test
    fun `mehrere quellen mit thema`() {
        assertEquals("Sicherheitslücke · 3 Quellen: A, B, C", quellenZeile(item("A", "B", "C", anzahl = 3)))
    }

    @Test
    fun `eine quelle ohne zahl`() {
        assertEquals("Technik · A", quellenZeile(item("A", thema = "tech_infra")))
    }

    @Test
    fun `nichts bekannt ergibt null`() {
        assertNull(quellenZeile(InboxItem(externalId = "x")))
    }

    @Test
    fun `treffer warnt, kein treffer nicht`() {
        assertTrue(bezugTrifft("Im Haus: Java auf gamehost (mc-velocity)"))
        assertFalse(bezugTrifft("Im Haus: kein Treffer (WordPress läuft hier nicht)"))
        assertFalse(bezugTrifft("Im Haus: nicht geprüft (Bestand fehlt)"))
        assertFalse(bezugTrifft(null))
    }

    @Test
    fun `nur http links und ohne dubletten`() {
        val i = InboxItem(
            externalId = "x",
            links = listOf(
                InboxLink("A", "https://a.example.invalid/1"),
                InboxLink("A2", "https://a.example.invalid/1"),
                InboxLink("B", "javascript:alert(1)"),
            ),
        )
        assertEquals(listOf("A"), oeffenbareLinks(i).map { it.quelle })
    }

    @Test
    fun `brief faellt auf action url zurueck`() {
        val i = InboxItem(externalId = "x", actionUrl = "https://ocr.example.invalid/1")
        assertEquals(1, oeffenbareLinks(i).size)
    }
}
