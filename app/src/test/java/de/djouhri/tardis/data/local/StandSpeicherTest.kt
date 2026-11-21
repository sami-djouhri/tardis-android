package de.djouhri.tardis.data.local

import de.djouhri.tardis.data.model.tardis.HostRollup
import de.djouhri.tardis.data.model.tardis.HostsResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class StandSpeicherTest {

    private class MerkAblage : StandAblage {
        val inhalt = mutableMapOf<String, String>()
        override fun lesen(schluessel: String) = inhalt[schluessel]
        override fun schreiben(schluessel: String, inhalt: String) { this.inhalt[schluessel] = inhalt }
    }

    private val hosts = HostsResponse(listOf(HostRollup(name = "edge", erreichbar = true, gesamt = 3)))
    private var jetzt = 1_000L
    private val ablage = MerkAblage()
    private val speicher = StandSpeicher(ablage) { jetzt }

    @Test
    fun `frisch kommt live und wird behalten`() = runTest {
        val s = speicher.mitSpeicher("hosts", HostsResponse.serializer()) { hosts }.getOrThrow()
        assertEquals(hosts, s.daten)
        assertNull(s.alt)
        assertNull(speicher.ausSpeicher.value)
        assertEquals(1_000L, speicher.letzter("hosts", HostsResponse.serializer())!!.zeit)
    }

    @Test
    fun `ohne verbindung kommt der letzte stand mit seinem zeitpunkt`() = runTest {
        speicher.mitSpeicher("hosts", HostsResponse.serializer()) { hosts }
        jetzt = 9_000L
        val s = speicher.mitSpeicher("hosts", HostsResponse.serializer()) { throw IOException("weg") }.getOrThrow()
        assertEquals(hosts, s.daten)
        // Der Zeitpunkt der Messung, nicht der des Abrufs: sonst saehe alt wie frisch aus.
        assertEquals(1_000L, s.alt)
        assertEquals(1_000L, speicher.ausSpeicher.value)
    }

    @Test
    fun `die naechste frische antwort nimmt das band wieder weg`() = runTest {
        speicher.mitSpeicher("hosts", HostsResponse.serializer()) { hosts }
        speicher.mitSpeicher("hosts", HostsResponse.serializer()) { throw IOException("weg") }
        speicher.mitSpeicher("hosts", HostsResponse.serializer()) { hosts }
        assertNull(speicher.ausSpeicher.value)
    }

    @Test
    fun `ohne gespeicherten stand bleibt der fehler ein fehler`() = runTest {
        val r = speicher.mitSpeicher("hosts", HostsResponse.serializer()) { throw IOException("weg") }
        assertTrue(r.exceptionOrNull() is IOException)
        assertNull(speicher.ausSpeicher.value)
    }

    @Test
    fun `kaputter inhalt ist kein stand`() = runTest {
        ablage.inhalt["hosts"] = "{kaputt"
        assertNull(speicher.letzter("hosts", HostsResponse.serializer()))
        assertTrue(speicher.mitSpeicher("hosts", HostsResponse.serializer()) { throw IOException() }.isFailure)
    }
}
