package de.djouhri.tardis.data.model.tardis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Alle Wirte mit Verlauf (/v1/ops/wirte, seit 2.0.17).
 *
 * Vorher zeigte die Startseite nur den Momentwert von host aus
 * /v1/dashboard/summary. Quelle ist jetzt der node-exporter je Wirt ueber
 * Prometheus; die Reihen liegen auf einem gemeinsamen Raster ab [start] im
 * Abstand [schritt], eine Luecke ist `null` und kein Nullwert.
 */
@Serializable
data class WirteResponse(
    /** false: Prometheus antwortete nicht. Leere Liste heisst dann „nicht gemessen". */
    val gemessen: Boolean = false,
    val fehler: String? = null,
    @SerialName("fenster_minuten") val fensterMinuten: Int = 60,
    val start: Long? = null,
    val schritt: Int? = null,
    val wirte: List<WirtMessung> = emptyList(),
)

@Serializable
data class WirtMessung(
    val name: String,
    /** Proxmox-Node eines LXC-Gastes. Ein Gast sieht CPU und Load seines Nodes. */
    val node: String? = null,
    val erreichbar: Boolean = false,
    val cpu: Double? = null,
    val ram: Double? = null,
    val platte: Double? = null,
    val load: Double? = null,
    val kerne: Int? = null,
    val temp: Double? = null,
    val verlauf: WirtVerlauf = WirtVerlauf(),
) {
    val istGast: Boolean get() = node != null

    /** Der hoechste Prozentwert; danach faerbt sich die Zeile. */
    val engpass: Double? get() = listOfNotNull(cpu, ram, platte).maxOrNull()
}

@Serializable
data class WirtVerlauf(
    val cpu: List<Double?> = emptyList(),
    val ram: List<Double?> = emptyList(),
    val platte: List<Double?> = emptyList(),
    val load: List<Double?> = emptyList(),
)
