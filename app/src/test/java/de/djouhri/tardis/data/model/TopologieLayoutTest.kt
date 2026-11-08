package de.djouhri.tardis.data.model

import de.djouhri.tardis.data.demo.DemoData
import de.djouhri.tardis.data.model.tardis.TopoDienst
import de.djouhri.tardis.data.model.tardis.TopoWirt
import de.djouhri.tardis.data.model.tardis.TopologieResponse
import de.djouhri.tardis.data.model.tardis.auffaelligeDienste
import de.djouhri.tardis.data.model.tardis.kartenLayout
import de.djouhri.tardis.data.model.tardis.sucheDienste
import de.djouhri.tardis.data.model.tardis.wirtKopf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TopologieLayoutTest {

    /** Groesse wie live am 2026-11-07: zwei grosse Wirte, ein paar kleine, viele leere. */
    private val echteGroesse = run {
        fun viele(wirt: String, n: Int, gruppen: Int) = (1..n).map { TopoDienst("$wirt-$it", "$wirt-$it", wirt, gruppe = "G${it % gruppen}") }
        val dienste = viele("host", 112, 9) + viele("node1", 108, 8) + viele("node2", 12, 1) +
            viele("jetson", 4, 1) + viele("netcup", 4, 1) + viele("geist", 3, 1)
        val wirte = listOf("host", "node1", "node2", "jetson", "netcup") .map { TopoWirt(it) } +
            (1..12).map { TopoWirt("leer$it") }
        TopologieResponse(wirte = wirte, dienste = dienste)
    }

    @Test
    fun jederDienstHatEinenPlatzImEigenenWirt() {
        for (t in listOf(DemoData.topologie, echteGroesse)) {
            val l = kartenLayout(t)
            assertEquals(t.dienste.size, l.dienste.size)
            t.dienste.forEach { d ->
                val p = l.dienste.getValue(d.id)
                val r = l.wirte.getValue(d.wirt)
                assertTrue("${d.id} liegt ausserhalb von ${d.wirt}", p.x > r.x && p.x < r.x + r.b && p.y > r.y && p.y < r.y + r.h)
            }
        }
    }

    @Test
    fun wirteUeberlappenNichtUndBleibenInDerBreite() {
        val l = kartenLayout(echteGroesse)
        val liste = l.wirte.values.toList()
        liste.forEachIndexed { i, a ->
            assertTrue(a.x >= 0f && a.x + a.b <= l.breite)
            liste.drop(i + 1).forEach { b -> assertFalse("$a ueberlappt $b", a.ueberlappt(b)) }
        }
    }

    @Test
    fun dienstOhneBekanntenWirtBekommtEinenKasten() {
        val l = kartenLayout(echteGroesse)
        assertTrue("geist" in l.wirte)
    }

    @Test
    fun keineZweiDiensteAufDemselbenPunkt() {
        val l = kartenLayout(echteGroesse)
        assertEquals(l.dienste.size, l.dienste.values.toSet().size)
    }

    private val klein = listOf(
        TopoDienst("a", "Wiki", "alpha", gruppe = "Wissen", zustand = "laeuft"),
        TopoDienst("b", "git-server", "beta", gruppe = "Werkzeug", zustand = "unbekannt"),
        TopoDienst("c", "legit", "alpha", gruppe = "Werkzeug", zustand = "aus"),
        TopoDienst("d", "Backup", "beta", zustand = "bewusst_aus"),
        TopoDienst("e", "anzeige", "alpha", zustand = "aus"),
    )

    @Test
    fun sucheFindetNameWirtUndGruppeOhneGrossKlein_NamensanfangZuerst() {
        assertEquals(listOf("git-server", "legit"), sucheDienste(klein, "GIT").map { it.label })
        assertEquals(listOf("anzeige", "legit", "Wiki"), sucheDienste(klein, "alpha").map { it.label })
        assertEquals(listOf("git-server", "legit"), sucheDienste(klein, "werkzeug").map { it.label })
        assertTrue(sucheDienste(klein, "   ").isEmpty())
    }

    @Test
    fun auffaelligeRotVorGelbDannNachWirt() {
        assertEquals(listOf("anzeige", "legit", "git-server"), auffaelligeDienste(klein).map { it.label })
    }

    @Test
    fun wirtKopfZaehltUndNenntLastNurWennGemessen() {
        val k = wirtKopf(TopoWirt("alpha", online = true, cpu = 23.7, ram = 61.2), klein.filter { it.wirt == "alpha" })
        assertEquals(3, k.dienste)
        assertEquals(2, k.auffaellig)
        assertEquals("CPU 23 % · RAM 61 %", k.last)
        val fremd = wirtKopf(null, klein.filter { it.wirt == "beta" })
        assertEquals(null, fremd.last)
        assertEquals(null, fremd.online)
        assertEquals(1, fremd.auffaellig)
    }
}
