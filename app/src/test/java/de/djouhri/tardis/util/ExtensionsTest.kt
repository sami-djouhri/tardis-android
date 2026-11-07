package de.djouhri.tardis.util

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException

class ExtensionsTest {

    @Test
    fun `io exception hints at the vpn`() {
        val msg = IOException("timeout").userMessage()
        assertTrue(msg.contains("VPN"))
    }

    @Test
    fun `401 maps to re-pair hint`() {
        val body = "".toResponseBody("text/plain".toMediaTypeOrNull())
        val ex = HttpException(Response.error<Any>(401, body))
        assertTrue(ex.userMessage().contains("koppeln"))
    }

    @Test
    fun `5xx maps to server error`() {
        val body = "".toResponseBody("text/plain".toMediaTypeOrNull())
        val ex = HttpException(Response.error<Any>(503, body))
        assertTrue(ex.userMessage().contains("Serverfehler"))
    }

    @Test
    fun `timeout is not blamed on the vpn`() {
        val msg = SocketTimeoutException("read timed out").userMessage()
        assertTrue(msg.contains("Zeitüberschreitung"))
        assertTrue(!msg.contains("VPN"))
    }

    @Test
    fun `gateway detail is appended`() {
        val body = """{"detail":"unbekannter Dienst: schach"}""".toResponseBody("application/json".toMediaTypeOrNull())
        val ex = HttpException(Response.error<Any>(404, body))
        assertEquals("Nicht gefunden: unbekannter Dienst: schach", ex.userMessage())
    }

    @Test
    fun `validation detail list is ignored`() {
        // FastAPI liefert bei 422 eine Liste von Objekten; die gehoert nicht in einen Satz.
        val body = """{"detail":[{"loc":["query","an"],"msg":"field required"}]}""".toResponseBody("application/json".toMediaTypeOrNull())
        val ex = HttpException(Response.error<Any>(422, body))
        assertEquals("Ungültige Daten.", ex.userMessage())
    }

    @Test
    fun `formats bytes in gib and mib`() {
        assertEquals("0 B", 0L.formatBytes())
        assertEquals("2.0 GiB", (2L * 1024 * 1024 * 1024).formatBytes())
        assertEquals("512 MiB", (512L * 1024 * 1024).formatBytes())
    }

    @Test
    fun `formats percent with fallback`() {
        assertEquals("–", (null as Double?).formatPercent())
        assertEquals("50 %", 50.0.formatPercent())
    }
}
