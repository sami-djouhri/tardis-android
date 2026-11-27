# Changelog

## Pflege (2026-11-27, 2.0.36)

Neue Werkzeugkette unter der Haube: AGP 9.1, Kotlin 2.4 und Navigation 2.10.
An der Bedienung ändert sich nichts.

## Pflege (2026-11-26, 2.0.35)

Bibliotheken auf neuem Stand: DataStore 1.3, Bouncy Castle 1.86 (Kopplung
und Gerätezertifikat). Ablehnungstexte unter Freigaben nennen
keine Werkzeugnamen mehr. Sonst ändert sich nichts an der Bedienung.

## Freigaben ausgebaut (2026-11-21, 2.0.32)

Oben stehen jetzt Zähler: wie viele Deploys warten, wie viele Befehle offen
sind, was gerade läuft und was gescheitert ist.

Befehle können auch auf node1 laufen (Marke „node1“ an der Karte). Je Wirt
steht da, ob sein Läufer eingerichtet ist und wann er zuletzt nachgesehen hat.
Skripte mit Probelauf haben den Knopf „Probelauf“: das Skript zeigt nur, was es
tun würde, und ändert nichts. Während ein Befehl läuft, steht die Ausgabe
live in der Karte, mit „Abbrechen“ (auch das per Fingerabdruck). Ist ein Lauf
gescheitert, ist die Ausgabe gleich offen.

Deploys und Befehle lassen sich ablehnen, mit einem Grund. Dann läuft nichts,
das Deploy wartet nicht mehr bzw. der Befehl steht als abgelehnt da, und
der Grund wird zur Aufgabe. Ein neuer
Stand wartet wieder. Deploy-Karten zeigen, wie lange sie schon warten (nach
24 h gelb).

Unten gibt es einen Verlauf: was freigegeben, abgelehnt oder abgebrochen wurde
und wie jeder Lauf ausging, mit Ausgabe zum Aufklappen.

Benachrichtigungen kommen jetzt auch für neue Befehle und für fertige Läufe
(gelungen oder gescheitert), und einmal als Erinnerung, wenn ein Deploy länger
als 24 h wartet.

## Blau, Befehle unter Freigaben, lesbare Logs (2026-11-21, 2.0.31)

Die App ist jetzt fest blau. Die Farbwahl in den Einstellungen ist weg, der
Schalter „Effekte“ bleibt. Fehler sind wieder rot statt pink.

Unter Freigaben gibt es den Abschnitt „Befehle“. Fertige Handgriffe liegen
dort als Skript. Du siehst den ganzen Text, und erst danach lässt sich
„Als root ausführen“ drücken. Nach dem Fingerabdruck führt host genau diesen
Text aus und zeigt Ergebnis und Ausgabe. Hat ein Schritt unter „Wartet auf
dich“ so ein Skript, steht dort ein Hinweis mit Sprung zu den Freigaben. Das
gilt erst, wenn der Läufer auf host einmal eingerichtet ist (einmaliger
Handgriff, die App nennt den Befehl).

Die Inbox hat neue Knöpfe: „Erledigt“ als Hauptknopf, „Später“ mit Wahl
(1 Stunde, 3 Stunden, morgen früh, eine Woche), Archiv und Quelle als Symbole.
Nach jeder Aktion kommt unten „Rückgängig“.

Die Verlaufslinien bei CPU, Speicher, Platte und Load tragen jetzt Uhrzeiten
mit feinen Hilfslinien. Antippen oder Ziehen zeigt Uhrzeit und Wert an der
Stelle. Das gilt für die Wirte und für den Verbrauch eines Dienstes.

Logs stehen jetzt als Zeilen: Fehler rot, Warnungen gelb, Debug leise. Es gibt
Filter (Alle, Warnungen, Fehler mit Zahl), eine Suche mit Markierung und einen
Schalter für den Zeilenumbruch. Gleiche Zeilen hintereinander erscheinen einmal
mit Zähler. Eine Vollbild-Ansicht ist dazugekommen.

## Topologie wie im Web: Netz, Gedankenspiel, Zeitpunkt (2026-11-21, 2.0.31)

Die Topologie zeigt jetzt als Vorgabe ein Netz wie der Graph in Obsidian:
jeder Wirt ein Ring, seine Dienste als Wolke darum, größere Punkte für Dienste
mit vielen Verbindungen. Beim Hineinzoomen erscheinen die Namen. „Ansicht“
schaltet zurück auf das bisherige Raster nach Wirten.

Im Blatt eines Dienstes oder Wirts gibt es „Ausfall durchspielen“. Die Karte
zeigt dann rot, was ausfällt (bei einem Wirt alles darauf), und orange, was
deshalb mit ausfällt. Oben steht die Liste der Folgen, ein Antippen springt
zum Dienst.

„Zeit“ zeigt den Stand von vor einer Stunde oder einem Tag: welche Dienste
damals liefen, ungesund waren oder gegen das Soll fehlten. Die Linien bleiben
beim heutigen Stand. Im Menü „Linien“ gibt es zusätzlich „Nur Abhängigkeiten“
und „Alle zeigen“, bei „Farbe“ die Wahl „Wirt“.

## Farbe wählen, Nebel und Glut (2026-11-21, 2.0.30)

In den Einstellungen gibt es die Karte „Aussehen“. Dort lässt sich die Farbe
der App wählen: Rot (wie bisher), Ultraviolett, Absinth oder Orchidee. Die
Farbe trägt Titel, Knöpfe, die Eckwinkel der Karten und den aktiven Eintrag der
Leiste. Grün, Gelb und Rot bleiben bei jeder Wahl die Ampel.

Dazu kommen Effekte: ein langsam ziehender Nebel hinter allem, feines Korn,
ein Glitch über dem Kopf, wenn während des Ansehens ein Alarm neu dazukommt,
und ein atmender Lichthof um ausgefallene Dienste und den Zähler kritischer
Alarme. Beim Seitenwechsel läuft eine Abtastlinie über die Seite. Der Schalter
„Effekte“ stellt alles ab; sind am Telefon die Animationen aus, steht ohnehin
alles still.

## Bezug zum Haus, Quellen, Alarm-ID überall (2026-11-20, 2.0.28)

Sicherheitsmeldungen in der Inbox sagen jetzt, ob die genannte Technik bei dir
läuft, zum Beispiel „Im Haus: Java auf gamehost (mc-velocity …)“, in Gelb. Läuft
sie nicht, steht dort „kein Treffer“. Ein Treffer heißt „läuft hier“, nicht
„verwundbar“.

Unter dem Titel stehen Thema und Quellen, etwa „Sicherheitslücke · 3 Quellen:
Heise, Golem, ZEIT“. Bei mehreren Quellen bietet „Mehr“ alle zur Auswahl an.

Die Alarm-ID lässt sich jetzt auch auf der Startseite kopieren: Alarmzeile
gedrückt halten. Das Widget zeigt die ID vor dem wichtigsten Alarm.

## Alarm-ID kopieren (2026-11-19, 2.0.27)

Einen Alarm gedrückt halten kopiert seine ID in die Zwischenablage. Es ist
dieselbe ID wie in der ntfy-Meldung und im Tardis-Web, dort per Rechtsklick.

## Inbox mit Quelle (2026-11-19, 2.0.26)

Jede Inbox-Karte mit einer Quelle bekommt den Knopf „Mehr". Bei Nachrichten
öffnet er den Artikel im Browser, bei Briefen die Textansicht. Vorher ging der
Link auf dem Weg in die Inbox verloren, die Karte zeigte nur die Kurzfassung.

## Sicherung, Sprach-host, Handgriffe, ohne Netz (2026-11-17, 2.0.25)

Oben rechts gibt es ein Mikrofon: antippen, den runden Knopf gedrückt halten,
sprechen, loslassen. host antwortet als Text und mit der Haus-Stimme, wie am
Lautsprecher. Erkennung und Stimme laufen im Haus, die Aufnahme wird nach dem
Senden gelöscht. Beim ersten Mal fragt die App nach dem Mikrofon.

Neu auf der Startseite: „Wartet auf dich" zeigt, was heute nur du tun kannst,
mit den Befehlen zum Kopieren. „Gemacht" meldet einen Schritt zurück; er bleibt
stehen, bis die Gegenprobe grün ist.

Neu ist auch „Sicherung": je Wirt, wie alt die letzte Sicherung ist und ob die
letzte Rückspielprobe gelungen ist, dazu Befunde wie ein Laptop, der seit Wochen
nicht gesichert hat.

Ohne Verbindung zeigt die App den letzten Stand von Start, Alarmen, Wirten und
Sicherung, mit einem Band „Ohne Verbindung" und der Uhrzeit dieses Standes.
Beim Öffnen steht der letzte Stand sofort da, statt „Lade Status…". Steuern
geht weiterhin nur mit Verbindung. Der Stand liegt verschlüsselt auf dem
Telefon und kommt in keine Gerätesicherung.

## Notknopf und Sperre (2026-11-15, 2.0.24)

Neu: der Notknopf. Oben auf der Startseite steht „Alle Tresore schließen“, ein
Druck genügt, ohne Rückfrage. Er schließt Filme, Musik, Privat, Briefkasten und
Medien und zeigt danach für jeden Tresor, ob er zu ist. Öffnen geht hier nicht.
Denselben Knopf gibt es als Schnelleinstellungs-Kachel „Tresore zu“: über die
Kachelbearbeitung der Systemleiste hinzufügen. Sie meldet das Ergebnis als
Benachrichtigung, auch wenn die Leiste gleich wieder zu ist.

Die App fragt jetzt bei jedem Start und nach 5 Minuten im Hintergrund nach
Fingerabdruck oder Geräte-PIN. Der Notknopf steht auch auf dem Sperrbildschirm
und geht ohne Entsperren.

Jeder Alarm trägt eine kurze Kennung wie A-7K2Q. Dieselbe steht im Titel der
ntfy-Meldung und in Tardis-Web; ein Tipp auf die ntfy-Meldung öffnet Tardis-Web
direkt bei diesem Alarm.

## Terminal-Look (2026-11-15, 2.0.23)

Neuer Look: dunkles Terminal in Dunkelrot, alle Schriften in JetBrains Mono,
eckige Flächen, feine Scanlines und ab und zu ein leichtes Flimmern. Rot ist
jetzt die Farbe für Knöpfe und Hervorhebungen; Fehler und Ausfälle stehen
deshalb in Pink, damit man sie nicht mit einem Knopf verwechselt. Wer am Telefon
die Animationen abschaltet, bekommt kein Flimmern. Das Widget trägt dieselben
Farben.

## Bildschirmschutz (2026-11-14, 2.0.22)

Die App lässt keine Bildschirmfotos und keine Aufnahme durch andere Apps mehr
zu, und in der App-Übersicht steht nur eine leere Vorschau. Hostnamen, Logs und
Freigaben gehören in keine Bildschirmaufnahme. Auch du selbst kannst in der App
kein Bildschirmfoto mehr machen.

## Freigaben für Pull-Deploy (2026-11-14, 2.0.21)

Neu: Freigaben für Deploys. Oben rechts zeigt ein Schild, wie viele Deploys auf
deine Freigabe warten. Jeder Eintrag nennt Projekt und Wirt, die Commits, den
Diff und eine Ampel: grün nur Inhalt und Code, gelb Abhängigkeiten oder Compose-
Grenzen, rot Rechte, Netz, Image oder der erste Stand. „Freigeben“ unterschreibt
mit einem eigenen Schlüssel, den das Telefon nur nach Fingerabdruck oder PIN
benutzt, für jede Freigabe einzeln. Der Wirt prüft die Unterschrift selbst und
holt den Stand binnen einer Minute; scheitert er, rollt er zurück, und die App
zeigt das. Alle 15 Minuten sieht die App im Hintergrund nach und meldet neue
Freigaben als Benachrichtigung.

## Topologie: Ausfall-Kette, Linsen, Wirt-Blatt, Änderungen (2026-11-08, 2.0.20)

Ein Dienst zeigt jetzt seine Kette: was er braucht und wer mit ausfällt, auch
über mehrere Stufen; auf der Karte nach oben in Akzentfarbe, nach unten orange.
Dienste, die laufen, aber etwas brauchen, das aus ist, tragen einen orangen
Ring und stehen oben als „mitbetroffen“. „Farbe“ schaltet die Karte auf
Erreichbarkeit (öffentlich, eigene Geräte, nur lokal) oder Klasse (CORE, OPS,
PUBLIC, INTERNAL) um, mit Legende; kaputte Dienste behalten dabei einen roten
Ring. „Linien“ blendet Verbindungsarten ein und aus. Ein Wirt-Kopf öffnet sein
Blatt: online, IP, Last, Temperatur, Load, Container, Backup-Alter, Drift, mit
Sprung zum Verlauf. Die Dienstkarte nennt Klasse, Erreichbarkeit und Port und
öffnet die Web-Oberfläche im Browser. Offen frischt sich die Karte jede Minute
auf; was sich seit dem letzten Öffnen geändert hat, pulsiert und steht in einem
Hinweis oben, antippen zeigt die Liste.

### Was am Code anders ist

- mobile-api `deec4dd`: Wirt-Felder (ip, temp, load, backup_alter_h,
  backup_alt, drift_geister/_fehlend, container), Dienst-Felder
  (erreichbarkeit in drei Stufen, port, url), Verbindungs-Label.
- `Topologie.kt`: `abhaengigkeitsKette` (depends zeigt vom Abhängigen auf das
  Gebrauchte, Zyklen sicher), `mitBetroffen`, `Linse`/`linsenWert`,
  `aenderungen` gegen `TopoSchnappschuss`, `zeitpunktText`.
- `TopologieGedaechtnis`: letzter gesehener Stand in SharedPreferences, im
  Demo-Modus weder gelesen noch geschrieben. Basis bleibt je Sitzung fest.
- Leises Auffrischen nur im Vordergrund (`repeatOnLifecycle(RESUMED)`), bei
  Fehler bleibt die Karte stehen, Hinweis „Auffrischen fehlgeschlagen“.
- Ist ein Dienst gewählt, dimmen Suche und Filter seine Kette nicht.
- Tests: `TopologieKetteTest` (7), `TopologieKarteTest` (+5).

## Topologie: Suche, Wirt-Köpfe, Gruppen (2026-11-08, 2.0.19)

Über der Karte ein Suchfeld (Dienst, Wirt, Gruppe oder Container): die Treffer
stehen als Liste über der Karte, Antippen springt zum Dienst und öffnet ihn.
„Nur Auffälliges“ zeigt dieselbe Liste mit den roten und gelben Diensten, rot
zuerst. Jeder Wirt trägt im Kopf einen Punkt (grün online, rot offline, Ring
unbekannt), die Zahl seiner Dienste, wie viele auffällig sind, und CPU/RAM, wo
Platz ist; Antippen des Kopfs zoomt auf den Wirt. Gruppennamen stehen jetzt
immer da, mit Dienstzahl. Zurück schließt einen gewählten Dienst.

### Was am Code anders ist

- `sucheDienste`, `auffaelligeDienste`, `wirtKopf` in `Topologie.kt`, mit
  Unit-Tests; Gruppenüberschrift „Name · Zahl“.
- Wirt-Kopf wird vorab gemessen; reicht die Breite nicht, fällt zuerst die
  Last weg, der Name wird gekürzt statt über die Zahlen zu laufen.
- `TopologieKarteTest`: Suche mit Antippen, Auffälliges-Liste, Wirt-Kopf.
- Demo-Daten: Last an zwei Wirten, `storage-box` offline.

## Topologie stürzt nicht mehr ab (2026-11-08, 2.0.18)

Die Topologie hat die App beim Öffnen beendet, sobald die Karte höher war als
der Bildschirm, also mit den echten rund 260 Diensten immer. Behoben. Dazu:
Dienstnamen beim Hineinzoomen auf Punktbreite gekürzt („…“) statt über den
Nachbarn geschrieben, die Karte bleibt beim Verschieben im Bild und malt nicht
mehr über die Filterknöpfe, „Erneut versuchen“ zeigt wieder den Ladezustand.

### Was am Code anders ist

- Ursache: `drawText(messer, text, topLeft)` leitet seine Grenzen aus der
  Canvas in Pixeln minus Startpunkt ab, der Startpunkt stand in
  Kartenkoordinaten. Unterhalb der Canvashöhe wurde die Höhe negativ,
  `Constraints` warf `IllegalArgumentException`.
- Beschriftungen werden einmal gemessen (`remember`) und als
  `TextLayoutResult` gezeichnet; kein Neu-Messen je Zoom-Frame mehr.
- Layout auf `Dispatchers.Default`, `clipToBounds`, Verschieben begrenzt.
- Test `TopologieKarteTest`: Größe wie live (erfundene Namen) auf
  360x740 dp, mit Zoom und Wischen. Nur im Aufnahmelauf
  (`recordRoborazziDebug`) wird wirklich gezeichnet; ein normaler
  `testDebugUnitTest` war mit demselben Fehler grün.

## Alle Wirte mit Verlauf (2026-11-08, 2.0.17)

Neue Karte „Wirte“ auf der Startseite statt des Momentwerts von host: alle
Wirte untereinander, je mit CPU-Linie der letzten Stunde und CPU, RAM und
Platte in Prozent; die Farbe folgt dem knappsten Wert, ein stummer Wirt heißt
„nicht erreichbar“. Antippen öffnet die Unterseite mit 1, 6 oder 24 Stunden
und vier Linien je Wirt (CPU, Speicher, Platte, Load bis zur Kernzahl).
Prozent steht immer auf 0 bis 100, damit sich Wirte vergleichen lassen.
LXC-Gäste tragen „Gast auf <Node>“: sie sehen CPU und Load ihres Nodes.

### Was am Code anders ist

- Quelle `GET /v1/ops/wirte?fenster=` (mobile-api) auf Tardis
  `/api/metrics/wirte`: je node-exporter-Job feste PromQL-Ausdrücke, ein Abruf
  für alle Wirte, Reihen auf gemeinsamem Raster, Lücke = `null`.
- `data/model/tardis/Wirte.kt`, `ui/wirte/WirteScreen.kt` (Unterseite und
  `WirteKarte` der Startseite), Demo-Daten mit jeder Färbung.
- `ui/components/Verlaufslinie.kt` ersetzt die private Linie der
  Verfügbarkeit: setzt an Lücken ab und kennt eine feste Skala.
- Fehlt die Wirte-Antwort, zeigt die Startseite weiter „host jetzt“.
- Tests: `WirteTest`, Bildschirmfoto `14-wirte`.

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
