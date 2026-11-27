package de.djouhri.tardis.util

import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TAG_MONAT = DateTimeFormatter.ofPattern("d.M.", Locale.GERMANY)
private val UHRZEIT = DateTimeFormatter.ofPattern("HH:mm", Locale.GERMANY)

fun seitText(iso: String?, jetzt: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String? {
    val beginn = iso?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() } ?: return null
    val dauer = Duration.between(beginn, jetzt)
    if (dauer.isNegative) return null
    val minuten = dauer.toMinutes()
    return when {
        minuten < 1 -> "seit eben"
        minuten < 60 -> "seit $minuten min"
        minuten < 24 * 60 -> "seit ${minuten / 60} h"
        else -> {
            val tage = minuten / (24 * 60)
            val wort = if (tage == 1L) "1 Tag" else "$tage Tagen"
            "seit $wort (${TAG_MONAT.format(beginn.atZone(zone))})"
        }
    }
}

/** „14:32" aus einem ISO-Zeitpunkt, in der Zone des Telefons. */
fun uhrzeitAus(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String? =
    iso?.let { runCatching { UHRZEIT.format(OffsetDateTime.parse(it).atZoneSameInstant(zone)) }.getOrNull() }

private val TAG_UHRZEIT = DateTimeFormatter.ofPattern("d.M. HH:mm", Locale.GERMANY)

fun zeitpunktText(iso: String?, zone: ZoneId = ZoneId.systemDefault()): String? =
    iso?.let { runCatching { TAG_UHRZEIT.format(OffsetDateTime.parse(it).atZoneSameInstant(zone)) }.getOrNull() }
