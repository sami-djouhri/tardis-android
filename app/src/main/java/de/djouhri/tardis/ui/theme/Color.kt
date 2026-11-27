package de.djouhri.tardis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BgDark              = Color(0xFF0A0907)
val SurfaceDarkFlaeche  = Color(0xFF16130F)
val SurfaceHoverDark    = Color(0xFF201C16)
val SurfaceSunkenDark   = Color(0xFF050403)
val BorderDark          = Color(0xFF2A251D)
val BorderStrongDark    = Color(0xFF6E6350)

val TextPrimaryDark     = Color(0xFFFBF3E6)
val TextSecondaryDark   = Color(0xFFC2B6A2)
val TextDimDark         = Color(0xFF948A78)

val AccentDark          = Color(0xFFFFB000)
val AccentHellDark      = Color(0xFFFFE066)
val AccentSoftDark      = Color(0xFF2A1F08)
val AccentForegroundDark = Color(0xFF140B00)

val SuccessDark         = Color(0xFF8FE3A0)
val WarningDark         = Color(0xFFFFE14D)
val DangerDark          = Color(0xFFFF5A4E)
val InfoDark            = Color(0xFFC2B6A2)
val SuccessSoftDark     = Color(0xFF0F2418)
val WarningSoftDark     = Color(0xFF2A2408)
val DangerSoftDark      = Color(0xFF2C0F0B)
val InfoSoftDark        = Color(0xFF1C1813)

// ───────────────────────── Hell, gleichwertig gebaut ──────────────────────
val BgLight             = Color(0xFFEEF0F2)
val SurfaceLightFlaeche = Color(0xFFFFFFFF)
val SurfaceHoverLight   = Color(0xFFF5F7F8)
val SurfaceSunkenLight  = Color(0xFFE6E9EB)
val BorderLight         = Color(0xFFD8DCDF)
val BorderStrongLight   = Color(0xFFB9C0C6)

val TextPrimaryLight    = Color(0xFF171A1D)  // 17.47:1
val TextSecondaryLight  = Color(0xFF545C64)  //  6.79:1
val TextDimLight        = Color(0xFF636B73)  //  4.74:1

val SuccessLight        = Color(0xFF366847)  //  5.69:1 auf dem Grund
val WarningLight        = Color(0xFF866017)  //  4.97:1 auf dem Grund
val DangerLight         = Color(0xFFA8412F)  //  6.07:1
val InfoLight           = Color(0xFF47647F)  //  6.18:1
val SuccessSoftLight    = Color(0xFFE3EFE6)
val WarningSoftLight    = Color(0xFFF6EEDA)
val DangerSoftLight     = Color(0xFFF6E3DF)
val InfoSoftLight       = Color(0xFFE5EBF1)

/*
 * Die vier Statusfarben sind Eigenschaften mit Composable-Getter, keine
 * Konstanten mehr.
 *
 * Vorher waren es feste Werte, und deshalb KONNTEN sie den Hellmodus nicht
 * kennen: dieselbe Farbe stand einmal auf #191C1A und einmal auf #FBFDF9.
 * Ein Wert kann nicht auf beidem richtig sein. Die Schreibweise als
 * Eigenschaft haelt die fuenf Bildschirme unveraendert, die sie benutzen,
 * inklusive ihrer Importe.
 *
 * Sie zeigen bewusst auf die semantischen Tokens und nicht auf eigene Werte:
 * "der Dienst laeuft" und "geprueft und in Ordnung" sind dieselbe Aussage,
 * und zwei Gruentoene dafuer waeren zwei Wahrheiten.
 */
val StatusUp: Color
    @Composable get() = if (istHell()) SuccessLight else SuccessDark

val StatusWarn: Color
    @Composable get() = if (istHell()) WarningLight else WarningDark

val StatusDown: Color
    @Composable get() = if (istHell()) DangerLight else DangerDark

/**
 * Leiser Text (Stand, Nebenzeilen). Nicht `colorScheme.outline`: das ist die
 * Randfarbe (BorderDark, rund 1,3:1 auf der Flaeche) und war bis 2.0.11 an fuenf
 * Stellen als Textfarbe benutzt, „Stand 06:13 Uhr" war damit unlesbar. Gefunden
 * erst im Bildschirmfoto-Test mit Demo-Daten.
 */
val TextLeise: Color
    @Composable get() = if (istHell()) TextDimLight else TextDimDark

val StatusIdle: Color
    @Composable get() = if (istHell()) TextDimLight else TextDimDark

/**
 * Hell oder dunkel, gelesen am Theme statt an der Geraeteeinstellung.
 *
 * Nicht `isSystemInDarkTheme()`: `HomelabTheme` nimmt den Modus als
 * Parameter entgegen, und eine Vorschau oder ein Bildschirmfoto setzt ihn
 * abweichend. Wer hier das Geraet fragt, bekommt in genau diesen Faellen die
 * Farben des falschen Satzes, ohne dass etwas scheitert.
 */
@Composable
private fun istHell(): Boolean = MaterialTheme.colorScheme.background == BgLight
