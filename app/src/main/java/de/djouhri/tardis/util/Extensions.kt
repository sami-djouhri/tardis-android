package de.djouhri.tardis.util

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

fun Throwable.userMessage(): String = when (this) {
    // Vor IOException: eine Zeitueberschreitung IST eine, und lief bis 2.0.9
    // als „Ist das VPN aktiv?" durch. Bei einem Gateway, das antwortet, nur
    // langsam, schickte der Text einen auf die Suche nach dem falschen Fehler.
    is SocketTimeoutException -> "Zeitüberschreitung: das Gateway hat nicht rechtzeitig geantwortet."
    is IOException -> "Netzwerkfehler. Ist das VPN aktiv?"
    is HttpException -> {
        val basis = when (code()) {
            401 -> "Nicht autorisiert. Bitte Gerät erneut koppeln."
            403 -> "Zugriff verweigert."
            404 -> "Nicht gefunden."
            422 -> "Ungültige Daten."
            in 500..599 -> "Serverfehler. Bitte später erneut versuchen."
            else -> "Fehler: ${code()} ${message()}"
        }
        // Der Grund, den das Gateway nennt („unbekannter Dienst: x",
        // „wake-api: ConnectError"), stand bisher nirgends. Ohne ihn sagen
        // „Nicht gefunden" und „Serverfehler" nicht, WAS fehlt.
        gatewayGrund()?.let { "${basis.removeSuffix(".")}: $it" } ?: basis
    }
    else -> message ?: "Unbekannter Fehler"
}

private val fehlerJson = Json { ignoreUnknownKeys = true; isLenient = true }

/** `detail` aus einer FastAPI-Fehlerantwort, nur wenn es ein kurzer Text ist. */
private fun HttpException.gatewayGrund(): String? = runCatching {
    val roh = response()?.errorBody()?.string()?.takeIf { it.isNotBlank() } ?: return null
    val detail = fehlerJson.parseToJsonElement(roh).jsonObject["detail"] as? JsonPrimitive
    detail?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() && it.length <= 160 }
}.getOrNull()

/** Bytes menschenlesbar (GiB/MiB) fuer die Host-Metriken. */
fun Long.formatBytes(): String {
    if (this <= 0) return "0 B"
    val gib = this / (1024.0 * 1024.0 * 1024.0)
    if (gib >= 1.0) return "%.1f GiB".format(gib)
    val mib = this / (1024.0 * 1024.0)
    return "%.0f MiB".format(mib)
}

fun Double?.formatPercent(): String = this?.let { "%.0f %%".format(it) } ?: "–"
