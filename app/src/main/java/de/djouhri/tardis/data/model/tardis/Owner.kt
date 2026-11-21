package de.djouhri.tardis.data.model.tardis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Was nur der Owner tun kann, aus `GET /v1/owner/schritte` (seit 2.0.25).
 *
 * Quelle ist der Aufgaben-Dienst (Projekt `homelab-owner`), ueber einen
 * Abgleich auf host alle 5 Minuten. „Gemacht" meldet nur zurueck; abgehakt
 * wird erst nach der Gegenprobe, deshalb bleibt ein gemeldeter Schritt stehen.
 */
@Serializable
data class OwnerSchritteResponse(
    val gemessen: Boolean = false,
    val fehler: String? = null,
    val stand: String? = null,
    @SerialName("stand_vor_min") val standVorMin: Int? = null,
    val veraltet: Boolean = false,
    val schritte: List<OwnerSchritt> = emptyList(),
) {
    /** Was jetzt dran ist: nicht wartend, nicht gemeldet, Plantag heute, frueher oder keiner. */
    fun dran(heute: LocalDate = LocalDate.now()): List<OwnerSchritt> =
        schritte.filter { it.istOffen && it.tagDatum?.isAfter(heute) != true }

    /**
     * Die Abschnitte der Unterseite (2.0.29). Verpasst ist ein Plantag vor heute:
     * der Termin ist vorbei, der Schritt nicht. Ein unlesbarer Tag zaehlt als
     * ohne Termin, damit nichts verschwindet.
     */
    fun gruppen(heute: LocalDate = LocalDate.now()): OwnerGruppen {
        val offen = schritte.filter { it.istOffen }
        return OwnerGruppen(
            verpasst = offen.filter { it.tagDatum?.isBefore(heute) == true }.sortedBy { it.tagDatum },
            heute = offen.filter { it.tagDatum == heute },
            ohneTermin = offen.filter { it.tagDatum == null },
            geplant = offen.filter { it.tagDatum?.isAfter(heute) == true }.sortedBy { it.tagDatum },
            zurueck = schritte.filter { !it.istOffen },
        )
    }
}

data class OwnerGruppen(
    val verpasst: List<OwnerSchritt>,
    val heute: List<OwnerSchritt>,
    val ohneTermin: List<OwnerSchritt>,
    val geplant: List<OwnerSchritt>,
    val zurueck: List<OwnerSchritt>,
)

/** Stand der Gegenprobe nach „Gemacht" (seit 2.0.29, vom Abgleich auf host). */
@Serializable
data class Gegenprobe(
    /** true: der Abgleich prueft selbst; false: die Gegenprobe geschieht von Hand. */
    val auto: Boolean = false,
    /** "gruen", "rot" oder null (noch nicht geprueft). */
    val zustand: String? = null,
    val befund: String? = null,
    val zeit: String? = null,
)

@Serializable
data class OwnerSchritt(
    val id: String,
    val titel: String,
    val text: String = "",
    val befehle: List<String> = emptyList(),
    /** Plantag (ISO-Datum) oder null. */
    val tag: String? = null,
    val faellig: String? = null,
    /** Wartegrund, z. B. „Owner meldet gemacht …, Gegenprobe steht aus". */
    val wartet: String? = null,
    /** „Gemacht" ist gemeldet, der Abgleich hat es noch nicht uebertragen. */
    val gemeldet: Boolean = false,
    /** Wann „Gemacht" beim Abgleich ankam (ISO mit Zone), seit 2.0.29. */
    @SerialName("gemeldet_am") val gemeldetAm: String? = null,
    val gegenprobe: Gegenprobe? = null,
) {
    val tagDatum: LocalDate? get() = tag?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Weder gemeldet noch wartend: der Owner ist am Zug. */
    val istOffen: Boolean get() = wartet == null && !gemeldet

    /** Rot nach Ablauf der Frist: der Schritt ist wieder dran, mit Befund. */
    val gegenprobeRot: Boolean get() = istOffen && gegenprobe?.zustand == "rot"
}

private val TAG_KURZ = DateTimeFormatter.ofPattern("EE d.M.", Locale.GERMANY)
private val UHR = DateTimeFormatter.ofPattern("d.M. HH:mm", Locale.GERMANY)

private fun lokaleZeit(iso: String?): LocalDateTime? =
    iso?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime() }.getOrNull() }

private fun tageText(n: Long): String = when (n) {
    0L -> "heute"
    1L -> "seit 1 Tag"
    else -> "seit $n Tagen"
}

/** „verpasst: Mi 18.11., seit 3 Tagen" oder null, wenn der Plantag nicht vorbei ist. */
fun verpasstText(s: OwnerSchritt, heute: LocalDate): String? {
    val tag = s.tagDatum ?: return null
    if (!tag.isBefore(heute)) return null
    return "verpasst: ${TAG_KURZ.format(tag)}, ${tageText(ChronoUnit.DAYS.between(tag, heute))}"
}

/**
 * Eine Zeile zur Gegenprobe, oder null, wenn es nichts zu sagen gibt.
 * Fuer Gemeldetes: wer prueft und seit wann; fuer einen wieder offenen
 * Schritt: der rote Befund, damit klar ist, warum er zurueck ist.
 */
fun gegenprobeText(s: OwnerSchritt, jetzt: LocalDateTime): String? {
    val g = s.gegenprobe
    if (s.gegenprobeRot && g != null) {
        val wann = lokaleZeit(g.zeit)?.let { " ${UHR.format(it)}" } ?: ""
        return "Gegenprobe rot$wann: ${g.befund ?: "ohne Befund"}. Bitte noch einmal ansehen."
    }
    if (s.istOffen || s.gemeldet) return null
    if (s.wartet?.startsWith("Owner meldet gemacht") != true) return null
    val seit = lokaleZeit(s.gemeldetAm)?.let {
        when (val n = ChronoUnit.DAYS.between(it.toLocalDate(), jetzt.toLocalDate())) {
            0L -> ", gemeldet heute"
            1L -> ", gemeldet gestern"
            else -> ", gemeldet vor $n Tagen"
        }
    } ?: ""
    if (g?.auto == true) {
        val letzte = when (g.zustand) {
            "rot" -> lokaleZeit(g.zeit)?.let { " Zuletzt ${UHR.format(it)} rot: ${g.befund ?: "ohne Befund"}." } ?: ""
            else -> " Noch nicht geprüft."
        }
        return "Gegenprobe läuft automatisch$seit.$letzte"
    }
    return "Gegenprobe von Hand$seit."
}

@Serializable
data class GemachtResponse(val id: String, val gemeldet: Boolean = true)
