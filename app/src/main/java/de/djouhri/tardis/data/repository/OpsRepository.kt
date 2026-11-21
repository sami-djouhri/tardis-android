package de.djouhri.tardis.data.repository

import de.djouhri.tardis.data.api.OpsApi
import de.djouhri.tardis.data.demo.DemoData
import de.djouhri.tardis.data.demo.DemoModeManager
import de.djouhri.tardis.data.local.Stand
import de.djouhri.tardis.data.local.StandSpeicher
import de.djouhri.tardis.data.model.tardis.BackupsResponse
import de.djouhri.tardis.data.model.tardis.GemachtResponse
import de.djouhri.tardis.data.model.tardis.HostsResponse
import de.djouhri.tardis.data.model.tardis.OwnerSchritteResponse
import de.djouhri.tardis.data.model.tardis.SpracheAnfrage
import de.djouhri.tardis.data.model.tardis.SpracheAntwort
import de.djouhri.tardis.data.model.tardis.AbrufResponse
import de.djouhri.tardis.data.model.tardis.AlarmeResponse
import de.djouhri.tardis.data.model.tardis.AllesZuResponse
import de.djouhri.tardis.data.model.tardis.TresorSchritt
import de.djouhri.tardis.data.model.tardis.DienstDetail
import de.djouhri.tardis.data.model.tardis.HostRollup
import de.djouhri.tardis.data.model.tardis.LabErgebnis
import de.djouhri.tardis.data.model.tardis.LogsResponse
import de.djouhri.tardis.data.model.tardis.ReliabilityResponse
import de.djouhri.tardis.data.model.tardis.WirteResponse
import de.djouhri.tardis.data.model.tardis.TopoZeitpunkt
import de.djouhri.tardis.data.model.tardis.TopologieResponse
import de.djouhri.tardis.data.model.tardis.ServiceActionResult
import de.djouhri.tardis.data.model.tardis.ServiceSummary
import de.djouhri.tardis.data.model.tardis.WartungErgebnis
import de.djouhri.tardis.data.model.tardis.WeckErgebnis
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpsRepository @Inject constructor(
    private val api: OpsApi,
    private val demo: DemoModeManager,
    /**
     * Letzter guter Stand fuer Start, Alarme, Wirte und Sicherung (2.0.25).
     * Die Vorgabe behaelt nichts; Hilt reicht den echten Speicher herein.
     */
    val speicher: StandSpeicher = StandSpeicher.OHNE,
) {
    /**
     * Steuerbare Hosts mit Zaehlstand.
     *
     * Die Uebersicht braucht Zahlen, nicht Container. Ueber die VPN-Strecke
     * macht das den Unterschied zwischen fuenf Zeilen und knapp zweihundert
     * Eintraegen.
     */
    suspend fun hosts(): Result<List<HostRollup>> = hostsStand().map { it.daten }

    /** Wie [hosts], mit der Angabe, ob der Stand aus dem Speicher kommt. */
    suspend fun hostsStand(): Result<Stand<List<HostRollup>>> {
        if (demo.isActive) return Result.success(Stand(DemoData.hosts))
        return speicher.mitSpeicher(StandSpeicher.HOSTS, HostsResponse.serializer()) { api.hosts() }
            .map { Stand(it.daten.hosts, it.alt) }
    }

    suspend fun services(host: String? = null): Result<List<ServiceSummary>> {
        if (demo.isActive) {
            return Result.success(DemoData.services.filter { host == null || it.host == host })
        }
        return runCatching { api.services(host) }
    }

    suspend fun action(host: String, name: String, action: String): Result<ServiceActionResult> {
        if (demo.isActive) {
            val status = if (action == "stop") "exited" else "running"
            return Result.success(ServiceActionResult(name = name, host = host, status = status))
        }
        return runCatching { api.action(host, name, action) }
    }

    suspend fun logs(host: String, name: String, lines: Int = 200): Result<LogsResponse> {
        if (demo.isActive) return Result.success(DemoData.logs(name))
        return runCatching { api.logs(host, name, lines) }
    }

    suspend fun detail(host: String, name: String): Result<DienstDetail> {
        if (demo.isActive) return Result.success(DemoData.detail(host, name))
        return runCatching { api.detail(host, name) }
    }

    /** Notknopf. Im Demo-Modus schliesst er nichts und sagt das auch. */
    suspend fun tresoreAllesZu(): Result<AllesZuResponse> {
        if (demo.isActive) {
            return Result.success(
                AllesZuResponse(ok = true, schritte = listOf(TresorSchritt("demo", "Demo-Tresor", "demo", true, "zu", "Demo, nichts geschlossen"))),
            )
        }
        return runCatching { api.tresoreAllesZu() }
    }

    suspend fun alarme(): Result<AlarmeResponse> = alarmeStand().map { it.daten }

    suspend fun alarmeStand(): Result<Stand<AlarmeResponse>> {
        if (demo.isActive) return Result.success(Stand(DemoData.alarme))
        return speicher.mitSpeicher(StandSpeicher.ALARME, AlarmeResponse.serializer()) { api.alarme() }
    }

    suspend fun reliability(fenster: Int = 60): Result<ReliabilityResponse> {
        if (demo.isActive) return Result.success(DemoData.reliability(fenster))
        return runCatching { api.reliability(fenster) }
    }

    suspend fun wirte(fenster: Int = 60): Result<WirteResponse> {
        if (demo.isActive) return Result.success(DemoData.wirte(fenster))
        // Nur das Startfenster wird behalten: 6 h und 24 h waehlt man bewusst und online.
        if (fenster != 60) return runCatching { api.wirte(fenster) }
        return speicher.mitSpeicher(StandSpeicher.WIRTE, WirteResponse.serializer()) { api.wirte(fenster) }.map { it.daten }
    }

    suspend fun topologie(): Result<TopologieResponse> {
        if (demo.isActive) return Result.success(DemoData.topologie)
        return runCatching { api.topologie() }
    }

    suspend fun topologieZeitpunkt(vor: String): Result<TopoZeitpunkt> {
        if (demo.isActive) return Result.success(DemoData.topologieZeitpunkt(vor))
        return runCatching { api.topologieZeitpunkt(vor) }
    }

    suspend fun abruf(): Result<AbrufResponse> {
        if (demo.isActive) return Result.success(DemoData.abruf)
        return runCatching { api.abruf() }
    }

    suspend fun wecken(name: String): Result<WeckErgebnis> {
        if (demo.isActive) return Result.success(WeckErgebnis(name = name, ok = true, zustand = "starting"))
        return runCatching { api.wecken(name) }
    }

    suspend fun wartung(name: String, an: Boolean): Result<WartungErgebnis> {
        if (demo.isActive) return Result.success(WartungErgebnis(dienst = name, wartung = an))
        return runCatching { api.wartung(name, an) }
    }

    suspend fun lab(aktion: String): Result<LabErgebnis> {
        if (demo.isActive) return Result.success(LabErgebnis(ok = true, rc = 0))
        return runCatching { api.lab(aktion) }
    }

    /**
     * Der letzte Stand fuer den ersten Bildschirm, ohne Netz (2.0.25). So steht
     * beim Start sofort etwas da statt „Lade Status…", gekennzeichnet als alt.
     * Im Demo-Modus nie: dort duerfen keine echten Daten erscheinen.
     */
    fun letzteHosts(): Stand<List<HostRollup>>? {
        if (demo.isActive) return null
        val g = speicher.letzter(StandSpeicher.HOSTS, HostsResponse.serializer()) ?: return null
        return Stand(g.daten.hosts, g.zeit)
    }

    fun letzteWirte(): WirteResponse? =
        if (demo.isActive) null else speicher.letzter(StandSpeicher.WIRTE, WirteResponse.serializer())?.daten

    fun letzteBackups(): BackupsResponse? =
        if (demo.isActive) null else speicher.letzter(StandSpeicher.BACKUPS, BackupsResponse.serializer())?.daten

    suspend fun backups(): Result<Stand<BackupsResponse>> {
        if (demo.isActive) return Result.success(Stand(DemoData.backups))
        return speicher.mitSpeicher(StandSpeicher.BACKUPS, BackupsResponse.serializer()) { api.backups() }
    }

    /** Handgriffe bewusst ohne Speicher: abhaken soll man nur, was gerade gilt. */
    suspend fun ownerSchritte(): Result<OwnerSchritteResponse> {
        if (demo.isActive) return Result.success(DemoData.ownerSchritte)
        return runCatching { api.ownerSchritte() }
    }

    suspend fun gemacht(id: String): Result<GemachtResponse> {
        if (demo.isActive) return Result.success(GemachtResponse(id = id))
        return runCatching { api.gemacht(id) }
    }

    /** Im Demo-Modus geht keine Aufnahme aus dem Telefon. */
    suspend fun sprechen(audioBase64: String, mimeType: String): Result<SpracheAntwort> {
        if (demo.isActive) return Result.success(DemoData.sprache)
        return runCatching { api.sprechen(SpracheAnfrage(audioBase64, mimeType)) }
    }
}
