package de.djouhri.tardis.data.model.tardis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Aufnahme an `POST /v1/sprache`: AAC in MP4, base64. */
@Serializable
data class SpracheAnfrage(
    @SerialName("audio_base64") val audioBase64: String,
    @SerialName("mime_type") val mimeType: String = "audio/mp4",
)

/** Antwort des Sprach-host: was er verstanden hat, was er sagt, und den Ton dazu. */
@Serializable
data class SpracheAntwort(
    val erkannt: String = "",
    val antwort: String = "",
    @SerialName("audio_base64") val audioBase64: String? = null,
    @SerialName("audio_typ") val audioTyp: String? = null,
    /** Warum kein Ton kam, obwohl Text da ist. */
    @SerialName("ton_fehler") val tonFehler: String? = null,
    @SerialName("dauer_ms") val dauerMs: Double? = null,
)
