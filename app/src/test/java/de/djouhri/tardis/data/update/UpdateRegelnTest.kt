package de.djouhri.tardis.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateRegelnTest {

    @Test
    fun `nach sechs Stunden wird wieder nachgesehen`() {
        val jetzt = 100_000_000L
        assertTrue(UpdateRegeln.darfPruefen(jetzt, jetzt - UpdateRegeln.PRUEF_ABSTAND_MS))
    }

    @Test
    fun `kurz nach der letzten Pruefung nicht noch einmal`() {
        val jetzt = 100_000_000L
        assertFalse(UpdateRegeln.darfPruefen(jetzt, jetzt - 60_000L))
    }

    @Test
    fun `ohne je geprueft zu haben wird sofort nachgesehen`() {
        assertTrue(UpdateRegeln.darfPruefen(100_000_000L, 0L))
    }

    @Test
    fun `nur eine hoehere Nummer ist ein Update`() {
        assertTrue(UpdateRegeln.istNeuer(angeboten = 5, installiert = 4))
    }

    @Test
    fun `Gleichstand ist kein Update`() {
        assertFalse(UpdateRegeln.istNeuer(angeboten = 4, installiert = 4))
    }

    @Test
    fun `ein Downgrade wird nicht angeboten`() {
        // Android lehnt es ohnehin ab; ein Angebot, das nicht installierbar
        // ist, sieht wie ein Fehler der App aus.
        assertFalse(UpdateRegeln.istNeuer(angeboten = 3, installiert = 4))
    }

    @Test
    fun `gleicher Hash besteht`() {
        assertEquals(
            HashUrteil.PASST,
            UpdateRegeln.hashUrteil("ABC123", "abc123"),
        )
    }

    @Test
    fun `Leerzeichen um den Hash aendern nichts`() {
        assertEquals(HashUrteil.PASST, UpdateRegeln.hashUrteil("  abc123  ", "abc123"))
    }

    @Test
    fun `abweichender Hash faellt durch`() {
        assertEquals(HashUrteil.ABWEICHUNG, UpdateRegeln.hashUrteil("abc123", "abc124"))
    }

    @Test
    fun `ohne angekuendigten Hash gibt es kein Urteil`() {
        // Der wichtigste Fall: "nicht verglichen" darf nicht wie "geprueft"
        // aussehen. Sonst steht ein Haekchen da, das nichts bedeutet.
        assertEquals(HashUrteil.OHNE_VERGLEICH, UpdateRegeln.hashUrteil(null, "abc123"))
        assertEquals(HashUrteil.OHNE_VERGLEICH, UpdateRegeln.hashUrteil("", "abc123"))
        assertEquals(HashUrteil.OHNE_VERGLEICH, UpdateRegeln.hashUrteil("   ", "abc123"))
    }
}
