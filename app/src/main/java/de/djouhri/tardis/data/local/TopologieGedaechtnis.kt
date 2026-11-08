package de.djouhri.tardis.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.djouhri.tardis.data.demo.DemoModeManager
import de.djouhri.tardis.data.model.tardis.TopoSchnappschuss
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zustand jedes Dienstes beim letzten Blick auf die Topologie, damit die Karte
 * zeigen kann, was sich seitdem geaendert hat.
 *
 * Im Demo-Modus wird weder gelesen noch geschrieben: die erfundenen Dienste
 * staenden sonst beim naechsten echten Oeffnen als „weg“ in der Liste.
 */
@Singleton
class TopologieGedaechtnis @Inject constructor(
    @ApplicationContext context: Context,
    private val demo: DemoModeManager,
) {
    private val prefs = context.getSharedPreferences("tardis_topologie", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun lesen(): TopoSchnappschuss? {
        if (demo.isActive) return null
        return prefs.getString(SCHLUESSEL, null)
            ?.let { runCatching { json.decodeFromString(TopoSchnappschuss.serializer(), it) }.getOrNull() }
    }

    fun schreiben(s: TopoSchnappschuss) {
        if (demo.isActive) return
        prefs.edit().putString(SCHLUESSEL, json.encodeToString(TopoSchnappschuss.serializer(), s)).apply()
    }

    private companion object {
        const val SCHLUESSEL = "zuletzt_gesehen"
    }
}
