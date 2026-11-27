package de.djouhri.tardis.data.model.tardis

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wie ein Container einzuordnen ist.
 *
 * Der Unterschied zwischen [BEWUSST_AUS] und [AUSGEFALLEN] ist der Grund, warum
 * es diese Aufzaehlung gibt: die Uebersicht meldete vorher jeden beendeten
 * Container als Problem. Auf host waren das dauerhaft elf, und alle elf sind
 * Absicht (`ai-llm` als Rueckfall, `schach` auf Abruf). Eine Liste, in der
 * nichts stimmt, liest niemand mehr.
 */
enum class ServiceZustand {
    /** Laeuft und meldet keinen Fehlbefund. */
    LAEUFT,

    /** Laeuft, aber der Healthcheck schlaegt an. Der einzige echte Alarm. */
    AUFFAELLIG,

    /** Steht still, und die control-map erwartet genau das. */
    BEWUSST_AUS,

    /** Steht still, obwohl er laufen soll. */
    AUSGEFALLEN,

    /**
     * Steht still, und es gibt keine Erwartung dazu. Tritt bei Remote-Hosts auf
     * (die control-map fuehrt `meta` nur fuer host) und bei Containern ohne
     * Eintrag. Aus dem Nichtwissen einen Ausfall zu machen waere eine
     * Behauptung, also sagt die App „nicht beurteilbar".
     */
    UNBEWERTET,
}

/** Ein Container, wie ihn /v1/ops/services liefert. */
@Serializable
data class ServiceSummary(
    val host: String = "",
    val name: String,
    val status: String = "unknown",
    /**
     * Health-Befund, aber nur solange der Container laeuft. Das Gateway setzt
     * ihn fuer beendete Container bewusst auf `null`: Docker behaelt den
     * letzten Befund nach dem Stoppen bei, sonst stuenden hier zehn
     * `unhealthy`, die alle nur beendet sind.
     */
    val health: String? = null,
    val image: String? = null,
    val ports: List<String> = emptyList(),
    /** '' = soll laufen · 'stopped'/'manual'/'migrated'/'job' = steht bewusst still. */
    @SerialName("expected_state") val expectedState: String? = null,
    /** Ob die control-map den Namen ueberhaupt kennt. `null` bei Remote-Hosts. */
    @SerialName("in_control_map") val inControlMap: Boolean? = null,
) {
    val isRunning: Boolean get() = status.equals("running", ignoreCase = true)
    val isUnhealthy: Boolean get() = health?.equals("unhealthy", ignoreCase = true) == true
    val isRestarting: Boolean get() = status.equals("restarting", ignoreCase = true)

    val zustand: ServiceZustand
        get() = when {
            isRunning && isUnhealthy -> ServiceZustand.AUFFAELLIG
            isRunning -> ServiceZustand.LAEUFT
            // Eine Neustartschleife bleibt ein Befund, auch wenn die control-map
            // den Dienst als gestoppt fuehrt: „aus" und „kommt nicht hoch" sind
            // zwei verschiedene Lagen.
            isRestarting -> ServiceZustand.AUFFAELLIG
            !expectedState.isNullOrBlank() -> ServiceZustand.BEWUSST_AUS
            inControlMap == true -> ServiceZustand.AUSGEFALLEN
            else -> ServiceZustand.UNBEWERTET
        }

    /** Braucht Aufmerksamkeit. Alles andere gehoert nicht in die Problemliste. */
    val istProblem: Boolean
        get() = zustand == ServiceZustand.AUFFAELLIG || zustand == ServiceZustand.AUSGEFALLEN

    /** Kurzform fuer die Liste, z. B. „laeuft · unhealthy" oder „aus (stopped)". */
    fun zustandsText(): String = when (zustand) {
        ServiceZustand.LAEUFT -> health?.let { "läuft · $it" } ?: "läuft"
        ServiceZustand.AUFFAELLIG -> if (isRestarting) "startet immer wieder neu" else "läuft · unhealthy"
        ServiceZustand.BEWUSST_AUS -> "aus (${expectedState.orEmpty()})"
        ServiceZustand.AUSGEFALLEN -> "$status · soll laufen"
        ServiceZustand.UNBEWERTET -> "$status · keine Erwartung hinterlegt"
    }
}

/**
 * Zaehlstand eines steuerbaren Docker-Hosts (/v1/ops/hosts).
 *
 * Ersetzt fuer die Uebersicht das Herunterladen aller Container. `null` steht
 * hier ueberall fuer „nicht beurteilbar", nie fuer null Stueck: ein
 * unerreichbarer Host liefert gar keine Zahlen, und `gestopptErwartet` kann nur
 * der lokale Host beantworten.
 */
@Serializable
data class HostRollup(
    val name: String,
    val erreichbar: Boolean = false,
    val gesamt: Int? = null,
    val laufend: Int? = null,
    val auffaellig: Int? = null,
    @SerialName("bewusst_aus") val bewusstAus: Int? = null,
    val ausgefallen: Int? = null,
    val unbewertet: Int? = null,
) {
    /**
     * Was hier Aufmerksamkeit braucht. Unbeurteilbares zaehlt bewusst nicht
     * mit: sonst meldete jeder Remote-Host seine gestoppten Container als
     * Ausfall, obwohl niemand eine Erwartung dazu hinterlegt hat.
     */
    val problemZahl: Int get() = (auffaellig ?: 0) + (ausgefallen ?: 0)
}

@Serializable
data class HostsResponse(val hosts: List<HostRollup> = emptyList())

/** Antwort auf eine start/stop/restart-Aktion (dev-portal-Durchreichung). */
@Serializable
data class ServiceActionResult(
    val name: String? = null,
    val host: String? = null,
    val status: String? = null,
)

@Serializable
data class LogsResponse(
    val name: String? = null,
    val host: String? = null,
    val logs: String = "",
)
