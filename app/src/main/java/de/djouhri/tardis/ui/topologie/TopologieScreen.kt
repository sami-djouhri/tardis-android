package de.djouhri.tardis.ui.topologie

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import de.djouhri.tardis.ui.components.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.tardis.data.local.TopologieGedaechtnis
import de.djouhri.tardis.data.model.tardis.Durchspiel
import de.djouhri.tardis.data.model.tardis.KartenLayout
import de.djouhri.tardis.data.model.tardis.KartenPunkt
import de.djouhri.tardis.data.model.tardis.NetzLayout
import de.djouhri.tardis.data.model.tardis.Kette
import de.djouhri.tardis.data.model.tardis.Linse
import de.djouhri.tardis.data.model.tardis.TopoAenderungen
import de.djouhri.tardis.data.model.tardis.TopoDienst
import de.djouhri.tardis.data.model.tardis.TopoSchnappschuss
import de.djouhri.tardis.data.model.tardis.TopoWirt
import de.djouhri.tardis.data.model.tardis.TopoZeitpunkt
import de.djouhri.tardis.data.model.tardis.TopologieResponse
import de.djouhri.tardis.data.model.tardis.VERBINDUNGS_ARTEN
import de.djouhri.tardis.data.model.tardis.WirtKopf
import de.djouhri.tardis.data.model.tardis.ZEITPUNKTE
import de.djouhri.tardis.data.model.tardis.ZEIT_TEXT
import de.djouhri.tardis.data.model.tardis.abhaengigkeitsKette
import de.djouhri.tardis.data.model.tardis.aenderungen
import de.djouhri.tardis.data.model.tardis.auffaelligeDienste
import de.djouhri.tardis.data.model.tardis.durchspielen
import de.djouhri.tardis.data.model.tardis.kartenLayout
import de.djouhri.tardis.data.model.tardis.linsenWert
import de.djouhri.tardis.data.model.tardis.mitBetroffen
import de.djouhri.tardis.data.model.tardis.netzLayout
import de.djouhri.tardis.data.model.tardis.schnappschuss
import de.djouhri.tardis.data.model.tardis.sucheDienste
import de.djouhri.tardis.data.model.tardis.wirtKopf
import de.djouhri.tardis.data.model.tardis.zeitpunktText
import de.djouhri.tardis.data.repository.OpsRepository
import de.djouhri.tardis.ui.components.ErrorMessage
import de.djouhri.tardis.ui.theme.StatusDown
import de.djouhri.tardis.ui.theme.StatusUp
import de.djouhri.tardis.ui.theme.StatusWarn
import de.djouhri.tardis.ui.theme.TextLeise
import de.djouhri.tardis.util.seitText
import de.djouhri.tardis.util.userMessage
import javax.inject.Inject
import kotlin.math.hypot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TopologieUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val antwort: TopologieResponse? = null,
    val layout: KartenLayout? = null,
    /** Netz-Ansicht (Kraefte-Layout); null, solange sie rechnet. */
    val netz: NetzLayout? = null,
    /** Was sich seit dem letzten Oeffnen geaendert hat; null beim allerersten Mal. */
    val aenderungen: TopoAenderungen? = null,
    /** Leises Auffrischen ging schief: die Karte zeigt weiter den alten Stand. */
    val auffrischenFehlt: Boolean = false,
    /** Gewaehlter Zeitpunkt (Schluessel aus [ZEITPUNKTE]); null = jetzt. */
    val zeitVor: String? = null,
    val zeitpunkt: TopoZeitpunkt? = null,
    val zeitLaedt: Boolean = false,
)

@HiltViewModel
class TopologieViewModel @Inject constructor(
    private val opsRepository: OpsRepository,
    private val gedaechtnis: TopologieGedaechtnis? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(TopologieUiState())
    val state = _state.asStateFlow()

    /**
     * Vergleichsbasis fuer diese Sitzung: der Stand beim LETZTEN Oeffnen. Sie
     * bleibt fest, waehrend die Karte sich auffrischt; gespeichert wird bei
     * jedem Abruf der neue Stand, der dann beim naechsten Oeffnen die Basis ist.
     */
    private var basis: TopoSchnappschuss? = null
    private var basisGelesen = false

    /** Das Netz haengt nur an Knoten und Linien; beim Auffrischen mit gleichem Aufbau nicht neu rechnen. */
    private var netzSchluessel: Int? = null

    /** [leise]: beim Auffrischen kein „Lade…“ und bei Fehler die alte Karte stehen lassen. */
    fun load(leise: Boolean = false) {
        if (!leise) _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            opsRepository.topologie().fold(
                onSuccess = { t ->
                    // Rund 260 Dienste: Raster und Netz nicht auf dem Hauptthread rechnen.
                    val schluessel = aufbau(t)
                    val alt = _state.value.netz.takeIf { schluessel == netzSchluessel }
                    val (layout, netz) = withContext(Dispatchers.Default) { kartenLayout(t) to (alt ?: netzLayout(t)) }
                    netzSchluessel = schluessel
                    if (!basisGelesen && t.fehler == null) {
                        basis = gedaechtnis?.lesen()
                        basisGelesen = true
                    }
                    val diff = basis?.let { aenderungen(it, t) }?.takeIf { !it.leer }
                    if (t.fehler == null) gedaechtnis?.schreiben(schnappschuss(t, System.currentTimeMillis()))
                    _state.update {
                        it.copy(loading = false, error = null, antwort = t, layout = layout, netz = netz, aenderungen = diff, auffrischenFehlt = false)
                    }
                },
                onFailure = { e ->
                    _state.update {
                        if (leise && it.antwort != null) it.copy(auffrischenFehlt = true)
                        else it.copy(loading = false, error = e.userMessage())
                    }
                },
            )
        }
    }

    private fun aufbau(t: TopologieResponse): Int =
        (t.wirte.map { it.id } + t.dienste.map { it.id + "@" + it.wirt } + t.verbindungen.map { it.von + ">" + it.nach }).hashCode()

    /** Zeitpunkt waehlen; null = zurueck auf jetzt. */
    fun zeitWaehlen(vor: String?) {
        _state.update { it.copy(zeitVor = vor, zeitpunkt = null, zeitLaedt = vor != null) }
        if (vor == null) return
        viewModelScope.launch {
            val z = opsRepository.topologieZeitpunkt(vor).getOrElse { e -> TopoZeitpunkt(vor = vor, fehler = e.userMessage()) }
            // Inzwischen anders gewaehlt: diese Antwort ist ueberholt.
            _state.update { if (it.zeitVor == vor) it.copy(zeitpunkt = z, zeitLaedt = false) else it }
        }
    }

    /** „Gesehen“: ab jetzt gilt der aktuelle Stand als Basis. */
    fun aenderungenGesehen() {
        val t = _state.value.antwort ?: return
        basis = schnappschuss(t, System.currentTimeMillis())
        _state.update { it.copy(aenderungen = null) }
    }
}

/** Wirte, deren Container die App steuern kann (wie die Dienste-Liste). */
private val STEUERBAR = setOf("host", "node1", "node2", "prodesk-ai")

@Composable
fun TopologieScreen(
    onOpenDetail: (host: String, name: String) -> Unit = { _, _ -> },
    onOpenWirte: () -> Unit = {},
    viewModel: TopologieViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.load() }
    // Geoeffnet und im Vordergrund jede Minute leise auffrischen; im Hintergrund nicht.
    val lebenszyklus = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lebenszyklus) {
        lebenszyklus.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(AUFFRISCHEN_MS)
                viewModel.load(leise = true)
            }
        }
    }
    val a = state.antwort
    val layout = state.layout
    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Lade Topologie…") }
        a == null || layout == null -> ErrorMessage(message = state.error ?: "Keine Antwort", onRetry = { viewModel.load() })
        a.fehler != null -> ErrorMessage(message = "Nicht gemessen: ${a.fehler}", onRetry = { viewModel.load() })
        else -> Karte(
            a, layout, onOpenDetail,
            netz = state.netz,
            aenderungen = state.aenderungen,
            auffrischenFehlt = state.auffrischenFehlt,
            onAenderungenGesehen = viewModel::aenderungenGesehen,
            onOpenWirte = onOpenWirte,
            zeitVor = state.zeitVor,
            zeitpunkt = state.zeitpunkt,
            zeitLaedt = state.zeitLaedt,
            onZeit = viewModel::zeitWaehlen,
        )
    }
}

private const val AUFFRISCHEN_MS = 60_000L

// Farben der Linsen und Linienarten. Bewusst nicht die Zustandsfarben
// (gruen/rot/gelb): in einer anderen Linse hiesse Rot sonst etwas anderes.
private val ORANGE = Color(0xFFE8833A)
private val BLAU = Color(0xFF5B9BD5)
private val TUERKIS = Color(0xFF56B6C2)
private val VIOLETT = Color(0xFFC678DD)
private val GRAUBLAU = Color(0xFF7A8A99)

/** Eine Farbe je Wirt fuer die Linse „Wirt“, nach Namen sortiert (also bei jedem Laden dieselbe). */
private val WIRT_PALETTE = listOf(
    Color(0xFF5B9BD5), Color(0xFFE8833A), Color(0xFF56B6C2), Color(0xFFC678DD), Color(0xFFD4B95E),
    Color(0xFF98C379), Color(0xFFE06C9F), Color(0xFF8E9BE0), Color(0xFFB5835A), Color(0xFF6FCFB0),
)

private fun wirtFarben(dienste: List<TopoDienst>): Map<String, Color> =
    dienste.map { it.wirt }.distinct().sorted().mapIndexed { i, w -> w to WIRT_PALETTE[i % WIRT_PALETTE.size] }.toMap()

/** Zustandsfarben aus dem Theme, einmal in der Komposition geholt (die Getter sind @Composable). */
private data class ZustandsFarben(val up: Color, val down: Color, val warn: Color, val leise: Color)

@Composable
private fun zustandsFarben() = ZustandsFarben(StatusUp, StatusDown, StatusWarn, TextLeise)

/** Legende je Linse: Schluessel aus [linsenWert], Text, Farbe; in dieser Reihenfolge. */
private fun legende(linse: Linse, f: ZustandsFarben, wirte: Map<String, Color> = emptyMap()): List<Triple<String, String, Color>> = when (linse) {
    Linse.ZUSTAND -> listOf(
        Triple("laeuft", "läuft", f.up),
        Triple("aus", "aus", f.down),
        Triple("unbekannt", "unbekannt", f.warn),
        Triple("bewusst_aus", "bewusst aus", f.leise),
    )
    Linse.ERREICHBARKEIT -> listOf(
        Triple("oeffentlich", "öffentlich", ORANGE),
        Triple("geraete", "eigene Geräte", BLAU),
        Triple("lokal", "nur lokal", GRAUBLAU),
        Triple("unbekannt", "unbekannt", f.leise),
    )
    Linse.KLASSE -> listOf(
        Triple("CORE", "CORE", VIOLETT),
        Triple("OPS", "OPS", BLAU),
        Triple("PUBLIC", "PUBLIC", ORANGE),
        Triple("INTERNAL", "INTERNAL", TUERKIS),
        Triple("sonst", "ohne Klasse", f.leise),
    )
    Linse.WIRT -> wirte.map { (w, c) -> Triple(w, w, c) }
}

private fun linsenFarbe(d: TopoDienst, linse: Linse, f: ZustandsFarben, wirte: Map<String, Color>): Color {
    if (linse == Linse.WIRT) return wirte[d.wirt] ?: f.leise
    val wert = linsenWert(d, linse)
    return legende(linse, f).firstOrNull { it.first == wert }?.third ?: f.leise
}

private fun zustandFarbe(zustand: String, f: ZustandsFarben): Color = when (zustand) {
    "laeuft" -> f.up
    "aus" -> f.down
    "unbekannt" -> f.warn
    else -> f.leise
}

/** Farbe eines Stands von damals; wie im Web rot nur fuer ungesund, fehlend und unerreichbar. */
private fun zeitFarbe(z: String?, f: ZustandsFarben): Color = when (z) {
    "lief", "erreichbar" -> f.up
    "ungesund", "fehlte", "nicht_erreichbar" -> f.down
    "startete" -> f.warn
    else -> f.leise
}

private val ZEIT_LEGENDE = listOf("lief", "ungesund", "fehlte", "startete", "lief_nicht", "unbekannt")

private fun linienFarbe(art: String, neutral: Color): Color = when (art) {
    "public" -> ORANGE
    "lan" -> TUERKIS
    "backup" -> BLAU
    "event" -> VIOLETT
    "adapter" -> GRAUBLAU
    else -> neutral
}

/** Was die Liste ueber der Karte gerade zeigt. */
private enum class ListenArt { SUCHE, AENDERUNGEN, AUFFAELLIG }

/** Netz wie Obsidian (Vorgabe) oder das feste Raster nach Wirten. */
internal enum class Ansicht(val titel: String) { NETZ("Netz"), RASTER("Raster") }

@Composable
internal fun Karte(
    a: TopologieResponse,
    layout: KartenLayout,
    onOpenDetail: (String, String) -> Unit,
    netz: NetzLayout? = null,
    aenderungen: TopoAenderungen? = null,
    auffrischenFehlt: Boolean = false,
    onAenderungenGesehen: () -> Unit = {},
    onOpenWirte: () -> Unit = {},
    zeitVor: String? = null,
    zeitpunkt: TopoZeitpunkt? = null,
    zeitLaedt: Boolean = false,
    onZeit: (String?) -> Unit = {},
    startAnsicht: Ansicht = Ansicht.NETZ,
) {
    var nurProbleme by rememberSaveable { mutableStateOf(false) }
    var zeigeAenderungen by rememberSaveable { mutableStateOf(false) }
    var gewaehlt by rememberSaveable { mutableStateOf<String?>(null) }
    var wirtGewaehlt by rememberSaveable { mutableStateOf<String?>(null) }
    var durchspielId by rememberSaveable { mutableStateOf<String?>(null) }
    var anfrage by rememberSaveable { mutableStateOf("") }
    var linse by rememberSaveable { mutableStateOf(Linse.ZUSTAND) }
    var ausgeblendet by rememberSaveable { mutableStateOf(listOf<String>()) }
    var ansichtWahl by rememberSaveable { mutableStateOf(startAnsicht) }
    var linsenMenue by remember { mutableStateOf(false) }
    var linienMenue by remember { mutableStateOf(false) }
    var zeitMenue by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var versatz by remember { mutableStateOf(Offset.Zero) }
    var flaechenGroesse by remember { mutableStateOf(IntSize.Zero) }
    // Ohne Netz (rechnet noch, oder Test ohne) bleibt es beim Raster.
    val ansicht = if (netz == null) Ansicht.RASTER else ansichtWahl
    val imNetz = ansicht == Ansicht.NETZ && netz != null
    val weltBreite = if (imNetz) netz!!.breite else layout.breite
    val weltHoehe = if (imNetz) netz!!.hoehe else layout.hoehe
    val maxZoom = if (imNetz) 12f else 6f
    val dichte = LocalDensity.current.density
    val messer = rememberTextMeasurer()
    val farben = zustandsFarben()
    val up = StatusUp
    val down = StatusDown
    val leise = TextLeise
    val linie = MaterialTheme.colorScheme.outlineVariant
    val hervor = MaterialTheme.colorScheme.primary
    val flaeche = MaterialTheme.colorScheme.surfaceVariant
    val schrift = MaterialTheme.colorScheme.onSurface
    val dienstNach = remember(a) { a.dienste.associateBy { it.id } }
    fun name(id: String) = dienstNach[id]?.label ?: id
    val wirtFarbe = remember(a) { wirtFarben(a.dienste) }
    val nachbarn = remember(a, gewaehlt) {
        gewaehlt?.let { id -> a.verbindungen.filter { it.von == id || it.nach == id }.map { if (it.von == id) it.nach else it.von }.toSet() }
            ?: emptySet()
    }
    val kette: Kette? = remember(a, gewaehlt) { gewaehlt?.let { abhaengigkeitsKette(it, a.verbindungen) } }
    // Kette nach oben (was der Dienst braucht) und nach unten (wer mit ausfaellt), jeweils samt Dienst.
    val oben = remember(kette, gewaehlt) { kette?.let { it.braucht + it.brauchtWeiter + gewaehlt!! } ?: emptySet() }
    val unten = remember(kette, gewaehlt) { kette?.let { it.betroffen + it.betroffenWeiter + gewaehlt!! } ?: emptySet() }
    val relevant = remember(nachbarn, oben, unten) { nachbarn + oben + unten }
    val probleme = a.dienste.count { it.istProblem }
    val betroffen = remember(a) { mitBetroffen(a.dienste, a.verbindungen) }
    val treffer = remember(a, anfrage) { sucheDienste(a.dienste, anfrage) }
    val trefferIds = remember(treffer) { treffer.map { it.id }.toSet() }
    val suchtGerade = anfrage.isNotBlank()
    val auffaellige = remember(a) { auffaelligeDienste(a.dienste) }
    val artenDa = remember(a) { a.verbindungen.groupingBy { it.art }.eachCount() }
    val pulsIds = aenderungen?.sichtbar.orEmpty()
    val durchspiel: Durchspiel? = remember(a, durchspielId) { durchspielId?.let { durchspielen(it, a) } }
    // Zeitpunkt zaehlt erst, wenn die Antwort da ist und keinen Fehler meldet.
    val zeit = zeitpunkt?.takeIf { zeitVor != null && it.vor == zeitVor && it.fehler == null }
    // Eine Liste ueber der Karte: Suchtreffer, sonst Aenderungen, sonst bei
    // „Nur Auffaelliges“ die auffaelligen Dienste. Ist ein Dienst oder Wirt
    // gewaehlt, weicht sie dessen Karte; „Schliessen“ bringt sie zurueck.
    val listenArt = when {
        suchtGerade -> ListenArt.SUCHE
        zeigeAenderungen && aenderungen != null -> ListenArt.AENDERUNGEN
        nurProbleme -> ListenArt.AUFFAELLIG
        else -> null
    }
    BackHandler(enabled = gewaehlt != null || wirtGewaehlt != null || durchspielId != null) {
        if (gewaehlt != null || wirtGewaehlt != null) { gewaehlt = null; wirtGewaehlt = null } else durchspielId = null
    }

    // Beschriftungen EINMAL messen, ohne Grenzen, und fertig zeichnen.
    // `drawText(messer, text, topLeft)` leitet seine Grenzen aus der Canvas in
    // Pixeln minus Startpunkt ab, der Startpunkt steht hier aber in
    // Kartenkoordinaten. Live ist die Karte rund 2500 Einheiten hoch, die
    // Canvas rund 1900 px: jede Beschriftung darunter ergab eine negative
    // Hoehe, `Constraints` warf, und die App stuerzte beim Oeffnen ab
    // (2.0.16/2.0.17). Nebenbei misst so kein Zoom-Frame mehr 260 Texte neu.
    // Schrift in Kartenkoordinaten: sie waechst mit dem Zoom wie alles andere.
    fun welt(groesse: Float) = (groesse / dichte).sp
    fun miss(text: String, groesse: Float, farbe: Color, breite: Int = Constraints.Infinity): TextLayoutResult =
        messer.measure(
            text, TextStyle(color = farbe, fontSize = welt(groesse)),
            overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = breite.coerceAtLeast(0)),
        )
    val wirtNach = remember(a) { a.wirte.associateBy { it.id } }
    val jeWirt = remember(a) { a.dienste.groupBy { it.wirt } }
    val koepfe = remember(layout, messer, schrift, leise, down) {
        layout.wirte.mapValues { (id, r) -> kopfMessen(id, r.b, wirtKopf(wirtNach[id], jeWirt[id].orEmpty()), ::miss, schrift, leise, down) }
    }
    val gruppenTexte = remember(layout, messer, leise) { layout.gruppen.map { miss(it.text, 9f, leise, breite = 300) } }
    val dienstTexte = remember(layout, dienstNach, messer, schrift) {
        // Hoechstens eine Rasterzelle (30) breit, sonst ueberdeckt der Name den Nachbarn.
        // Gezeichnet ab p.y + 12, unter dem Auswahlring (Radius 11+1).
        layout.dienste.keys.mapNotNull { id -> dienstNach[id]?.let { id to miss(it.label, 5.5f, schrift, breite = 28) } }.toMap()
    }
    // Netz: Namen in Bildschirmgroesse (sp), ausserhalb der Kartentransformation
    // gezeichnet; ebenfalls einmal gemessen und ohne Grenzen.
    val netzTexte = remember(netz, dienstNach, messer, schrift) {
        netz?.punkte?.keys?.associateWith { id ->
            val wirt = id in netz.wirte
            messer.measure(
                if (wirt) id else name(id),
                TextStyle(color = schrift, fontSize = if (wirt) 12.sp else 9.sp),
                overflow = TextOverflow.Ellipsis, maxLines = 1, constraints = Constraints(maxWidth = (120 * dichte).toInt()),
            )
        }.orEmpty()
    }
    // Geaenderte Dienste pulsieren; ohne Aenderung laeuft keine Animation.
    val puls: State<Float>? = if (pulsIds.isNotEmpty()) {
        rememberInfiniteTransition(label = "puls").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "puls",
        )
    } else null

    /** Mittelpunkt eines Knotens in der aktuellen Ansicht. */
    fun mitte(id: String): KartenPunkt? = if (imNetz) netz!!.punkte[id] else layout.mitte(id)

    // Die Karte darf nicht ganz aus dem Bild rutschen: mindestens die halbe
    // Flaeche bleibt Karte, sonst sucht man sie nach einem Wisch.
    fun begrenzt(v: Offset, breite: Float, hoehe: Float, s: Float): Offset = Offset(
        v.x.coerceIn(minOf(0f, breite / 2 - weltBreite * s), breite / 2),
        v.y.coerceIn(minOf(0f, hoehe / 2 - weltHoehe * s), hoehe / 2),
    )

    /** Kartenpunkt [x]/[y] mit Zoom [z] ins Bild holen: waagerecht mittig, im oberen Drittel. */
    fun hinZu(x: Float, y: Float, z: Float) {
        val w = flaechenGroesse.width.toFloat()
        val h = flaechenGroesse.height.toFloat()
        if (w <= 0f) return
        val s = w / weltBreite * z
        zoom = z
        versatz = begrenzt(Offset(w / 2 - x * s, h / 3 - y * s), w, h, s)
    }

    /** Ganze Karte zeigen. Das Netz passt ganz ins Bild, das Raster fuellt die Breite. */
    fun ganzZeigen() {
        val w = flaechenGroesse.width.toFloat()
        val h = flaechenGroesse.height.toFloat()
        if (!imNetz || w <= 0f) { zoom = 1f; versatz = Offset.Zero; return }
        val s0 = w / weltBreite
        val z = minOf(1f, h / (weltHoehe * s0)).coerceAtLeast(0.6f)
        zoom = z
        versatz = Offset((w - weltBreite * s0 * z) / 2, maxOf(0f, (h - weltHoehe * s0 * z) / 2))
    }
    // Beim Wechsel der Ansicht (und beim ersten Messen der Flaeche) einpassen.
    var eingepasst by remember(ansicht) { mutableStateOf(false) }
    LaunchedEffect(ansicht, flaechenGroesse) {
        if (!eingepasst && flaechenGroesse.width > 0) { ganzZeigen(); eingepasst = true }
    }

    fun zeigeDienst(id: String) {
        val p = mitte(id) ?: return
        wirtGewaehlt = null
        gewaehlt = id
        hinZu(p.x, p.y, maxOf(zoom, if (imNetz) 3f else 2.5f))
    }

    fun zeigeWirt(id: String) {
        if (imNetz) {
            val p = netz!!.punkte[id] ?: return
            hinZu(p.x, p.y, maxOf(zoom, 2f))
            return
        }
        val r = layout.wirte[id] ?: return
        val w = flaechenGroesse.width.toFloat()
        if (w <= 0f) return
        // So weit hinein, dass der Kasten die Breite fuellt; volle Kaesten bleiben bei 1.
        val z = (layout.breite / (r.b + 16f)).coerceIn(1f, 6f)
        val s = w / layout.breite * z
        zoom = z
        // Kasten waagerecht mittig, Oberkante mit etwas Luft oben im Bild.
        versatz = begrenzt(Offset(w / 2 - r.mitteX * s, 12f * dichte - r.y * s), w, flaechenGroesse.height.toFloat(), s)
    }

    fun durchspielStarten(id: String) {
        durchspielId = id
        gewaehlt = null
        wirtGewaehlt = null
    }

    /**
     * Wie ein Dienst gezeichnet wird: Fuellfarbe und ob er gedimmt ist.
     * Vorrang: Durchspiel, dann Zeitpunkt, dann die Linse.
     */
    fun stil(d: TopoDienst): Pair<Color, Boolean> {
        val id = d.id
        durchspiel?.let { ds ->
            return when {
                id in ds.aus -> down to false
                id in ds.folgen -> ORANGE to false
                else -> linsenFarbe(d, linse, farben, wirtFarbe) to true
            }
        }
        // Ist ein Dienst gewaehlt, zaehlt nur seine Kette; Suche und
        // Filter wuerden sonst die Glieder dimmen, um die es gerade geht.
        val gedimmt = if (gewaehlt != null) id != gewaehlt && id !in relevant
        else (suchtGerade && id !in trefferIds) || (!suchtGerade && nurProbleme && !d.istProblem)
        val farbe = if (zeit != null) zeitFarbe(zeit.zustaende[id], farben) else linsenFarbe(d, linse, farben, wirtFarbe)
        return farbe to gedimmt
    }

    /** Linienfarbe und -staerke einer Verbindung; null = nicht zeichnen. */
    fun linienStil(von: String, nach: String, art: String): Pair<Color, Float>? {
        durchspiel?.let { ds ->
            val drin = { x: String -> x in ds.aus || x in ds.folgen }
            return if (art == "depends" && drin(von) && drin(nach)) ORANGE to 2f else linie.copy(alpha = 0.06f) to 0.6f
        }
        return when {
            gewaehlt == null -> linienFarbe(art, linie).copy(alpha = 0.5f) to 0.8f
            art == "depends" && von in oben && nach in oben -> hervor to 2f
            art == "depends" && von in unten && nach in unten -> ORANGE to 2f
            von == gewaehlt || nach == gewaehlt -> linienFarbe(art, hervor) to 1.2f
            else -> null
        }
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                "${a.dienste.count { it.zustand == "laeuft" }} laufen · ${a.dienste.count { it.zustand == "bewusst_aus" }} bewusst aus · $probleme auffällig" +
                    (if (betroffen.isNotEmpty()) " · ${betroffen.size} mitbetroffen" else "") +
                    (a.gesundheit?.let { " · Gesundheit $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = leise,
            )
            val standText = listOfNotNull(
                seitText(a.stand)?.let { "Stand $it" },
                "Auffrischen fehlgeschlagen".takeIf { auffrischenFehlt },
            ).joinToString(" · ")
            if (standText.isNotEmpty()) {
                Text(standText, style = MaterialTheme.typography.bodySmall, color = if (auffrischenFehlt) StatusWarn else leise)
            }
            aenderungen?.let { ae ->
                AenderungsBanner(
                    ae,
                    offen = zeigeAenderungen,
                    onUmschalten = { zeigeAenderungen = !zeigeAenderungen; gewaehlt = null; wirtGewaehlt = null },
                    onGesehen = { zeigeAenderungen = false; onAenderungenGesehen() },
                )
            }
            zeitVor?.let { vor ->
                ZeitBanner(ZEITPUNKTE[vor] ?: vor, zeitLaedt, zeitpunkt?.takeIf { it.vor == vor }, onJetzt = { onZeit(null) })
            }
            OutlinedTextField(
                value = anfrage,
                onValueChange = { anfrage = it; gewaehlt = null; wirtGewaehlt = null },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                placeholder = { Text("Dienst, Wirt oder Gruppe suchen") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (suchtGerade) {
                    { IconButton(onClick = { anfrage = ""; gewaehlt = null }) { Icon(Icons.Filled.Close, contentDescription = "Suche leeren") } }
                } else null,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = nurProbleme,
                    onClick = { nurProbleme = !nurProbleme; gewaehlt = null; wirtGewaehlt = null },
                    label = { Text("Nur Auffälliges $probleme") },
                )
                if (netz != null) {
                    FilterChip(
                        selected = ansicht == Ansicht.NETZ,
                        onClick = { ansichtWahl = if (ansicht == Ansicht.NETZ) Ansicht.RASTER else Ansicht.NETZ },
                        label = { Text("Ansicht: ${ansicht.titel}") },
                    )
                }
                Box {
                    FilterChip(
                        selected = linse != Linse.ZUSTAND,
                        onClick = { linsenMenue = true },
                        label = { Text("Farbe: ${linse.titel}") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                    )
                    DropdownMenu(expanded = linsenMenue, onDismissRequest = { linsenMenue = false }) {
                        Linse.entries.forEach { l ->
                            DropdownMenuItem(text = { Text(l.titel) }, onClick = { linse = l; linsenMenue = false })
                        }
                    }
                }
                Box {
                    FilterChip(
                        selected = ausgeblendet.isNotEmpty(),
                        onClick = { linienMenue = true },
                        label = { Text(if (ausgeblendet.isEmpty()) "Linien" else "Linien ${artenDa.keys.count { it !in ausgeblendet }}/${artenDa.size}") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                    )
                    DropdownMenu(expanded = linienMenue, onDismissRequest = { linienMenue = false }) {
                        (VERBINDUNGS_ARTEN.keys.filter { it in artenDa } + artenDa.keys.filter { it !in VERBINDUNGS_ARTEN }).forEach { art ->
                            val an = art !in ausgeblendet
                            DropdownMenuItem(
                                text = { Text("${VERBINDUNGS_ARTEN[art] ?: art.ifEmpty { "ohne Art" }} (${artenDa[art]})") },
                                leadingIcon = { Box(Modifier.size(12.dp).background(linienFarbe(art, linie), CircleShape)) },
                                trailingIcon = { Checkbox(checked = an, onCheckedChange = null) },
                                onClick = { ausgeblendet = if (an) ausgeblendet + art else ausgeblendet - art },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Nur Abhängigkeiten") },
                            onClick = { ausgeblendet = artenDa.keys.filter { it != "depends" }; linienMenue = false },
                        )
                        DropdownMenuItem(text = { Text("Alle zeigen") }, onClick = { ausgeblendet = emptyList(); linienMenue = false })
                    }
                }
                Box {
                    FilterChip(
                        selected = zeitVor != null,
                        onClick = { zeitMenue = true },
                        label = { Text("Zeit: ${zeitVor?.let { ZEITPUNKTE[it] } ?: "jetzt"}") },
                        trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) },
                    )
                    DropdownMenu(expanded = zeitMenue, onDismissRequest = { zeitMenue = false }) {
                        DropdownMenuItem(text = { Text("jetzt") }, onClick = { onZeit(null); zeitMenue = false })
                        ZEITPUNKTE.forEach { (k, text) ->
                            DropdownMenuItem(text = { Text(text) }, onClick = { onZeit(k); zeitMenue = false })
                        }
                    }
                }
                FilterChip(selected = false, onClick = { ganzZeigen() }, label = { Text("Ganz zeigen") })
            }
            if (zeit != null) ZeitLegende(zeit) else Legende(linse, a.dienste, wirtFarbe)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .onSizeChanged { flaechenGroesse = it }
                    .semantics { contentDescription = "Karte der Wirte und Dienste, mit zwei Fingern zoomen" }
                    .pointerInput(layout, netz, ansicht) {
                        detectTransformGestures { mitte, schieben, faktor, _ ->
                            val neu = (zoom * faktor).coerceIn(0.6f, maxZoom)
                            // Um den Fingermittelpunkt zoomen, nicht um die Ecke.
                            val roh = mitte - (mitte - versatz) * (neu / zoom) + schieben
                            versatz = begrenzt(roh, size.width.toFloat(), size.height.toFloat(), size.width / weltBreite * neu)
                            zoom = neu
                        }
                    }
                    .pointerInput(layout, netz, ansicht) {
                        detectTapGestures { tipp ->
                            // Derselbe Massstab wie beim Zeichnen.
                            val s = size.width / weltBreite * zoom
                            val welt = (tipp - versatz) / s
                            if (imNetz) {
                                val n = netz!!
                                val naechster = n.punkte.minByOrNull { (_, p) -> hypot(p.x - welt.x, p.y - welt.y) }
                                    ?.takeIf { (id, p) -> hypot(p.x - welt.x, p.y - welt.y) * s < maxOf(24.dp.toPx(), (n.radien[id] ?: 4f) * s) }?.key
                                when {
                                    naechster == null -> { gewaehlt = null; wirtGewaehlt = null }
                                    naechster in n.wirte -> { gewaehlt = null; wirtGewaehlt = naechster }
                                    else -> { wirtGewaehlt = null; gewaehlt = naechster }
                                }
                                return@detectTapGestures
                            }
                            val naechster = layout.dienste.minByOrNull { (_, p) -> hypot(p.x - welt.x, p.y - welt.y) }
                                ?.takeIf { (_, p) -> hypot(p.x - welt.x, p.y - welt.y) * s < 24.dp.toPx() }?.key
                            // Kopfzeile eines Wirts: dessen Blatt oeffnen.
                            val kopf = layout.wirte.entries.firstOrNull { (_, r) ->
                                welt.x in r.x..(r.x + r.b) && welt.y in r.y..(r.y + KOPF_HOEHE)
                            }?.key
                            when {
                                naechster != null -> { wirtGewaehlt = null; gewaehlt = naechster }
                                kopf != null -> { gewaehlt = null; wirtGewaehlt = kopf }
                                else -> { gewaehlt = null; wirtGewaehlt = null }
                            }
                        }
                    },
            ) {
                val s = size.width / weltBreite * zoom
                val phase = puls?.value ?: 0f

                /** Ringe fuer Probleme, Mitbetroffene, Kette und Aenderungen; gleich in beiden Ansichten. */
                fun androidx.compose.ui.graphics.drawscope.DrawScope.ringe(id: String, d: TopoDienst, mitte: Offset, r: Float, gedimmt: Boolean) {
                    if (!gedimmt && durchspiel == null) {
                        // In fremden Linsen bleibt sichtbar, was kaputt ist.
                        if (zeit == null && linse != Linse.ZUSTAND && d.istProblem) {
                            drawCircle(zustandFarbe(d.zustand, farben), radius = r + 1.5f, center = mitte, style = Stroke(1.5f))
                        }
                        // Laeuft, braucht aber etwas, das aus ist: orangener Ring.
                        if (zeit == null && gewaehlt == null && id in betroffen) {
                            drawCircle(ORANGE, radius = r + 2f, center = mitte, style = Stroke(1.5f))
                        }
                        if (gewaehlt != null && id != gewaehlt && id in unten) {
                            drawCircle(ORANGE, radius = r + 2f, center = mitte, style = Stroke(1.5f))
                        }
                        if (id in pulsIds) {
                            drawCircle(hervor.copy(alpha = 1f - phase), radius = r + 1f + 7f * phase, center = mitte, style = Stroke(1.5f))
                        }
                    }
                    if (id == gewaehlt) drawCircle(hervor, radius = r + 3f, center = mitte, style = Stroke(2f))
                }

                if (imNetz) {
                    val n = netz!!
                    withTransform({
                        translate(versatz.x, versatz.y)
                        scale(s, s, Offset.Zero)
                    }) {
                        // Zugehoerigkeit Dienst -> Wirt: duenn, sie formt die Wolken.
                        a.dienste.forEach { d ->
                            val p = n.punkte[d.id] ?: return@forEach
                            val q = n.punkte[d.wirt] ?: return@forEach
                            val schwach = durchspiel != null || gewaehlt != null || suchtGerade
                            drawLine(linie.copy(alpha = if (schwach) 0.08f else 0.3f), Offset(p.x, p.y), Offset(q.x, q.y), strokeWidth = 0.6f)
                        }
                        a.verbindungen.forEach { v ->
                            if (v.art in ausgeblendet) return@forEach
                            val p = n.punkte[v.von] ?: return@forEach
                            val q = n.punkte[v.nach] ?: return@forEach
                            val (farbe, staerke) = linienStil(v.von, v.nach, v.art) ?: return@forEach
                            drawLine(farbe, Offset(p.x, p.y), Offset(q.x, q.y), strokeWidth = staerke)
                        }
                        a.dienste.forEach { d ->
                            val p = n.punkte[d.id] ?: return@forEach
                            val r = n.radien[d.id] ?: 4f
                            val (farbe, gedimmt) = stil(d)
                            val mitte = Offset(p.x, p.y)
                            drawCircle(farbe.copy(alpha = if (gedimmt) 0.15f else 1f), radius = r, center = mitte)
                            ringe(d.id, d, mitte, r, gedimmt)
                        }
                        // Wirte zuletzt, gross: Fuellung nach Erreichbarkeit (jetzt oder damals).
                        n.wirte.forEach { id ->
                            val p = n.punkte[id] ?: return@forEach
                            val r = n.radien[id] ?: 12f
                            val mitte = Offset(p.x, p.y)
                            val zustand = when {
                                zeit != null -> zeitFarbe(zeit.wirte[id], farben)
                                wirtNach[id]?.online == true -> up
                                wirtNach[id]?.online == false -> down
                                else -> leise
                            }
                            val imSpiel = durchspiel?.aus?.contains(id) == true
                            val gedimmt = (durchspiel != null && !imSpiel) || (gewaehlt != null && id !in relevant)
                            val fuell = if (linse == Linse.WIRT) wirtFarbe[id] ?: flaeche else flaeche
                            drawCircle(fuell.copy(alpha = if (gedimmt) 0.25f else 1f), radius = r, center = mitte)
                            drawCircle((if (imSpiel) down else zustand).copy(alpha = if (gedimmt) 0.3f else 1f), radius = r, center = mitte, style = Stroke(2f))
                            if (id == wirtGewaehlt) drawCircle(hervor, radius = r + 3f, center = mitte, style = Stroke(2f))
                        }
                    }
                    // Namen in Bildschirmgroesse. Wirte immer; Dienste ab genug
                    // Abstand, sonst nur die, um die es gerade geht.
                    val dienstNamenAlle = KANTE_NETZ * s >= 70f * dichte
                    val hervorgehoben: Set<String> = when {
                        durchspiel != null -> (durchspiel.folgen.keys + durchspiel.aus).takeIf { it.size <= 40 }.orEmpty()
                        gewaehlt != null -> relevant + gewaehlt!!
                        suchtGerade -> trefferIds.takeIf { it.size <= 40 }.orEmpty()
                        else -> emptySet()
                    }
                    netzTexte.forEach { (id, text) ->
                        val p = n.punkte[id] ?: return@forEach
                        val istWirt = id in n.wirte
                        if (!istWirt && !dienstNamenAlle && id !in hervorgehoben) return@forEach
                        val bx = versatz.x + p.x * s
                        val by = versatz.y + p.y * s + (n.radien[id] ?: 4f) * s + 2f * dichte
                        if (bx < -200f || by < -50f || bx > size.width + 200f || by > size.height + 50f) return@forEach
                        drawText(text, topLeft = Offset(bx - text.size.width / 2f, by))
                    }
                    return@Canvas
                }

                withTransform({
                    translate(versatz.x, versatz.y)
                    scale(s, s, Offset.Zero)
                }) {
                    // Wirte mit Kopf: Punkt online/offline, Name, rechts Zahlen
                    layout.wirte.forEach { (id, r) ->
                        drawRoundRect(flaeche, Offset(r.x, r.y), Size(r.b, r.h), CornerRadius(6f, 6f))
                        if (id == wirtGewaehlt) {
                            drawRoundRect(hervor, Offset(r.x, r.y), Size(r.b, r.h), CornerRadius(6f, 6f), style = Stroke(1.5f))
                        } else if (durchspiel?.aus?.contains(id) == true) {
                            drawRoundRect(down, Offset(r.x, r.y), Size(r.b, r.h), CornerRadius(6f, 6f), style = Stroke(1.5f))
                        }
                        val k = koepfe[id] ?: return@forEach
                        val mitteKopf = r.y + 12f
                        val damals = zeit?.wirte?.get(id)
                        when {
                            zeit != null -> drawCircle(if (damals == null) leise else zeitFarbe(damals, farben), radius = 3f, center = Offset(r.x + 9f, mitteKopf))
                            k.online == true -> drawCircle(up, radius = 3f, center = Offset(r.x + 9f, mitteKopf))
                            k.online == false -> drawCircle(down, radius = 3f, center = Offset(r.x + 9f, mitteKopf))
                            else -> drawCircle(leise, radius = 2.5f, center = Offset(r.x + 9f, mitteKopf), style = Stroke(0.8f))
                        }
                        drawText(k.name, topLeft = Offset(r.x + 16f, mitteKopf - k.name.size.height / 2f))
                        var rechts = r.x + r.b - 6f
                        for (teil in k.rechts.asReversed()) {
                            rechts -= teil.size.width
                            drawText(teil, topLeft = Offset(rechts, mitteKopf - teil.size.height / 2f))
                            rechts -= 4f
                        }
                    }
                    // Verbindungen. Ohne Wahl duenn in der Farbe ihrer Art. Mit Wahl
                    // nur die Kette: nach oben (was gebraucht wird) in der Akzentfarbe,
                    // nach unten (wer mit ausfaellt) orange, direkte andere Arten duenn.
                    a.verbindungen.forEach { v ->
                        if (v.art in ausgeblendet) return@forEach
                        val p = layout.mitte(v.von) ?: return@forEach
                        val q = layout.mitte(v.nach) ?: return@forEach
                        val (farbe, staerke) = linienStil(v.von, v.nach, v.art) ?: return@forEach
                        drawLine(farbe, Offset(p.x, p.y), Offset(q.x, q.y), strokeWidth = staerke)
                    }
                    // Gruppen-Ueberschriften immer: ohne sie wirken die Zeilen wie zufaellige Luecken.
                    layout.gruppen.forEachIndexed { i, g ->
                        drawText(gruppenTexte[i], topLeft = Offset(g.x, g.y - 11f))
                    }
                    // Dienste
                    layout.dienste.forEach { (id, p) ->
                        val d = dienstNach[id] ?: return@forEach
                        val mitte = Offset(p.x, p.y)
                        val (farbe, gedimmt) = stil(d)
                        drawCircle(farbe.copy(alpha = if (gedimmt) 0.2f else 1f), radius = 8f, center = mitte)
                        ringe(id, d, mitte, 8f, gedimmt)
                        if (zoom >= 2.5f && !gedimmt) {
                            dienstTexte[id]?.let { drawText(it, topLeft = Offset(p.x - it.size.width / 2f, p.y + 12f)) }
                        }
                    }
                }
            }
            if (gewaehlt == null && wirtGewaehlt == null && durchspiel == null) {
                when (listenArt) {
                    ListenArt.SUCHE -> DienstListe("${treffer.size} Treffer", treffer.map { it to null }, Modifier.align(Alignment.TopCenter)) { zeigeDienst(it.id) }
                    ListenArt.AUFFAELLIG -> DienstListe("${auffaellige.size} auffällig", auffaellige.map { it to null }, Modifier.align(Alignment.TopCenter)) { zeigeDienst(it.id) }
                    ListenArt.AENDERUNGEN -> aenderungen?.let { ae ->
                        val eintraege = ae.neuAuffaellig.mapNotNull { dienstNach[it]?.to("neu auffällig") } +
                            ae.wiederGut.mapNotNull { dienstNach[it]?.to("wieder gut") } +
                            ae.neu.mapNotNull { dienstNach[it]?.to("neu") }
                        DienstListe(
                            "Seit ${zeitpunktText(ae.seitMs)}",
                            eintraege,
                            Modifier.align(Alignment.TopCenter),
                            fuss = ae.weg.takeIf { it.isNotEmpty() }?.let { "Nicht mehr da: ${it.joinToString(", ")}" },
                        ) { zeigeDienst(it.id) }
                    }
                    null -> Unit
                }
            }
            durchspiel?.let { ds ->
                DurchspielBand(
                    titel = name(durchspielId!!),
                    ds = ds,
                    name = ::name,
                    modifier = Modifier.align(Alignment.TopCenter),
                    onWahl = { zeigeDienst(it) },
                    onBeenden = { durchspielId = null },
                )
            }
            gewaehlt?.let { dienstNach[it] }?.let { d ->
                DienstKarte(
                    d,
                    kette = kette,
                    andere = (nachbarn - (kette?.alle ?: emptySet())).map { name(it) }.sorted(),
                    name = ::name,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onSchliessen = { gewaehlt = null },
                    onOeffnen = d.container?.takeIf { d.wirt in STEUERBAR }?.let { c -> { onOpenDetail(d.wirt, c) } },
                    damals = zeit?.let { z -> z.zustaende[d.id]?.let { "${ZEITPUNKTE[z.vor] ?: z.vor}: ${ZEIT_TEXT[it] ?: it}" } },
                    onDurchspielen = { durchspielStarten(d.id) },
                )
            }
            wirtGewaehlt?.let { id ->
                WirtKarte(
                    id,
                    wirtNach[id],
                    wirtKopf(wirtNach[id], jeWirt[id].orEmpty()),
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onZeigen = { zeigeWirt(id) },
                    onVerlauf = onOpenWirte,
                    onSchliessen = { wirtGewaehlt = null },
                    onDurchspielen = { durchspielStarten(id) },
                )
            }
        }
    }
}

/** Typischer Abstand zweier Punkte im Netz (gemessen rund 33): ab dieser Bildgroesse passen alle Namen. */
private const val KANTE_NETZ = 32f

@Composable
private fun ZeitBanner(titel: String, laedt: Boolean, z: TopoZeitpunkt?, onJetzt: () -> Unit) {
    val text = when {
        laedt || z == null -> "Lade den Stand $titel…"
        z.fehler != null -> "Stand $titel nicht abrufbar: ${z.fehler}"
        else -> listOfNotNull(
            "Punkte zeigen den Stand $titel, Linien den heutigen",
            z.auffaellig.size.takeIf { it > 0 }?.let { "$it damals auffällig" },
            z.ohneLaufdaten.takeIf { it.isNotEmpty() }?.let { "ohne Daten: ${it.joinToString(", ")}" },
        ).joinToString(" · ")
    }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
    ) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            TextButton(onClick = onJetzt) { Text("Jetzt") }
        }
    }
}

@Composable
private fun ZeitLegende(z: TopoZeitpunkt) {
    val zahl = remember(z) { z.zustaende.values.groupingBy { it }.eachCount() }
    val farben = zustandsFarben()
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ZEIT_LEGENDE.filter { (zahl[it] ?: 0) > 0 }.forEach { k ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(zeitFarbe(k, farben), CircleShape))
                Text(" ${ZEIT_TEXT[k] ?: k} ${zahl[k]}", style = MaterialTheme.typography.labelSmall, color = TextLeise)
            }
        }
    }
}

/** Ergebnis des Gedankenspiels oben ueber der Karte; Folgen antippen springt zum Dienst. */
@Composable
private fun DurchspielBand(
    titel: String,
    ds: Durchspiel,
    name: (String) -> String,
    modifier: Modifier,
    onWahl: (String) -> Unit,
    onBeenden: () -> Unit,
) {
    val folgen = remember(ds) { ds.folgen.keys.sortedBy { name(it).lowercase() } }
    val mit = ds.aus.size - 1
    val satz = when {
        ds.istWirt -> "$mit ${if (mit == 1) "Dienst" else "Dienste"} darauf fallen weg" +
            (if (folgen.isNotEmpty()) ", dazu ${folgen.size} auf anderen Wirten." else ".")
        folgen.isNotEmpty() -> "${folgen.size} ${if (folgen.size == 1) "Dienst fällt" else "Dienste fallen"} mit aus."
        else -> "Kein laufender Dienst hängt davon ab."
    }
    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).heightIn(max = 260.dp)) {
        Row(Modifier.padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                Text("Gedankenspiel: $titel aus", style = MaterialTheme.typography.titleSmall)
                Text(satz, style = MaterialTheme.typography.bodySmall, color = if (folgen.isNotEmpty()) ORANGE else TextLeise)
            }
            TextButton(onClick = onBeenden) { Text("Beenden") }
        }
        LazyColumn(Modifier.padding(bottom = 6.dp)) {
            items(folgen, key = { it }) { id ->
                val wegen = ds.folgen[id].orEmpty().map(name).sorted()
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onWahl(id) }.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(8.dp).background(ORANGE, CircleShape))
                    Text(
                        name(id) + (if (ds.istWirt || wegen.size > 1 || wegen.firstOrNull() != titel) " · wegen ${wegen.take(3).joinToString(", ")}" + (if (wegen.size > 3) " …" else "") else ""),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AenderungsBanner(ae: TopoAenderungen, offen: Boolean, onUmschalten: () -> Unit, onGesehen: () -> Unit) {
    val teile = listOfNotNull(
        ae.neuAuffaellig.size.takeIf { it > 0 }?.let { "$it neu auffällig" },
        ae.wiederGut.size.takeIf { it > 0 }?.let { "$it wieder gut" },
        ae.neu.size.takeIf { it > 0 }?.let { "$it neu" },
        ae.weg.size.takeIf { it > 0 }?.let { "$it weg" },
    )
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp).clickable(onClick = onUmschalten),
    ) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                Text("Seit ${zeitpunktText(ae.seitMs)}: ${teile.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall)
                Text(if (offen) "Liste schließen" else "Antippen für die Liste", style = MaterialTheme.typography.labelSmall, color = TextLeise)
            }
            TextButton(onClick = onGesehen) { Text("Gesehen") }
        }
    }
}

@Composable
private fun Legende(linse: Linse, dienste: List<TopoDienst>, wirte: Map<String, Color>) {
    val zahl = remember(linse, dienste) { dienste.groupingBy { linsenWert(it, linse) }.eachCount() }
    val farben = zustandsFarben()
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        legende(linse, farben, wirte).filter { (zahl[it.first] ?: 0) > 0 }.forEach { (schluessel, text, farbe) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(farbe, CircleShape))
                Text(" $text ${zahl[schluessel]}", style = MaterialTheme.typography.labelSmall, color = TextLeise)
            }
        }
    }
}

/** Hoehe der Kopfzeile eines Wirt-Kastens in Kartenkoordinaten (Antippen oeffnet das Blatt). */
private const val KOPF_HOEHE = 24f

private class GemessenerKopf(val online: Boolean?, val name: TextLayoutResult, val rechts: List<TextLayoutResult>)

/**
 * Kopf eines Wirt-Kastens vorab messen. Rechts stehen Dienstzahl, davon
 * auffaellig (rot) und die Last; reicht die Breite nicht, faellt zuerst die
 * Last weg, und der Name wird gekuerzt statt ueber die Zahlen zu laufen.
 */
private fun kopfMessen(
    id: String,
    breite: Float,
    k: WirtKopf,
    miss: (String, Float, Color, Int) -> TextLayoutResult,
    schrift: Color,
    leise: Color,
    rot: Color,
): GemessenerKopf {
    fun teile(mitLast: Boolean) = buildList {
        if (k.dienste > 0) add(miss(if (k.dienste == 1) "1 Dienst" else "${k.dienste} Dienste", 8f, leise, Constraints.Infinity))
        if (k.auffaellig > 0) add(miss("${k.auffaellig} auffällig", 8f, rot, Constraints.Infinity))
        if (mitLast) k.last?.let { add(miss(it, 8f, leise, Constraints.Infinity)) }
    }
    fun breiteVon(t: List<TextLayoutResult>) = t.sumOf { it.size.width + 4 }
    val frei = (breite - 22f).toInt()
    var rechts = teile(mitLast = true)
    if (frei - breiteVon(rechts) < 40) rechts = teile(mitLast = false)
    val name = miss(id, 11f, schrift, (frei - breiteVon(rechts)).coerceAtLeast(24))
    return GemessenerKopf(k.online, name, rechts)
}

@Composable
private fun DienstListe(
    titel: String,
    dienste: List<Pair<TopoDienst, String?>>,
    modifier: Modifier,
    fuss: String? = null,
    onWahl: (TopoDienst) -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).heightIn(max = 280.dp)) {
        Text(titel, style = MaterialTheme.typography.labelMedium, color = TextLeise, modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 4.dp))
        if (dienste.isEmpty() && fuss == null) {
            Text("Nichts gefunden.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 14.dp, bottom = 12.dp))
        }
        LazyColumn {
            items(dienste, key = { it.first.id }) { (d, zusatz) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onWahl(d) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(10.dp).background(zustandFarbe(d.zustand, zustandsFarben()), CircleShape))
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text(d.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(zusatz, d.wirt, d.gruppe, ZUSTAND_TEXT[d.zustand]).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextLeise,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            fuss?.let { f ->
                item { Text(f, style = MaterialTheme.typography.bodySmall, color = TextLeise, modifier = Modifier.padding(14.dp)) }
            }
        }
    }
}

private val ZUSTAND_TEXT = mapOf(
    "laeuft" to "läuft",
    "bewusst_aus" to "bewusst aus",
    "aus" to "aus, sollte laufen",
    "unbekannt" to "Zustand unbekannt",
)

private val ERREICHBARKEIT_TEXT = mapOf(
    "oeffentlich" to "öffentlich",
    "geraete" to "von eigenen Geräten",
    "lokal" to "nur lokal",
)

/** Namensliste fuer die Kette: direkte Namen, dahinter die Zahl der weiteren. */
private fun ketteText(direkt: Set<String>, weiter: Set<String>, name: (String) -> String): String? {
    if (direkt.isEmpty() && weiter.isEmpty()) return null
    val namen = direkt.map(name).sorted().joinToString(", ")
    return if (weiter.isEmpty()) namen else "$namen, dahinter ${weiter.size} weitere"
}

@Composable
private fun DienstKarte(
    d: TopoDienst,
    kette: Kette?,
    andere: List<String>,
    name: (String) -> String,
    modifier: Modifier,
    onSchliessen: () -> Unit,
    onOeffnen: (() -> Unit)?,
    damals: String? = null,
    onDurchspielen: () -> Unit = {},
) {
    val uri = LocalUriHandler.current
    Card(modifier = modifier.fillMaxWidth().padding(12.dp).heightIn(max = 320.dp)) {
        Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState())) {
            Text(d.label, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(d.wirt, d.gruppe, ZUSTAND_TEXT[d.zustand] ?: d.zustand, d.erwartung?.let { "Erwartung $it" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = zustandFarbe(d.zustand, zustandsFarben()),
            )
            val technik = listOfNotNull(
                d.klasse,
                d.erreichbarkeit?.let { ERREICHBARKEIT_TEXT[it] ?: it },
                d.port?.let { "Port $it" },
            )
            if (technik.isNotEmpty()) Text(technik.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = TextLeise)
            damals?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp)) }
            kette?.let { k ->
                ketteText(k.braucht, k.brauchtWeiter, name)?.let {
                    Text("Braucht: $it", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
                ketteText(k.betroffen, k.betroffenWeiter, name)?.let {
                    Text(
                        "Fällt mit aus: $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = ORANGE,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            if (andere.isNotEmpty()) {
                Text("Verbunden mit: ${andere.joinToString(", ")}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                onOeffnen?.let { TextButton(onClick = it) { Text("Dienst öffnen") } }
                TextButton(onClick = onDurchspielen) { Text("Ausfall durchspielen") }
                d.url?.let { u -> TextButton(onClick = { runCatching { uri.openUri(u) } }) { Text("Im Browser") } }
                TextButton(onClick = onSchliessen) { Text("Schließen") }
            }
        }
    }
}

@Composable
private fun WirtKarte(
    id: String,
    w: TopoWirt?,
    k: WirtKopf,
    modifier: Modifier,
    onZeigen: () -> Unit,
    onVerlauf: () -> Unit,
    onSchliessen: () -> Unit,
    onDurchspielen: () -> Unit = {},
) {
    Card(modifier = modifier.fillMaxWidth().padding(12.dp).heightIn(max = 340.dp)) {
        Column(Modifier.padding(14.dp).verticalScroll(rememberScrollState())) {
            Text(id, style = MaterialTheme.typography.titleMedium)
            Text(
                listOfNotNull(
                    when (k.online) { true -> "online"; false -> "nicht erreichbar"; null -> "Zustand unbekannt" },
                    w?.rolle,
                    w?.ip,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = when (k.online) { true -> StatusUp; false -> StatusDown; null -> TextLeise },
            )
            val zeilen = listOfNotNull(
                ("Dienste" to "${k.dienste}" + (if (k.auffaellig > 0) ", ${k.auffaellig} auffällig" else "")).takeIf { k.dienste > 0 },
                w?.container?.let { "Container" to "$it" },
                listOfNotNull(
                    w?.cpu?.let { "CPU ${it.toInt()} %" },
                    w?.ram?.let { "RAM ${it.toInt()} %" },
                    w?.platte?.let { "Platte ${it.toInt()} %" },
                ).joinToString(" · ").ifEmpty { null }?.let { "Last" to it },
                listOfNotNull(
                    w?.temp?.let { "${"%.0f".format(it)} °C" },
                    w?.load?.let { "Load ${"%.1f".format(it)}" },
                ).joinToString(" · ").ifEmpty { null }?.let { "Fühler" to it },
                w?.backupAlterH?.let { "Backup" to "vor ${backupText(it)}" + (if (w.backupAlt == true) ", zu alt" else "") },
                w?.let { x ->
                    val g = x.driftGeister ?: 0
                    val f = x.driftFehlend ?: 0
                    if (x.driftGeister == null && x.driftFehlend == null) null
                    else "Drift" to (if (g == 0 && f == 0) "keine" else listOfNotNull(
                        g.takeIf { it > 0 }?.let { "$it ohne Eintrag" },
                        f.takeIf { it > 0 }?.let { "$it fehlen" },
                    ).joinToString(", "))
                },
            )
            Column(Modifier.padding(top = 6.dp)) {
                zeilen.forEach { (titel, wert) ->
                    val warnen = (titel == "Backup" && w?.backupAlt == true) || (titel == "Dienste" && k.auffaellig > 0)
                    Row {
                        Text(titel, style = MaterialTheme.typography.bodySmall, color = TextLeise, modifier = Modifier.weight(0.3f))
                        Text(wert, style = MaterialTheme.typography.bodySmall, color = if (warnen) StatusDown else Color.Unspecified, modifier = Modifier.weight(0.7f))
                    }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                TextButton(onClick = onZeigen) { Text("Auf Karte zeigen") }
                TextButton(onClick = onDurchspielen) { Text("Ausfall durchspielen") }
                TextButton(onClick = onVerlauf) { Text("Verlauf") }
                TextButton(onClick = onSchliessen) { Text("Schließen") }
            }
        }
    }
}

private fun backupText(stunden: Double): String = when {
    stunden < 1 -> "unter 1 h"
    stunden < 48 -> "${stunden.toInt()} h"
    else -> "${(stunden / 24).toInt()} Tagen"
}
