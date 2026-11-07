package de.djouhri.tardis.data.model.tardis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ein Container im Detail (/v1/ops/services/{host}/{name}/detail, seit 2.0.12).
 *
 * Jeder der vier Teile sagt selbst, ob er gemessen wurde. Das ist der Punkt:
 * eine leere Ereignisliste auf einem Wirt, den niemand beobachtet, saehe sonst
 * genauso aus wie „nie ausgefallen".
 */
@Serializable
data class DienstDetail(
    val host: String = "",
    val name: String = "",
    val container: ContainerLauf = ContainerLauf(),
    val verbrauch: Verbrauch = Verbrauch(),
    val wirt: WirtLast = WirtLast(),
    val verfuegbarkeit: Verfuegbarkeit = Verfuegbarkeit(),
    /** `false`: Alarmquelle nicht erreicht. Dann heisst eine leere Liste nichts. */
    @SerialName("alarme_gemessen") val alarmeGemessen: Boolean = false,
    val alarme: List<Alarm> = emptyList(),
)

@Serializable
data class ContainerLauf(
    val gefunden: Boolean = false,
    val status: String? = null,
    /** ISO-8601 (UTC). Verlaesslicher als der Verlauf: auch ein schneller Neuaufbau setzt ihn. */
    val gestartet: String? = null,
    /** Nur gesetzt, wenn der Container steht. */
    val beendet: String? = null,
    @SerialName("exit_code") val exitCode: Int? = null,
    val neustarts: Int? = null,
    val erstellt: String? = null,
)

@Serializable
data class Verbrauch(
    val gemessen: Boolean = false,
    val grund: String? = null,
    @SerialName("cpu_kerne") val cpuKerne: Double? = null,
    @SerialName("ram_bytes") val ramBytes: Double? = null,
    /** `null` heisst „keine Grenze gesetzt", nicht 0. */
    @SerialName("ram_grenze_bytes") val ramGrenzeBytes: Double? = null,
    /** Paare [unix_s, wert] ueber 24 h. */
    @SerialName("verlauf_cpu") val verlaufCpu: List<List<Double>> = emptyList(),
    @SerialName("verlauf_ram") val verlaufRam: List<List<Double>> = emptyList(),
    @SerialName("schritt_s") val schrittS: Int? = null,
) {
    val ramAnteil: Double?
        get() = if (ramBytes != null && ramGrenzeBytes != null && ramGrenzeBytes > 0) ramBytes / ramGrenzeBytes else null
}

@Serializable
data class WirtLast(
    val gemessen: Boolean = false,
    val grund: String? = null,
    @SerialName("cpu_prozent") val cpuProzent: Double? = null,
    @SerialName("ram_prozent") val ramProzent: Double? = null,
    @SerialName("platte_prozent") val platteProzent: Double? = null,
)

@Serializable
data class Wechsel(
    val alt: String? = null,
    val neu: String = "",
    val zeit: String? = null,
)

@Serializable
data class Verfuegbarkeit(
    /** `false`: dieser Wirt wird nicht beobachtet (nur der Hauptrechner). Nicht „nie ausgefallen". */
    val erfasst: Boolean = false,
    val grund: String? = null,
    @SerialName("uptime_24h") val uptime24h: Double? = null,
    @SerialName("uptime_7d") val uptime7d: Double? = null,
    @SerialName("uptime_30d") val uptime30d: Double? = null,
    @SerialName("zuletzt_beendet") val zuletztBeendet: String? = null,
    /** Wie oft der Beobachter nachsieht. Was kuerzer dauert, fehlt im Verlauf. */
    @SerialName("abtastung_s") val abtastungS: Int? = null,
    val ereignisse: List<Wechsel> = emptyList(),
)

/** „läuft → beendet" in Worten, fuer die Verlaufsliste. */
fun Wechsel.text(): String = when (neu) {
    "running" -> if (alt == "restarting") "wieder hochgekommen" else "gestartet"
    "exited" -> "beendet"
    "dead" -> "abgestürzt"
    "restarting" -> "startet neu"
    "removed" -> "entfernt (Neuaufbau)"
    "created" -> "angelegt"
    else -> neu
}
