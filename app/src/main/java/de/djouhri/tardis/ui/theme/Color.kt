package de.djouhri.tardis.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/*
 * Tardis, die Leitwarte in der Hand. Spiegel von `tardis/app/theme.css`.
 *
 * WAS HIER VORHER STAND. Eine Palette aus dem Material Theme Builder, ein
 * Teal-Gruen mit dem Kommentar "Homelab/Tech feel", dazu die vollstaendigen
 * Saetze fuer primary/secondary/tertiary/error in je vier Abstufungen. Das
 * ist die Vorgabe des Assistenten mit einem anderen Grundton, und man sieht
 * ihr an, dass die Frage "was ist dieser Dienst eigentlich" nie gestellt
 * wurde: ein Tardis hat keine Marke, es hat einen Zustand.
 *
 * Der Web-Zwilling auf :8125 hat diese Frage am 2026-09-27 beantwortet, und
 * die Antwort gilt hier genauso. Aus seinem Kopf:
 *
 *   1. DUNKEL, WEIL EINE LEITWARTE DUNKEL IST. Auf einem ruhigen Grund
 *      sprechen die wenigen farbigen Stellen. Der Hellmodus bleibt und ist
 *      gleichwertig gebaut, fuer den Blick bei Tageslicht.
 *   2. FARBE IST DRINGLICHKEIT, NICHT MARKE. Es gibt keinen Hausakzent.
 *      Bernstein heisst "sieh mal nach", Rot heisst "jetzt", Gruen heisst
 *      "geprueft und in Ordnung". Alles andere ist grau.
 *
 * Der "Akzent" ist deshalb schlicht der Vordergrund: was hervorgehoben wird,
 * wird heller, nicht bunter. Der Grundton ist ein neutrales Blaugrau und
 * bewusst NICHT das Blauschwarz von `launchpad` oder das warme Tintenschwarz
 * von `saganta`: diese Flaechen stehen nebeneinander.
 *
 * DREI WERTE WEICHEN VOM WEB AB, und zwar nach oben. Gerechnet mit
 * interne Notiz fielen sie durch:
 *
 *   - textDim dunkel  #6F7780 -> #848C95  (3.65:1, jetzt 4.87:1)
 *   - textDim hell    #838C94 -> #636B73  (3.42:1, jetzt 4.74:1)
 *   - danger dunkel   #D44A3A -> #E4685A  (3.82:1, jetzt 5.07:1)
 *   - success hell    #3D7A50 -> #366847  (4.49:1, jetzt 5.69:1)
 *   - warning hell    #926A1C -> #866017  (4.27:1, jetzt 4.97:1)
 *
 * Gemessen wird gegen den SCHLECHTESTEN Grund, und die letzten zwei
 * Zeilen sind genau dafuer der Beleg: `success` und `warning` bestanden
 * gegen die weisse Karte (5.12:1 und 4.88:1) und fielen gegen die Seite
 * (#EEF0F2) durch. Sie standen schon als "geprueft" in diesem Kopf.
 * Aufgefallen ist es erst, als die Werte am Ende nicht aus der Notiz,
 * sondern aus DIESER DATEI gelesen und neu gerechnet wurden. Eine Farbe
 * gilt erst als geprueft, wenn sie gegen jede Flaeche geprueft ist, auf
 * der sie vorkommt.
 *
 * Der Abgleich mit dem Web steht in der MERKLISTE; dort sind alle fuenf
 * Werte noch in ihrer alten Fassung.
 *
 * Die alten Statusfarben waren der schwerere Fall: gemessen erreichten
 * DREI VON VIER im Hellmodus keine 3:1 (#4CAF50 gruen 2.72, #FFB300
 * bernstein 1.75, #9E9E9E grau 2.62). Es waren die Vorgabewerte von Material
 * Design, gewaehlt fuer farbige Flaechen, nicht fuer Text auf Weiss. In einer
 * App, deren ganzer Inhalt ein Zustand ist, war damit im Hellmodus der
 * Zustand selbst am schlechtesten lesbar.
 */

// ───────────────────────── Dunkel, der Normalfall ─────────────────────────
// Seit 2.0.31 (2026-11-21) Blau, Entscheid: eine feste Farbe statt der
// Farbwahl aus 2.0.30. Grund und Flaechen sind Blauschwarz, der Akzent ein
// helles Blau; Fehler ist wieder Rot (vorher Pink, weil Rot der Akzent war).
// Kontraste prueft SchattenTest gegen das fertige Schema.
val BgDark              = Color(0xFF04070D)
val SurfaceDarkFlaeche  = Color(0xFF0A1220)
val SurfaceHoverDark    = Color(0xFF111B2D)
val SurfaceSunkenDark   = Color(0xFF03050A)
val BorderDark          = Color(0xFF1A2A44)
val BorderStrongDark    = Color(0xFF27406A)

val TextPrimaryDark     = Color(0xFFD6E2F2)
val TextSecondaryDark   = Color(0xFF97AAC4)
val TextDimDark         = Color(0xFF8196B2)

val AccentDark          = Color(0xFF4C9AFF)
val AccentHellDark      = Color(0xFF8CBEFF)
val AccentSoftDark      = Color(0xFF0D1E38)
val AccentForegroundDark = Color(0xFF020814)

val SuccessDark         = Color(0xFF7FD18B)  // 10.98:1
val WarningDark         = Color(0xFFE8C547)  // 12.05:1
val DangerDark          = Color(0xFFFF6B6B)
val InfoDark            = Color(0xFF8FB3D9)  //  9.26:1
val SuccessSoftDark     = Color(0xFF0F2213)
val WarningSoftDark     = Color(0xFF2A230C)
val DangerSoftDark      = Color(0xFF2E0E12)
val InfoSoftDark        = Color(0xFF0F1A26)

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
