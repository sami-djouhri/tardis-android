package de.djouhri.tardis.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as BildCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Ein Farbton: Akzent, Schrift auf voller Akzentflaeche und die zwei Nebelfarben. */
data class TardisTon(
    val schluessel: String,
    val name: String,
    val akzent: Color,
    val akzentHell: Color,
    val akzentVorne: Color,
    val nebelOben: Color,
    val nebelUnten: Color,
) {
    companion object {
        val AMBER = TardisTon(
            "amber", "Amber",
            akzent = AccentDark, akzentHell = AccentHellDark, akzentVorne = AccentForegroundDark,
            nebelOben = Color(0xFFFFB000), nebelUnten = Color(0xFFFF7A00),
        )
        val VORGABE = AMBER
    }
}

/** Der gewaehlte Ton; HomelabTheme setzt ihn, der Bildschirmfoto-Test je Variante. */
val LocalTardisTon = staticCompositionLocalOf { TardisTon.VORGABE }

/** Schalter „Effekte" in den Einstellungen. */
val LocalTardisEffekte = staticCompositionLocalOf { true }

/** Systemeinstellung „Animationen entfernen" (Animator-Dauer 0) respektieren. */
@Composable
fun animationenErlaubt(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

/** Bewegte Effekte nur, wenn das System Animationen erlaubt und der Schalter an ist. */
@Composable
fun effekteAn(): Boolean = animationenErlaubt() && LocalTardisEffekte.current

// ---------------------------------------------------------------- Speicher

/**
 * Effekt-Schalter, gespeichert in SharedPreferences statt im DataStore: das
 * Theme braucht ihn beim ersten Bild. Ein alter Eintrag „ton" aus 2.0.30 bleibt
 * liegen und wird nicht mehr gelesen.
 */
object Aussehen {
    private const val DATEI = "aussehen"
    var effekte by mutableStateOf(true)
        private set

    fun laden(context: Context) {
        effekte = context.getSharedPreferences(DATEI, Context.MODE_PRIVATE).getBoolean("effekte", true)
    }

    fun setzeEffekte(context: Context, an: Boolean) {
        effekte = an
        context.getSharedPreferences(DATEI, Context.MODE_PRIVATE).edit().putBoolean("effekte", an).apply()
    }
}

// ---------------------------------------------------------------- Nebel

/**
 * Grund, zwei langsam wandernde Lichtflecken und eine Vignette. Eigene Ebene,
 * 15 Schritte je Sekunde: das Wandern malt nur diese Ebene neu.
 */
@Composable
fun BoxScope.Nebel(grund: Color, ton: TardisTon) {
    val bewegt = effekteAn()
    var t by remember { mutableFloatStateOf(0.18f) }
    if (bewegt) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(66)
                t = (t + 66f / 40_000f) % 1f
            }
        }
    }
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer()
            .drawBehind {
                drawRect(grund)
                val w = size.width
                val h = size.height
                val a = t * 2f * PI.toFloat()
                val oben = Offset(w * (0.78f + 0.14f * sin(a)), h * (0.08f + 0.05f * cos(a * 2f)))
                val unten = Offset(w * (0.18f + 0.12f * cos(a)), h * (0.90f + 0.04f * sin(a * 3f)))
                drawRect(
                    Brush.radialGradient(
                        0f to ton.nebelOben.copy(alpha = 0.13f),
                        0.45f to ton.nebelOben.copy(alpha = 0.04f),
                        1f to Color.Transparent,
                        center = oben,
                        radius = w * 0.95f,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        0f to ton.nebelUnten.copy(alpha = 0.11f),
                        0.5f to ton.nebelUnten.copy(alpha = 0.03f),
                        1f to Color.Transparent,
                        center = unten,
                        radius = w * 0.85f,
                    ),
                )
            },
    )
}

// ---------------------------------------------------------------- Koernung

private val rauschen: ImageBitmap by lazy {
    val kante = 160
    val bild = ImageBitmap(kante, kante)
    val leinwand = BildCanvas(bild)
    val r = Random(1729)
    val hell = ArrayList<Offset>()
    val dunkel = ArrayList<Offset>()
    repeat(kante * kante / 5) {
        val p = Offset(r.nextInt(kante) + 0.5f, r.nextInt(kante) + 0.5f)
        if (r.nextBoolean()) hell += p else dunkel += p
    }
    val pinsel = Paint().apply { strokeWidth = 1f }
    pinsel.color = Color.White
    leinwand.drawPoints(PointMode.Points, hell, pinsel)
    pinsel.color = Color.Black
    leinwand.drawPoints(PointMode.Points, dunkel, pinsel)
    bild
}

/** Feines, stehendes Filmkorn ueber dem Inhalt; ein gekacheltes Bild, ein Rechteck je Bild. */
fun Modifier.koernung(staerke: Float = 0.04f): Modifier = drawWithContent {
    drawContent()
    drawRect(ShaderBrush(ImageShader(rauschen, TileMode.Repeated, TileMode.Repeated)), alpha = staerke)
}

// ---------------------------------------------------------------- Glitch

class GlitchZustand {
    var staerke by mutableFloatStateOf(0f)
    var keim by mutableIntStateOf(0)

    /** Zwei harte Spruenge, zusammen unter 250 ms. */
    suspend fun stoss(maximal: Float = 1f) {
        repeat(2) {
            keim = Random.nextInt()
            staerke = maximal * Random.nextDouble(0.55, 1.0).toFloat()
            delay(Random.nextLong(45, 85))
            staerke = 0f
            delay(Random.nextLong(30, 60))
        }
    }
}

/**
 * Ein Stoss, wenn [neu] wahr wird und [schluessel] sich gegenueber dem
 * letzten Stand geaendert hat. Beim ersten Zeichnen nie: wer die App oeffnet,
 * soll nicht jedes Mal einen Glitch sehen, nur wenn waehrenddessen etwas
 * dazukommt.
 */
@Composable
fun rememberGlitch(schluessel: Any?, neu: (vorher: Any?, jetzt: Any?) -> Boolean): GlitchZustand {
    val g = remember { GlitchZustand() }
    val vorher = remember { mutableStateOf<Any?>(null) }
    val erster = remember { mutableStateOf(true) }
    val bewegt = effekteAn()
    LaunchedEffect(schluessel) {
        val alt = vorher.value
        vorher.value = schluessel
        if (erster.value) {
            erster.value = false
        } else if (bewegt && neu(alt, schluessel)) {
            g.stoss()
        }
    }
    return g
}

/**
 * Farbversatz wie auf einem gestoerten Bildschirm: Rotkanal nach links, Gruen
 * und Blau nach rechts (zweite Lage additiv), dazu verrutschte Streifen. In
 * Ruhe zeichnet der Modifier nur den Inhalt.
 */
fun Modifier.glitch(zustand: GlitchZustand, weite: Float = 0.014f): Modifier = drawWithContent {
    val s = zustand.staerke
    if (s <= 0f) {
        drawContent()
        return@drawWithContent
    }
    val r = Random(zustand.keim)
    val dx = size.width * weite * s
    val grenzen = Rect(-dx * 2, 0f, size.width + dx * 2, size.height)
    drawIntoCanvas { leinwand ->
        leinwand.saveLayer(grenzen, Paint())
        leinwand.saveLayer(grenzen, Paint().apply { colorFilter = ColorFilter.tint(Color(0xFFFF0000), BlendMode.Modulate) })
        translate(-dx, 0f) { this@drawWithContent.drawContent() }
        leinwand.restore()
        leinwand.saveLayer(grenzen, Paint().apply {
            colorFilter = ColorFilter.tint(Color(0xFF00FFFF), BlendMode.Modulate)
            blendMode = BlendMode.Plus
        })
        translate(dx, 0f) { this@drawWithContent.drawContent() }
        leinwand.restore()
        leinwand.restore()
    }
    repeat(r.nextInt(2, 4)) {
        val y = r.nextFloat() * size.height
        val hoehe = size.height * (0.06f + r.nextFloat() * 0.18f)
        val versatz = (r.nextFloat() - 0.5f) * size.width * weite * 6f * s
        clipRect(0f, y, size.width, y + hoehe) {
            translate(versatz, 0f) { this@drawWithContent.drawContent() }
        }
    }
}

// ---------------------------------------------------------------- Glut

/**
 * Ein atmender Lichthof hinter einem Element (Statuspunkt „ausgefallen",
 * kritischer Zaehler). Eine Runde dauert 2,4 s, weit unter jeder
 * Flackerschwelle; ohne Effekte steht ein ruhiger Hof.
 */
@Composable
fun Modifier.glut(farbe: Color, weite: Float = 1.9f): Modifier {
    val staerke = if (effekteAn()) {
        val lauf = rememberInfiniteTransition(label = "glut")
        lauf.animateFloat(
            initialValue = 0.28f,
            targetValue = 0.62f,
            animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing), RepeatMode.Reverse),
            label = "glut-staerke",
        ).value
    } else {
        0.4f
    }
    return drawBehind {
        drawCircle(
            lichtfleck(farbe, center, size.minDimension / 2f * weite, staerke),
            radius = size.minDimension / 2f * weite,
            center = center,
        )
    }
}

/** Ein Kreis aus Licht. */
fun lichtfleck(farbe: Color, mitte: Offset, radius: Float, staerke: Float): Brush =
    Brush.radialGradient(
        0f to farbe.copy(alpha = staerke),
        0.55f to farbe.copy(alpha = staerke * 0.35f),
        1f to Color.Transparent,
        center = mitte,
        radius = radius.coerceAtLeast(1f),
    )

// ---------------------------------------------------------------- Einblenden

/**
 * Beim Wechsel von [schluessel] baut sich der Inhalt von oben nach unten auf,
 * vorne eine leuchtende Abtastlinie (260 ms). Beim ersten Zeichnen steht er
 * sofort da, sonst bliebe der Bildschirmfoto-Test leer.
 */
@Composable
fun Modifier.einblenden(schluessel: Any?, linie: Color): Modifier {
    val p = remember { Animatable(1f) }
    val erster = remember { mutableStateOf(true) }
    val bewegt = effekteAn()
    LaunchedEffect(schluessel) {
        if (erster.value) {
            erster.value = false
        } else if (bewegt) {
            p.snapTo(0f)
            p.animateTo(1f, tween(260, easing = LinearEasing))
        }
    }
    return drawWithContent {
        val v = p.value
        if (v >= 1f) {
            drawContent()
            return@drawWithContent
        }
        val y = size.height * v
        clipRect(0f, 0f, size.width, y) { this@drawWithContent.drawContent() }
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 1f to linie.copy(alpha = 0.22f), startY = y - 60f, endY = y),
            topLeft = Offset(0f, y - 60f),
            size = Size(size.width, 60f),
        )
        drawRect(linie.copy(alpha = 0.9f), topLeft = Offset(0f, y - 1.5f), size = Size(size.width, 3f))
    }
}
