package de.djouhri.tardis.data.repository

import de.djouhri.tardis.data.api.OpsApi
import de.djouhri.tardis.data.demo.DemoData
import de.djouhri.tardis.data.demo.DemoModeManager
import de.djouhri.tardis.data.model.tardis.AbrufResponse
import de.djouhri.tardis.data.model.tardis.AlarmeResponse
import de.djouhri.tardis.data.model.tardis.DienstDetail
import de.djouhri.tardis.data.model.tardis.HostRollup
import de.djouhri.tardis.data.model.tardis.LabErgebnis
import de.djouhri.tardis.data.model.tardis.LogsResponse
import de.djouhri.tardis.data.model.tardis.ReliabilityResponse
import de.djouhri.tardis.data.model.tardis.WirteResponse
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
) {
    /**
     * Steuerbare Hosts mit Zaehlstand.
     *
     * Die Uebersicht braucht Zahlen, nicht Container. Ueber die VPN-Strecke
     * macht das den Unterschied zwischen fuenf Zeilen und knapp zweihundert
     * Eintraegen.
     */
    suspend fun hosts(): Result<List<HostRollup>> {
        if (demo.isActive) return Result.success(DemoData.hosts)
        return runCatching { api.hosts().hosts }
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

    suspend fun alarme(): Result<AlarmeResponse> {
        if (demo.isActive) return Result.success(DemoData.alarme)
        return runCatching { api.alarme() }
    }

    suspend fun reliability(fenster: Int = 60): Result<ReliabilityResponse> {
        if (demo.isActive) return Result.success(DemoData.reliability(fenster))
        return runCatching { api.reliability(fenster) }
    }

    suspend fun wirte(fenster: Int = 60): Result<WirteResponse> {
        if (demo.isActive) return Result.success(DemoData.wirte(fenster))
        return runCatching { api.wirte(fenster) }
    }

    suspend fun topologie(): Result<TopologieResponse> {
        if (demo.isActive) return Result.success(DemoData.topologie)
        return runCatching { api.topologie() }
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
}
