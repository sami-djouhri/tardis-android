package de.djouhri.tardis.ui.theme

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
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

internal val dunklesSchemaAmber = darkColorScheme(
    primary = AccentDark,
    onPrimary = AccentForegroundDark,
    primaryContainer = AccentSoftDark,
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
    outline = TextDimDark,
    outlineVariant = BorderDark,
    surfaceContainerLowest = SurfaceSunkenDark,
    surfaceContainerLow = BgDark,
    surfaceContainer = SurfaceDarkFlaeche,
    surfaceContainerHigh = SurfaceHoverDark,
    surfaceContainerHighest = SurfaceHoverDark,
    surfaceBright = SurfaceHoverDark,
    surfaceDim = SurfaceSunkenDark,
    inverseSurface = TextPrimaryDark,
    inverseOnSurface = BgDark,
)

@Composable
fun HomelabTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    effekte: Boolean = Aussehen.effekte,
    content: @Composable () -> Unit
) {
    val ton = TardisTon.AMBER
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> dunklesSchemaAmber
        else -> LightColorScheme
    }

    val typographie = remember(darkTheme) { if (darkTheme) terminalTypography(ton.akzent) else Typography }
    CompositionLocalProvider(LocalTardisTon provides ton, LocalTardisEffekte provides effekte) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typographie,
            shapes = if (darkTheme) TerminalShapes else MaterialTheme.shapes,
        ) {
            if (darkTheme) {
                // Von hinten nach vorn: Grund mit Nebel, Inhalt, Korn, Scanlines.
                // Wer darueber eine deckende Flaeche in der Grundfarbe legt
                // (Scaffold, Surface mit `background`), verdeckt den Nebel.
                Box(Modifier.fillMaxSize()) {
                    Nebel(colorScheme.background, ton)
                    Box(Modifier.fillMaxSize().koernung().scanlines().flimmern()) { content() }
                }
            } else {
                content()
            }
        }
    }
}
