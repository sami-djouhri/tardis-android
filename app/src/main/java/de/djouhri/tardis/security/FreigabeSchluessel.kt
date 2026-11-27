package de.djouhri.tardis.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreigabeSchluessel @Inject constructor() {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "tardis_freigabe_key"
        private const val SIG_ALG = "SHA256withECDSA"
        private const val KOPF = "homelab-pull-deploy-v1"

        /** Der unterschriebene Text. Gleich zu freigabe.py und mobile-api/routers/freigaben.py. */
        fun nachricht(wirt: String, projekt: String, repo: String, commit: String, ausgestellt: Long): ByteArray =
            "$KOPF\nwirt=$wirt\nprojekt=$projekt\nrepo=$repo\ncommit=$commit\nausgestellt=$ausgestellt\n"
                .toByteArray(Charsets.UTF_8)

        /** Befehls-Freigabe: gleich zu befehls-freigabe/laeufer.py und mobile-api/routers/befehle.py. */
        fun befehlNachricht(
            id: String, sha256: String, ausgestellt: Long, wirt: String = "host", modus: String = "lauf",
        ): ByteArray = (
            if (modus == "lauf") "homelab-befehl-v1\nwirt=$wirt\nid=$id\nsha256=$sha256\nausgestellt=$ausgestellt\n"
            // v2 traegt den Modus: eine Probe- oder Abbruch-Unterschrift taugt fuer keinen echten Lauf.
            else "homelab-befehl-v2\nwirt=$wirt\nid=$id\nsha256=$sha256\nmodus=$modus\nausgestellt=$ausgestellt\n"
            ).toByteArray(Charsets.UTF_8)

        /** SHA-256 ueber SubjectPublicKeyInfo, klein-hex; dieselbe Zahl rechnet der Zielwirt. */
        fun fingerabdruck(spki: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(spki).joinToString("") { "%02x".format(it) }

        /** In Vierergruppen zum Abtippen am Zielwirt. */
        fun gruppiert(fp: String): String = fp.chunked(4).joinToString(" ")
    }

    private fun store(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun vorhanden(): Boolean = runCatching { store().containsAlias(ALIAS) }.getOrDefault(false)

    fun erzeugen() {
        loeschen()
        try {
            erzeugen(strongBox = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        } catch (e: StrongBoxUnavailableException) {
            erzeugen(strongBox = false)
        }
    }

    private fun erzeugen(strongBox: Boolean) {
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .setUserAuthenticationRequired(true)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // 0 Sekunden: jede Unterschrift braucht ihre eigene Bestaetigung.
                    setUserAuthenticationParameters(
                        0,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setUserAuthenticationValidityDurationSeconds(-1)
                }
                if (strongBox) setIsStrongBoxBacked(true)
            }
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEYSTORE).run {
            initialize(spec)
            generateKeyPair()
        }
    }

    fun loeschen() {
        val s = store()
        if (s.containsAlias(ALIAS)) s.deleteEntry(ALIAS)
    }

    private fun spki(): ByteArray = store().getCertificate(ALIAS).publicKey.encoded

    fun fingerabdruck(): String? = if (vorhanden()) fingerabdruck(spki()) else null

    fun pem(): String {
        val b64 = Base64.encodeToString(spki(), Base64.NO_WRAP).chunked(64).joinToString("\n")
        return "-----BEGIN PUBLIC KEY-----\n$b64\n-----END PUBLIC KEY-----\n"
    }

    /**
     * Eine fuer die Unterschrift vorbereitete [Signature]. Sie wird erst nutzbar,
     * nachdem BiometricPrompt sie als CryptoObject bestaetigt hat; vorher wirft
     * `sign()` eine UserNotAuthenticatedException.
     */
    fun vorbereiten(): Signature =
        Signature.getInstance(SIG_ALG).apply { initSign(store().getKey(ALIAS, null) as PrivateKey) }

    /** Unterschreibt mit der bestaetigten Signature; Ergebnis Base64 (DER). */
    fun unterschreiben(bestaetigt: Signature, daten: ByteArray): String {
        bestaetigt.update(daten)
        return Base64.encodeToString(bestaetigt.sign(), Base64.NO_WRAP)
    }
}
