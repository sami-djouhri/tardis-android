package de.djouhri.tardis.data.repository

import de.djouhri.tardis.data.api.OpsApi
import de.djouhri.tardis.data.demo.DemoData
import de.djouhri.tardis.data.demo.DemoModeManager
import de.djouhri.tardis.data.model.tardis.AbrufResponse
import de.djouhri.tardis.data.model.tardis.AlarmeResponse
import de.djouhri.tardis.data.model.tardis.AllesZuResponse
import de.djouhri.tardis.data.model.tardis.BackupsResponse
import de.djouhri.tardis.data.model.tardis.GemachtResponse
import de.djouhri.tardis.data.model.tardis.OwnerSchritteResponse
import de.djouhri.tardis.data.model.tardis.SpracheAnfrage
import de.djouhri.tardis.data.model.tardis.SpracheAntwort
import de.djouhri.tardis.data.local.StandAblage
import de.djouhri.tardis.data.local.StandSpeicher
import org.junit.Assert.assertNull
import de.djouhri.tardis.data.model.tardis.DienstDetail
import de.djouhri.tardis.data.model.tardis.HostsResponse
import de.djouhri.tardis.data.model.tardis.LabErgebnis
import de.djouhri.tardis.data.model.tardis.LogsResponse
import de.djouhri.tardis.data.model.tardis.ReliabilityResponse
import de.djouhri.tardis.data.model.tardis.TopologieResponse
import de.djouhri.tardis.data.model.tardis.ServiceActionResult
import de.djouhri.tardis.data.model.tardis.ServiceSummary
import de.djouhri.tardis.data.model.tardis.WartungErgebnis
import de.djouhri.tardis.data.model.tardis.WeckErgebnis
import de.djouhri.tardis.data.model.tardis.WirteResponse
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpsRepositoryTest {

    private class FakeOpsApi : OpsApi {
        var servicesCalls = 0
        var hostsCalls = 0
        var lastServicesHost: String? = null
        var lastAction: String? = null
        val canned = listOf(ServiceSummary(host = "h1", name = "svc", status = "running"))

        override suspend fun hosts(): HostsResponse {
            hostsCalls++
            return HostsResponse()
        }

        override suspend fun services(host: String?): List<ServiceSummary> {
            servicesCalls++
            lastServicesHost = host
            return canned
        }

        override suspend fun action(host: String, name: String, action: String): ServiceActionResult {
            lastAction = action
            return ServiceActionResult(name = name, host = host, status = "running")
        }

        override suspend fun logs(host: String, name: String, lines: Int): LogsResponse =
            LogsResponse(name = name, host = host, logs = "real-logs")

        var schreibAufrufe = 0

        override suspend fun alarme(): AlarmeResponse = AlarmeResponse(erreichbar = true)
        override suspend fun tresoreAllesZu(): AllesZuResponse = AllesZuResponse(ok = true)

        override suspend fun detail(host: String, name: String): DienstDetail =
            DienstDetail(host = host, name = name)

        override suspend fun abruf(): AbrufResponse = AbrufResponse()

        override suspend fun reliability(fenster: Int): ReliabilityResponse = ReliabilityResponse(fensterMinuten = fenster)

        override suspend fun topologie(): TopologieResponse = TopologieResponse()

        override suspend fun topologieZeitpunkt(vor: String) = de.djouhri.tardis.data.model.tardis.TopoZeitpunkt(vor = vor)

        override suspend fun wirte(fenster: Int): WirteResponse = WirteResponse(fensterMinuten = fenster)

        override suspend fun wecken(name: String): WeckErgebnis {
            schreibAufrufe++
            return WeckErgebnis(name = name, ok = true, zustand = "starting")
        }

        override suspend fun wartung(name: String, an: Boolean): WartungErgebnis {
            schreibAufrufe++
            return WartungErgebnis(dienst = name, wartung = an)
        }

        override suspend fun lab(aktion: String): LabErgebnis {
            schreibAufrufe++
            return LabErgebnis(ok = true, rc = 0)
        }

        var netzWeg = false
        override suspend fun backups(): BackupsResponse {
            if (netzWeg) throw java.io.IOException("weg")
            return BackupsResponse(gemessen = true)
        }

        override suspend fun ownerSchritte(): OwnerSchritteResponse {
            if (netzWeg) throw java.io.IOException("weg")
            return OwnerSchritteResponse(gemessen = true)
        }

        override suspend fun gemacht(id: String): GemachtResponse {
            schreibAufrufe++
            return GemachtResponse(id)
        }

        var gesendet = 0
        override suspend fun sprechen(anfrage: SpracheAnfrage): SpracheAntwort {
            gesendet++
            return SpracheAntwort(antwort = "echt")
        }
    }

    private class MerkAblage : StandAblage {
        val inhalt = mutableMapOf<String, String>()
        override fun lesen(schluessel: String) = inhalt[schluessel]
        override fun schreiben(schluessel: String, inhalt: String) { this.inhalt[schluessel] = inhalt }
    }

    @Test
    fun `demo schickt keine aufnahme und meldet nichts als gemacht`() = runTest {
        val api = FakeOpsApi()
        val repo = OpsRepository(api, DemoModeManager().apply { enable() })
        repo.sprechen("AAAA", "audio/mp4").getOrThrow()
        repo.gemacht("x").getOrThrow()
        assertEquals(0, api.gesendet)
        assertEquals(0, api.schreibAufrufe)
    }

    @Test
    fun `sicherung kommt ohne netz aus dem speicher, owner-schritte nicht`() = runTest {
        val api = FakeOpsApi()
        val repo = OpsRepository(api, DemoModeManager(), StandSpeicher(MerkAblage()))
        assertNull(repo.backups().getOrThrow().alt)
        repo.ownerSchritte().getOrThrow()
        api.netzWeg = true
        assertTrue(repo.backups().getOrThrow().alt != null)
        // Abhaken nur, was gerade gilt: kein alter Stand fuer Handgriffe.
        assertTrue(repo.ownerSchritte().isFailure)
    }

    @Test
    fun `demo liest keinen echten gespeicherten stand`() = runTest {
        val ablage = MerkAblage()
        OpsRepository(FakeOpsApi(), DemoModeManager(), StandSpeicher(ablage)).hosts()
        assertTrue(ablage.inhalt.isNotEmpty())
        val demoRepo = OpsRepository(FakeOpsApi(), DemoModeManager().apply { enable() }, StandSpeicher(ablage))
        assertNull(demoRepo.letzteHosts())
    }

    @Test
    fun `demo mode never wakes or starts anything real`() = runTest {
        // Die Demo ist oeffentlich klickbar. Ein Knopf darin, der echt weckt
        // oder ein Lab startet, waere ein Weg ins Haus fuer jeden, der sie oeffnet.
        val api = FakeOpsApi()
        val repo = OpsRepository(api, DemoModeManager().apply { enable() })

        repo.wecken("chess").getOrThrow()
        repo.wartung("chess", true).getOrThrow()
        repo.lab("start").getOrThrow()

        assertEquals(0, api.schreibAufrufe)
        assertEquals(DemoData.alarme, repo.alarme().getOrThrow())
    }

    @Test
    fun `demo mode returns demo data without touching the api`() = runTest {
        val api = FakeOpsApi()
        val demo = DemoModeManager().apply { enable() }
        val repo = OpsRepository(api, demo)

        val services = repo.services().getOrThrow()

        assertEquals(DemoData.services, services)
        assertEquals(0, api.servicesCalls)
    }

    @Test
    fun `demo action is simulated locally`() = runTest {
        val demo = DemoModeManager().apply { enable() }
        val repo = OpsRepository(FakeOpsApi(), demo)

        assertEquals("exited", repo.action("h", "svc", "stop").getOrThrow().status)
        assertEquals("running", repo.action("h", "svc", "start").getOrThrow().status)
    }

    @Test
    fun `non-demo delegates to the api`() = runTest {
        val api = FakeOpsApi()
        val repo = OpsRepository(api, DemoModeManager())

        val services = repo.services().getOrThrow()

        assertEquals(1, api.servicesCalls)
        assertEquals("svc", services.single().name)
        assertFalse(services === DemoData.services)

        repo.action("h", "svc", "restart")
        assertEquals("restart", api.lastAction)
    }

    @Test
    fun `der gewaehlte host geht an die api, nicht verloren`() = runTest {
        // Das Gegenstueck zum Gateway-Test: die App darf den Host nicht schon
        // auf ihrer Seite wegwerfen.
        val api = FakeOpsApi()
        OpsRepository(api, DemoModeManager()).services("node1")
        assertEquals("node1", api.lastServicesHost)
    }

    @Test
    fun `demo hosts kommen ohne api-aufruf`() = runTest {
        val api = FakeOpsApi()
        val repo = OpsRepository(api, DemoModeManager().apply { enable() })

        assertEquals(DemoData.hosts, repo.hosts().getOrThrow())
        assertEquals(0, api.hostsCalls)
    }

    @Test
    fun `demo logs are canned`() = runTest {
        val repo = OpsRepository(FakeOpsApi(), DemoModeManager().apply { enable() })
        assertTrue(repo.logs("h", "svc").getOrThrow().logs.contains("demo"))
    }
}
