# Changelog

## Topologie als Karte (2026-11-07, 2.0.16)

Neue Karte „Topologie“ auf der Startseite: alle Wirte als Kästen, darin die
Dienste als farbige Punkte nach Gruppe (grün läuft, grau bewusst aus, rot aus
obwohl er laufen soll, gelb unbekannt), dazu die Abhängigkeiten als Linien. Mit
zwei Fingern zoomen und verschieben; ab etwas Zoom erscheinen Gruppennamen,
weiter hinein die Dienstnamen. Ein Dienst antippen hebt seine Verbindungen
hervor und zeigt, womit er verbunden ist, samt „Dienst öffnen“. „Nur
Auffälliges“ dimmt alles, was in Ordnung ist. Jeder Dienst steht bei jedem
Öffnen an derselben Stelle (festes Raster statt Simulation).

### Was am Code anders ist

- `data/model/tardis/Topologie.kt`: Modell und `kartenLayout()` (reine Funktion,
  `TopologieLayoutTest` prüft Platz im eigenen Wirt, keine Überlappung, Breite,
  mit einem Bestand in echter Größe).
- `ui/topologie/TopologieScreen.kt` (Canvas, `detectTransformGestures` um den
  Fingermittelpunkt, Antippen im selben Maßstab wie das Zeichnen).
- Dienstseite: mobile-api 727e014 (`GET v1/ops/topologie`, Zustand in vier Stufen).

## Verlauf mit Filtern (2026-11-07, 2.0.15)

Der Verlauf in der Inbox kann jetzt, was die Web-Fläche Events kann: nach Typ
(Post, Nachricht, Angebot, Dienst …) und nach Quelle filtern, mit der Zahl je
Quelle, und dieselbe Meldung wird zu einer Zeile gebündelt („5× seit …“). Das
Bündeln lässt sich abschalten. Gezeigt werden die letzten 120 Ereignisse statt 50.

### Was am Code anders ist

- `data/model/tardis/Inbox.kt`: `VerlaufZeile` und `gebuendelt()` (Quelle + Titel,
  ältestes Vorkommen), Unit-Test in `StreamItemTest`.
- `InboxApi.stream(limit, kinds, sources)`: gefiltert wird am Dienst wie im Web,
  damit ein Typfilter 120 Treffer dieses Typs zeigt und nicht die wenigen im
  ungefilterten Fenster.
- Typ-Chips nur aus dem ungefilterten Fenster gelernt (wie im Web), Quellen
  gesammelt. Bildschirmfoto-Test `12-verlauf`.

## Verfügbarkeit und schnellere Antworten (2026-11-07, 2.0.14)

Auf der Startseite führt eine neue Karte „Verfügbarkeit“ zu dem, was im Web
unter Reliability steht: wie verfügbar jeder Dienst in 24 Stunden, 7 und
30 Tagen war (das Auffällige zuerst, der Rest auf Wunsch), die letzten
Vorfälle, die Systemmetriken mit Schwelle und der Verlauf von CPU, Speicher,
Platte und Last über 1, 6 oder 24 Stunden. Dienste, die bewusst nicht laufen
oder in keiner Liste stehen, stehen unten mit ihrem Grund statt als Ausfall.
Dazu antworten Start, Dienste und Inbox schneller: der Tagesüberblick kommt aus
dem letzten Stand und wird im Hintergrund erneuert, die Containerlisten fragen
nicht mehr jeden Container einzeln ab.

### Was am Code anders ist

- `ui/reliability/ReliabilityScreen.kt` (neu) mit eigenem ViewModel, Route
  `ReliabilityRoute` als Unterseite der Startseite (die Leiste hat schon fünf
  Reiter). Karte `SprungKarte` in `OverviewScreen.kt`.
- `data/model/tardis/Reliability.kt`, `OpsApi.reliability(fenster)`, Demo-Daten
  mit den drei Arten von „nicht 100 %“; Bildschirmfoto-Test `11-verfuegbarkeit`.
- Dienstseite: mobile-api 725440a (`GET v1/ops/reliability`), Tardis 0ede7dd und
  c91b3f3 (schlanke Containerliste), life-ops-api d28c8bd (Tagesüberblick).

## Alarme nach Bereich filtern, Inbox räumt sich auf (2026-11-07, 2.0.13)

Unter Alarme steht statt „nur kritische“ eine Reihe Chips nach Bereich:
Betrieb, Backup, Netz & Sicherheit, Saganta, Smart Home, Medien, Spiele,
KI & Wissen, Bots, jeweils mit der Zahl der Alarme. Kritische Alarme stehen
weiterhin oben. Die Inbox räumt sich jetzt selbst auf: Nachrichten und
Hinweise gehen nach 24 Stunden ins Archiv, Warnungen und Angebote nach
7 Tagen, Kritisches bleibt, bis du es abhakst. Die gleiche Meldung aus
mehreren Kanälen steht nur noch einmal da.

### Was am Code anders ist

- `ui/alarme/AlarmeScreen.kt`: Bereichs-Chips statt `nurKritisch`; ein Bereich,
  der nach dem Aktualisieren leer ist, fällt auf „Alle“ zurück.
- `data/model/tardis/Abruf.kt`: Feld `bereich` (mobile-api 28a57d4, aus der
  Regelgruppe, die der alerts-Dienst seit 42b8082 mitliefert).
- Inbox-Regeln liegen im Dienst (life-ops-api 315b453), die App brauchte dafür
  nichts.

## Dienst im Detail, Widget und ein Blick auf jede Fläche (2026-11-03, 2.0.12)

Tippt man einen Dienst an, steht dort jetzt mehr als Zustand und Logs: seit
wann er läuft und wie oft Docker ihn neu gestartet hat, CPU und
Arbeitsspeicher jetzt und als Linie über 24 Stunden, wie voll der Rechner ist,
auf dem er läuft, die Alarme genau dieses Dienstes und, wo es aufgezeichnet
wird, Verfügbarkeit und die letzten Wechsel. Wo etwas nicht gemessen wird,
steht das da, statt einer Null. Neu ist ein Widget für den Startbildschirm:
Zahl der Alarme, der wichtigste davon, und ob Dienste Aufmerksamkeit brauchen,
mit der Uhrzeit des Stands; ↻ fragt neu. Dazu sind mehrere graue Zeilen wieder
lesbar (etwa „Stand 06:13 Uhr"), ausgeschaltete Schalter und Knopfränder
deutlicher, und der erste Reiter heißt „Start", damit er auch bei großer
Schrift ganz dasteht.

### Was am Code anders ist

- `data/model/tardis/DienstDetail.kt`, `ui/services/DetailKarten.kt`: das
  Detail aus `GET v1/ops/services/{host}/{name}/detail`, parallel zu Logs und
  Zustand geladen. Ein älteres Gateway ohne die Route kostet nur die Karten.
- `widget/`: Glance-Widget. Die offene App schreibt den Stand (Alarme jede
  Minute, Dienste mit der Übersicht), Android frischt alle 30 min auf. `null`
  heißt „nicht gemessen", nie 0. Im Demo-Modus wird nichts geschrieben.
- `test/.../bildschirm/BildschirmeTest.kt`: zehn Flächen per Robolectric und
  Roborazzi, ausschließlich mit den Demo-Daten (die API scheitert im Test bei
  jedem Aufruf). `./gradlew :app:recordRoborazziDebug` legt die Bilder unter
  `app/build/outputs/roborazzi/` ab. Gefunden damit: Text in
  `colorScheme.outline` (der Randfarbe, rund 1,3:1) an fünf Stellen, jetzt
  `TextLeise`; `outline` und `outlineVariant` waren im Thema vertauscht.
- `AbrufViewModel` und `ServiceDetailViewModel` nehmen für den Test den Flow
  der Bestätigungseinstellung statt des ganzen `SettingsStore`.

## Verlauf in der Inbox (2026-10-31, 2.0.11)

In der Inbox gibt es oben den Knopf „Verlauf". Er zeigt statt der offenen
Einträge die Zeitleiste aller Ereignisse im Haus: eingegangene Post, neue
Nachrichten, Alarme, Dienste, die ausfallen und wiederkommen. Jüngste zuerst,
mit Art, Quelle und wie lange es her ist. „Offen" führt zurück. Die Liste ist
nur zum Lesen; abgearbeitet wird weiter in der offenen Inbox.

### Was am Code anders ist

- `data/api/InboxApi.kt`: `GET v1/stream`, den `mobile-api` seit Langem
  anbietet und den keine App abrief.
- `data/model/tardis/Inbox.kt`: `StreamItem`. Der Dienst liefert
  `occurred_at` ohne Zonenangabe (naive Spalte, in UTC beschrieben);
  `zeitpunktUtc` ergänzt das `Z`, sonst schlüge das Parsen fehl und die
  Zeitangabe fiele still weg.
- Ein Umschalter im Bereich statt eines sechsten Reiters: die Leiste trägt
  schon fünf.

## Alarme, Dienste auf Abruf und das AD-Lab (2026-10-31, 2.0.10)

Unten in der Leiste gibt es zwei neue Bereiche. „Alarme" zeigt alles, was
gerade im Haus Alarm schlägt, auch was nicht an einem Container hängt, etwa
ein ausgebliebenes Backup oder ein Rechner, der auf seinen Neustart wartet.
Gleiche Alarme an mehreren Rechnern stehen zusammen, kritische ganz oben, und
die Zahl der aktiven Alarme steht am Symbol. Ist die Alarmquelle nicht zu
erreichen, sagt die App „nicht gemessen" statt „alles ruhig". „Abruf" zeigt
die Dienste, die nur bei Bedarf laufen: wecken mit einem Tipp, wach halten mit
einem Schalter. Darunter lässt sich das Windows-Lab starten, stoppen und in
den Wartungsmodus setzen. Fehlermeldungen nennen jetzt den Grund, den das
Gateway mitschickt, und eine Zeitüberschreitung heißt nicht mehr
„Ist das VPN aktiv?".

### Was am Code anders ist

- `ui/alarme/AlarmeScreen.kt`: `AlarmeViewModel` gehört der Activity (wie
  `UpdateViewModel`), damit Zähler an der Leiste, Karte auf der Übersicht und
  Liste denselben Stand zeigen. Fragt jede Minute, nur im Vordergrund.
- `ui/abruf/AbrufScreen.kt`: Wecken ohne Bestätigung (es tut dasselbe wie das
  Aufrufen der Seite im Browser), Wartung und Lab mit der Aktionsbestätigung.
  Nach dem Wecken wird bis zu 90 s nachgesehen, bis der Healthcheck durch ist.
- `di/AppModule.kt`: `fristInterceptor` verlängert die Lesefrist für genau den
  Aufruf, der `X-Tardis-Frist` trägt (Lab: 210 s), und nimmt die Kopfzeile vor
  dem Senden heraus. Mit den 30 s brach ein VM-Start ab, der weiterlief.
- `util/Extensions.kt`: `SocketTimeoutException` eigener Text; `detail` aus
  FastAPI-Fehlern wird angehängt, Validierungslisten nicht.
- `util/Zeit.kt`: `seitText`/`uhrzeitAus` über `OffsetDateTime`, weil der
  Alarmdienst Offsets (`+00:00`) statt `Z` schreibt.
- Monochrom-Ebene fürs Launcher-Symbol (Designsymbole ab Android 13).
- Gateway-Seite: `/v1/ops/alarme`, `/v1/ops/abruf`, `/v1/ops/abruf/{name}/wecken`,
  `/v1/ops/abruf/{name}/wartung`, `/v1/ops/lab/{aktion}` (siehe API_CONTRACT).

## Neuer Paketname: de.djouhri.tardis (2026-10-31, ausgeliefert als 2.0.9)

Die App heißt jetzt auch intern Tardis. Android behandelt sie deshalb als
neue App: Cockpit einmal deinstallieren, Tardis von Hand installieren
(`/downloads/tardis.apk` oder über das Gateway `/tardis.apk`) und neu
koppeln. Ab dann laufen Updates wieder über den Updater.

### Was am Code anders ist

- `applicationId`/`namespace` `de.djouhri.tardis`, Quellbaum unter
  `de/djouhri/tardis`, Gradle-Schalter `tardis.versionCode` und
  `tardis.gatewayUrl` (`TARDIS_*`).
- Gateway liefert `/tardis.apk` samt `tardis-version.json`, Token-Scope
  `mobile.tardis`, Inbox-Quelle `android-tardis`.

## Die App heißt jetzt Tardis (2026-10-30, ausgeliefert als 2.0.8)

Aus Cockpit wird Tardis, nach der Zeitmaschine aus Doctor Who. Das
Web-Portal dazu heißt ebenfalls so. Kopplung, Einstellungen und Updates
bleiben, wie sie sind: es ist dieselbe App mit neuem Namen.

### Was am Code anders ist

- `app_name`, Titel der Startseite und Kopplungsbildschirm sagen „Tardis".
- `applicationId` bleibt `de.djouhri.cockpit`, ebenso `/cockpit.apk` und
  `cockpit-version.json`: ein neuer Paketname wäre eine zweite App ohne
  Kopplung und ohne Update-Weg.

## Updates werden vor der Installation geprüft (2026-10-30, ausgeliefert als 2.0.7)

Bevor die App ein Update an Android übergibt, prüft sie jetzt selbst, ob die
Datei zu ihr passt: dieselbe App, die angekündigte Version, derselbe
Schlüssel. Passt etwas nicht, steht der Grund in der App. Bisher lehnte
Android so eine Datei nur mit „App nicht installiert" ab. Ohne Prüfsumme vom
Gateway installiert die App nichts mehr.

Dazu ist das dunkle Design jetzt die Vorgabe, und alle Texte stehen in der
Schrift Inter.

### Was am Code anders ist

- `data/update/ApkPruefung.kt` + `AndroidApkPruefung.kt`: Paketname,
  `versionCode == angekuendigt` und Signatur gegen die installierte App, vor
  dem Installer. Leere Zertifikatsmenge gilt als unbekannt, nicht als gleich.
- `UpdateRepository`: Download unter `.teil`, Umbenennen erst nach Prüfsumme
  und Vorprüfung; `HashUrteil.OHNE_VERGLEICH` bricht ab statt zu installieren.
- Gleicher Stand wie die Updater in `saganta-apps`, `lernen-android`,
  `bibliothek-android` und `karten-android`.

## Entkoppeln widerruft jetzt wirklich (2026-10-09, ausgeliefert 2026-10-10 als 2.0.6)

<!-- DIESER ERSTE ABSATZ IST DER TEXT, DEN DER OWNER IM UPDATE-HINWEIS DER
     APP LIEST. `ausliefern.sh` schneidet ihn bis zur ersten `###`-Zeile heraus
     und schreibt ihn ins Manifest. Alles darunter ist fuer den, der am Code
     arbeitet. Deshalb hier keine Abkuerzungen und keine Dateinamen: wer das
     liest, will wissen, was seine App jetzt anders macht. -->

„Gerät entkoppeln" hat bisher nur auf dem Telefon aufgeräumt. Der Zugang blieb
beim Gateway noch bis zu 30 Tage gültig, und die App meldete trotzdem
„entkoppelt". Jetzt widerruft sie zuerst dort und löscht erst danach das Gerät.
Ohne Netz geht das Entkoppeln weiterhin, die App fragt dann noch einmal nach
und nennt die 30 Tage, bevor sie es tut.

Neu sind „Gekoppelte Geräte" in den Einstellungen. Dort stehen alle Kopplungen,
und jede lässt sich einzeln aussperren. Dafür ist die Liste da: ein verlorenes
Telefon sperrt man von einem anderen Gerät aus.

Die Farben sind neu. Der Grund ist jetzt ein neutrales Blaugrau statt des
Vorlagen-Grüns, und Farbe steht nur noch für Dringlichkeit. Drei der vier
Zustandsfarben waren im Hellmodus zu blass zum Lesen, Bernstein am stärksten;
sie sind nachgemessen und so gewählt, dass sie auch gegen den Seitengrund
stehen.

### Was falsch war

- **„Gerät entkoppeln" löschte nur lokal.** JWT, Zertifikate und
  Keystore-Schlüssel waren weg, das Token blieb beim Gateway aber **bis zu 30
  Tage gültig** (`jwt_ttl_days`). Die Oberfläche meldete „entkoppelt", und
  genau das ist die Lage, die der Docstring der Gateway-Route als das
  schlechtere Ergebnis benennt: ein Widerruf, der nichts widerruft, erzeugt die
  Annahme, das Gerät sei draußen.
- **Ein verlorenes Telefon ließ sich nicht aussperren.**
  `GET /v1/auth/geraete` und `POST /v1/auth/geraete/{id}/widerrufen` sind
  ausdrücklich dafür gebaut, dass man es **von einem anderen Gerät aus** tun
  kann. Beide hatten hausweit keinen einzigen Aufrufer, und ohne Fläche in der
  App existierte der Weg nicht.

### Was jetzt gilt

- **Zuerst das Gateway, dann lokal.** Die Reihenfolge ist keine Kosmetik: die
  Route läuft dort durch `require_device` und braucht das noch gültige Token.
  Wer erst lokal löscht, bekommt eine 401 und widerruft nichts.
- **Das lokale Entkoppeln hängt trotzdem nie am Gateway.** Ohne VPN oder bei
  totem Gateway muss man die App loswerden können, sonst sitzt man in einer
  Kopplung fest, die sich nicht auflösen lässt.
- **Scheitert der Widerruf, fragt die App noch einmal** und nennt die 30 Tage.
  Sie löscht lokal erst nach dieser zweiten Bestätigung, und das ist der Grund
  für die Aufteilung in zwei Schritte: danach kippt `isPaired`, die App wechselt
  auf den Pairing-Screen, und eine Warnung wäre dort nicht mehr zu sehen
  gewesen. Wer abbricht, ist weiter gekoppelt und kann erst das VPN einschalten.
- **„Gekoppelte Geräte" in den Einstellungen** listet alle Kopplungen mit
  Zustand und sperrt jede einzeln aus. Bewusst kein eigener Reiter: es ist
  Geräte-Verwaltung und gehört dorthin, wo „Entkoppeln" steht. Die Liste wird
  erst auf Tippen geholt, denn über WireGuard kostet jede Anfrage eine
  Verbindung, die niemand angefordert hat.
- **Der eigene Eintrag ist als „dieses Gerät" markiert**, und `jti_kurz`
  unterscheidet zwei Kopplungen desselben Geräts. Ein Widerruf läuft durch
  dieselbe Biometrie-/PIN-Bestätigung wie start/stop/restart.
- **`widerrufen: 0` wird als Auskunft gemeldet, nicht als Erfolg** („unbekannt
  oder schon widerrufen"). Das Gateway antwortet dort mit 200 statt 404, weil
  ein 404 verraten würde, welche Geräte-Kennungen existieren.

### Nebenbei geprüft

- Zwei ZIELBILD-Einträge waren überholt und sind **nicht** angefasst worden:
  AND-03 („`dev_portal.py` verwirft `host`, trifft immer host") ist seit dem
  2026-08-29 behoben, der Docstring dort datiert es selbst; „Cockpit auf alle
  Hosts" ebenso, die App nutzt `v1/ops/hosts` und `v1/ops/services?host=`.
- Die Reihenfolge liegt als reine Funktion in `data/repository/Entkoppeln.kt`,
  weil `PairingRepository` an DataStore und AndroidKeyStore hängt und im
  JVM-Test nicht instanziierbar ist. Eine Regel ohne Test wäre hier ein Vorsatz.

## Die App holt ihre Updates selbst (2026-09-27, 2.0.5)

Bis hierher war der Weg zu einer neuen Version: daran denken, Einstellungen
öffnen, „Auf Updates prüfen" tippen, der Browser lädt, die APK von Hand
öffnen. Drei der vier Schritte waren Arbeit für den Nutzer, und der erste war
der, an dem es scheiterte.

### Update
- **Die App sieht alle sechs Stunden selbst nach** und meldet sich mit einem
  Hinweis über allen Flächen. Der Abstand ist Absicht: bei jedem Start zu
  fragen hieße, bei jedem Start eine Verbindung über WireGuard aufzubauen, die
  niemand angefordert hat.
- **Sie lädt die Datei selbst** und **vergleicht die Prüfsumme** mit der
  angekündigten, bevor sie sie dem Installer übergibt. Vorher stand der Hash in
  der Oberfläche und wurde nie mit etwas verglichen. Stimmt er nicht, wird die
  Datei gelöscht statt liegengelassen: eine nicht passende APK im
  Download-Ordner ist genau das, was beim nächsten Mal jemand von Hand antippt.
- Nennt das Gateway **keinen** Hash, sagt die App „verglichen wurde nichts"
  statt ein Häkchen zu zeigen, das nichts bedeutet.
- „Nicht jetzt" merkt sich genau diese Nummer. Wer von Hand nachsieht, bekommt
  sie trotzdem wieder: sonst antwortet die App „Aktuell", obwohl sie es nicht
  ist.
- Die Herkunftsprüfung bleibt, wie sie war: die APK darf nur vom gekoppelten
  Gateway kommen (gleiches Schema, gleicher Host, gleicher Port). Über die
  Echtheit entscheidet weiterhin Androids Signaturprüfung beim Installieren.

### Bedienung
- **Ein Befund auf der Übersicht ist anklickbar** und führt zu seinem Dienst.
  Vorher führte die ganze Karte in die Dienstliste, und man suchte den Namen,
  den man gerade angetippt hatte, noch einmal.
- **Die Übersicht frischt sich alle 30 Sekunden still auf**, solange sie offen
  ist, und sagt, von wann ihr Stand ist. Vorher stand dort der Stand vom Öffnen
  der App, auch nach einer Stunde, und nichts daran sah alt aus.

### Erscheinungsbild
- **Die Oberfläche schreibt Deutsch mit Umlauten.** „Uebersicht", „auffaellig",
  „Zurueckgestellt" und dreißig weitere Stellen waren Umschrift.
- **Die App benutzt wieder ihre eigene Palette.** Mit `dynamicColor = true`
  ersetzt Android ab Version 12 die gesamten Farben durch eine aus dem
  Hintergrundbild abgeleitete: die sorgfältig gesetzten Werte in `Color.kt`
  kamen auf einem aktuellen Telefon nie zum Vorschein. In einem Cockpit trägt
  Farbe Bedeutung, und ein Hinweis in `primaryContainer` soll nicht je nach
  Wandbild wie eine Warnung aussehen.

### Nebenbei geprüft
- Der Vorgabewert von `cockpit.versionCode` stand auf **2**, ausgeliefert war
  **4**. Ein Bau ohne gesetzte Property hätte ein Downgrade erzeugt, das
  Android wortlos ablehnt.
- Signatur der neuen APK gegen die ausgelieferte verglichen (beide
  `1039220b57be…`): dieses Update legt sich über die installierte App, anders
  als der Sprung auf 2.0.4.

## Wahrheit der Anzeige (2026-08-29)

Die App war funktional vollständig und sagte trotzdem drei Dinge, die nicht
stimmten. Alle drei lagen nicht in der Oberfläche, sondern in den Daten, die
sie bekam.

### Was falsch war
- **Jeder beendete Container galt als Problem.** Auf dem Hauptknoten waren das
  dauerhaft elf, und alle elf sind Absicht (Rückfall-Instanzen, Dienste auf
  Abruf, ein Einmal-Job). Die Übersicht war also dauerhaft rot ohne Anlass.
- **`unhealthy` konnte gar nicht angezeigt werden.** Das Feld wurde vom Portal
  nie gesetzt; die App zeichnete deshalb jeden laufenden Container grün. Der
  Fehler war unsichtbar, weil ein fehlendes Feld wie „alles gesund" aussieht.
- **Der Host wurde unterwegs verworfen.** Das Gateway nahm `host` entgegen und
  ignorierte es: die App sah 93 von 196 Containern und behauptete mit ihrer
  Host-Gruppierung trotzdem, den ganzen Bestand zu zeigen. Beim Stoppen hätte
  es den gleichnamigen Container auf dem falschen Rechner getroffen.

### Was jetzt gilt
- Fünf Zustände statt zwei (`LAEUFT`/`AUFFAELLIG`/`BEWUSST_AUS`/`AUSGEFALLEN`/
  `UNBEWERTET`), abgeleitet aus `expected_state` und `in_control_map` der
  control-map. Ein Zustand ohne hinterlegte Erwartung wird als solcher benannt,
  nicht zum Ausfall erklärt.
- `health` kommt durch, aber nur für laufende Container: Docker behält den
  letzten Befund nach dem Stoppen bei, ein roher Durchgriff hätte die elf
  Fehlalarme nur umbenannt.
- Alle steuerbaren Hosts, mit Host-Auswahl in der Dienste-Liste. Die Übersicht
  holt dafür einen Zählstand (`/v1/ops/hosts`) statt knapp zweihundert
  Container-Einträgen über die VPN-Strecke; die Container-Liste eines Hosts
  wird nur geladen, wenn dort überhaupt etwas gemeldet wird.
- Die Übersicht führt mit dem Befundstand, nicht mit der Laufzahl.

### Bedienung
- Suche über Name und Image, Filter nach Zustand mit Trefferzahl je Filter.
- Logs springen ans Ende (die interessante Zeile ist die letzte), lassen sich
  kopieren und einzeln neu laden.
- Start/Stop nur dort anklickbar, wo sie etwas bewirken.
- Der Demo-Modus zeigt jeden der fünf Zustände; sein Zählstand wird aus den
  Beispieldiensten gerechnet statt danebengeschrieben.

## Härtung & Portfolio-Aufbereitung

Sicherheits- und Robustheits-Welle; keine Funktion entfernt, bestehende
Architektur schrittweise verbessert.

### Sicherheit
- QR-`gateway_url` und `ca_fingerprint` werden jetzt **genutzt**: dynamische
  Gateway-Basis-URL + Fingerprint-Pinning der CA beim Pairing.
- **mTLS** im laufenden API-Verkehr: der gerätegebundene Keystore-Schlüssel +
  Client-Zertifikat werden über einen `X509ExtendedKeyManager` präsentiert;
  TrustManager pinnt die Homelab-CA (kein „trust-all"). Inert über HTTP.
- JWT und Zertifikate liegen **verschlüsselt** im DataStore (AndroidKeyStore-
  AES-GCM) statt im Klartext.
- Globales `usesCleartextTraffic` entfernt → `network_security_config`:
  Release verbietet Klartext (TLS-Pflicht), Debug erlaubt HTTP über VPN.
- Update-URLs werden gegen **Same-Origin** zum Gateway validiert; Herkunft/
  Integrität über Androids APK-Signaturprüfung (dokumentiert).
- Schreibende Aktionen einheitlich bestätigt + optionale **Biometrie/PIN**-
  Zusatzschicht.

### Robustheit
- Auth-Interceptor ohne `runBlocking` (In-Memory-`SessionState`).
- Ein `401` löscht nicht mehr den kompletten Pairing-Zustand, sondern
  signalisiert nur eine abgelaufene Sitzung.

### Qualität
- **Öffentlicher Demo-Modus** (offline, statische Beispieldaten).
- **Unit-/Integrationstests** (JVM): CertUtils, UpdateUrl, Extensions,
  QR-Parsing, OpsRepository (inkl. Demo).
- **CI**: Tests + Lint + Debug-Build; kein irreführendes Debug-Release mehr,
  minimale Rechte (`contents: read`).
- **Doku**: README neu, `docs/ARCHITECTURE.md`, `docs/SECURITY.md`,
  `docs/API_CONTRACT.md`.
