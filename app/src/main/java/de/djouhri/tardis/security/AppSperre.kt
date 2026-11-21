package de.djouhri.tardis.security

/**
 * Wann die App nach Biometrie fragt (Owner B84, bestaetigt B85): bei jedem
 * Start und nach 5 Minuten im Hintergrund. Reine Logik mit eingespeister Uhr,
 * damit sie ohne Geraet testbar ist.
 *
 * Der Zustand lebt im Prozess, nicht in der Activity: ein Drehen des Telefons
 * baut die Activity neu und darf nicht erneut fragen. Stirbt der Prozess, ist
 * die App beim naechsten Oeffnen wieder gesperrt, und genau so soll es sein.
 */
class AppSperre(private val uhr: () -> Long = System::currentTimeMillis) {

    private var entsperrt = false
    private var hintergrundSeit: Long? = null

    val gesperrt: Boolean get() = !entsperrt

    fun entsperren() {
        entsperrt = true
        hintergrundSeit = null
    }

    /** Activity.onStop: die App ist nicht mehr zu sehen. */
    fun inDenHintergrund() {
        if (entsperrt && hintergrundSeit == null) hintergrundSeit = uhr()
    }

    /** Activity.onStart: true, wenn jetzt neu gefragt werden muss. */
    fun inDenVordergrund(): Boolean {
        val seit = hintergrundSeit
        hintergrundSeit = null
        if (seit != null && uhr() - seit >= FRIST_MS) entsperrt = false
        return gesperrt
    }

    companion object {
        const val FRIST_MS = 5 * 60 * 1000L

        /** Eine Sperre je Prozess. */
        val prozess = AppSperre()
    }
}
