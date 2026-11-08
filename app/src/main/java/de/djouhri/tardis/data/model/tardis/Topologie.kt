package de.djouhri.tardis.data.model.tardis

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Topologie wie die Web-Flaeche, verschlankt von mobile-api (/v1/ops/topologie,
 * seit 2.0.16). Den Zustand jedes Dienstes rechnet der Dienst in vier Stufen,
 * die App zeichnet nur.
 */
@Serializable
data class TopoWirt(
    val id: String,
    val rolle: String? = null,
    val status: String? = null,
    val online: Boolean? = null,
    val cpu: Double? = null,
    val ram: Double? = null,
    val platte: Double? = null,
    val dienste: Int = 0,
    // Wirt-Blatt (seit 2.0.20); aeltere mobile-api liefert sie nicht, dann null.
    val ip: String? = null,
    val temp: Double? = null,
    val load: Double? = null,
    @SerialName("backup_alter_h") val backupAlterH: Double? = null,
    @SerialName("backup_alt") val backupAlt: Boolean? = null,
    @SerialName("drift_geister") val driftGeister: Int? = null,
    @SerialName("drift_fehlend") val driftFehlend: Int? = null,
    val container: Int? = null,
)

@Serializable
data class TopoDienst(
    val id: String,
    val label: String,
    val wirt: String,
    val gruppe: String? = null,
    /** laeuft | bewusst_aus | aus | unbekannt */
    val zustand: String = "unbekannt",
    val status: String? = null,
    val erwartung: String? = null,
    val klasse: String? = null,
    val container: String? = null,
    /** oeffentlich | geraete | lokal, null wenn unbekannt */
    val erreichbarkeit: String? = null,
    val port: Int? = null,
    val url: String? = null,
) {
    val istProblem: Boolean get() = zustand == "aus" || zustand == "unbekannt"
}

@Serializable
data class TopoVerbindung(val von: String, val nach: String, val art: String = "", val label: String? = null)

@Serializable
data class TopologieResponse(
    val stand: String? = null,
    val wirte: List<TopoWirt> = emptyList(),
    val dienste: List<TopoDienst> = emptyList(),
    val verbindungen: List<TopoVerbindung> = emptyList(),
    val gesundheit: Int? = null,
    val fehler: String? = null,
)

/**
 * Was im Kopf eines Wirt-Kastens steht: Zahl der Dienste, davon auffaellig, und
 * die Last, soweit gemessen. Fuer Kaesten, die die Wirtliste nicht kennt
 * (Dienste an fremdem Wirt), ist [wirt] null.
 */
data class WirtKopf(val dienste: Int, val auffaellig: Int, val online: Boolean?, val last: String?)

fun wirtKopf(wirt: TopoWirt?, dienste: List<TopoDienst>): WirtKopf {
    val last = listOfNotNull(
        wirt?.cpu?.let { "CPU ${it.toInt()} %" },
        wirt?.ram?.let { "RAM ${it.toInt()} %" },
    ).joinToString(" · ").ifEmpty { null }
    return WirtKopf(dienste.size, dienste.count { it.istProblem }, wirt?.online, last)
}

/**
 * Suche ueber Name, Kennung, Wirt und Gruppe, ohne Gross/Klein. Treffer am
 * Namensanfang zuerst, dann alphabetisch: wer „gi“ tippt, will gitea oben sehen.
 */
fun sucheDienste(dienste: List<TopoDienst>, anfrage: String): List<TopoDienst> {
    val q = anfrage.trim().lowercase()
    if (q.isEmpty()) return emptyList()
    return dienste
        .filter { d -> listOfNotNull(d.label, d.id, d.wirt, d.gruppe, d.container).any { q in it.lowercase() } }
        .sortedWith(compareBy<TopoDienst> { !it.label.lowercase().startsWith(q) }.thenBy { it.label.lowercase() })
}

/** Auffaellige Dienste fuer die Liste: rot vor gelb, dann nach Wirt und Name. */
fun auffaelligeDienste(dienste: List<TopoDienst>): List<TopoDienst> =
    dienste.filter { it.istProblem }
        .sortedWith(compareBy<TopoDienst> { if (it.zustand == "aus") 0 else 1 }.thenBy { it.wirt }.thenBy { it.label.lowercase() })

/**
 * Abhaengigkeitskette eines Dienstes ueber `depends`-Verbindungen. Die Quelle
 * zeigt vom Abhaengigen auf das, was er braucht (`immich-server -> immich-postgres`).
 * [braucht]/[betroffen] sind die direkten Nachbarn, [brauchtWeiter]/[betroffenWeiter]
 * alles, was transitiv dahinter liegt (ohne die direkten).
 */
data class Kette(
    val braucht: Set<String>,
    val brauchtWeiter: Set<String>,
    val betroffen: Set<String>,
    val betroffenWeiter: Set<String>,
) {
    val alle: Set<String> get() = braucht + brauchtWeiter + betroffen + betroffenWeiter
}

private fun erreichbar(start: String, nachbarn: Map<String, List<String>>): Set<String> {
    val gesehen = LinkedHashSet<String>()
    val offen = ArrayDeque(nachbarn[start].orEmpty())
    while (offen.isNotEmpty()) {
        val n = offen.removeFirst()
        if (n == start || !gesehen.add(n)) continue
        offen.addAll(nachbarn[n].orEmpty())
    }
    return gesehen
}

fun abhaengigkeitsKette(id: String, verbindungen: List<TopoVerbindung>): Kette {
    val dep = verbindungen.filter { it.art == "depends" }
    val brauchtVon = dep.groupBy({ it.von }, { it.nach })
    val gebrauchtVon = dep.groupBy({ it.nach }, { it.von })
    val braucht = brauchtVon[id].orEmpty().toSet() - id
    val betroffen = gebrauchtVon[id].orEmpty().toSet() - id
    return Kette(
        braucht = braucht,
        brauchtWeiter = erreichbar(id, brauchtVon) - braucht,
        betroffen = betroffen,
        betroffenWeiter = erreichbar(id, gebrauchtVon) - betroffen,
    )
}

/**
 * Dienste, die selbst laufen, aber (transitiv) einen Dienst brauchen, der aus
 * ist, obwohl er laufen soll. Sie sind gruen und trotzdem kaputt.
 */
fun mitBetroffen(dienste: List<TopoDienst>, verbindungen: List<TopoVerbindung>): Set<String> {
    val gebrauchtVon = verbindungen.filter { it.art == "depends" }.groupBy({ it.nach }, { it.von })
    val zustand = dienste.associate { it.id to it.zustand }
    return dienste.filter { it.zustand == "aus" }
        .flatMap { erreichbar(it.id, gebrauchtVon) }
        .filter { zustand[it] == "laeuft" }
        .toSet()
}

/** Wie die Karte einfaerbt. */
enum class Linse(val titel: String) {
    ZUSTAND("Zustand"),
    ERREICHBARKEIT("Erreichbarkeit"),
    KLASSE("Klasse"),
}

/** Schluessel, nach dem eine Linse einen Dienst einordnet; die Farbe macht die Oberflaeche. */
fun linsenWert(d: TopoDienst, linse: Linse): String = when (linse) {
    Linse.ZUSTAND -> d.zustand
    Linse.ERREICHBARKEIT -> d.erreichbarkeit ?: "unbekannt"
    Linse.KLASSE -> d.klasse?.uppercase()?.takeIf { it in setOf("CORE", "OPS", "PUBLIC", "INTERNAL") } ?: "sonst"
}

/** Deutsche Namen der Verbindungsarten, in der Reihenfolge der Auswahl. */
val VERBINDUNGS_ARTEN = linkedMapOf(
    "depends" to "Abhängigkeit",
    "public" to "Öffentlich",
    "lan" to "LAN",
    "backup" to "Backup",
    "event" to "Ereignis",
    "adapter" to "Adapter",
)

/** Was die App beim letzten Blick gesehen hat: Zustand je Dienst. */
@Serializable
data class TopoSchnappschuss(val standMs: Long, val zustaende: Map<String, String>)

fun schnappschuss(t: TopologieResponse, jetztMs: Long): TopoSchnappschuss =
    TopoSchnappschuss(jetztMs, t.dienste.associate { it.id to it.zustand })

data class TopoAenderungen(
    val seitMs: Long,
    /** war ok oder bewusst aus, ist jetzt aus/unbekannt */
    val neuAuffaellig: List<String>,
    /** war aus/unbekannt, laeuft jetzt oder ist bewusst aus */
    val wiederGut: List<String>,
    val neu: List<String>,
    val weg: List<String>,
) {
    val leer: Boolean get() = neuAuffaellig.isEmpty() && wiederGut.isEmpty() && neu.isEmpty() && weg.isEmpty()
    /** Dienste, die auf der Karte pulsieren (weg ist nicht mehr da). */
    val sichtbar: Set<String> get() = (neuAuffaellig + wiederGut + neu).toSet()
}

private fun auffaellig(zustand: String) = zustand == "aus" || zustand == "unbekannt"

fun aenderungen(alt: TopoSchnappschuss, t: TopologieResponse): TopoAenderungen {
    val jetzt = t.dienste.associate { it.id to it.zustand }
    return TopoAenderungen(
        seitMs = alt.standMs,
        neuAuffaellig = jetzt.filter { (id, z) -> auffaellig(z) && alt.zustaende[id]?.let { !auffaellig(it) } == true }.keys.sorted(),
        wiederGut = jetzt.filter { (id, z) -> !auffaellig(z) && alt.zustaende[id]?.let { auffaellig(it) } == true }.keys.sorted(),
        neu = (jetzt.keys - alt.zustaende.keys).sorted(),
        weg = (alt.zustaende.keys - jetzt.keys).sorted(),
    )
}

/** „heute 14:02“, „gestern 14:02“, sonst „31.10. 14:02“. */
fun zeitpunktText(ms: Long, heute: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochMilli(ms).atZone(zone)
    val uhr = t.format(DateTimeFormatter.ofPattern("HH:mm"))
    return when (t.toLocalDate()) {
        heute -> "heute $uhr"
        heute.minusDays(1) -> "gestern $uhr"
        else -> t.format(DateTimeFormatter.ofPattern("dd.MM. HH:mm"))
    }
}

/** Rechteck in Kartenkoordinaten (dp). */
data class KartenRechteck(val x: Float, val y: Float, val b: Float, val h: Float) {
    val mitteX: Float get() = x + b / 2
    val mitteY: Float get() = y + h / 2
    fun ueberlappt(o: KartenRechteck): Boolean =
        x < o.x + o.b && o.x < x + b && y < o.y + o.h && o.y < y + h
}

data class KartenPunkt(val x: Float, val y: Float)

data class KartenUeberschrift(val text: String, val x: Float, val y: Float)

data class KartenLayout(
    val breite: Float,
    val hoehe: Float,
    val wirte: Map<String, KartenRechteck>,
    val dienste: Map<String, KartenPunkt>,
    val gruppen: List<KartenUeberschrift>,
) {
    /** Mittelpunkt eines Knotens, egal ob Wirt oder Dienst. */
    fun mitte(id: String): KartenPunkt? =
        dienste[id] ?: wirte[id]?.let { KartenPunkt(it.mitteX, it.mitteY) }
}

/**
 * Festes Raster statt Kraeftesimulation: auf dem Telefon soll ein Dienst bei
 * jedem Oeffnen an derselben Stelle stehen, sonst sucht man ihn jedes Mal neu.
 *
 * Grosse Wirte (ab [GROSS_AB] Diensten) bekommen die volle Breite und darin je
 * Gruppe einen Block; kleine stehen zu zweit nebeneinander; Wirte ohne Dienste
 * (Internet, Cloudflare, Proxmox-Knoten) als schmale Kaestchen zu dritt.
 */
fun kartenLayout(t: TopologieResponse): KartenLayout {
    val jeWirt = t.dienste.groupBy { it.wirt }
    val reihenfolge = t.wirte.sortedWith(compareByDescending<TopoWirt> { jeWirt[it.id]?.size ?: 0 }.thenBy { it.id })
    // Dienste an Wirten, die die Liste nicht kennt, bekommen einen eigenen Kasten.
    val fremde = jeWirt.keys.filter { id -> t.wirte.none { it.id == id } }.sorted().map { TopoWirt(it) }
    val alle = reihenfolge.filter { (jeWirt[it.id]?.size ?: 0) > 0 } + fremde + reihenfolge.filter { (jeWirt[it.id]?.size ?: 0) == 0 }

    val wirte = LinkedHashMap<String, KartenRechteck>()
    val dienste = HashMap<String, KartenPunkt>()
    val gruppen = ArrayList<KartenUeberschrift>()
    var y = 0f

    fun punkteBlock(liste: List<TopoDienst>, x0: Float, y0: Float, spalten: Int): Float {
        liste.forEachIndexed { i, d ->
            val sp = i % spalten
            val zeile = i / spalten
            dienste[d.id] = KartenPunkt(x0 + ZELLE / 2 + sp * ZELLE, y0 + ZELLE / 2 + zeile * ZELLE)
        }
        val zeilen = (liste.size + spalten - 1) / spalten
        return zeilen * ZELLE
    }

    // 1) Grosse Wirte, volle Breite, Gruppen untereinander.
    for (w in alle.filter { (jeWirt[it.id]?.size ?: 0) >= GROSS_AB }) {
        val start = y
        var innen = y + KOPF
        val nachGruppe = jeWirt.getValue(w.id).sortedBy { it.label.lowercase() }
            .groupBy { it.gruppe ?: "Ohne Gruppe" }.toSortedMap(compareBy { it.lowercase() })
        for ((name, liste) in nachGruppe) {
            gruppen += KartenUeberschrift("$name · ${liste.size}", RAND + INNEN, innen + 12f)
            innen += GRUPPEN_KOPF
            innen += punkteBlock(liste, RAND + INNEN, innen, GROSS_SPALTEN) + 6f
        }
        wirte[w.id] = KartenRechteck(RAND, start, BREITE - 2 * RAND, innen - start + INNEN)
        y = innen + INNEN + ABSTAND
    }

    // 2) Kleine Wirte mit Diensten, zu zweit.
    val klein = alle.filter { (jeWirt[it.id]?.size ?: 0) in 1 until GROSS_AB }
    val halb = (BREITE - 2 * RAND - ABSTAND) / 2
    klein.chunked(2).forEach { paar ->
        var zeilenHoehe = 0f
        paar.forEachIndexed { i, w ->
            val x = RAND + i * (halb + ABSTAND)
            val h = KOPF + punkteBlock(jeWirt.getValue(w.id).sortedBy { it.label.lowercase() }, x + INNEN, y + KOPF, KLEIN_SPALTEN) + INNEN
            wirte[w.id] = KartenRechteck(x, y, halb, h)
            zeilenHoehe = maxOf(zeilenHoehe, h)
        }
        y += zeilenHoehe + ABSTAND
    }

    // 3) Wirte ohne Dienste, zu dritt.
    val drittel = (BREITE - 2 * RAND - 2 * ABSTAND) / 3
    alle.filter { (jeWirt[it.id]?.size ?: 0) == 0 }.chunked(3).forEach { reihe ->
        reihe.forEachIndexed { i, w ->
            wirte[w.id] = KartenRechteck(RAND + i * (drittel + ABSTAND), y, drittel, LEER_HOEHE)
        }
        y += LEER_HOEHE + ABSTAND
    }
    return KartenLayout(BREITE, y, wirte, dienste, gruppen)
}

private const val BREITE = 360f
private const val RAND = 8f
private const val INNEN = 8f
private const val ABSTAND = 10f
private const val KOPF = 28f
private const val GRUPPEN_KOPF = 18f
private const val ZELLE = 30f
private const val GROSS_AB = 20
private const val GROSS_SPALTEN = 10
private const val KLEIN_SPALTEN = 5
private const val LEER_HOEHE = 34f
