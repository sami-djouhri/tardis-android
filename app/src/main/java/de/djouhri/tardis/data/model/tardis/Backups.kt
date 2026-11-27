package de.djouhri.tardis.data.model.tardis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BackupsResponse(
    val gemessen: Boolean = false,
    val fehler: String? = null,
    val ok: Boolean? = null,
    @SerialName("geprueft_vor_h") val geprueftVorH: Double? = null,
    val veraltet: Boolean = false,
    val probleme: List<BackupProblem> = emptyList(),
    val wirte: List<BackupWirt> = emptyList(),
    @SerialName("geheimnisse_ok") val geheimnisseOk: Boolean? = null,
    @SerialName("geheimnisse_alter_h") val geheimnisseAlterH: Double? = null,
    @SerialName("ablage_prozent") val ablageProzent: Double? = null,
    @SerialName("ablage_frei_gb") val ablageFreiGb: Double? = null,
    @SerialName("proben_gemessen") val probenGemessen: Boolean = false,
) {
    /** Wirte mit Befund: Sicherung zu alt, Probe gescheitert oder zu alt. */
    val auffaellig: List<BackupWirt> get() = wirte.filter { it.istAuffaellig }
}

@Serializable
data class BackupProblem(
    val bereich: String = "",
    val art: String = "",
    val grund: String = "",
)

@Serializable
data class BackupWirt(
    val name: String,
    val ok: Boolean? = null,
    /** On-demand-Wirt (proxmoxtv): aus ist kein Befund. */
    @SerialName("bekannt_aus") val bekanntAus: Boolean = false,
    @SerialName("alter_h") val alterH: Double? = null,
    @SerialName("grenze_h") val grenzeH: Double? = null,
    val snapshots: Int? = null,
    val probleme: List<String> = emptyList(),
    @SerialName("probe_ok") val probeOk: Boolean? = null,
    @SerialName("probe_alter_h") val probeAlterH: Double? = null,
) {
    val istAuffaellig: Boolean get() = !bekanntAus && (ok == false || probeOk == false)
}

/**
 * „16 h", „3 Tage": Stunden in die Einheit, die man liest. [dativ] fuer
 * „vor 3 Tagen" und „seit 3 Tagen".
 */
fun stundenText(h: Double?, dativ: Boolean = false): String? = when {
    h == null -> null
    h < 48 -> "${h.toInt()} h"
    else -> "${(h / 24).toInt()} " + if (dativ) "Tagen" else "Tage"
}
