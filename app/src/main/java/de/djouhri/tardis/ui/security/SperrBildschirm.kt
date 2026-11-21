package de.djouhri.tardis.ui.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.djouhri.tardis.ui.theme.StatusDown
import de.djouhri.tardis.ui.theme.TextLeise
import de.djouhri.tardis.ui.tresore.NotknopfKarte

/**
 * Sperrbildschirm der App (Owner B84): Biometrie oder Geraete-PIN bei jedem
 * Start und nach 5 Minuten im Hintergrund. Der Notknopf steht hier mit drauf,
 * er geht ohne Entsperren.
 *
 * Ohne eingerichteten Geraeteschutz gibt es nichts, wogegen geprueft werden
 * koennte. Dann sagt der Bildschirm das und laesst weiter, statt die App
 * unbenutzbar zu machen: die Sperre waere ohnehin nicht staerker als das
 * ungesperrte Telefon.
 */
@Composable
fun SperrBildschirm(onEntsperrt: () -> Unit) {
    val auth = rememberDeviceAuth()
    val moeglich = auth?.canAuthenticate() == true
    var fehler by remember { mutableStateOf<String?>(null) }

    fun fragen() {
        auth?.authenticate(
            title = "Tardis entsperren",
            subtitle = "Fingerabdruck oder Geräte-PIN",
            onSuccess = onEntsperrt,
            onFailed = { fehler = it },
        )
    }

    LaunchedEffect(moeglich) { if (moeglich) fragen() }

    Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent) {
        Column(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Tardis ist gesperrt", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            if (moeglich) {
                Button(onClick = { fehler = null; fragen() }, modifier = Modifier.fillMaxWidth()) {
                    Text("Entsperren")
                }
            } else {
                Text(
                    "Auf diesem Telefon ist keine Displaysperre eingerichtet. Ohne sie kann die App nicht prüfen, wer sie öffnet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StatusDown,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onEntsperrt, modifier = Modifier.fillMaxWidth()) {
                    Text("Ohne Sperre weiter")
                }
            }
            fehler?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = TextLeise)
            }
            Spacer(Modifier.height(32.dp))
            NotknopfKarte()
        }
    }
}
