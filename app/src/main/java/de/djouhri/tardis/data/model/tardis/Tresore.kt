package de.djouhri.tardis.data.model.tardis

import kotlinx.serialization.Serializable

/** Ein Schritt des Notknopfs (/v1/ops/tresore/alles-zu), je Tresor oder Tresor-Gruppe. */
@Serializable
data class TresorSchritt(
    val id: String,
    val name: String,
    val wirt: String,
    val ok: Boolean,
    /** zu | nicht eingerichtet | nicht erreichbar | HTTP <code> | erreichbar (Probe) */
    val zustand: String,
    val meldung: String? = null,
)

@Serializable
data class AllesZuResponse(
    val ok: Boolean,
    val probe: Boolean = false,
    val schritte: List<TresorSchritt> = emptyList(),
    /** Gesetzt, wenn tardis selbst nicht antwortet: dann ist ueber keinen Tresor etwas bekannt. */
    val fehler: String? = null,
)

/**
 * Eine Zeile fuer Benachrichtigung und Kachel. Ein gescheiterter Schritt steht
 * immer namentlich da: „alle zu" darf nur heissen, dass jeder Schritt zu ist.
 */
fun AllesZuResponse.kurzfassung(): String {
    if (fehler != null) return "Nicht ausgeführt: $fehler"
    if (schritte.isEmpty()) return "Keine Antwort über die Tresore"
    val offen = schritte.filter { !it.ok }
    return if (offen.isEmpty()) "Alle Tresore zu"
    else "NICHT zu: " + offen.joinToString(", ") { "${it.name} (${it.zustand})" }
}
