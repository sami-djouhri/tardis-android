package de.djouhri.tardis.tresore

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import de.djouhri.tardis.R
import de.djouhri.tardis.data.model.tardis.AllesZuResponse
import de.djouhri.tardis.data.model.tardis.kurzfassung
import de.djouhri.tardis.data.repository.OpsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Notknopf als Schnelleinstellungs-Kachel (Owner B84, ZIELBILD 100 (b)): nur
 * schliessen, ein Druck, ohne die App zu entsperren.
 *
 * Die Kachel selbst schliesst nichts. Android bindet eine TileService nur,
 * solange das Schnelleinstellungsfeld offen ist; wer es nach dem Tippen sofort
 * zuschiebt, haette den Aufruf mitten im Aushaengen abgebrochen. Deshalb plant
 * sie einen JobService, der bis zu zehn Minuten laufen darf und das Ergebnis
 * als Benachrichtigung meldet.
 */
class NotknopfKachel : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            label = "Tresore zu"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = "Notknopf"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        NotknopfJob.starten(applicationContext)
        qsTile?.apply {
            state = Tile.STATE_UNAVAILABLE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) subtitle = "schließt…"
            updateTile()
        }
    }
}

@AndroidEntryPoint
class NotknopfJob : JobService() {

    @Inject lateinit var repository: OpsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lauf: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        melden(applicationContext, null)
        lauf = scope.launch {
            val antwort = withTimeoutOrNull(240_000) { repository.tresoreAllesZu() }
            val ergebnis = antwort?.getOrElse { AllesZuResponse(ok = false, fehler = it.message ?: it.javaClass.simpleName) }
                ?: AllesZuResponse(ok = false, fehler = "keine Antwort in 4 min")
            melden(applicationContext, ergebnis)
            TileService.requestListeningState(
                applicationContext, ComponentName(applicationContext, NotknopfKachel::class.java),
            )
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        lauf?.cancel()
        melden(applicationContext, AllesZuResponse(ok = false, fehler = "von Android abgebrochen"))
        return false
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val JOB_ID = 4721
        private const val KANAL = "notknopf"
        private const val NOTIZ_ID = 4722

        fun starten(context: Context) {
            val js = context.getSystemService(JobScheduler::class.java) ?: return
            val info = JobInfo.Builder(JOB_ID, ComponentName(context, NotknopfJob::class.java))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build()
            js.schedule(info)
        }

        /** `null` = laeuft noch. Sichtbar auch auf dem Sperrbildschirm: es stehen nur Tresorgruppen darin. */
        fun melden(context: Context, ergebnis: AllesZuResponse?) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) return
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(KANAL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(KANAL, "Notknopf", NotificationManager.IMPORTANCE_HIGH).apply {
                        description = "Ergebnis des Notknopfs: welche Tresore zu sind"
                    },
                )
            }
            val notiz = NotificationCompat.Builder(context, KANAL)
                .setSmallIcon(R.drawable.ic_launcher_monochrome)
                .setContentTitle(if (ergebnis == null) "Tresore werden geschlossen…" else if (ergebnis.ok) "Tresore zu" else "Notknopf: nicht alles zu")
                .setContentText(ergebnis?.kurzfassung() ?: "Der Notknopf läuft.")
                .setStyle(NotificationCompat.BigTextStyle().bigText(ergebnis?.kurzfassung() ?: "Der Notknopf läuft."))
                .setOngoing(ergebnis == null)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .build()
            runCatching { NotificationManagerCompat.from(context).notify(NOTIZ_ID, notiz) }
        }
    }
}
