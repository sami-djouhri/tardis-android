package de.djouhri.tardis.ui.tresore

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import de.djouhri.tardis.ui.components.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.djouhri.tardis.data.model.tardis.AllesZuResponse
import de.djouhri.tardis.data.model.tardis.kurzfassung
import de.djouhri.tardis.data.repository.OpsRepository
import de.djouhri.tardis.ui.theme.StatusDown
import de.djouhri.tardis.ui.theme.StatusUp
import de.djouhri.tardis.ui.theme.TextLeise
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotknopfZustand(val laeuft: Boolean = false, val ergebnis: AllesZuResponse? = null)

@HiltViewModel
class NotknopfViewModel @Inject constructor(private val ops: OpsRepository) : ViewModel() {
    private val _zustand = MutableStateFlow(NotknopfZustand())
    val zustand = _zustand.asStateFlow()

    /** Ein Druck, sofort (Owner Runde 73): keine Rueckfrage, kein Entsperren. */
    fun allesZu() {
        if (_zustand.value.laeuft) return
        _zustand.value = NotknopfZustand(laeuft = true)
        viewModelScope.launch {
            val ergebnis = ops.tresoreAllesZu().getOrElse {
                AllesZuResponse(ok = false, fehler = it.message ?: it.javaClass.simpleName)
            }
            _zustand.value = NotknopfZustand(ergebnis = ergebnis)
        }
    }
}

/**
 * Notknopf: alle Tresore schliessen, nie oeffnen. Steht oben auf der
 * Startseite und auf dem Sperrbildschirm der App; dieselbe Wirkung hat die
 * Schnelleinstellungs-Kachel „Tresore zu".
 */
@Composable
fun NotknopfKarte(modifier: Modifier = Modifier, viewModel: NotknopfViewModel = hiltViewModel()) {
    val zustand by viewModel.zustand.collectAsState()
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Button(
                onClick = { viewModel.allesZu() },
                enabled = !zustand.laeuft,
                colors = ButtonDefaults.buttonColors(containerColor = StatusDown),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (zustand.laeuft) "Schließe Tresore…" else "Alle Tresore schließen")
            }
            val ergebnis = zustand.ergebnis
            if (ergebnis != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    ergebnis.kurzfassung(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ergebnis.ok) StatusUp else StatusDown,
                )
                ergebnis.schritte.forEach { s ->
                    Text(
                        "${s.name} · ${s.wirt}: ${s.zustand}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (s.ok) TextLeise else StatusDown,
                    )
                }
            } else if (!zustand.laeuft) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Schließt Filme, Musik, Privat, Briefkasten und Medien. Öffnen geht hier nicht.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextLeise,
                )
            }
        }
    }
}
