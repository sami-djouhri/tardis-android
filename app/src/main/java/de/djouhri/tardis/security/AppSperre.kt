package de.djouhri.tardis.security

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
