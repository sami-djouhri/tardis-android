package de.djouhri.tardis

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import de.djouhri.tardis.freigaben.FreigabeWaechter
import de.djouhri.tardis.navigation.TardisMainScreen
import de.djouhri.tardis.ui.components.LoadingIndicator
import de.djouhri.tardis.ui.pairing.PairingScreen
import de.djouhri.tardis.ui.theme.HomelabTheme

// FragmentActivity (statt ComponentActivity), damit BiometricPrompt die lokale
// Zusatzbestaetigung schreibender Aktionen anzeigen kann.
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    /** Zaehlt hoch bei jedem Oeffnen ueber die Freigabe-Benachrichtigung (auch bei laufender App). */
    private val oeffneFreigaben = mutableIntStateOf(0)

    private fun ziel(intent: Intent?) {
        if (intent?.getStringExtra(FreigabeWaechter.EXTRA_ZIEL) == FreigabeWaechter.ZIEL_FREIGABEN) {
            oeffneFreigaben.intValue += 1
            intent.removeExtra(FreigabeWaechter.EXTRA_ZIEL)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        ziel(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Bildschirmschutz (seit 2.0.22): keine Bildschirmfotos, keine Aufnahme
        // durch andere Apps, leere Vorschau in der App-Uebersicht. Die App zeigt
        // Hostnamen, Logs und Freigaben; das gehoert in keine Bildschirmaufnahme.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        if (savedInstanceState == null) ziel(intent)
        // Immer dunkel, also helle Leistensymbole, unabhaengig vom System.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            HomelabTheme {
                TardisRoot(oeffneFreigaben = oeffneFreigaben.intValue)
            }
        }
    }
}

@Composable
private fun TardisRoot(oeffneFreigaben: Int, rootViewModel: RootViewModel = hiltViewModel()) {
    val target by rootViewModel.target.collectAsState()
    val demoActive by rootViewModel.demoActive.collectAsState()
    val sessionExpired by rootViewModel.sessionExpired.collectAsState()
    when (target) {
        RootTarget.LOADING -> LoadingIndicator()
        RootTarget.PAIRING -> PairingScreen()
        RootTarget.TARDIS -> TardisMainScreen(
            demoActive = demoActive,
            sessionExpired = sessionExpired,
            oeffneFreigaben = oeffneFreigaben,
        )
    }
}
