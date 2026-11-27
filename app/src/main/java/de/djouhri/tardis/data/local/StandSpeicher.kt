package de.djouhri.tardis.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Wo der letzte Stand liegt. Eigene Schnittstelle, damit Tests ohne Keystore laufen. */
interface StandAblage {
    fun lesen(schluessel: String): String?
    fun schreiben(schluessel: String, inhalt: String)
}

/** Ablage, die nichts behaelt: Tests und der Fall, dass der Keystore streikt. */
object KeineAblage : StandAblage {
    override fun lesen(schluessel: String): String? = null
    override fun schreiben(schluessel: String, inhalt: String) = Unit
}

/**
 * Je Schluessel eine Datei im `noBackupFilesDir`, AES-GCM mit einem Schluessel
 * aus dem AndroidKeyStore (verlaesst das Geraet nie).
 *
 * Verschluesselt, obwohl es „nur" Status ist: darin stehen Wirtnamen,
 * Alarmtexte und welche Sicherung fehlt, also eine Landkarte des Hauses. Das
 * Token liegt aus demselben Grund verschluesselt; der Stand soll nicht der
 * leichtere Weg an dieselbe Auskunft sein. `noBackupFilesDir`, damit er in
 * keiner Geraetesicherung landet.
 */
class KeystoreAblage(context: Context) : StandAblage {
    private val ordner = File(context.noBackupFilesDir, "stand").apply { mkdirs() }

    private fun schluessel(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    override fun lesen(schluessel: String): String? = runCatching {
        val roh = File(ordner, "$schluessel.bin").takeIf { it.exists() }?.readBytes() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, schluessel(), GCMParameterSpec(128, roh, 0, IV_LAENGE))
        String(cipher.doFinal(roh, IV_LAENGE, roh.size - IV_LAENGE), Charsets.UTF_8)
    }.getOrNull()

    override fun schreiben(schluessel: String, inhalt: String) {
        runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, schluessel())
            val iv = cipher.iv
            check(iv.size == IV_LAENGE)
            val ziel = File(ordner, "$schluessel.bin")
            val tmp = File(ordner, "$schluessel.tmp")
            tmp.writeBytes(iv + cipher.doFinal(inhalt.toByteArray(Charsets.UTF_8)))
            tmp.renameTo(ziel)
        }
    }

    private companion object {
        const val ALIAS = "tardis_stand"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LAENGE = 12
    }
}

@Serializable
private data class Umschlag(val zeit: Long, val daten: JsonElement)

/** Ein gespeicherter Stand mit seinem Zeitpunkt (epoch ms). */
data class Gespeichert<T>(val zeit: Long, val daten: T)

/**
 * Ergebnis eines Abrufs: [alt] ist der Zeitpunkt, wenn die Daten aus dem
 * Speicher kommen, sonst `null`. Wer daraus etwas mit „jetzt" stempelt
 * (Widget, „Stand 14:32"), muss das pruefen.
 */
data class Stand<T>(val daten: T, val alt: Long? = null)

@Singleton
class StandSpeicher(
    private val ablage: StandAblage,
    private val uhr: () -> Long = System::currentTimeMillis,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(KeystoreAblage(context))

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    private val _ausSpeicher = MutableStateFlow<Long?>(null)

    val ausSpeicher: StateFlow<Long?> = _ausSpeicher.asStateFlow()

    fun <T> letzter(schluessel: String, serializer: KSerializer<T>): Gespeichert<T>? {
        val text = ablage.lesen(schluessel) ?: return null
        return runCatching {
            val u = json.decodeFromString(Umschlag.serializer(), text)
            Gespeichert(u.zeit, json.decodeFromJsonElement(serializer, u.daten))
        }.getOrNull()
    }

    suspend fun <T> mitSpeicher(
        schluessel: String,
        serializer: KSerializer<T>,
        abruf: suspend () -> T,
    ): Result<Stand<T>> {
        val frisch = runCatching { abruf() }
        frisch.onSuccess { daten ->
            _ausSpeicher.value = null
            runCatching {
                val u = Umschlag(uhr(), json.encodeToJsonElement(serializer, daten))
                ablage.schreiben(schluessel, json.encodeToString(Umschlag.serializer(), u))
            }
            return Result.success(Stand(daten))
        }
        val alt = letzter(schluessel, serializer) ?: return Result.failure(frisch.exceptionOrNull()!!)
        _ausSpeicher.value = alt.zeit
        return Result.success(Stand(alt.daten, alt.zeit))
    }

    companion object {
        /** Fuer Tests und Vorschauen: behaelt nichts. */
        val OHNE: StandSpeicher get() = StandSpeicher(KeineAblage)

        const val HOSTS = "hosts"
        const val WIRTE = "wirte"
        const val ALARME = "alarme"
        const val BACKUPS = "backups"
    }
}
