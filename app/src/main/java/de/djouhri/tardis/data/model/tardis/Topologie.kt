package de.djouhri.tardis.data.model.tardis

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
) {
    val istProblem: Boolean get() = zustand == "aus" || zustand == "unbekannt"
}

@Serializable
data class TopoVerbindung(val von: String, val nach: String, val art: String = "")

@Serializable
data class TopologieResponse(
    val stand: String? = null,
    val wirte: List<TopoWirt> = emptyList(),
    val dienste: List<TopoDienst> = emptyList(),
    val verbindungen: List<TopoVerbindung> = emptyList(),
    val gesundheit: Int? = null,
    val fehler: String? = null,
)

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
            gruppen += KartenUeberschrift(name, RAND + INNEN, innen + 12f)
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
