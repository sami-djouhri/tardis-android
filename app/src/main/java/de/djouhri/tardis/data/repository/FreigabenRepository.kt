package de.djouhri.tardis.data.repository

import de.djouhri.tardis.data.api.FreigabenApi
import de.djouhri.tardis.data.demo.DemoModeManager
import de.djouhri.tardis.data.model.tardis.Befehl
import de.djouhri.tardis.data.model.tardis.BefehlAngenommen
import de.djouhri.tardis.data.model.tardis.BefehlErgebnis
import de.djouhri.tardis.data.model.tardis.BefehlUnterschrift
import de.djouhri.tardis.data.model.tardis.BefehleAntwort
import de.djouhri.tardis.data.model.tardis.FreigabeCommit
import de.djouhri.tardis.data.model.tardis.FreigabeErgebnis
import de.djouhri.tardis.data.model.tardis.FreigabeStand
import de.djouhri.tardis.data.model.tardis.FreigabenAntwort
import de.djouhri.tardis.data.model.tardis.SchluesselGemeldet
import de.djouhri.tardis.data.model.tardis.SchluesselMelden
import de.djouhri.tardis.data.model.tardis.Unterschrift
import de.djouhri.tardis.data.model.tardis.WartendeFreigabe
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FreigabenRepository @Inject constructor(
    private val api: FreigabenApi,
    private val demo: DemoModeManager,
) {
    suspend fun liste(): Result<FreigabenAntwort> {
        if (demo.isActive) return Result.success(DEMO)
        return runCatching { api.liste() }
    }

    suspend fun schluesselMelden(pem: String): Result<SchluesselGemeldet> {
        if (demo.isActive) return Result.success(SchluesselGemeldet("0".repeat(64), neu = false))
        return runCatching { api.schluesselMelden(SchluesselMelden(pem)) }
    }

    suspend fun freigeben(w: WartendeFreigabe, u: Unterschrift): Result<FreigabeErgebnis> {
        if (demo.isActive) return Result.success(FreigabeErgebnis(freigegeben = true, neu = true))
        return runCatching { api.freigeben(w.wirt, w.projekt, w.commit, u) }
    }

    suspend fun befehle(): Result<BefehleAntwort> {
        if (demo.isActive) return Result.success(DEMO_BEFEHLE)
        return runCatching { api.befehle() }
    }

    suspend fun befehlFreigeben(b: Befehl, u: BefehlUnterschrift): Result<BefehlAngenommen> {
        if (demo.isActive) return Result.success(BefehlAngenommen(true))
        return runCatching { api.befehlFreigeben(b.id, u) }
    }

    companion object {
        val DEMO_BEFEHLE = BefehleAntwort(
            eingerichtet = true,
            befehle = listOf(
                Befehl(
                    id = "beispiel-timer", titel = "Beispiel-Timer einschalten",
                    warum = "Der Timer prüft nachts die Sicherung.", rueckweg = "systemctl disable --now beispiel.timer",
                    sha256 = "5d41402abc4b2a76b9719d911017c592ae2b6fd0b0b5b3e0a1c2d3e4f5a6b7c8",
                    inhalt = "#!/usr/bin/env bash\n# titel: Beispiel-Timer einschalten\nset -euo pipefail\nsystemctl enable --now beispiel.timer\nsystemctl is-active beispiel.timer\n",
                ),
                Befehl(
                    id = "beispiel-firewall", titel = "Beispiel-Regel setzen",
                    sha256 = "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3a94a8fe5ccb19ba61c4c0873",
                    inhalt = "#!/usr/bin/env bash\nufw allow from 192.0.2.10 to any port 9\n",
                    ergebnis = BefehlErgebnis(zustand = "ok", rc = 0, ausgabe = "Rule added", ende = 1_800_000_000),
                ),
            ),
        )


        /** Erfundene Beispieldaten fuer Demo-Modus und Bildschirmfoto-Test. */
        val DEMO = FreigabenAntwort(
            konfiguriert = true,
            wartend = listOf(
                WartendeFreigabe(
                    wirt = "node1", projekt = "beispiel-seite", repo = "kunden/beispiel-seite",
                    commit = "3f9c2a71d0b84e6f5a1c9d8e7b6a5f4e3d2c1b0a", betreff = "Startseite: neuer Text",
                    autor = "host", basis = "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678",
                    commits = listOf(FreigabeCommit("3f9c2a71d0b84e6f5a1c9d8e7b6a5f4e3d2c1b0a", "Startseite: neuer Text")),
                    ampel = "gruen",
                    diff = "diff --git a/site/index.html b/site/index.html\n-<h1>Willkommen</h1>\n+<h1>Hallo</h1>\n",
                ),
                WartendeFreigabe(
                    wirt = "node1", projekt = "beispiel-dashboard", repo = "kunden/beispiel-dashboard",
                    commit = "9e8d7c6b5a4f3e2d1c0b9a8f7e6d5c4b3a2f1e0d", betreff = "Basis-Image php 8.4",
                    autor = "host", basis = "0f1e2d3c4b5a69788796a5b4c3d2e1f0a9b8c7d6",
                    commits = listOf(FreigabeCommit("9e8d7c6b5a4f3e2d1c0b9a8f7e6d5c4b3a2f1e0d", "Basis-Image php 8.4")),
                    ampel = "rot", gruende = listOf("Dockerfile: Basis-Image geändert"),
                    diff = "diff --git a/Dockerfile b/Dockerfile\n-FROM php:8.2-apache\n+FROM php:8.4-apache\n",
                ),
            ),
            stand = listOf(
                FreigabeStand("node1", "beispiel-seite", freigegeben = "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678",
                    ausgerollt = "a1b2c3d4e5f60718293a4b5c6d7e8f9012345678", gemessen = true),
                FreigabeStand("node1", "beispiel-dashboard", freigegeben = "0f1e2d3c4b5a69788796a5b4c3d2e1f0a9b8c7d6",
                    ausgerollt = "0f1e2d3c4b5a69788796a5b4c3d2e1f0a9b8c7d6",
                    gescheitert = "77aa66bb55cc44dd33ee22ff1100aa99bb88cc77", gemessen = true),
            ),
            schluessel = listOf("0".repeat(64)),
        )
    }
}
