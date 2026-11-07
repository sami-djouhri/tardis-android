package de.djouhri.tardis.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.djouhri.tardis.MainActivity
import de.djouhri.tardis.data.repository.OpsRepository
import de.djouhri.tardis.ui.theme.DangerDark
import de.djouhri.tardis.ui.theme.SuccessDark
import de.djouhri.tardis.ui.theme.SurfaceDarkFlaeche
import de.djouhri.tardis.ui.theme.TextPrimaryDark
import de.djouhri.tardis.ui.theme.TextSecondaryDark
import de.djouhri.tardis.ui.theme.WarningDark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEinstieg {
    fun opsRepository(): OpsRepository
    fun widgetSpeicher(): WidgetSpeicher
}

private fun einstieg(context: Context) =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEinstieg::class.java)

/**
 * Fragt Alarme und Zaehlstand ab und zeichnet das Widget neu.
 *
 * Laeuft beim Tippen auf ↻ und bei Androids eigener Auffrischung (alle 30 min,
 * das Mindestmass fuer `updatePeriodMillis`). Ausserhalb von WLAN oder VPN
 * scheitert sie; dann bleibt der alte Stand mit seiner Uhrzeit stehen und
 * bekommt den Zusatz „nicht erreichbar", statt zu verschwinden.
 */
suspend fun widgetAuffrischen(context: Context, fristMs: Long = 25_000) {
    val e = einstieg(context)
    val repo = e.opsRepository()
    val speicher = e.widgetSpeicher()
    val ergebnis = withTimeoutOrNull(fristMs) {
        coroutineScope {
            val a = async { repo.alarme() }
            val h = async { repo.hosts() }
            a.await() to h.await()
        }
    }
    val alarme = ergebnis?.first?.getOrNull()
    val hosts = ergebnis?.second?.getOrNull()
    alarme?.let { speicher.alarmeGesehen(it) }
    hosts?.let { speicher.hostsGesehen(it) }
    if (alarme == null && hosts == null) speicher.fehlschlag()
    TardisWidget().updateAll(context)
}

/** Aus der App heraus: nur neu zeichnen, die Zahlen hat die App gerade geschrieben. */
suspend fun widgetNeuZeichnen(context: Context) {
    runCatching { TardisWidget().updateAll(context) }
}

class TardisWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val stand = einstieg(context).widgetSpeicher().lesen()
        provideContent { Inhalt(stand) }
    }
}

class TardisWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TardisWidget()

    /**
     * Androids Takt (alle 30 min) holt neue Zahlen. Hier und nicht in
     * `provideGlance`: das laeuft bei JEDEM Neuzeichnen, auch dem aus der App,
     * und eine Abfrage dort loeste ihr eigenes Neuzeichnen wieder aus.
     */
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        super.onUpdate(context, manager, ids)
        val ende = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                // `goAsync` gibt einer Sendung rund zehn Sekunden, danach
                // beendet Android den Prozess.
                widgetAuffrischen(context, fristMs = 8_000)
            } finally {
                ende.finish()
            }
        }
    }
}

class AuffrischenAktion : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        widgetAuffrischen(context)
    }
}

// Die App ist nur dunkel; das Widget nimmt dieselbe Palette statt des
// Systemthemas (Kontrastwerte stehen in ui/theme/Color.kt).
private val GRUND = SurfaceDarkFlaeche
private val TEXT = TextPrimaryDark
private val LEISE = TextSecondaryDark

enum class Ton { LEISE, GUT, WARNUNG, KRITISCH }

private fun Ton.farbe(): Color = when (this) {
    Ton.LEISE -> TextSecondaryDark
    Ton.GUT -> SuccessDark
    Ton.WARNUNG -> WarningDark
    Ton.KRITISCH -> DangerDark
}

private val UHR = DateTimeFormatter.ofPattern("HH:mm")

private fun uhrzeit(ms: Long?): String? =
    ms?.let { UHR.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) }

/** Die Texte getrennt vom Zeichnen, damit sie ohne Widget-Host pruefbar sind. */
data class WidgetZeilen(
    val alarmZeile: String,
    val alarmTon: Ton,
    val alarmText: String?,
    val diensteZeile: String,
    val diensteTon: Ton,
    val fuss: String,
)

fun widgetZeilen(s: WidgetStand): WidgetZeilen {
    val alarmZeile = when (val n = s.alarme) {
        null -> "Alarme nicht gemessen"
        0 -> "Keine Alarme"
        1 -> "1 Alarm"
        else -> "$n Alarme"
    } + if (s.kritisch > 0) " · ${s.kritisch} kritisch" else ""
    val alarmTon = when {
        s.alarme == null -> Ton.LEISE
        s.kritisch > 0 -> Ton.KRITISCH
        s.alarme > 0 -> Ton.WARNUNG
        else -> Ton.GUT
    }
    val diensteZeile = when (val p = s.probleme) {
        null -> "Dienste nicht gemessen"
        0 -> "Dienste in Ordnung"
        1 -> "1 Dienst braucht dich"
        else -> "$p Dienste brauchen dich"
    } + if (s.stumm > 0) " · ${s.stumm} Rechner stumm" else ""
    val diensteTon = when {
        s.probleme == null -> Ton.LEISE
        s.probleme > 0 -> Ton.WARNUNG
        s.stumm > 0 -> Ton.LEISE
        else -> Ton.GUT
    }
    val stand = listOfNotNull(s.alarmeStandMs, s.diensteStandMs).minOrNull()
    val fuss = buildString {
        append(uhrzeit(stand)?.let { "Stand $it" } ?: "noch kein Stand")
        if (s.zuletztFehler) append(" · nicht erreichbar")
    }
    return WidgetZeilen(alarmZeile, alarmTon, s.ersterAlarm, diensteZeile, diensteTon, fuss)
}

@Composable
private fun Inhalt(stand: WidgetStand) {
    val z = widgetZeilen(stand)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GRUND)
            .cornerRadius(16.dp)
            .padding(12.dp)
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Tardis",
                style = TextStyle(color = ColorProvider(LEISE), fontSize = 12.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.defaultWeight(),
            )
            // 48 dp Trefferflaeche: das Widget liegt zwischen anderen Symbolen.
            Text(
                "↻",
                style = TextStyle(color = ColorProvider(LEISE), fontSize = 18.sp),
                modifier = GlanceModifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    .clickable(actionRunCallback<AuffrischenAktion>()),
            )
        }
        Text(
            z.alarmZeile,
            style = TextStyle(color = ColorProvider(z.alarmTon.farbe()), fontSize = 16.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        z.alarmText?.let {
            Text(it, style = TextStyle(color = ColorProvider(TEXT), fontSize = 12.sp), maxLines = 2)
        }
        Spacer(GlanceModifier.height(6.dp))
        Text(
            z.diensteZeile,
            style = TextStyle(color = ColorProvider(z.diensteTon.farbe()), fontSize = 13.sp),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(z.fuss, style = TextStyle(color = ColorProvider(LEISE), fontSize = 11.sp), maxLines = 1)
    }
}
