package de.djouhri.tardis.data.update

object UpdateRegeln {

    /**
     * Abstand zwischen zwei selbsttaetigen Pruefungen.
     *
     * Sechs Stunden, nicht bei jedem Start: die Pruefung geht ueber WireGuard
     * ans Gateway, und wer die App zehnmal am Tag oeffnet, soll nicht zehnmal
     * eine Verbindung aufbauen, die er nicht angefordert hat.
     */
    const val PRUEF_ABSTAND_MS: Long = 6 * 60 * 60 * 1000L

    /** true, wenn seit der letzten Pruefung genug Zeit vergangen ist. */
    fun darfPruefen(jetzt: Long, letztePruefung: Long): Boolean =
        jetzt - letztePruefung >= PRUEF_ABSTAND_MS

    /**
     * Nur eine hoehere Nummer ist ein Update.
     *
     * Gleichstand ist kein Update, und eine NIEDRIGERE Nummer erst recht nicht:
     * Android lehnt ein Downgrade ohnehin ab, und ein Angebot, das sich nicht
     * installieren laesst, sieht wie ein Fehler der App aus.
     */
    fun istNeuer(angeboten: Int, installiert: Int): Boolean = angeboten > installiert

    fun hashUrteil(erwartet: String?, gemessen: String): HashUrteil {
        val soll = erwartet?.trim()?.lowercase().orEmpty()
        if (soll.isEmpty()) return HashUrteil.OHNE_VERGLEICH
        return if (soll == gemessen.trim().lowercase()) HashUrteil.PASST else HashUrteil.ABWEICHUNG
    }
}

enum class HashUrteil {
    PASST,

    ABWEICHUNG,

    /** Der Server hat keinen Hash genannt. Kein Urteil, und das wird gesagt. */
    OHNE_VERGLEICH,
}
