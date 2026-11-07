package de.djouhri.tardis.data.model.tardis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Liste von /v1/inbox (life-ops-Durchreichung). Extra-Felder werden ignoriert. */
@Serializable
data class InboxList(
    val items: List<InboxItem> = emptyList(),
    val counts: InboxCounts = InboxCounts(),
)

@Serializable
data class InboxItem(
    @SerialName("external_id") val externalId: String,
    val source: String = "unknown",
    val kind: String = "",
    val title: String = "",
    val detail: String? = null,
    val severity: String = "info",
    val category: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    val state: String = "open",
    val note: String? = null,
    @SerialName("age_hours") val ageHours: Double? = null,
)

@Serializable
data class InboxNoteRequest(
    val title: String,
    val content: String = "",
    val tags: List<String> = emptyList(),
    val source: String = "android-tardis",
)

@Serializable
data class InboxNoteResponse(
    val path: String,
    val slug: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class InboxActionRequest(
    val action: String,
    @SerialName("snooze_minutes") val snoozeMinutes: Int? = null,
    val note: String? = null,
)

/**
 * Ein Eintrag aus `/v1/stream`: die Zeitleiste der Ereignisse (Post, Nachrichten,
 * Alarme, Dienste), die `mobile-api` aus dem Ereignisspeicher durchreicht.
 * Dieselbe Quelle wie die Seite „Events“ der Weboberflaeche.
 *
 * `occurred_at` kommt ohne Zonenangabe: die Spalte ist naiv und wird in
 * UTC beschrieben. [zeitpunktUtc] macht daraus einen lesbaren Zeitpunkt,
 * statt dass die Anzeige am fehlenden Offset scheitert und nichts zeigt.
 */
@Serializable
data class StreamItem(
    @SerialName("external_id") val externalId: String = "",
    val source: String = "",
    val kind: String = "other",
    @SerialName("event_type") val eventType: String = "",
    val title: String? = null,
    val body: String? = null,
    val severity: String = "info",
    @SerialName("occurred_at") val occurredAt: String? = null,
) {
    val zeitpunktUtc: String?
        get() = occurredAt?.takeIf { it.isNotBlank() }?.let { roh ->
            val iso = roh.replace(' ', 'T')
            val zeitteil = iso.substringAfter('T', "")
            if (iso.endsWith("Z") || zeitteil.contains('+') || zeitteil.contains('-')) iso else iso + "Z"
        }
}

/**
 * Eine Zeile der Zeitleiste: gleiche Quelle und gleicher Titel gebuendelt, wie
 * auf der Web-Flaeche Events. Dort standen in 120 Ereignissen nur 74 Titel, eine
 * Wissenssuche kam 25-mal vor und fuellte den halben Bildschirm.
 */
data class VerlaufZeile(
    val item: StreamItem,
    val anzahl: Int = 1,
    /** Zeitpunkt des aeltesten Vorkommens; nur gesetzt, wenn gebuendelt wurde. */
    val aeltestUtc: String? = null,
)

/** Die Liste kommt juengste zuerst, das zuletzt gesehene Vorkommen ist das aelteste. */
fun List<StreamItem>.gebuendelt(): List<VerlaufZeile> {
    val zeilen = LinkedHashMap<String, VerlaufZeile>()
    for (e in this) {
        val schluessel = e.source + "|" + (e.title?.takeIf { it.isNotBlank() } ?: e.eventType)
        val da = zeilen[schluessel]
        zeilen[schluessel] = if (da == null) VerlaufZeile(e) else da.copy(anzahl = da.anzahl + 1, aeltestUtc = e.zeitpunktUtc)
    }
    return zeilen.values.toList()
}

