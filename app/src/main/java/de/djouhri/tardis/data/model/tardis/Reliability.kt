package de.djouhri.tardis.data.model.tardis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Verfuegbarkeit wie die Web-Flaeche Reliability (/v1/ops/reliability, seit 2.0.14).
 *
 * Sortierung, „bewusst aus" und „nicht gefuehrt" rechnet mobile-api; die App
 * zeichnet nur. So stehen die Regeln an einer Stelle neben ihrer Quelle.
 */
@Serializable
data class DienstVerfuegbarkeit(
    val name: String,
    @SerialName("uptime_24h") val uptime24h: Double? = null,
    @SerialName("uptime_7d") val uptime7d: Double? = null,
    @SerialName("uptime_30d") val uptime30d: Double? = null,
    /** Grund, warum 0 % hier in Ordnung ist („on demand", „bewusst aus" ...). */
    val erwartung: String? = null,
    @SerialName("nicht_gefuehrt") val nichtGefuehrt: Boolean = false,
    @SerialName("ohne_wertung") val ohneWertung: Boolean = false,
) {
    /** Auffaellig ist, was gewertet wird und nicht voll verfuegbar war, oder ohne Wertung. */
    val auffaellig: Boolean get() = ohneWertung || uptime24h == null || uptime24h < 100.0
}

@Serializable
data class ReliabilityKennzahlen(
    @SerialName("unter_99") val unter99: Int = 0,
    @SerialName("bewusst_aus") val bewusstAus: Int = 0,
    @SerialName("nicht_gefuehrt") val nichtGefuehrt: Int = 0,
    val unauffaellig: Int = 0,
    @SerialName("incidents_24h") val incidents24h: Int = 0,
)

@Serializable
data class Vorfall(
    val container: String,
    val alt: String? = null,
    val neu: String? = null,
    /** ISO-8601 mit Zone (UTC). */
    val zeit: String? = null,
    val details: String? = null,
)

@Serializable
data class SystemMetrik(
    val metrik: String,
    val werte: List<Double> = emptyList(),
    val letzter: Double? = null,
    val schwelle: Double? = null,
    @SerialName("ueber_schwelle") val ueberSchwelle: Boolean = false,
)

@Serializable
data class VerlaufPunkt(val t: Long, val v: Double)

@Serializable
data class VerlaufReihe(
    val key: String,
    val titel: String,
    val einheit: String = "",
    val erreichbar: Boolean = false,
    val punkte: List<VerlaufPunkt> = emptyList(),
)

@Serializable
data class ReliabilityResponse(
    val dienste: List<DienstVerfuegbarkeit> = emptyList(),
    val kennzahlen: ReliabilityKennzahlen = ReliabilityKennzahlen(),
    val incidents: List<Vorfall> = emptyList(),
    val system: List<SystemMetrik> = emptyList(),
    @SerialName("fenster_minuten") val fensterMinuten: Int = 60,
    val verlauf: List<VerlaufReihe> = emptyList(),
    /** Je Quelle, die nicht antwortete: deren Karte ist „nicht gemessen", nicht leer. */
    val fehler: Map<String, String> = emptyMap(),
)
