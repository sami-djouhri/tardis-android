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
            if (jetzt == vorher || (jetzt - vorher).isEmpty()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) return
            kanal(context)
            val oeffnen = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_ZIEL, ZIEL_FREIGABEN)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val rot = wartend.count { it.ampel == "rot" }
            val text = wartend.joinToString(", ") { "${it.projekt} auf ${it.wirt}" }
            val notiz = NotificationCompat.Builder(context, KANAL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(
                    if (wartend.size == 1) "Ein Deploy wartet auf Freigabe" else "${wartend.size} Deploys warten auf Freigabe",
                )
                .setContentText(if (rot > 0) "$text ($rot rot)" else text)
                .setContentIntent(oeffnen)
                .setAutoCancel(true)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .build()
            runCatching { nm.notify(NOTIZ_ID, notiz) }
            prefs.edit().putStringSet(GEMELDET, jetzt).apply()
        }

        private fun kanal(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(KANAL) != null) return
            nm.createNotificationChannel(
                NotificationChannel(KANAL, "Deploy-Freigaben", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Ein Commit wartet auf deine Freigabe"
                },
            )
        }
    }
}
