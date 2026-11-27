package de.djouhri.tardis.security

import de.djouhri.tardis.BuildConfig
import de.djouhri.tardis.data.local.SettingsStore
import de.djouhri.tardis.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionState @Inject constructor(
    settingsStore: SettingsStore,
    @ApplicationScope scope: CoroutineScope,
) {
    @Volatile
    var token: String? = null
        private set

    @Volatile
    private var storedGatewayUrl: String? = null

    @Volatile
    var caFingerprint: String? = null
        private set

    @Volatile
    var clientCertPem: String? = null
        private set

    @Volatile
    var caCertPem: String? = null
        private set

    // Waehrend des Pairings gesetzt (aus dem QR), bevor der Wert persistiert ist.
    @Volatile
    private var gatewayOverride: String? = null

    private val _sessionExpired = MutableStateFlow(false)

    /** Wird true, sobald das Gateway ein gueltiges Token mit 401 abweist. */
    val sessionExpired: StateFlow<Boolean> = _sessionExpired.asStateFlow()

    init {
        scope.launch { settingsStore.jwtToken.collect { token = it } }
        scope.launch { settingsStore.gatewayUrl.collect { storedGatewayUrl = it } }
        scope.launch { settingsStore.caFingerprint.collect { caFingerprint = it } }
        scope.launch { settingsStore.clientCertPem.collect { clientCertPem = it } }
        scope.launch { settingsStore.caCertPem.collect { caCertPem = it } }
    }

    /** Aktive Gateway-Basis-URL: QR-Override > persistiert > eingebauter Platzhalter. */
    fun gatewayBaseUrl(): String =
        gatewayOverride ?: storedGatewayUrl ?: BuildConfig.GATEWAY_URL

    /** Setzt das Gateway aus dem gescannten QR fuer die Dauer des Pairings. */
    fun overrideGateway(url: String?) {
        gatewayOverride = url?.takeIf { it.isNotBlank() }
    }

    /**
     * Uebernimmt den Auth-Zustand nach erfolgreichem Pairing SOFORT - ohne auf
     * die (asynchronen) DataStore-Flows zu warten. Damit traegt der erste
     * Request nach dem Pairing garantiert schon Token/Gateway und laeuft nicht
     * ins Race (leerer Bearer → 401). Die Flow-Collectoren bestaetigen dieselben
     * Werte danach nur noch.
     */
    fun applyPairing(
        gatewayUrl: String?,
        jwt: String,
        clientCert: String,
        caCert: String,
        fingerprint: String?,
    ) {
        token = jwt
        clientCertPem = clientCert
        caCertPem = caCert
        if (!fingerprint.isNullOrBlank()) caFingerprint = fingerprint
        if (!gatewayUrl.isNullOrBlank()) storedGatewayUrl = gatewayUrl
        gatewayOverride = null
        clearUnauthorized()
    }

    fun clearSession() {
        token = null
        clientCertPem = null
        caCertPem = null
        caFingerprint = null
        storedGatewayUrl = null
        gatewayOverride = null
        clearUnauthorized()
    }

    fun reportUnauthorized() {
        _sessionExpired.value = true
    }

    /** Zuruecksetzen nach erfolgreicher Antwort oder erneutem Pairing. */
    fun clearUnauthorized() {
        if (_sessionExpired.value) _sessionExpired.value = false
    }
}
