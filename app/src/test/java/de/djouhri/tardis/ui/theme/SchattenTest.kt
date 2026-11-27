package de.djouhri.tardis.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import de.djouhri.tardis.navigation.kommtNeuerAlarmDazu
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchattenTest {

    private fun kontrast(a: Color, b: Color): Double {
        val (hell, dunkel) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hell + 0.05) / (dunkel + 0.05)
    }

    @Test
    fun `amber ist der einzige ton und traegt das schema aus color_kt`() {
        assertEquals(TardisTon.AMBER, TardisTon.VORGABE)
        assertEquals(AccentDark, dunklesSchemaAmber.primary)
        assertEquals(DangerDark, dunklesSchemaAmber.error)
    }

    @Test
    fun `amber haelt die kontraste`() {
        val s = dunklesSchemaAmber
        assertTrue("Text", kontrast(s.onSurface, s.surface) >= 7.0)
        assertTrue("zweiter Text", kontrast(s.onSurfaceVariant, s.surfaceContainerHigh) >= 4.5)
        assertTrue("leiser Text", kontrast(TextDimDark, SurfaceHoverDark) >= 4.5)
        assertTrue("Akzent", kontrast(s.primary, s.surface) >= 4.5)
        assertTrue("Schrift auf Akzent", kontrast(s.onPrimary, s.primary) >= 4.5)
        assertTrue("Fehler", kontrast(s.error, s.surface) >= 4.5)
        assertTrue("Warnung", kontrast(WarningDark, s.surface) >= 4.5)
        assertTrue("Erfolg", kontrast(SuccessDark, s.surface) >= 4.5)
    }

    @Test
    fun `glitch nur bei neuer kennung`() {
        assertTrue(kommtNeuerAlarmDazu(setOf("A-1"), setOf("A-1", "A-2")))
        assertFalse(kommtNeuerAlarmDazu(setOf("A-1", "A-2"), setOf("A-1")))
        assertFalse(kommtNeuerAlarmDazu(setOf("A-1"), setOf("A-1")))
        // Verbindung kommt zurueck: kein Anlass.
        assertFalse(kommtNeuerAlarmDazu(null, setOf("A-1")))
        assertFalse(kommtNeuerAlarmDazu(setOf("A-1"), null))
    }
}
