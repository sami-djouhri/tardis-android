package de.djouhri.tardis.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.djouhri.tardis.BuildConfig
import de.djouhri.tardis.data.demo.DemoModeManager
import de.djouhri.tardis.data.model.tardis.AppVersion
import de.djouhri.tardis.data.update.HashUrteil
import de.djouhri.tardis.data.update.UpdateRegeln
import de.djouhri.tardis.data.update.pruefeApk
import de.djouhri.tardis.security.SessionState
import de.djouhri.tardis.util.UpdateUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Ergebnis eines Downloads: die gepruefte Datei plus das Urteil ueber den Hash. */
data class GeladeneApk(
    val datei: File,
    val urteil: HashUrteil,
    val gemessenerHash: String,
)

@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dashboardRepository: DashboardRepository,
    private val sessionState: SessionState,
    private val okHttpClient: OkHttpClient,
    private val demo: DemoModeManager,
) {

    /** Neueste angebotene Version, oder Fehler. */
    suspend fun neuesteVersion(): Result<AppVersion> = dashboardRepository.latestVersion()

    /** true, wenn das Angebot hoeher ist als der eigene Stand. */
    fun istNeuer(version: AppVersion): Boolean =
        UpdateRegeln.istNeuer(version.versionCode, BuildConfig.VERSION_CODE)

    /**
     * Laedt die APK in den eigenen Zwischenspeicher und misst ihren SHA-256.
     *
     * Bei Abweichung wird die Datei geloescht, bevor der Fehler zurueckgeht:
     * eine liegengebliebene, nicht passende APK ist genau das, was beim
     * naechsten Mal jemand von Hand anklickt.
     */
    suspend fun herunterladen(version: AppVersion): Result<GeladeneApk> = withContext(Dispatchers.IO) {
        if (demo.isActive) {
            return@withContext Result.failure(IllegalStateException(
                "Im Demo-Modus gibt es kein Gateway, von dem geladen werden könnte.",
            ))
        }
        val adresse = UpdateUrl.resolve(sessionState.gatewayBaseUrl(), version.apkUrl)
            .getOrElse { return@withContext Result.failure(it) }

        val ordner = File(context.cacheDir, "updates").apply { mkdirs() }
        // Alte Versuche wegraeumen: der Zwischenspeicher soll nicht mit APKs
        // volllaufen, und es soll immer nur eine Datei zur Auswahl stehen.
        ordner.listFiles()?.forEach { it.delete() }
        val ziel = File(ordner, "tardis-${version.versionCode}.apk")
        // Erst unter `.teil`, umbenannt wird nach bestandener Pruefung: unter dem
        // echten Namen liegt so nie eine halbe oder falsche Datei.
        val teil = File(ordner, ziel.name + ".teil")

        runCatching {
            val antwort = okHttpClient.newCall(Request.Builder().url(adresse).build()).execute()
            antwort.use { a ->
                if (!a.isSuccessful) error("Gateway antwortete mit HTTP ${a.code}")
                val koerper = a.body ?: error("Leere Antwort vom Gateway")
                val digest = MessageDigest.getInstance("SHA-256")
                koerper.byteStream().use { ein ->
                    teil.outputStream().use { aus ->
                        val puffer = ByteArray(64 * 1024)
                        while (true) {
                            val gelesen = ein.read(puffer)
                            if (gelesen <= 0) break
                            digest.update(puffer, 0, gelesen)
                            aus.write(puffer, 0, gelesen)
                        }
                    }
                }
                val gemessen = digest.digest().joinToString("") { "%02x".format(it) }
                val urteil = UpdateRegeln.hashUrteil(version.sha256, gemessen)
                when (urteil) {
                    HashUrteil.ABWEICHUNG -> {
                        teil.delete()
                        error(
                            "Prüfsumme stimmt nicht. Angekündigt war " +
                                "${version.sha256.take(12)}…, geladen wurde ${gemessen.take(12)}…",
                        )
                    }
                    HashUrteil.OHNE_VERGLEICH -> {
                        teil.delete()
                        error("Das Gateway nennt keine Prüfsumme, die Datei wird nicht installiert.")
                    }
                    HashUrteil.PASST -> Unit
                }
                // Vorpruefung, bevor der Systemdialog kommt: dessen Ablehnung
                // nennt keinen Grund (siehe ApkPruefung).
                pruefeApk(context, teil, version.versionCode.toLong())?.let { grund ->
                    teil.delete()
                    error(grund)
                }
                check(teil.renameTo(ziel)) { "Geladene Datei ließ sich nicht ablegen." }
                GeladeneApk(ziel, urteil, gemessen)
            }
        }
    }
}
