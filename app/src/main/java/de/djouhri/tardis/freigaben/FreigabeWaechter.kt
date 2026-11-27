package de.djouhri.tardis.freigaben

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import de.djouhri.tardis.MainActivity
import de.djouhri.tardis.R
import de.djouhri.tardis.data.model.tardis.Befehl
import de.djouhri.tardis.data.model.tardis.HausbusFreigabe
import de.djouhri.tardis.data.model.tardis.WartendeFreigabe
import de.djouhri.tardis.data.repository.FreigabenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Sieht alle 15 min nach, ob ein Deploy auf Freigabe wartet, und meldet neue
 * per Android-Benachrichtigung (Entscheid P5: ohne Google, ohne ntfy).
 * Seit 2.0.32 auch: neue Befehle, fertige Laeufe (gelungen oder gescheitert)
 * und eine Erinnerung, wenn ein Deploy laenger als 24 h wartet.
 * Seit 2.0.33 auch Hausbus-Freigaben (brain-bus), die nach 24 h verfallen.
 *
 * Androids eigener JobScheduler statt WorkManager: keine neue Abhaengigkeit.
 * 15 min ist das kleinste Intervall, das Android fuer periodische Jobs zulaesst.
 * Ausserhalb von WLAN oder WireGuard scheitert der Abruf still; der naechste
 * Lauf versucht es wieder.
 */
@AndroidEntryPoint
class FreigabeWaechter : JobService() {

    @Inject lateinit var repository: FreigabenRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lauf: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        lauf = scope.launch {
            val antwort = withTimeoutOrNull(30_000) { repository.liste().getOrNull() }
            if (antwort != null) melden(applicationContext, antwort.wartend)
            val befehle = withTimeoutOrNull(30_000) { repository.befehle().getOrNull() }
            if (befehle != null) meldenBefehle(applicationContext, befehle.befehle)
            val hausbus = withTimeoutOrNull(30_000) { repository.hausbus().getOrNull() }
            if (hausbus != null && hausbus.erreichbar) meldenHausbus(applicationContext, hausbus.freigaben)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        lauf?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 4711
        private const val KANAL = "freigaben"
        private const val NOTIZ_ID = 4712
        private const val PREFS = "freigabe_waechter"
        private const val GEMELDET = "gemeldet"
        private const val ERINNERT = "erinnert"
        private const val BEFEHLE_OFFEN = "befehle_offen"
        private const val BEFEHLE_FERTIG = "befehle_fertig"
        private const val NOTIZ_BEFEHL = 4713
        private const val NOTIZ_ERGEBNIS = 4714
        private const val NOTIZ_HAUSBUS = 4715
        private const val HAUSBUS = "hausbus"
        const val EXTRA_ZIEL = "ziel"
        const val ZIEL_FREIGABEN = "freigaben"

        /** Idempotent: ein schon geplanter Job wird nicht neu gestartet (das setzte die Uhr zurueck). */
        fun planen(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            if (js.getPendingJob(JOB_ID) != null) return
            val info = JobInfo.Builder(JOB_ID, ComponentName(context, FreigabeWaechter::class.java))
                .setPeriodic(15 * 60 * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build()
            js.schedule(info)
        }

        fun absagen(context: Context) {
            context.getSystemService(JobScheduler::class.java)?.cancel(JOB_ID)
        }

        /**
         * Meldet nur, was noch nicht gemeldet wurde; ist nichts mehr offen, verschwindet
         * die Benachrichtigung. Oeffentlich fuer den Aufruf aus der App nach einem Abruf.
         */
        fun melden(context: Context, wartend: List<WartendeFreigabe>) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val jetzt = wartend.map { it.schluessel }.toSet()
            val vorher = prefs.getStringSet(GEMELDET, emptySet()) ?: emptySet()
            val nm = NotificationManagerCompat.from(context)
            if (jetzt.isEmpty()) {
                nm.cancel(NOTIZ_ID)
                prefs.edit().putStringSet(GEMELDET, emptySet()).apply()
                return
            }
            // Einmal je Ziel erinnern, wenn es laenger als 24 h wartet.
            val erinnert = prefs.getStringSet(ERINNERT, emptySet()) ?: emptySet()
            val lange = wartend.filter { FreigabenLogik.wartetLange(it.seit) }.map { it.schluessel }.toSet()
            val neuLange = lange - erinnert
            prefs.edit().putStringSet(ERINNERT, erinnert intersect jetzt union lange).apply()
            val neu = (jetzt - vorher).isNotEmpty()
            if (!neu && neuLange.isEmpty()) return
            if (!darfMelden(context)) return
            kanal(context)
            val rot = wartend.count { it.ampel == "rot" }
            val text = wartend.joinToString(", ") { "${it.projekt} auf ${it.wirt}" }
            val titel = when {
                neu && wartend.size == 1 -> "Ein Deploy wartet auf Freigabe"
                neu -> "${wartend.size} Deploys warten auf Freigabe"
                else -> "Wartet seit über 24 h: ${neuLange.size} Deploy(s)"
            }
            zeigen(context, NOTIZ_ID, titel, if (rot > 0) "$text ($rot rot)" else text)
            prefs.edit().putStringSet(GEMELDET, jetzt).apply()
        }

        /**
         * Neue offene Befehle und fertige Laeufe. Beim ersten Aufruf nur merken, nicht melden
         * (sonst kaemen alle alten Ergebnisse auf einmal).
         */
        fun meldenBefehle(context: Context, befehle: List<Befehl>) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val gemerktOffen = if (prefs.contains(BEFEHLE_OFFEN)) prefs.getStringSet(BEFEHLE_OFFEN, emptySet()) else null
            val gemerktFertig = if (prefs.contains(BEFEHLE_FERTIG)) prefs.getStringSet(BEFEHLE_FERTIG, emptySet()) else null
            val m = FreigabenLogik.neuZuMelden(befehle, gemerktOffen, gemerktFertig)
            prefs.edit()
                .putStringSet(BEFEHLE_OFFEN, befehle.filter { it.offen }.map { FreigabenLogik.offenSchluessel(it) }.toSet())
                .putStringSet(BEFEHLE_FERTIG, befehle.mapNotNull { FreigabenLogik.ergebnisSchluessel(it) }.toSet())
                .apply()
            if (befehle.none { it.offen }) NotificationManagerCompat.from(context).cancel(NOTIZ_BEFEHL)
            if ((m.neuOffen.isEmpty() && m.neuFertig.isEmpty()) || !darfMelden(context)) return
            kanal(context)
            if (m.neuOffen.isNotEmpty()) {
                zeigen(
                    context, NOTIZ_BEFEHL,
                    if (m.neuOffen.size == 1) "Ein Befehl wartet auf dich" else "${m.neuOffen.size} Befehle warten auf dich",
                    m.neuOffen.joinToString(", ") { it.titel },
                )
            }
            if (m.neuFertig.isNotEmpty()) {
                val schlecht = m.neuFertig.filter { it.ergebnis?.zustand != "ok" }
                val titel = when {
                    schlecht.isNotEmpty() -> "Befehl gescheitert: ${schlecht.first().titel}"
                    m.neuFertig.size == 1 -> "Befehl gelaufen: ${m.neuFertig.first().titel}"
                    else -> "${m.neuFertig.size} Befehle gelaufen"
                }
                val text = m.neuFertig.joinToString(", ") {
                    val e = it.ergebnis!!
                    "${it.titel}: ${if (e.modus == "probe") "Probe " else ""}${if (e.zustand == "ok") "ok" else "${e.zustand}, Exit ${e.rc ?: "?"}"}"
                }
                zeigen(context, NOTIZ_ERGEBNIS, titel, text)
            }
        }

        /** Neue Hausbus-Freigaben einmal melden; ist keine mehr offen, verschwindet die Benachrichtigung. */
        fun meldenHausbus(context: Context, offen: List<HausbusFreigabe>) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val jetzt = offen.map { it.id }.toSet()
            val vorher = prefs.getStringSet(HAUSBUS, emptySet()) ?: emptySet()
            prefs.edit().putStringSet(HAUSBUS, jetzt).apply()
            if (jetzt.isEmpty()) {
                NotificationManagerCompat.from(context).cancel(NOTIZ_HAUSBUS)
                return
            }
            val neu = offen.filter { it.id !in vorher }
            if (neu.isEmpty() || !darfMelden(context)) return
            kanal(context)
            zeigen(
                context, NOTIZ_HAUSBUS,
                if (neu.size == 1) "Hausbus fragt: ${neu.first().titel}" else "Hausbus fragt ${neu.size}-mal",
                neu.joinToString(", ") { it.titel } + ". Verfällt nach 24 h.",
            )
        }

        private fun darfMelden(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED

        private fun zeigen(context: Context, id: Int, titel: String, text: String) {
            val oeffnen = PendingIntent.getActivity(
                context, id,
                Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_ZIEL, ZIEL_FREIGABEN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notiz = NotificationCompat.Builder(context, KANAL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(titel)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(oeffnen)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(id, notiz) }
        }

        private fun kanal(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(KANAL) != null) return
            nm.createNotificationChannel(
                NotificationChannel(KANAL, "Freigaben", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Ein Commit oder Befehl wartet auf deine Freigabe, ein Befehl ist gelaufen"
                },
            )
        }
    }
}
