package de.djouhri.tardis.data.demo

import de.djouhri.tardis.data.model.tardis.AbrufDienst
import de.djouhri.tardis.data.model.tardis.AbrufResponse
import de.djouhri.tardis.data.model.tardis.Alarm
import de.djouhri.tardis.data.model.tardis.AlarmeResponse
import de.djouhri.tardis.data.model.tardis.ContainerLauf
import de.djouhri.tardis.data.model.tardis.DashboardSummary
import de.djouhri.tardis.data.model.tardis.DienstDetail
import de.djouhri.tardis.data.model.tardis.Verbrauch
import de.djouhri.tardis.data.model.tardis.Verfuegbarkeit
import de.djouhri.tardis.data.model.tardis.Wechsel
import de.djouhri.tardis.data.model.tardis.WirtLast
import de.djouhri.tardis.data.model.tardis.HostHealth
import de.djouhri.tardis.data.model.tardis.HostRollup
import de.djouhri.tardis.data.model.tardis.InboxCounts
import de.djouhri.tardis.data.model.tardis.InboxItem
import de.djouhri.tardis.data.model.tardis.InboxList
import de.djouhri.tardis.data.model.tardis.StreamItem
import de.djouhri.tardis.data.model.tardis.Lab
import de.djouhri.tardis.data.model.tardis.LogsResponse
import de.djouhri.tardis.data.model.tardis.ServiceSummary
import de.djouhri.tardis.data.model.tardis.ServiceZustand
import de.djouhri.tardis.data.model.tardis.Wecker
import de.djouhri.tardis.data.model.tardis.DienstVerfuegbarkeit
import de.djouhri.tardis.data.model.tardis.ReliabilityKennzahlen
import de.djouhri.tardis.data.model.tardis.ReliabilityResponse
import de.djouhri.tardis.data.model.tardis.SystemMetrik
import de.djouhri.tardis.data.model.tardis.VerlaufPunkt
import de.djouhri.tardis.data.model.tardis.VerlaufReihe
import de.djouhri.tardis.data.model.tardis.Vorfall
import de.djouhri.tardis.data.model.tardis.TopoDienst
import de.djouhri.tardis.data.model.tardis.TopoVerbindung
import de.djouhri.tardis.data.model.tardis.TopoWirt
import de.djouhri.tardis.data.model.tardis.TopologieResponse
import de.djouhri.tardis.data.model.tardis.WirtMessung
import de.djouhri.tardis.data.model.tardis.WirtVerlauf
import de.djouhri.tardis.data.model.tardis.WirteResponse

/**
 * Statische, bewusst neutrale Beispieldaten fuer den Demo-Modus. Host-Namen sind
 * generisch (`edge`/`app-node`/`compute`) - keine realen Homelab-Details.
 */
object DemoData {

    // Die Beispiele decken bewusst alle fuenf Zustaende ab: eine Demo, in der
    // nur laufende Container vorkommen, zeigt genau das nicht, worum es der App
    // geht (den Unterschied zwischen „aus" und „ausgefallen").
    val services: List<ServiceSummary> = listOf(
        ServiceSummary("edge", "reverse-proxy", "running", "healthy", "caddy:2", expectedState = "", inControlMap = true),
        ServiceSummary("edge", "vpn-gateway", "running", null, "wireguard:latest", expectedState = "", inControlMap = true),
        ServiceSummary("edge", "status-page", "running", "healthy", "gatus:v5", expectedState = "", inControlMap = true),
        ServiceSummary("app-node", "web-portal", "running", "healthy", "ghcr.io/demo/web:1.4.2", expectedState = "", inControlMap = true),
        ServiceSummary("app-node", "notes-api", "running", "healthy", "ghcr.io/demo/notes:0.9.1", expectedState = "", inControlMap = true),
        // Neustartschleife: bleibt ein Befund, egal was die control-map sagt.
        ServiceSummary("app-node", "photo-vault", "restarting", null, "ghcr.io/demo/photos:2.1", expectedState = "", inControlMap = true),
        ServiceSummary("app-node", "search-index", "running", "unhealthy", "opensearch:2.13", expectedState = "", inControlMap = true),
        // Soll laufen, tut es nicht. Der einzige echte Ausfall in der Demo.
        ServiceSummary("app-node", "mail-relay", "exited", null, "ghcr.io/demo/relay:3.2", expectedState = "", inControlMap = true),
        ServiceSummary("compute", "metrics", "running", "healthy", "prom/prometheus:v2.53", expectedState = "", inControlMap = true),
        ServiceSummary("compute", "dashboards", "running", "healthy", "grafana/grafana:11.1", expectedState = "", inControlMap = true),
        ServiceSummary("compute", "llm-gateway", "running", "healthy", "ghcr.io/demo/llm-gw:0.6", expectedState = "", inControlMap = true),
        // Einmal-Job und bewusster Rueckfall: sehen aus wie Ausfaelle, sind keine.
        ServiceSummary("compute", "batch-worker", "exited", null, "ghcr.io/demo/worker:1.0", expectedState = "job", inControlMap = true),
        ServiceSummary("compute", "model-fallback", "exited", null, "ghcr.io/demo/llm:0.4", expectedState = "stopped", inControlMap = true),
        // Remote-Host ohne Erwartung in der control-map: nicht beurteilbar.
        ServiceSummary("compute", "altes-archiv", "exited", null, "ghcr.io/demo/archiv:1.0", expectedState = null, inControlMap = null),
    )

    /**
     * Der Zaehlstand wird aus [services] gerechnet, nicht danebengeschrieben.
     * Eine Demo mit zwei Wahrheiten wuerde genau den Fehler vorfuehren, den
     * diese App loswerden soll.
     */
    val hosts: List<HostRollup> = services.groupBy { it.host }.map { (host, liste) ->
        HostRollup(
            name = host,
            erreichbar = true,
            gesamt = liste.size,
            laufend = liste.count { it.zustand == ServiceZustand.LAEUFT },
            auffaellig = liste.count { it.zustand == ServiceZustand.AUFFAELLIG },
            bewusstAus = liste.count { it.zustand == ServiceZustand.BEWUSST_AUS },
            ausgefallen = liste.count { it.zustand == ServiceZustand.AUSGEFALLEN },
            unbewertet = liste.count { it.zustand == ServiceZustand.UNBEWERTET },
        )
    } + listOf(
        // Ein nicht erreichbarer Host liefert keine Zahlen. Eine 0 waere die
        // Aussage „dort laeuft nichts" und damit falsch.
        HostRollup("lab-node", erreichbar = false),
    )

    val hostHealth = HostHealth(
        cpuPercent = 18.0,
        memoryTotal = 16L * 1024 * 1024 * 1024,
        memoryUsed = 6L * 1024 * 1024 * 1024,
        memoryPercent = 37.0,
        diskTotal = 512L * 1024 * 1024 * 1024,
        diskUsed = 233L * 1024 * 1024 * 1024,
        diskPercent = 45.0,
        cpuTemp = 47.0,
        load1m = 0.72,
        load5m = 0.65,
        load15m = 0.58,
    )

    val inboxCounts = InboxCounts(open = 3, snoozed = 1, done = 12, archived = 40, totalVisible = 4)

    val summary = DashboardSummary(health = hostHealth, inboxCounts = inboxCounts)

    val inboxItems: List<InboxItem> = listOf(
        InboxItem(
            externalId = "demo-1",
            source = "monitoring",
            title = "search-index meldet unhealthy",
            detail = "Cluster-Status yellow seit 12 min - 1 Shard nicht zugewiesen.",
            severity = "warning",
            ageHours = 0.4,
        ),
        InboxItem(
            externalId = "demo-2",
            source = "backup",
            title = "Off-Site-Snapshot abgeschlossen",
            detail = "restic: 4,1 GiB neu, 0 Fehler.",
            severity = "info",
            ageHours = 6.0,
        ),
        InboxItem(
            externalId = "demo-3",
            source = "cert",
            title = "TLS-Zertifikat läuft in 12 Tagen ab",
            detail = "status.example.org - Auto-Renewal aktiv, nur zur Info.",
            severity = "info",
            ageHours = 20.0,
        ),
    )

    val inbox = InboxList(items = inboxItems, counts = inboxCounts)

    val stream: List<StreamItem> = listOf(
        StreamItem(
            externalId = "demo-s1", source = "monitoring", kind = "service",
            eventType = "service.unhealthy", title = "search-index meldet unhealthy",
            severity = "warning", occurredAt = "2026-01-01T09:12:00Z",
        ),
        // Dieselbe Meldung frueher noch einmal: zeigt das Buendeln im Verlauf.
        StreamItem(
            externalId = "demo-s1b", source = "monitoring", kind = "service",
            eventType = "service.unhealthy", title = "search-index meldet unhealthy",
            severity = "warning", occurredAt = "2026-01-01T07:30:00Z",
        ),
        StreamItem(
            externalId = "demo-s2", source = "nachrichten", kind = "news",
            eventType = "news.published", title = "Neuer Artikel im Feed",
            body = "Kurzfassung des Artikels.", occurredAt = "2026-01-01T08:40:00Z",
        ),
        StreamItem(
            externalId = "demo-s3", source = "briefkasten", kind = "mail",
            eventType = "mail.received", title = "Brief eingegangen", occurredAt = "2026-01-01T07:05:00Z",
        ),
    )

    fun logs(name: String): LogsResponse = LogsResponse(
        name = name,
        host = "demo",
        logs = buildString {
            appendLine("2026-01-01T09:00:01Z INFO  $name gestartet (demo)")
            appendLine("2026-01-01T09:00:02Z INFO  Konfiguration geladen")
            appendLine("2026-01-01T09:00:03Z INFO  Lausche auf :8080")
            appendLine("2026-01-01T09:14:55Z WARN  Upstream langsam (312 ms)")
            appendLine("2026-01-01T09:15:00Z INFO  Healthcheck ok")
        },
    )

    // Ein kritischer und zwei gleichnamige Alarme an verschiedenen Wirten:
    // so sieht man in der Demo, dass gleiche Befunde zusammengefasst werden.
    val alarme = AlarmeResponse(
        erreichbar = true,
        stand = "2026-10-31T12:00:00Z",
        alarme = listOf(
            Alarm("BackupVeraltet", "critical", "Kein erfolgreiches Backup seit 2 Tagen", instanz = "app-node", seit = "2026-10-29T03:30:00Z"),
            Alarm("NeustartOffen", "warning", "edge wartet seit über einer Woche auf einen Neustart", instanz = "edge", seit = "2026-10-21T17:46:00Z"),
            Alarm("NeustartOffen", "warning", "compute wartet seit über einer Woche auf einen Neustart", instanz = "compute", seit = "2026-10-21T17:51:00Z"),
            Alarm("ZertifikatLaeuftAb", "warning", "Zertifikat von status-page läuft in 12 Tagen ab", instanz = "edge", seit = "2026-10-30T08:00:00Z"),
        ),
    )

    // Ein wackelnder Dienst oben, einer auf Abruf und einer ohne Eintrag unten:
    // so sieht man in der Demo die drei Arten von „nicht 100 %".
    fun reliability(fenster: Int): ReliabilityResponse {
        val punkte = { basis: Double -> (0..30).map { VerlaufPunkt(1_761_900_000L + it * 60L * fenster / 30, basis + (it % 7) * 2.5) } }
        return ReliabilityResponse(
            dienste = listOf(
                DienstVerfuegbarkeit("status-page", 97.4, 99.6, 99.9),
                DienstVerfuegbarkeit("recipes", 100.0, 100.0, 100.0),
                DienstVerfuegbarkeit("journal", 100.0, 100.0, 99.98),
                DienstVerfuegbarkeit("chess", 0.0, 3.1, 4.0, erwartung = "on demand", ohneWertung = true),
                DienstVerfuegbarkeit("preview-db", 64.3, 64.3, 64.3, nichtGefuehrt = true, ohneWertung = true),
            ),
            kennzahlen = ReliabilityKennzahlen(unter99 = 1, bewusstAus = 1, nichtGefuehrt = 1, unauffaellig = 1, incidents24h = 2),
            incidents = listOf(
                Vorfall("status-page", "running", "exited", "2026-10-31T11:20:00+00:00", "exit 137"),
                Vorfall("status-page", "exited", "running", "2026-10-31T11:22:00+00:00"),
            ),
            system = listOf(
                SystemMetrik("cpu", listOf(12.0, 18.0, 35.0, 22.0, 15.0), 15.0, 90.0),
                SystemMetrik("disk", listOf(51.0, 51.2, 51.5, 51.6), 51.6, 85.0),
            ),
            fensterMinuten = fenster,
            verlauf = listOf(
                VerlaufReihe("cpu", "CPU", "%", true, punkte(14.0)),
                VerlaufReihe("mem", "Speicher", "%", true, punkte(48.0)),
                VerlaufReihe("disk", "Disk /", "%", true, punkte(51.0)),
                VerlaufReihe("load", "Load (1m)", "", true, punkte(0.8)),
            ),
        )
    }

    // Ein ruhiger Wirt, einer unter Last, einer mit voller Platte, ein Gast, der
    // die Last seines Nodes zeigt, und einer, der gerade nicht antwortet (Reihe
    // mit Luecke, kein Momentwert). So sieht man in der Demo jede Faerbung.
    fun wirte(fenster: Int): WirteResponse {
        val n = 90
        fun welle(basis: Double, hub: Double, phase: Int = 0): List<Double?> =
            (0 until n).map { basis + hub * kotlin.math.sin((it + phase) / 9.0) }
        fun wirt(name: String, cpu: List<Double?>, ram: Double, platte: Double, load: Double, node: String? = null, kerne: Int = 4) =
            WirtMessung(
                name = name, node = node, erreichbar = true,
                cpu = cpu.last(), ram = ram, platte = platte, load = load, kerne = kerne, temp = 48.0,
                verlauf = WirtVerlauf(cpu = cpu, ram = List(n) { ram }, platte = List(n) { platte }, load = cpu.map { it?.div(25) }),
            )
        val vmHost = welle(55.0, 30.0, 3)
        return WirteResponse(
            gemessen = true,
            fensterMinuten = fenster,
            start = 1_761_900_000L,
            schritt = fenster * 60 / n,
            wirte = listOf(
                wirt("edge", welle(8.0, 4.0), 31.0, 22.0, 0.3),
                wirt("app-node", welle(41.0, 20.0, 20), 78.0, 63.0, 2.1),
                wirt("compute", welle(18.0, 6.0, 40), 52.0, 91.0, 0.9, kerne = 8),
                wirt("vm-host", vmHost, 44.0, 38.0, 3.4),
                wirt("ai-guest", vmHost, 61.0, 47.0, 3.4, node = "vm-host"),
                WirtMessung(
                    name = "lab-node",
                    erreichbar = false,
                    verlauf = WirtVerlauf(cpu = welle(12.0, 3.0).mapIndexed { i, v -> if (i < 50) v else null }),
                ),
            ),
        )
    }

    // Ein grosser Wirt mit Gruppen, zwei kleine, zwei ohne Dienste; je ein
    // Dienst in jedem der vier Zustaende und ein paar Abhaengigkeiten.
    val topologie: TopologieResponse = run {
        val gross = (1..24).map { i ->
            TopoDienst(
                "app-$i", "app-$i", "app-node",
                gruppe = if (i <= 12) "Apps" else "Wissen",
                zustand = when (i) { 5 -> "aus"; 9 -> "unbekannt"; 15, 16 -> "bewusst_aus"; else -> "laeuft" },
                container = "app-$i",
            )
        }
        val klein = listOf(
            TopoDienst("edge-proxy", "edge-proxy", "edge", zustand = "laeuft"),
            TopoDienst("status-page", "status-page", "edge", zustand = "laeuft"),
            TopoDienst("llm", "llm", "compute", zustand = "laeuft"),
        )
        TopologieResponse(
            stand = "2026-10-31T12:00:00Z",
            wirte = listOf(
                TopoWirt("app-node", dienste = 24, online = true, cpu = 23.0, ram = 61.0),
                TopoWirt("edge", dienste = 2, online = true, cpu = 4.0, ram = 38.0),
                TopoWirt("compute", dienste = 1, online = true),
                TopoWirt("internet"),
                TopoWirt("storage-box", online = false),
            ),
            dienste = gross + klein,
            verbindungen = listOf(
                TopoVerbindung("app-1", "app-2", "depends"),
                TopoVerbindung("app-1", "llm", "depends"),
                TopoVerbindung("app-5", "app-13", "depends"),
                TopoVerbindung("edge", "internet", "public"),
                TopoVerbindung("app-node", "storage-box", "backup"),
            ),
            gesundheit = 82,
        )
    }

    val abruf = AbrufResponse(
        wecker = Wecker(
            erreichbar = true,
            freiMb = 5200,
            reserveMb = 900,
            dienste = listOf(
                AbrufDienst("chess", "Schach", idleTimeoutS = 1800),
                AbrufDienst("journal", "Tagebuch", laeuft = true, bereit = true, leerlaufS = 600, idleTimeoutS = 1800),
                AbrufDienst("recipes", "Rezepte", laeuft = true, bereit = true, leerlaufS = 2400, idleTimeoutS = 1800, wartung = true),
                AbrufDienst("tasks", "Aufgaben", startbar = false, idleTimeoutS = 1800),
            ),
        ),
        lab = Lab(verfuegbar = true, alterS = 40, freiMb = 10200),
    )

    /**
     * Ein Detail, das beide Lagen zeigt: `edge` wird beobachtet (Verlauf mit
     * einem Neustart in der Nacht), jeder andere Wirt nicht. So sieht man in
     * der Demo, dass „nicht aufgezeichnet" etwas anderes ist als „nie aus".
     */
    fun detail(host: String, name: String): DienstDetail {
        val svc = services.firstOrNull { it.host == host && it.name == name }
        val laeuft = svc?.isRunning ?: false
        val beobachtet = host == "edge"
        val start = 1767254400.0 // 2026-01-01T08:00:00Z
        return DienstDetail(
            host = host,
            name = name,
            container = ContainerLauf(
                gefunden = svc != null,
                status = svc?.status,
                gestartet = "2026-01-01T03:12:00Z",
                beendet = if (laeuft) null else "2026-01-01T07:40:00Z",
                exitCode = if (laeuft) null else 1,
                neustarts = if (svc?.isRestarting == true) 14 else 1,
                erstellt = "2025-12-02T10:00:00Z",
            ),
            verbrauch = if (!laeuft) Verbrauch(gemessen = false, grund = "keine Messreihe") else Verbrauch(
                gemessen = true,
                cpuKerne = 0.12,
                ramBytes = 182.0 * 1024 * 1024,
                ramGrenzeBytes = 512.0 * 1024 * 1024,
                verlaufCpu = (0..48).map { i -> listOf(start + i * 1800, 0.05 + 0.04 * kotlin.math.sin(i / 4.0) + if (i in 30..33) 0.3 else 0.0) },
                verlaufRam = (0..48).map { i -> listOf(start + i * 1800, (150.0 + i * 0.7 + 10 * kotlin.math.sin(i / 3.0)) * 1024 * 1024) },
                schrittS = 1800,
            ),
            wirt = WirtLast(gemessen = true, cpuProzent = 18.0, ramProzent = 37.0, platteProzent = 45.0),
            verfuegbarkeit = if (!beobachtet) {
                Verfuegbarkeit(erfasst = false, grund = "Verlauf wird nur auf dem Hauptrechner aufgezeichnet")
            } else {
                Verfuegbarkeit(
                    erfasst = true,
                    uptime24h = 99.7,
                    uptime7d = 99.9,
                    uptime30d = 99.95,
                    zuletztBeendet = "2026-01-01T03:08:00Z",
                    abtastungS = 120,
                    ereignisse = listOf(
                        Wechsel("exited", "running", "2026-01-01T03:12:00Z"),
                        Wechsel("running", "exited", "2026-01-01T03:08:00Z"),
                        Wechsel("running", "removed", "2025-12-28T19:30:00Z"),
                    ),
                )
            },
            alarmeGemessen = true,
            alarme = alarme.alarme.filter { it.instanz == host }.take(1),
        )
    }
}
