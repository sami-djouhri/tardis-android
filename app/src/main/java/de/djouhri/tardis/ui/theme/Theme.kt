package de.djouhri.tardis.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    // Kein Markenakzent: der "Akzent" ist der Vordergrund. Was hervorgehoben
    // wird, wird dunkler beziehungsweise heller, nicht bunter.
    primary = TextPrimaryLight,
    onPrimary = SurfaceLightFlaeche,
    primaryContainer = SurfaceSunkenLight,
    onPrimaryContainer = TextPrimaryLight,
    secondary = TextSecondaryLight,
    onSecondary = SurfaceLightFlaeche,
    secondaryContainer = SurfaceHoverLight,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = InfoLight,
    onTertiary = SurfaceLightFlaeche,
    tertiaryContainer = InfoSoftLight,
    onTertiaryContainer = InfoLight,
    error = DangerLight,
    onError = SurfaceLightFlaeche,
    errorContainer = DangerSoftLight,
    onErrorContainer = DangerLight,
    background = BgLight,
    onBackground = TextPrimaryLight,
    surface = SurfaceLightFlaeche,
    onSurface = TextPrimaryLight,
    surfaceVariant = SurfaceSunkenLight,
    onSurfaceVariant = TextSecondaryLight,
    // Material: `outline` ist der kraeftige Rand (Schalter aus, OutlinedButton),
    // `outlineVariant` der zarte (Trenner). Bis 2.0.11 vertauscht: ein
    // ausgeschalteter Schalter war auf der Flaeche kaum zu sehen.
    outline = BorderStrongLight,
    outlineVariant = BorderLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = TextPrimaryDark,
    onPrimary = BgDark,
    primaryContainer = SurfaceHoverDark,
    onPrimaryContainer = TextPrimaryDark,
    secondary = TextSecondaryDark,
    onSecondary = BgDark,
    secondaryContainer = SurfaceHoverDark,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = InfoDark,
    onTertiary = BgDark,
    tertiaryContainer = InfoSoftDark,
    onTertiaryContainer = InfoDark,
    error = DangerDark,
    onError = BgDark,
    errorContainer = DangerSoftDark,
    onErrorContainer = TextPrimaryDark,
    background = BgDark,
    onBackground = TextPrimaryDark,
    surface = SurfaceDarkFlaeche,
    onSurface = TextPrimaryDark,
    surfaceVariant = SurfaceSunkenDark,
    onSurfaceVariant = TextSecondaryDark,
    // Material: `outline` ist der kraeftige Rand (Schalter aus, OutlinedButton),
    // `outlineVariant` der zarte (Trenner). Bis 2.0.11 vertauscht: ein
    // ausgeschalteter Schalter war auf der Flaeche kaum zu sehen.
    outline = BorderStrongDark,
    outlineVariant = BorderDark,
)

/**
 * @param dynamicColor Steht seit 2026-09-27 auf false, und das ist die
 *   eigentliche Aenderung am Erscheinungsbild. Mit `true` ersetzt Android ab
 *   Version 12 die gesamte Palette durch eine aus dem Hintergrundbild
 *   abgeleitete: die hier sorgfaeltig gesetzten Farben kamen auf einem aktuellen
 *   Telefon also nie zum Vorschein, die App sah bei jedem Hintergrundwechsel
 *   anders aus. Schlimmer ist das bei einem Tardis als bei einer Spiele-App,
 *   weil Farbe hier Bedeutung traegt: der Update-Hinweis benutzt
 *   `primaryContainer`, und wenn das je nach Wandbild rot-orange ausfaellt,
 *   liest es sich wie eine Warnung. Die Statusfarben (gruen/gelb/rot) lagen
 *   ohnehin fest und standen dann neben einer fremden Palette.
 *   Zurueckdrehen ist ein Wort an dieser Stelle.
 */
@Composable
fun HomelabTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
