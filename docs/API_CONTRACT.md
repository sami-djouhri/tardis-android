# Gateway-Vertrag (`mobile-api`)

Die App ist ein dünner Client. Dieses Dokument hält den erwarteten Vertrag fest,
den das Gateway erfüllen und **serverseitig durchsetzen** muss. Beispiel-Host:
`https://gateway.internal:8140` (die reale Adresse kommt zur Laufzeit aus dem QR).

Alle Endpunkte außer Pairing/Health verlangen `Authorization: Bearer <jwt>`.
Autorisierung, Rate-Limiting und Eingabevalidierung liegen beim Gateway.

## Auth / Pairing

| Methode | Pfad | Auth | Zweck |
|---------|------|------|-------|
| `POST` | `/v1/auth/pair` | keine (einmalige Nonce) | CSR + Nonce → JWT + Client-/CA-Zertifikat |
| `GET` | `/v1/health` | keine | Liveness-Probe (Verbindungstest) |
| `POST` | `/v1/auth/unpair` | Bearer | Widerruft die Token **dieses** Geräts |
| `GET` | `/v1/auth/geraete` | Bearer | Alle bekannten Geräte mit Zustand |
| `POST` | `/v1/auth/geraete/{id}/widerrufen` | Bearer | Widerruft die Token eines **anderen** Geräts |

`POST /v1/auth/pair` - Request:
```json
{ "nonce": "<einmalig, kurzlebig>", "csr_pem": "<PKCS#10>", "device_label": "Pixel 8" }
```
Response:
```json
{ "device_id": "<hash>", "jwt": "<token>",
  "client_cert_pem": "<PEM>", "ca_cert_pem": "<PEM>" }
```
Server-Pflichten: Nonce **einmalig** und ablaufend; CSR-Signatur prüfen;
Client-Cert von der Homelab-CA signieren; JWT mit begrenzter Gültigkeit ausstellen.

Der QR-Code, den die interne Pairing-Seite erzeugt, trägt:
```json
{ "gateway_url": "https://gateway.internal:8140",
  "ca_fingerprint": "AB:CD:…", "nonce": "…" }
```
Die App verifiziert `sha256(ca_cert_pem) == ca_fingerprint`.

### Abmelden und Geräte aussperren

`POST /v1/auth/unpair` und `POST /v1/auth/geraete/{id}/widerrufen` antworten:
```json
{ "geraet_id": "<id>", "widerrufen": 2 }
```

Drei Dinge daran sind Vertrag, nicht Umsetzungsdetail:

- **`unpair` braucht das noch gültige Token.** Die App ruft es deshalb **vor**
  dem lokalen Löschen auf. Umgekehrt käme eine 401 zurück, und das Abmelden
  würde nichts widerrufen, obwohl die Oberfläche „entkoppelt" meldet. Genau
  dieser Zustand lag bis zum 2026-10-09 vor: die App löschte nur lokal, das
  Token blieb bis zu 30 Tage gültig.
- **`widerrufen: 0` ist kein Fehler**, sondern „Kennung unbekannt oder schon
  widerrufen". Das Gateway antwortet dann mit 200 statt 404, weil ein 404 eine
  Auskunft darüber wäre, welche Geräte-Kennungen existieren. Die App nennt die
  Zahl, statt einen Erfolg zu behaupten.
- **Mehr als 1 ist gewollt:** ein zweimal gekoppeltes Gerät hätte sonst nach dem
  Abmelden noch ein zweites gültiges Token.

`GET /v1/auth/geraete` liefert:
```json
{ "geraete": [
  { "geraet_id": "<id>", "jti_kurz": "1a2b3c4d", "etikett": "Pixel 8",
    "ausgestellt_am": 1790000000, "laeuft_ab_am": 1792592000,
    "widerrufen_am": null, "zuletzt_gesehen": 1791500000, "gueltig": true }
] }
```
Zeiten sind Unix-Sekunden. `gueltig` rechnet das **Gateway** aus (nicht
widerrufen und nicht abgelaufen); die App rechnet es bewusst nicht nach, denn
die Uhr des Telefons ist nicht die Uhr, gegen die geprüft wird. `jti_kurz` sind
die ersten acht Zeichen der Token-Kennung und halten zwei Kopplungen desselben
Geräts auseinander.

Server-Pflicht: die Liste ist für **jedes** gekoppelte Gerät lesbar, nicht nur
für ein Verwalter-Konto. Der Zweck ist, ein verlorenes Telefon von einem anderen
Gerät aus aussperren zu können.

## Ops

| Methode | Pfad | Zweck |
|---------|------|-------|
| `GET` | `/v1/ops/hosts` | Steuerbare Hosts mit Zählstand (ohne Container-Liste) |
| `GET` | `/v1/ops/services?host=` | Container-Liste eines Hosts |
| `POST` | `/v1/ops/services/{host}/{name}/action?action=start\|stop\|restart` | Aktion ausführen |
| `GET` | `/v1/ops/logs/{host}/{name}?lines=` | Log-Ausschnitt |

Server-Pflichten: `action` gegen eine Allowlist prüfen; nur erlaubte Hosts/
Container; keine Shell-Injection über `name`/`host`. **`host` muss bis zum
Docker-Endpunkt durchgereicht werden.** Ein verworfener Hostname ist beim Lesen
ein Anzeigefehler, beim Stoppen der falsche Rechner.

`GET /v1/ops/services` liefert je Container:

```json
{ "host": "node1", "name": "immich", "status": "running",
  "health": "healthy", "image": "…", "ports": [],
  "expected_state": "", "in_control_map": true }
```

Zwei Felder tragen die ganze Einordnung:

- **`health`** darf nur gesetzt sein, solange der Container **läuft**. Docker
  behält den letzten Health-Befund nach dem Stoppen bei; ein roher Durchgriff
  meldet beendete Container als `unhealthy`. `null` heißt „keine Aussage", nicht
  „gesund".
- **`expected_state`** ist die control-map-Erwartung: `""` = soll laufen,
  `stopped`/`manual`/`migrated`/`job` = steht bewusst still. **`in_control_map`**
  trennt „soll laufen" von „steht nirgends"; ohne das Feld bedeutet ein leeres
  `expected_state` beides zugleich. Für Remote-Hosts sind beide `null`, weil die
  control-map ihre Erwartung nur für den lokalen Host führt.

Der Client leitet daraus fünf Zustände ab (`ServiceZustand`): `LAEUFT`,
`AUFFAELLIG`, `BEWUSST_AUS`, `AUSGEFALLEN`, `UNBEWERTET`. Nur die beiden
Problemfälle (`AUFFAELLIG`, `AUSGEFALLEN`) erscheinen in der Übersicht.

`GET /v1/ops/hosts` liefert denselben Zählstand aggregiert:

```json
{ "hosts": [
  { "name": "host", "erreichbar": true, "gesamt": 93, "laufend": 82,
    "auffaellig": 0, "bewusst_aus": 11, "ausgefallen": 0, "unbewertet": 0 },
  { "name": "jetson", "erreichbar": false }
] }
```

Die fünf Fächer **müssen sich auf `gesamt` summieren** und dieselbe Grenze
ziehen wie die Einzel-Einordnung, sonst kann die Übersicht „1 auffällig"
melden und die Liste darunter nichts Auffälliges enthalten. Ein nicht
erreichbarer Host liefert **gar keine Zahlen**: eine `0` wäre die Aussage
„dort läuft nichts".

### Alarme und Dienste auf Abruf (seit 2.0.10)

| Methode | Pfad | Zweck |
|---------|------|-------|
| `GET` | `/v1/ops/alarme` | Aktive Alarme: `{erreichbar, stand, fehler, alarme[]}` |
| `GET` | `/v1/ops/services/{host}/{name}/detail` | Ein Container: `{container, verbrauch, wirt, verfuegbarkeit, alarme_gemessen, alarme[]}` (seit 2.0.12) |
| `GET` | `/v1/ops/abruf` | `{wecker: {erreichbar, frei_mb, reserve_mb, dienste[]}, lab: {verfuegbar, veraltet, laeuft, vms[], reserviert}}` |
| `POST` | `/v1/ops/abruf/{name}/wecken` | Startet einen schlafenden Dienst, Antwort `{ok, zustand}` |
| `POST` | `/v1/ops/abruf/{name}/wartung?an=true\|false` | Wach halten bzw. freigeben |
| `POST` | `/v1/ops/lab/{aktion}` | `start`, `stop`, `start-wartung`, `wartung-an`, `wartung-aus`; Antwort `{ok, rc, conflict, log[]}` |

Server-Pflichten: `erreichbar: false` heißt „nicht gemessen" und darf nie als
leere Liste ankommen. Dasselbe gilt im Dienst-Detail für jeden der vier Teile
(`gemessen`/`erfasst`) und für `alarme_gemessen`; `verfuegbarkeit.ereignisse`
enthält nur echte Wechsel (kein `alt: null`), `ram_grenze_bytes` ist `null`,
wenn keine Grenze gesetzt ist. Dienstnamen und Lab-Aktionen nur aus einer festen Menge,
nie als Pfad durchgereicht. `zustand` ist der Weckerzustand (`starting`,
`ready`, `throttled`, `lowmem`, `queued`); nur die ersten beiden sind Erfolg.
`conflict: true` ist keine Störung: eine belegte Rolle hat Vorrang. Ein
Lab-Start darf bis zu 200 s dauern, die App wartet dafür 210 s.

## Dashboard

| Methode | Pfad | Zweck |
|---------|------|-------|
| `GET` | `/v1/dashboard/summary` | Host-Metriken (`health`) + `inbox_counts` |

Die App nutzt aus der Summary bewusst nur `health` und `inbox_counts`.

## Inbox

| Methode | Pfad | Zweck |
|---------|------|-------|
| `GET` | `/v1/inbox?limit=&include_done=` | Liste + Zähler |
| `POST` | `/v1/inbox/note` | Notiz anlegen (`title`, `content`, `tags`) |
| `POST` | `/v1/inbox/{external_id}/action` | Triage (`done`/`snooze`/`archive`), optional `snooze_minutes` |

## Update

| Methode | Pfad | Zweck |
|---------|------|-------|
| `GET` | `/app-version` | `{ versionCode, versionName, changelog, apkUrl, sha256? }` |

`apkUrl` muss **Same-Origin** zum Gateway sein (die App lehnt fremde Hosts ab).
Die Integrität sichert die APK-Signaturprüfung von Android; `sha256` ist optional
und wird nur angezeigt.

## Optionaler mTLS-Modus (Phase 2)

Antwortet das Gateway über TLS mit Client-Zertifikatsprüfung, präsentiert die App
ihr Client-Cert. Das Gateway bindet dann den JWT-`sub` an die Zertifikats-CN
(z. B. über `X-SSL-Client-Verify: SUCCESS` + `X-SSL-Client-Sub` vom TLS-
terminierenden Reverse-Proxy) und weist Nichtübereinstimmungen ab.
