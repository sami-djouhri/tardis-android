package de.djouhri.tardis.data.model.tardis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Pull-Deploy-Freigaben (/v1/freigaben), siehe mobile-api/app/routers/freigaben.py. */
@Serializable
data class FreigabeCommit(val sha: String, val betreff: String = "", val zeit: String? = null)

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
    /** ISO-Zeit des aeltesten noch nicht ausgerollten Commits: so lange wartet das Ziel schon. */
    val seit: String? = null,
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
    val gemessen: Boolean = false,
)

@Serializable
data class AbgelehnteFreigabe(
    val wirt: String,
    val projekt: String,
    val commit: String,
    val betreff: String = "",
    val grund: String = "",
    val zeit: Long = 0,
)

@Serializable
data class FreigabenAntwort(
    val konfiguriert: Boolean = false,
    val wartend: List<WartendeFreigabe> = emptyList(),
    val abgelehnt: List<AbgelehnteFreigabe> = emptyList(),
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

@Serializable
data class BefehleAntwort(
    /** false: auf host laeuft noch kein Laeufer, der den Eingang abnimmt. */
    val eingerichtet: Boolean = false,
    val befehle: List<Befehl> = emptyList(),
    /** Je Wirt: eingerichtet, Fassung, letzter Durchgang (seit 2.0.32). */
    val laeufer: List<Laeufer> = emptyList(),
) {
    fun laeufer(wirt: String): Laeufer? = laeufer.firstOrNull { it.wirt == wirt }
}

@Serializable
data class Laeufer(
    val wirt: String,
    val eingerichtet: Boolean = false,
    val version: Int = 0,
    val zeit: Long? = null,
    /** Letzter Durchgang laenger als 20 min her: Timer oder Bote stehen. */
    val still: Boolean = false,
)

@Serializable
data class BefehlAblehnung(val grund: String = "", val zeit: Long = 0)

@Serializable
data class Befehl(
    val id: String,
    val titel: String,
    val warum: String? = null,
    val rueckweg: String? = null,
    /** Aufgaben-ID des Handgriffs, zu dem das Skript gehoert. */
    val schritt: String? = null,
    /** Wirt aus `# wirt:`; der Laeufer dort prueft die Unterschrift mit genau diesem Namen. */
    val wirt: String = "host",
    /** Skript kennt einen Probelauf (PROBE=1) und der Laeufer kann ihn. */
    val probe: Boolean = false,
    val sha256: String,
    val inhalt: String,
    /** Freigegeben, der Laeufer hat die Anfrage noch nicht abgeholt. */
    val wartet: Boolean = false,
    val ergebnis: BefehlErgebnis? = null,
    val abgelehnt: BefehlAblehnung? = null,
) {
    val laeuft: Boolean get() = ergebnis?.zustand == "laeuft" && !ergebnis.veraltet

    /** Ein Probelauf zaehlt nicht als erledigt. */
    private val gruenGelaufen: Boolean
        get() = ergebnis != null && !ergebnis.veraltet && ergebnis.zustand == "ok" && ergebnis.modus == "lauf"

    /** Braucht eine Freigabe: noch nie (echt) gelaufen, gescheitert oder seither geaendert, und nicht abgelehnt. */
    val offen: Boolean
        get() = !wartet && !laeuft && abgelehnt == null && !gruenGelaufen

    val gescheitert: Boolean
        get() = ergebnis != null && !ergebnis.veraltet && ergebnis.zustand in setOf("fehler", "abgelehnt")
}

@Serializable
data class BefehlErgebnis(
    /** laeuft, ok, fehler, abgelehnt */
    val zustand: String,
    val rc: Int? = null,
    val grund: String? = null,
    val ausgabe: String = "",
    val ende: Long? = null,
    val angenommen: Long? = null,
    /** "lauf" oder "probe". */
    val modus: String = "lauf",
    val veraltet: Boolean = false,
)

@Serializable
data class BefehlUnterschrift(
    val sha256: String,
    val ausgestellt: Long,
    val schluessel: String,
    val signatur: String,
    /** "lauf", "probe" oder "abbruch"; unterschrieben ist jeweils ein eigener Text. */
    val modus: String = "lauf",
)

@Serializable
data class BefehlAblehnen(val sha256: String, val grund: String)

@Serializable
data class DeployAblehnen(val wirt: String, val projekt: String, val commit: String, val grund: String)

@Serializable
data class Abgelehnt(val abgelehnt: Boolean = false)

/**
 * Hausbus-Freigabe (seit 2.0.33, `GET /v1/freigaben/hausbus`): brain-bus schlaegt
 * eine Aktion vor (z. B. Container neu starten) und wartet auf Ja oder Nein.
 * Ohne Unterschrift: entschieden wird ueber tardis, ausgefuehrt von brain-bus.
 */
@Serializable
data class HausbusFreigabe(
    val id: String,
    val titel: String,
    val regel: String = "",
    val werkzeug: String = "",
    val ziel: String = "",
    val risiko: String = "",
    val begruendung: String = "",
    val erstellt: String = "",
    /** ISO-Zeit; danach verfaellt die Anfrage in brain-bus. */
    val verfaellt: String = "",
)

@Serializable
data class HausbusAntwort(
    /** false: brain-bus antwortet nicht, die Liste ist dann unbekannt, nicht leer. */
    val erreichbar: Boolean = false,
    val freigaben: List<HausbusFreigabe> = emptyList(),
    val fehler: String? = null,
)

@Serializable
data class HausbusEntscheidung(val entscheidung: String, val grund: String = "")

@Serializable
data class HausbusErgebnis(val ok: Boolean = false, val ausgefuehrt: Boolean = false, val meldung: String = "")

/** Ein Eintrag aus `/v1/freigaben/verlauf`, neueste zuerst. */
@Serializable
data class VerlaufEintrag(
    val zeit: Long,
    /** deploy, befehl oder hausbus */
    val art: String,
    /** freigegeben, probe, abbruch, abgelehnt, ergebnis */
    val aktion: String,
    val titel: String,
    val wirt: String? = null,
    val zustand: String? = null,
    val modus: String? = null,
    val rc: Int? = null,
    val grund: String? = null,
    val betreff: String? = null,
    val ausgabe: String? = null,
    val geraet: String? = null,
)

@Serializable
data class BefehlAngenommen(val angenommen: Boolean = false)
