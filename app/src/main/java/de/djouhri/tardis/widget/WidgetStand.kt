package de.djouhri.tardis.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.djouhri.tardis.data.demo.DemoModeManager
import de.djouhri.tardis.data.model.tardis.AlarmeResponse
import de.djouhri.tardis.data.model.tardis.HostRollup
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Was das Widget auf dem Startbildschirm zeigt, zuletzt gemessen.
 *
 * Zwei Teile mit eigener Uhrzeit: die Alarme kommen jede Minute aus der
 * offenen App, die Dienste nur, wenn die Uebersicht laedt. Ein gemeinsamer
 * Zeitstempel saehe frischer aus, als die aeltere Haelfte ist.
 *
 * `null` heisst „nie gemessen", nicht 0. Ein Widget, das nach der Installation
 * „0 Alarme" zeigt, behauptet Ruhe, die niemand geprueft hat.
 */
@Serializable
data class WidgetStand(
    val alarme: Int? = null,
    val kritisch: Int = 0,
    /** Kurztext des wichtigsten Alarms (die Liste ist nach Schwere sortiert). */
    val ersterAlarm: String? = null,
    val alarmeStandMs: Long? = null,
    val probleme: Int? = null,
    /** Rechner, die nicht antworteten: deren Dienste sind nicht mitgezaehlt. */
    val stumm: Int = 0,
    val diensteStandMs: Long? = null,
    /** Letzter Versuch ging schief (VPN aus, Gateway weg). Der alte Stand bleibt stehen. */
    val zuletztFehler: Boolean = false,
)

@Singleton
class WidgetSpeicher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val demo: DemoModeManager,
) {
    private val prefs = context.getSharedPreferences("tardis_widget", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun lesen(): WidgetStand =
        prefs.getString(SCHLUESSEL, null)
            ?.let { runCatching { json.decodeFromString<WidgetStand>(it) }.getOrNull() }
            ?: WidgetStand()

    private fun schreiben(neu: WidgetStand) {
        prefs.edit().putString(SCHLUESSEL, json.encodeToString(WidgetStand.serializer(), neu)).apply()
    }

    /**
     * Im Demo-Modus wird nichts geschrieben: die Beispieldaten stuenden sonst
     * auf dem Startbildschirm, als waeren es die echten.
     */
    /** @return ob sich an der Anzeige etwas geaendert hat (ausser der Uhrzeit). */
    fun alarmeGesehen(a: AlarmeResponse, jetztMs: Long = System.currentTimeMillis()): Boolean {
        if (demo.isActive) return false
        val alt = lesen()
        schreiben(
            if (!a.erreichbar) alt.copy(alarme = null, ersterAlarm = null, kritisch = 0, alarmeStandMs = jetztMs)
            else alt.copy(
                alarme = a.alarme.size,
                kritisch = a.kritisch,
                ersterAlarm = a.alarme.firstOrNull()?.zusammenfassung,
                alarmeStandMs = jetztMs,
                zuletztFehler = false,
            ),
        )
        val neu = lesen()
        return neu.copy(alarmeStandMs = null) != alt.copy(alarmeStandMs = null)
    }

    fun hostsGesehen(hosts: List<HostRollup>, jetztMs: Long = System.currentTimeMillis()): Boolean {
        if (demo.isActive) return false
        val alt = lesen()
        schreiben(
            alt.copy(
                probleme = hosts.filter { it.erreichbar }.sumOf { it.problemZahl },
                stumm = hosts.count { !it.erreichbar },
                diensteStandMs = jetztMs,
                zuletztFehler = false,
            ),
        )
        return lesen().copy(diensteStandMs = null) != alt.copy(diensteStandMs = null)
    }

    /** Nur bei sichtbarer Aenderung: jede Minute ein Neuzeichnen ohne Anlass waere Akku fuer nichts. */
    suspend fun neuZeichnen() = widgetNeuZeichnen(context)

    fun fehlschlag() {
        if (demo.isActive) return
        schreiben(lesen().copy(zuletztFehler = true))
    }

    private companion object {
        const val SCHLUESSEL = "stand"
    }
}
