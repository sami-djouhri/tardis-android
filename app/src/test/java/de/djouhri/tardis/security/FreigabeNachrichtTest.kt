package de.djouhri.tardis.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Der unterschriebene Text muss Zeichen fuer Zeichen zu dem passen, den der
 * Zielwirt nachbaut (docker/pull-deploy/agent/freigabe.py) und den das Gateway
 * vorab prueft (mobile-api/app/routers/freigaben.py). Derselbe feste Vektor
 * steht in beiden Python-Testsuiten; aendert sich eine Seite, bricht sie hier.
 */
class FreigabeNachrichtTest {

    @Test
    fun nachrichtIstMitWirtUndGatewayAbgestimmt() {
        val erwartet = ("homelab-pull-deploy-v1\nwirt=node1\nprojekt=tanko\nrepo=clients/tanko\n" +
            "commit=" + "a".repeat(40) + "\nausgestellt=1800000000\n").toByteArray(Charsets.UTF_8)
        assertArrayEquals(erwartet, FreigabeSchluessel.nachricht("node1", "tanko", "clients/tanko", "a".repeat(40), 1800000000L))
    }

    /** Derselbe Vektor steht in befehls-freigabe/tests/test_laeufer.py und mobile-api/tests/test_befehle.py. */
    @Test
    fun befehlNachrichtIstMitLaeuferUndGatewayAbgestimmt() {
        val erwartet = ("homelab-befehl-v1\nwirt=host\nid=a-1\nsha256=" + "0".repeat(64) + "\nausgestellt=5\n")
            .toByteArray(Charsets.UTF_8)
        assertArrayEquals(erwartet, FreigabeSchluessel.befehlNachricht("a-1", "0".repeat(64), 5L))
    }

    @Test
    fun fingerabdruckIstSha256KleinHex() {
        // SHA-256 von "abc", bekannter Pruefwert
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            FreigabeSchluessel.fingerabdruck("abc".toByteArray()),
        )
    }

    @Test
    fun gruppiertInVierern() {
        assertEquals("ba78 16bf 8f01", FreigabeSchluessel.gruppiert("ba7816bf8f01"))
    }
}
