package de.djouhri.tardis.data.model.tardis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ein aktiver Prometheus-Alarm (/v1/ops/alarme).
 *
 * Die App sah bis 2.0.9 nur den Containerzustand. Was nicht an einem Container
 * haengt (ein Backup ohne Lauf, ein Wirt mit offenem Neustart, ein Zigbee-Netz
 * ohne Router), kam auf dem Telefon nicht an.
 */
@Serializable
data class Alarm(
    val name: String,
    val schwere: String = "warning",
    val zusammenfassung: String = "",
    val beschreibung: String? = null,
    val instanz: String? = null,
    val job: String? = null,
    val gruppe: String? = null,
    /** Grober Bereich aus der Regelgruppe (mobile-api), fuer die Filter-Chips. */
    val bereich: String = "Sonstiges",
    /** ISO-8601 (UTC), seit wann der Alarm feuert. */
    val seit: String? = null,
) {
    val istKritisch: Boolean get() = schwere == "critical" || schwere == "page"
}

@Serializable
data class AlarmeResponse(
    /**
     * `false` heisst „nicht gemessen", nicht „keine Alarme". Die Oberflaeche
     * darf daraus nie Ruhe machen: genau dann ist Ruhe am wenigsten belegt.
     */
    val erreichbar: Boolean = false,
    val stand: String? = null,
    val fehler: String? = null,
    val alarme: List<Alarm> = emptyList(),
) {
    val kritisch: Int get() = alarme.count { it.istKritisch }
}

/**
 * Alarme gleichen Namens zusammengefasst.
 *
 * 38 Alarme sind als Liste unlesbar, und mehrere davon sind derselbe Befund an
 * verschiedenen Wirten (ein offener Neustart auf zwei Knoten).
 * Die Reihenfolge der Gruppen folgt dem schwersten und dann dem aeltesten
 * Eintrag: das Gateway sortiert schon so, und `groupBy` haelt die Reihenfolge
 * des ersten Auftretens.
 */
data class AlarmGruppe(val name: String, val alarme: List<Alarm>) {
    val istKritisch: Boolean get() = alarme.any { it.istKritisch }
}

fun List<Alarm>.gruppiert(): List<AlarmGruppe> =
    groupBy { it.name }.map { (name, liste) -> AlarmGruppe(name, liste) }

/** Ein on-demand-Dienst des Weckers (/v1/ops/abruf). */
@Serializable
data class AbrufDienst(
    val name: String,
    val titel: String = name,
    val laeuft: Boolean = false,
    /** Laeuft UND der Healthcheck ist durch. `laeuft` allein heisst nur „Prozess da". */
    val bereit: Boolean = false,
    @SerialName("leerlauf_s") val leerlaufS: Int? = null,
    @SerialName("idle_timeout_s") val idleTimeoutS: Int = 0,
    /** Ob der Speicherboden einen Start JETZT zuliesse. */
    val startbar: Boolean = true,
    /** Wartungsmodus: wird nicht schlafen gelegt und nicht verdraengt. */
    val wartung: Boolean = false,
) {
    fun zustandsText(): String = when {
        bereit && wartung -> "wach · wird wach gehalten"
        bereit -> schlafHinweis()?.let { "wach · $it" } ?: "wach"
        laeuft -> "startet…"
        !startbar -> "schläft · zu wenig Speicher zum Starten"
        else -> "schläft"
    }

    /**
     * Wann der Wecker den Dienst wieder schlafen legt. `null`, wenn sich das
     * nicht sagen laesst: ohne Zugriffsspur gibt es keine Leerlaufzeit, und
     * eine erfundene waere eine Behauptung.
     */
    private fun schlafHinweis(): String? {
        val leer = leerlaufS ?: return null
        if (idleTimeoutS <= 0) return null
        val rest = idleTimeoutS - leer
        return if (rest <= 60) "schläft gleich ein" else "schläft in ${rest / 60} min"
    }
}

@Serializable
data class Wecker(
    val erreichbar: Boolean = false,
    val fehler: String? = null,
    @SerialName("frei_mb") val freiMb: Int? = null,
    @SerialName("reserve_mb") val reserveMb: Int? = null,
    val dienste: List<AbrufDienst> = emptyList(),
)

/** Windows-AD-Lab, aus dem Snapshot des Arbiters auf seinem Knoten. */
@Serializable
data class Lab(
    /** `false`: kein Snapshot. Dann gilt weder „laeuft" noch „aus". */
    val verfuegbar: Boolean = false,
    /** Snapshot aelter als 3 min. */
    val veraltet: Boolean = false,
    @SerialName("alter_s") val alterS: Int? = null,
    val fehler: String? = null,
    val laeuft: Boolean = false,
    val vms: List<String> = emptyList(),
    val reserviert: Boolean = false,
    @SerialName("frei_mb") val freiMb: Int? = null,
)

@Serializable
data class AbrufResponse(
    val wecker: Wecker = Wecker(),
    val lab: Lab = Lab(),
)

@Serializable
data class WeckErgebnis(
    val name: String,
    val ok: Boolean = false,
    /** `X-Wake-State` des Weckers: starting, ready, throttled, lowmem, queued. */
    val zustand: String? = null,
) {
    fun meldung(titel: String): String = when (zustand) {
        "ready" -> "$titel ist wach."
        "starting" -> "$titel startet."
        "throttled" -> "Gerade zu viele Starts hintereinander. In einer Minute noch einmal versuchen."
        "lowmem" -> "Zu wenig freier Speicher auf dem Wirt, $titel bleibt aus."
        "queued" -> "Alle Plätze belegt, ein anderer Dienst wird gerade schlafen gelegt. Gleich noch einmal versuchen."
        else -> "Wecker meldet: ${zustand ?: "nichts"}"
    }
}

/** Ergebnis einer Lab-Aktion, durchgereicht von der wake-bridge. */
@Serializable
data class LabErgebnis(
    val ok: Boolean = false,
    val rc: Int? = null,
    /** Eine belegte Rolle hat Vorrang. Keine Stoerung, Erzwingen gibt es nicht. */
    val conflict: Boolean = false,
    val status: Int? = null,
    val error: String? = null,
    val log: List<String> = emptyList(),
)

@Serializable
data class WartungErgebnis(
    val dienst: String = "",
    val wartung: Boolean = false,
    /** `false`: gilt nur bis zum naechsten Neustart des Weckers. */
    val dauerhaft: Boolean = true,
    val warnung: String? = null,
)
