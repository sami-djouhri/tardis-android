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

/**
 * Befehls-Freigabe (seit 2.0.31, `GET /v1/befehle`): Owner-Skripte, die der
 * root-Laeufer auf host nach deiner Unterschrift ausfuehrt. Unterschrieben
 * wird der sha256 des ganzen Skripttexts, den die App vorher zeigt.
 */
@Serializable
data class BefehleAntwort(
    /** false: auf host laeuft noch kein Laeufer, der den Eingang abnimmt. */
    val eingerichtet: Boolean = false,
    val befehle: List<Befehl> = emptyList(),
)

@Serializable
data class Befehl(
    val id: String,
    val titel: String,
    val warum: String? = null,
    val rueckweg: String? = null,
    /** Aufgaben-ID des Handgriffs, zu dem das Skript gehoert. */
    val schritt: String? = null,
    val sha256: String,
    val inhalt: String,
    /** Freigegeben, der Laeufer hat die Anfrage noch nicht abgeholt. */
    val wartet: Boolean = false,
    val ergebnis: BefehlErgebnis? = null,
) {
    /** Braucht eine Freigabe: noch nie gelaufen, gescheitert, abgelehnt oder seither geaendert. */
    val offen: Boolean
        get() = !wartet && ergebnis?.zustand != "laeuft" &&
            (ergebnis == null || ergebnis.veraltet || ergebnis.zustand != "ok")
}

@Serializable
data class BefehlErgebnis(
    /** laeuft, ok, fehler, abgelehnt */
    val zustand: String,
    val rc: Int? = null,
    val grund: String? = null,
    val ausgabe: String = "",
    val ende: Long? = null,
    val veraltet: Boolean = false,
)

@Serializable
data class BefehlUnterschrift(val sha256: String, val ausgestellt: Long, val schluessel: String, val signatur: String)

@Serializable
data class BefehlAngenommen(val angenommen: Boolean = false)
