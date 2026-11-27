package de.djouhri.tardis.data.repository

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntkoppelnTest {

    @Test
    fun `widerruf laeuft vor dem lokalen aufraeumen`() = runTest {
        val protokoll = mutableListOf<String>()

        entkoppelAblauf(
            widerrufen = { protokoll += "widerruf"; Result.success(true) },
            lokalRaeumen = { protokoll += "lokal" },
        )

        // Die Gateway-Route laeuft dort durch `require_device` und braucht das
        // noch gueltige Token. Umgekehrt waere es eine 401, also ein Widerruf,
        // der nichts widerruft.
        assertEquals(listOf("widerruf", "lokal"), protokoll)
    }

    @Test
    fun `lokal wird auch dann geraeumt wenn der widerruf scheitert`() = runTest {
        val protokoll = mutableListOf<String>()

        val ergebnis = entkoppelAblauf(
            widerrufen = {
                protokoll += "widerruf"
                Result.failure(IllegalStateException("kein VPN"))
            },
            lokalRaeumen = { protokoll += "lokal" },
        )

        // Ohne VPN oder bei totem Gateway muss man die App loswerden koennen,
        // sonst sitzt man in einer Kopplung fest, die sich nicht aufloesen laesst.
        assertEquals(listOf("widerruf", "lokal"), protokoll)
        assertTrue(ergebnis.isFailure)
    }

    @Test
    fun `der fehlschlag wird gemeldet und nicht verschluckt`() = runTest {
        val ergebnis = entkoppelAblauf(
            widerrufen = { Result.failure(IllegalStateException("Zeitablauf")) },
            lokalRaeumen = { },
        )

        // Das ist der Kern: ein stilles Scheitern erzeugt die Annahme, das
        // Geraet sei draussen. Genau diese Annahme soll ein Widerruf
        // ausschliessen, also darf der Fehler nicht hier versanden.
        assertTrue(ergebnis.isFailure)
        assertEquals("Zeitablauf", ergebnis.exceptionOrNull()?.message)
    }

    @Test
    fun `ein gelungener widerruf ohne betroffene token meldet false statt erfolg`() = runTest {
        val ergebnis = entkoppelAblauf(
            widerrufen = { Result.success(false) },
            lokalRaeumen = { },
        )

        assertTrue(ergebnis.isSuccess)
        assertFalse(ergebnis.getOrThrow())
    }

    @Test
    fun `null widerrufene token sind kein fehler sondern eine auskunft`() {
        // Das Gateway antwortet mit 200 und `widerrufen: 0`, wenn die Kennung
        // unbekannt oder schon widerrufen war. Ein 404 waere dort eine Auskunft
        // darueber, welche Kennungen existieren. Die Oberflaeche nennt deshalb
        // die Zahl, statt einen Erfolg zu behaupten.
        assertEquals("Nichts zu widerrufen (unbekannt oder schon widerrufen).", widerrufHinweis(0))
        assertEquals("1 Token widerrufen.", widerrufHinweis(1))
        // Mehr als eines ist gewollt: ein zweimal gekoppeltes Geraet haette
        // sonst nach dem Abmelden noch ein zweites gueltiges Token.
        assertEquals("3 Token widerrufen.", widerrufHinweis(3))
    }
}
