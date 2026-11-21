package de.djouhri.tardis.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Die Karte der App: Material-Card mit Eckwinkeln im gewaehlten Ton (oben
 * links, unten rechts), wie ein Sucher. Gleicher Name und gleiche Form wie
 * `androidx.compose.material3.Card`, damit die Bildschirme nur den Import
 * tauschen. Der Ton soll sichtbar sein, ohne Bedeutung zu tragen: Titel und
 * Zahlen bleiben in den Ampelfarben, die Winkel sagen nichts ueber den Zustand.
 */
@Composable
fun Card(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    androidx.compose.material3.Card(
        modifier = modifier.eckwinkel(MaterialTheme.colorScheme.primary),
        content = content,
    )
}

/** Zwei Winkel an gegenueberliegenden Ecken, ueber dem Inhalt gezeichnet. */
fun Modifier.eckwinkel(farbe: Color, laenge: Dp = 14.dp, dicke: Dp = 1.5.dp): Modifier = drawWithContent {
    drawContent()
    val l = laenge.toPx()
    val d = dicke.toPx()
    val h = d / 2
    val c = farbe.copy(alpha = 0.85f)
    drawLine(c, Offset(0f, h), Offset(l, h), strokeWidth = d)
    drawLine(c, Offset(h, 0f), Offset(h, l), strokeWidth = d)
    val w = size.width
    val y = size.height
    drawLine(c, Offset(w, y - h), Offset(w - l, y - h), strokeWidth = d)
    drawLine(c, Offset(w - h, y), Offset(w - h, y - l), strokeWidth = d)
}
