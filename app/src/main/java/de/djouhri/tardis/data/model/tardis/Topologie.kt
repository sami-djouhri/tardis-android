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
    WIRT("Wirt"),
}

/** Schluessel, nach dem eine Linse einen Dienst einordnet; die Farbe macht die Oberflaeche. */
fun linsenWert(d: TopoDienst, linse: Linse): String = when (linse) {
    Linse.ZUSTAND -> d.zustand
    Linse.ERREICHBARKEIT -> d.erreichbarkeit ?: "unbekannt"
    Linse.KLASSE -> d.klasse?.uppercase()?.takeIf { it in setOf("CORE", "OPS", "PUBLIC", "INTERNAL") } ?: "sonst"
    Linse.WIRT -> d.wirt
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


/**
 * „Was waere, wenn X aus ist?“ wie `durchspielen()` im Web (kette.ts). Ein Wirt
 * nimmt alles mit, was auf ihm laeuft, danach folgt die Rechnung den
 * depends-Verbindungen. Nur was gerade laeuft, kann mit ausfallen: was schon
 * aus ist, ist kein neuer Schaden.
 */
data class Durchspiel(
    /** Was im Gedankenspiel ausfaellt: der gewaehlte Knoten, bei einem Wirt auch alles darauf. */
    val aus: Set<String>,
    /** Wer deshalb mit ausfaellt -> die ausgefallenen Ursachen. */
    val folgen: Map<String, List<String>>,
    val istWirt: Boolean,
)

fun durchspielen(id: String, t: TopologieResponse): Durchspiel {
    val istWirt = t.wirte.any { it.id == id } || (t.dienste.none { it.id == id } && t.dienste.any { it.wirt == id })
    val aus = LinkedHashSet<String>().apply {
        add(id)
        if (istWirt) t.dienste.filter { it.wirt == id }.forEach { add(it.id) }
    }
    val gebrauchtVon = t.verbindungen.filter { it.art == "depends" }.groupBy({ it.nach }, { it.von })
    val zustand = t.dienste.associate { it.id to it.zustand }
    val folgen = LinkedHashMap<String, MutableList<String>>()
    for (u in aus) {
        for (abh in erreichbar(u, gebrauchtVon)) {
            if (abh in aus || zustand[abh] != "laeuft") continue
            val liste = folgen.getOrPut(abh) { mutableListOf() }
            if (u !in liste) liste += u
        }
    }
    return Durchspiel(aus, folgen, istWirt)
}

/**
 * Stand zu einem frueheren Zeitpunkt, gerechnet von mobile-api
 * (/v1/ops/topologie/zeitpunkt) wie `zeitpunkt.ts` im Web. Linien zeigen
 * weiter den heutigen Stand: Topologie-Schnappschuesse gibt es keine.
 */
@Serializable
data class TopoZeitpunkt(
    val vor: String = "",
    val at: Double? = null,
    /** Dienst-ID -> lief | ungesund | fehlte | startete | lief_nicht | unbekannt */
    val zustaende: Map<String, String> = emptyMap(),
    /** Wirt -> erreichbar | nicht_erreichbar; leer, wenn damals nicht gemessen */
    val wirte: Map<String, String> = emptyMap(),
    @SerialName("ohne_laufdaten") val ohneLaufdaten: List<String> = emptyList(),
    val fehler: String? = null,
) {
    /** Damals ungesund oder gegen das Soll fehlend; ein schlafender Abruf-Dienst ist kein Befund. */
    val auffaellig: List<String> get() = zustaende.filterValues { it == "ungesund" || it == "fehlte" }.keys.sorted()
}

/** Was die Zeitpunkt-Auswahl anbietet: Schluessel fuer mobile-api und Text. */
val ZEITPUNKTE = linkedMapOf("1h" to "vor 1 Stunde", "1d" to "vor 1 Tag")

val ZEIT_TEXT = mapOf(
    "lief" to "lief",
    "ungesund" to "ungesund",
    "fehlte" to "fehlte gegen Soll",
    "startete" to "startete gerade",
    "lief_nicht" to "lief nicht",
    "unbekannt" to "keine Daten",
    "erreichbar" to "erreichbar",
    "nicht_erreichbar" to "nicht erreichbar",
)

/**
 * Netz-Ansicht wie der Graph in Obsidian (Web: Layout „Netz“): jeder Wirt und
 * jeder Dienst ein Punkt, Dienste haengen an ihrem Wirt, Verbindungen ziehen
 * zusaetzlich. Punktgroesse nach Zahl der Verbindungen.
 */
data class NetzLayout(
    val breite: Float,
    val hoehe: Float,
    val punkte: Map<String, KartenPunkt>,
    val radien: Map<String, Float>,
    /** Welche Punkte Wirte sind (auch Wirte, die nur als `wirt` eines Dienstes vorkommen). */
    val wirte: Set<String>,
)

/**
 * Kraefte-Layout ohne Zufall: Wirte starten auf einem Kreis, ihre Dienste auf
 * einem kleinen Kreis um sie, alles nach Kennung sortiert. Gleiche Daten ergeben
 * damit bei jedem Oeffnen dasselbe Bild, ein Dienst steht immer an derselben
 * Stelle. Danach Fruchterman-Reingold mit Abkuehlung und leichtem Zug zur Mitte.
 * Ist das Ergebnis breiter als hoch, wird es gedreht: das Telefon ist hochkant.
 */
fun netzLayout(t: TopologieResponse, runden: Int = 260): NetzLayout {
    val wirtIds = (t.wirte.map { it.id } + t.dienste.map { it.wirt }).distinct().sorted()
    val wirtSet = wirtIds.toSet()
    val dienste = t.dienste.filter { it.id !in wirtSet }.sortedBy { it.id }
    val ids = wirtIds + dienste.map { it.id }
    val index = ids.withIndex().associate { (i, id) -> id to i }
    val n = ids.size
    if (n == 0) return NetzLayout(RAND_NETZ * 2, RAND_NETZ * 2, emptyMap(), emptyMap(), emptySet())

    // Kanten: Zugehoerigkeit zum Wirt (kurz) und echte Verbindungen (laenger).
    data class Feder(val a: Int, val b: Int, val laenge: Float, val staerke: Float)
    val federn = ArrayList<Feder>()
    val grad = IntArray(n)
    dienste.forEach { d ->
        val a = index.getValue(d.id); val b = index.getValue(d.wirt)
        federn += Feder(a, b, KANTE * 0.8f, 1f)
    }
    t.verbindungen.forEach { v ->
        val a = index[v.von] ?: return@forEach
        val b = index[v.nach] ?: return@forEach
        if (a == b) return@forEach
        grad[a]++; grad[b]++
        federn += Feder(a, b, KANTE * 1.6f, 0.35f)
    }

    val x = FloatArray(n); val y = FloatArray(n)
    val ring = KANTE * 2.2f * kotlin.math.sqrt(wirtIds.size.toFloat())
    wirtIds.forEachIndexed { i, _ ->
        val w = 2 * Math.PI * i / wirtIds.size
        x[i] = (ring * kotlin.math.cos(w)).toFloat(); y[i] = (ring * kotlin.math.sin(w)).toFloat()
    }
    dienste.groupBy { it.wirt }.forEach { (wirt, liste) ->
        val m = index.getValue(wirt)
        liste.forEachIndexed { j, d ->
            val w = 2 * Math.PI * j / liste.size
            val r = KANTE * (0.6f + 0.15f * (j % 4))
            val k = index.getValue(d.id)
            x[k] = x[m] + (r * kotlin.math.cos(w)).toFloat(); y[k] = y[m] + (r * kotlin.math.sin(w)).toFloat()
        }
    }

    val dx = FloatArray(n); val dy = FloatArray(n)
    val abstossung = KANTE * KANTE
    // Abstossung nur in der Naehe: ohne Grenze treiben Wirte ohne Verbindung
    // weit hinaus und die Wolken schrumpfen auf dem Telefon zu Staub
    // (Punktabstand im Mittel 5 dp statt 16 dp, nachgerechnet mit 280 Knoten).
    val reichweite2 = (KANTE * 6f) * (KANTE * 6f)
    var temperatur = KANTE * 2f
    repeat(runden) {
        java.util.Arrays.fill(dx, 0f); java.util.Arrays.fill(dy, 0f)
        for (i in 0 until n) {
            for (j in i + 1 until n) {
                var ex = x[i] - x[j]; var ey = y[i] - y[j]
                var d2 = ex * ex + ey * ey
                if (d2 > reichweite2) continue
                if (d2 < 0.01f) { ex = 0.1f * ((i - j) % 3); ey = 0.1f; d2 = 0.02f }
                val f = abstossung / d2
                dx[i] += ex * f; dy[i] += ey * f
                dx[j] -= ex * f; dy[j] -= ey * f
            }
            // Zug zur Mitte haelt lose Inseln (Wirte ohne Dienste) beim Rest.
            dx[i] -= x[i] * 0.08f; dy[i] -= y[i] * 0.08f
        }
        for (fe in federn) {
            val ex = x[fe.a] - x[fe.b]; val ey = y[fe.a] - y[fe.b]
            val d = kotlin.math.sqrt(ex * ex + ey * ey).coerceAtLeast(0.01f)
            val f = (d - fe.laenge) / d * fe.staerke
            dx[fe.a] -= ex * f; dy[fe.a] -= ey * f
            dx[fe.b] += ex * f; dy[fe.b] += ey * f
        }
        for (i in 0 until n) {
            val l = kotlin.math.sqrt(dx[i] * dx[i] + dy[i] * dy[i])
            if (l > 0f) {
                val schritt = minOf(l, temperatur)
                x[i] += dx[i] / l * schritt; y[i] += dy[i] / l * schritt
            }
        }
        temperatur = maxOf(temperatur * 0.975f, 0.5f)
    }

    // Hochkant drehen, dann in den positiven Bereich mit Rand schieben.
    val breitRoh = (x.max() - x.min()) > (y.max() - y.min())
    val px = if (breitRoh) y else x
    val py = if (breitRoh) x else y
    val minX = px.min(); val minY = py.min()
    val radien = HashMap<String, Float>()
    val punkte = HashMap<String, KartenPunkt>()
    ids.forEachIndexed { i, id ->
        punkte[id] = KartenPunkt(px[i] - minX + RAND_NETZ, py[i] - minY + RAND_NETZ)
        val basis = if (id in wirtSet) 12f else 6f
        radien[id] = minOf(22f, basis + kotlin.math.sqrt(grad[i].toFloat()) * 1.6f)
    }
    return NetzLayout(px.max() - minX + 2 * RAND_NETZ, py.max() - minY + 2 * RAND_NETZ, punkte, radien, wirtSet)
}

private const val KANTE = 30f
private const val RAND_NETZ = 30f
