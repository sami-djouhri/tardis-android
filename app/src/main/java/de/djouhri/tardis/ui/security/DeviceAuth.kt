package de.djouhri.tardis.ui.security

import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import de.djouhri.tardis.util.findFragmentActivity

/**
 * Duenne Huelle um [BiometricPrompt] fuer die lokale Zusatzbestaetigung
 * schreibender Aktionen (Biometrie oder Geraete-PIN/-Muster).
 *
 * Bewusst als ZUSAETZLICHE clientseitige Schutzschicht dokumentiert - die
 * eigentliche Autorisierung erzwingt das Gateway serverseitig (JWT/mTLS). Fehlt
 * ein Geraeteschutz, degradiert der Aufrufer auf die textuelle Bestaetigung.
 */
class DeviceAuthController(private val activity: FragmentActivity) {

    // BIOMETRIC_STRONG|DEVICE_CREDENTIAL ist erst ab API 30 kombinierbar; darunter
    // nur starke Biometrie (Geraete-PIN-Fallback ist dort ueber diese API nicht
    // erlaubt - der Aufrufer nutzt dann die textuelle Bestaetigung).
    private val authenticators: Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        else BIOMETRIC_STRONG

    fun canAuthenticate(): Boolean =
        BiometricManager.from(activity).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailed: (String) -> Unit = {},
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Nutzerabbruch ist kein Fehler, den wir melden muessen.
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onFailed(errString.toString())
                    }
                }
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .apply {
                // Ohne Credential-Fallback braucht der Prompt einen Negativ-Button.
                if (authenticators and DEVICE_CREDENTIAL == 0) setNegativeButtonText("Abbrechen")
            }
            .build()

        prompt.authenticate(info)
    }

    /**
     * Gibt eine Keystore-[Signature] per Fingerabdruck/PIN frei (CryptoObject).
     *
     * Anders als [authenticate] ist das keine Zusatzschicht: der Freigabeschluessel
     * unterschreibt ohne diesen Schritt gar nicht. Ohne Geraeteschutz gibt es
     * deshalb auch keinen Rueckfall auf einen Textdialog.
     */
    fun unterschreiben(
        title: String,
        subtitle: String,
        signature: java.security.Signature,
        onSuccess: (java.security.Signature) -> Unit,
        onFailed: (String) -> Unit,
    ) {
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val sig = result.cryptoObject?.signature
                    if (sig == null) onFailed("Keystore hat die Unterschrift nicht freigegeben") else onSuccess(sig)
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
                    ) {
                        onFailed(errString.toString())
                    }
                }
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .apply { if (authenticators and DEVICE_CREDENTIAL == 0) setNegativeButtonText("Abbrechen") }
            .build()
        prompt.authenticate(info, BiometricPrompt.CryptoObject(signature))
    }
}

/** Liefert einen [DeviceAuthController], wenn eine FragmentActivity vorhanden ist. */
@Composable
fun rememberDeviceAuth(): DeviceAuthController? {
    val activity = LocalContext.current.findFragmentActivity() ?: return null
    return remember(activity) { DeviceAuthController(activity) }
}
