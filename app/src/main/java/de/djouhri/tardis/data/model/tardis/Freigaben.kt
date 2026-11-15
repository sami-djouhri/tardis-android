package de.djouhri.tardis.data.model.tardis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Pull-Deploy-Freigaben (/v1/freigaben), siehe mobile-api/app/routers/freigaben.py. */
@Serializable
data class FreigabeCommit(val sha: String, val betreff: String = "")

@Serializable
data class WartendeFreigabe(
    val wirt: String,
    val projekt: String,
    val repo: String,
    val commit: String,
    val betreff: String = "",
    val autor: String = "",
    val zeit: String? = null,
    /** Zuletzt freigegebener Commit; `null` heisst erste Freigabe, der ganze Stand geht live. */
    val basis: String? = null,
    val commits: List<FreigabeCommit> = emptyList(),
    /** "gruen", "gelb" oder "rot"; regelbasiert, ohne LLM. */
    val ampel: String = "rot",
    val gruende: List<String> = emptyList(),
    val diff: String = "",
    @SerialName("diff_gekuerzt") val diffGekuerzt: Boolean = false,
) {
    val schluessel: String get() = "$wirt/$projekt/$commit"
}

@Serializable
data class FreigabeStand(
    val wirt: String,
    val projekt: String,
    val freigegeben: String? = null,
    val ausgerollt: String? = null,
    /** Zuletzt gescheiterter Commit; der Wirt ist danach auf den letzten guten Stand zurueck. */
    val gescheitert: String? = null,
    /** `false`: Prometheus nicht erreichbar, also nicht gemessen (nicht "nichts ausgerollt"). */
    val gemessen: Boolean = false,
)

@Serializable
data class FreigabenAntwort(
    val konfiguriert: Boolean = false,
    val wartend: List<WartendeFreigabe> = emptyList(),
    val stand: List<FreigabeStand> = emptyList(),
    /** Fingerabdruecke der im Freigaben-Repo gemeldeten Schluessel. */
    val schluessel: List<String> = emptyList(),
    val fehler: List<String> = emptyList(),
)

@Serializable
data class Unterschrift(val ausgestellt: Long, val schluessel: String, val signatur: String)

@Serializable
data class SchluesselMelden(val pem: String)

@Serializable
data class SchluesselGemeldet(val fingerabdruck: String, val neu: Boolean)

@Serializable
data class FreigabeErgebnis(val freigegeben: Boolean = false, val neu: Boolean = false)
