package de.djouhri.tardis.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import de.djouhri.tardis.data.model.tardis.ServiceSummary
import de.djouhri.tardis.data.model.tardis.ServiceZustand
import de.djouhri.tardis.ui.theme.StatusDown
import de.djouhri.tardis.ui.theme.StatusIdle
import de.djouhri.tardis.ui.theme.StatusUp
import de.djouhri.tardis.ui.theme.StatusWarn
import de.djouhri.tardis.ui.theme.glut

/**
 * Ampelfarbe fuer einen Container.
 *
 * Rot bekommt nur, was wirklich abweicht. Ein bewusst gestoppter Dienst ist
 * grau: er ist kein Ausfall, sondern ein Zustand. Vorher war hier jeder
 * beendete Container rot, auf host also dauerhaft elf Stueck.
 */
@Composable
fun serviceStatusColor(service: ServiceSummary): Color = when (service.zustand) {
    ServiceZustand.AUFFAELLIG -> StatusWarn
    ServiceZustand.LAEUFT -> StatusUp
    ServiceZustand.AUSGEFALLEN -> StatusDown
    ServiceZustand.BEWUSST_AUS -> StatusIdle
    ServiceZustand.UNBEWERTET -> StatusIdle
}

/** Ampelfarbe fuer einen Auslastungswert (Prozent). */
@Composable
fun usageColor(percent: Double?): Color = when {
    percent == null -> StatusIdle
    percent >= 90 -> StatusDown
    percent >= 75 -> StatusWarn
    else -> StatusUp
}

/**
 * Ampelpunkt. Ein roter Punkt (ausgefallen) bekommt einen atmenden Lichthof,
 * damit er in einer langen Liste nicht neben den gruenen untergeht.
 */
@Composable
fun StatusDot(color: Color, modifier: Modifier = Modifier) {
    val rot = color == StatusDown
    Box(
        modifier = modifier
            .size(12.dp)
            .then(if (rot) Modifier.glut(color) else Modifier)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}
