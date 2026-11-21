package de.djouhri.tardis.ui.sprache

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import de.djouhri.tardis.data.repository.OpsRepository
import de.djouhri.tardis.ui.theme.StatusDown
import de.djouhri.tardis.ui.theme.TextLeise
import de.djouhri.tardis.util.userMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class SprachPhase { BEREIT, HOERT, DENKT, SPRICHT }

data class SprachUiState(
    val phase: SprachPhase = SprachPhase.BEREIT,
    val erkannt: String? = null,
    val antwort: String? = null,
    val fehler: String? = null,
    /** Warum kein Ton kam, obwohl Text da ist. */
    val ohneTon: String? = null,
)

/** Kuerzer ist ein versehentlicher Tipp, kein Satz. */
internal const val MIN_AUFNAHME_MS = 400L

/** Laenger ist kein Sprachbefehl mehr; das Gateway nimmt hoechstens 1,5 MB. */
internal const val MAX_AUFNAHME_MS = 60_000

/**
 * Sprach-host in der App (ZIELBILD 5b, seit 2.0.25): halten zum Sprechen,
 * loslassen schickt. Die Aufnahme geht ueber die mobile-api an den
 * host-router, Erkennung und Stimme laufen im Haus. Die Datei liegt nur
 * im Cache der App und wird nach dem Senden geloescht.
 */
@HiltViewModel
class SprachViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val opsRepository: OpsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SprachUiState())
    val state = _state.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null
    private var beginn = 0L
    private val aufnahme get() = File(context.cacheDir, "sprache.m4a")
    private val antwortTon get() = File(context.cacheDir, "antwort.wav")

    fun start() {
        stoppeTon()
        runCatching {
            @Suppress("DEPRECATION")
            val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(16_000)
            r.setAudioEncodingBitRate(64_000)
            r.setMaxDuration(MAX_AUFNAHME_MS)
            r.setOutputFile(aufnahme.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            beginn = System.currentTimeMillis()
            _state.value = SprachUiState(phase = SprachPhase.HOERT)
        }.onFailure { e ->
            recorder?.release()
            recorder = null
            _state.value = SprachUiState(fehler = "Mikrofon nicht verfügbar: ${e.message}")
        }
    }

    fun stopp() {
        val r = recorder ?: return
        recorder = null
        val dauer = System.currentTimeMillis() - beginn
        // stop() wirft, wenn noch keine Daten geschrieben sind (sehr kurzer Druck).
        val gut = runCatching { r.stop() }.isSuccess
        r.release()
        if (!gut || dauer < MIN_AUFNAHME_MS) {
            aufnahme.delete()
            _state.value = SprachUiState(fehler = "Zu kurz. Knopf gedrückt halten, sprechen, dann loslassen.")
            return
        }
        senden()
    }

    private fun senden() {
        _state.update { it.copy(phase = SprachPhase.DENKT, fehler = null) }
        viewModelScope.launch {
            val b64 = withContext(Dispatchers.IO) {
                val bytes = aufnahme.readBytes()
                aufnahme.delete()
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
            opsRepository.sprechen(b64, "audio/mp4").fold(
                onSuccess = { a ->
                    _state.value = SprachUiState(
                        phase = SprachPhase.BEREIT,
                        erkannt = a.erkannt.ifBlank { null },
                        antwort = a.antwort,
                        ohneTon = a.tonFehler,
                    )
                    a.audioBase64?.let { spiele(it) }
                },
                onFailure = { e -> _state.value = SprachUiState(fehler = e.userMessage()) },
            )
        }
    }

    private suspend fun spiele(b64: String) {
        val datei = withContext(Dispatchers.IO) {
            antwortTon.apply { writeBytes(Base64.decode(b64, Base64.DEFAULT)) }
        }
        runCatching {
            stoppeTon()
            val p = MediaPlayer()
            p.setDataSource(datei.absolutePath)
            p.setOnCompletionListener {
                _state.update { s -> s.copy(phase = SprachPhase.BEREIT) }
                stoppeTon()
            }
            p.prepare()
            p.start()
            player = p
            _state.update { it.copy(phase = SprachPhase.SPRICHT) }
        }.onFailure { e -> _state.update { it.copy(ohneTon = "Ton nicht abspielbar: ${e.message}") } }
    }

    fun stoppeTon() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        antwortTon.delete()
        if (_state.value.phase == SprachPhase.SPRICHT) _state.update { it.copy(phase = SprachPhase.BEREIT) }
    }

    override fun onCleared() {
        recorder?.runCatching { stop() }
        recorder?.release()
        recorder = null
        aufnahme.delete()
        stoppeTon()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SprachBlatt(onSchliessen: () -> Unit, viewModel: SprachViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    DisposableEffect(Unit) { onDispose { viewModel.stoppeTon() } }
    ModalBottomSheet(onDismissRequest = onSchliessen) {
        SprachInhalt(state, onStart = viewModel::start, onStopp = viewModel::stopp, onStill = viewModel::stoppeTon)
    }
}

/** Ohne ViewModel, damit der Bildschirmfoto-Test ihn zeichnen kann. */
@Composable
fun SprachInhalt(
    state: SprachUiState,
    onStart: () -> Unit,
    onStopp: () -> Unit,
    onStill: () -> Unit,
) {
    val context = LocalContext.current
    val erlaubnis = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun darf(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("host", style = MaterialTheme.typography.titleLarge)
        state.erkannt?.let {
            Text("„$it“", style = MaterialTheme.typography.bodyMedium, color = TextLeise, textAlign = TextAlign.Center)
        }
        state.antwort?.let {
            Text(it, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        }
        state.ohneTon?.let { Text("Ohne Ton: $it", style = MaterialTheme.typography.bodySmall, color = TextLeise) }
        state.fehler?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = StatusDown, textAlign = TextAlign.Center) }

        val aktiv = state.phase == SprachPhase.HOERT
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(96.dp)
                .clip(CircleShape)
                .background(if (aktiv) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer)
                .pointerInput(state.phase) {
                    detectTapGestures(onPress = {
                        if (state.phase == SprachPhase.DENKT) return@detectTapGestures
                        if (!darf()) {
                            erlaubnis.launch(Manifest.permission.RECORD_AUDIO)
                            return@detectTapGestures
                        }
                        onStart()
                        tryAwaitRelease()
                        onStopp()
                    })
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = "Gedrückt halten und sprechen",
                modifier = Modifier.size(48.dp),
                tint = if (aktiv) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Text(
            when (state.phase) {
                SprachPhase.BEREIT -> "Gedrückt halten, sprechen, loslassen"
                SprachPhase.HOERT -> "Ich höre…"
                SprachPhase.DENKT -> "host denkt nach…"
                SprachPhase.SPRICHT -> "host spricht"
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextLeise,
        )
        if (state.phase == SprachPhase.SPRICHT) {
            TextButton(onClick = onStill) { Text("Still") }
        }
    }
}
