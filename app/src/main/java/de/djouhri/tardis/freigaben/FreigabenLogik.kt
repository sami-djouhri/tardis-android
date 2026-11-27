package de.djouhri.tardis.freigaben

import de.djouhri.tardis.data.model.tardis.Befehl
import de.djouhri.tardis.data.model.tardis.BefehleAntwort
import de.djouhri.tardis.data.model.tardis.FreigabenAntwort
import de.djouhri.tardis.data.model.tardis.HausbusAntwort
import de.djouhri.tardis.data.model.tardis.VerlaufEintrag
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Rechnungen fuer Freigaben ohne Android: Zaehler im Kopf, Erinnerung nach 24 h,
 * Texte im Verlauf und was der Waechter neu melden soll. Getestet in FreigabenLogikTest.
 */
object FreigabenLogik {

    /** Ab hier erinnert der Waechter noch einmal und die Karte zeigt die Wartezeit gelb. */
    val ERINNERN_NACH: Duration = Duration.ofHours(24)

    data class Zaehler(
        val deploys: Int,
        val befehleOffen: Int,
        val laufen: Int,
        val gescheitert: Int,
        val hausbus: Int = 0,
    ) {
        val leer: Boolean get() = deploys == 0 && befehleOffen == 0 && laufen == 0 && gescheitert == 0 && hausbus == 0
    }

    fun zaehler(a: FreigabenAntwort?, b: BefehleAntwort?, h: HausbusAntwort? = null): Zaehler {
        val befehle = b?.befehle.orEmpty()
        return Zaehler(
            deploys = a?.wartend?.size ?: 0,
            befehleOffen = befehle.count { it.offen },
            laufen = befehle.count { it.laeuft || it.wartet },
            gescheitert = befehle.count { it.gescheitert && it.abgelehnt == null },
            hausbus = h?.freigaben?.size ?: 0,
        )
    }

    /** Restzeit bis zum Verfall einer Hausbus-Freigabe; `null` bei fehlender oder vergangener Zeit. */
    fun verfaelltIn(verfaellt: String, jetzt: Instant = Instant.now()): Duration? {
        val ende = runCatching { OffsetDateTime.parse(verfaellt).toInstant() }.getOrNull() ?: return null
        val d = Duration.between(jetzt, ende)
        return if (d.isNegative) null else d
    }

    /** "noch 5 h", "noch 40 min"; unter einer Stunde soll die Karte warnen. */
    fun restText(d: Duration): String = if (d.toHours() >= 1) "noch ${d.toHours()} h" else "noch ${d.toMinutes()} min"

    fun wartezeit(seit: String?, jetzt: Instant = Instant.now()): Duration? {
        val beginn = seit?.let { runCatching { OffsetDateTime.parse(it).toInstant() }.getOrNull() } ?: return null
        val d = Duration.between(beginn, jetzt)
        return if (d.isNegative) null else d
    }

    fun wartetLange(seit: String?, jetzt: Instant = Instant.now()): Boolean =
        wartezeit(seit, jetzt)?.let { it >= ERINNERN_NACH } == true

    private val TAG_UHRZEIT = DateTimeFormatter.ofPattern("d.M. HH:mm", Locale.GERMANY)

    fun zeitText(sekunden: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        TAG_UHRZEIT.format(Instant.ofEpochSecond(sekunden).atZone(zone))

    /** Eine Zeile je Eintrag, ohne Titel und Zeit (die stehen daneben). */
    fun verlaufText(e: VerlaufEintrag): String = when (e.aktion) {
        "freigegeben" -> when (e.art) {
            "deploy" -> "freigegeben${e.betreff?.let { ": $it" } ?: ""}"
            "hausbus" -> if (e.zustand == "ok") "freigegeben, Hausbus hat es ausgeführt" else "freigegeben"
            else -> "zum Lauf freigegeben"
        }
        "probe" -> "Probelauf freigegeben"
        "abbruch" -> "Abbruch unterschrieben"
        "abgelehnt" -> "abgelehnt${e.grund?.let { ": $it" } ?: ""}"
        "ergebnis" -> {
            val wie = if (e.modus == "probe") "Probelauf" else "Lauf"
            when (e.zustand) {
                "ok" -> "$wie gelungen, Exit 0"
                "abgebrochen" -> "$wie abgebrochen"
                "abgelehnt" -> "vom Läufer abgewiesen${e.grund?.let { ": $it" } ?: ""}"
                else -> "$wie gescheitert, Exit ${e.rc ?: "?"}"
            }
        }
        else -> e.aktion
    }

    /** gruen, gelb, rot oder neutral fuer die Farbe einer Verlaufszeile. */
    fun verlaufStufe(e: VerlaufEintrag): String = when {
        e.aktion == "ergebnis" && e.zustand == "ok" -> "gruen"
        e.aktion == "ergebnis" && e.zustand == "abgebrochen" -> "gelb"
        e.aktion == "ergebnis" -> "rot"
        e.aktion == "abgelehnt" -> "gelb"
        else -> "neutral"
    }

    /** Kennung einer offenen Fassung: eine geaenderte meldet neu. */
    fun offenSchluessel(b: Befehl): String = "${b.wirt}/${b.id}:${b.sha256}"

    /** Kennung eines fertigen Ergebnisses (laeuft zaehlt nicht). */
    fun ergebnisSchluessel(b: Befehl): String? {
        val e = b.ergebnis ?: return null
        if (e.zustand == "laeuft" || e.veraltet) return null
        return "${b.wirt}/${b.id}:${b.sha256}:${e.ende ?: e.angenommen ?: 0}:${e.modus}"
    }

    data class Meldung(val neuOffen: List<Befehl>, val neuFertig: List<Befehl>)

    /**
     * Was neu ist gegenueber dem Gemerkten. Beim allerersten Lauf (`gemerkt == null`)
     * wird nichts gemeldet, nur gemerkt: sonst kaemen alle alten Ergebnisse auf einmal.
     */
    fun neuZuMelden(befehle: List<Befehl>, gemerktOffen: Set<String>?, gemerktFertig: Set<String>?): Meldung {
        val offen = befehle.filter { it.offen }
        val fertig = befehle.filter { ergebnisSchluessel(it) != null }
        val neuFertig = if (gemerktFertig == null) emptyList() else fertig.filter { ergebnisSchluessel(it) !in gemerktFertig }
        // Ein gerade gescheiterter Befehl ist wieder offen; er wird einmal gemeldet, als Ergebnis.
        val schonGemeldet = neuFertig.map { it.wirt to it.id }.toSet()
        return Meldung(
            neuOffen = if (gemerktOffen == null) emptyList()
            else offen.filter { offenSchluessel(it) !in gemerktOffen && (it.wirt to it.id) !in schonGemeldet },
            neuFertig = neuFertig,
        )
    }
}
