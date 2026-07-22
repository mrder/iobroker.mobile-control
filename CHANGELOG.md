# Changelog

Format angelehnt an [Keep a Changelog](https://keepachangelog.com/), Versionsschema siehe
[README.md "Branches & Versionierung"](README.md#branches--versionierung): `master` führt
Zwischenversionen `0.0.x`, ein Release auf `main` erhält `0.x.0`.

## [Unreleased]

Noch nichts nach `main` released.

## [0.0.16] - master, Testbuild

**Feature-Wunsch:** Der Objektbrowser in der Android-App (Hauptnavigation "Objekte") war eine
flache, unstrukturierte Liste aller freigegebenen Datenpunkte - auf einem Gerät mit vielen
Objekten mühsam zu durchsuchen. Baut jetzt denselben Ansatz wie der Freigabe-Baum im Admin-Tab
([0.0.12]): ein echter, aufklappbarer Ordnerbaum, gruppiert nach `path` (rein clientseitig
synthetisiert, da die App - anders als der Admin-Tab - nie echte Channel-/Device-Objekte sieht,
nur den bereits gefilterten flachen Katalog).

- Neues `ObjectTreeNode`-Modell + `buildObjectTree()`/`visibleLeafIds()`
  ([ObjectTreeNode.kt](android-app/app/src/main/java/com/mobilecontrol/app/domain/model/ObjectTreeNode.kt))
- Ohne aktiven Suchbegriff/Filter: aufklappbarer Ordnerbaum (Standardansicht). Mit Suchbegriff
  oder Filter (Raum/Rolle/nur schreibbar): bisherige flache, durchsuchbare Liste bleibt erhalten
- Nur Objekte in tatsächlich aufgeklappten Ordnern werden live abonniert (`visibleLeafIds`) -
  vermeidet, Hunderte verborgener States gleichzeitig zu abonnieren
- Neue Tests: `ObjectTreeNodeTest.kt`, Erweiterungen in `ObjectBrowserViewModelTest.kt`
- **Erfordert einen neuen Android-Build.**

## [0.0.15] - master, Testbuild

**Sicherheits-Härtung**, ausgelöst durch eine berechtigte Live-Nachfrage: "ist das Pairing sicher,
gibt es eine Warnung oder ein Blacklisting bei wiederholten Fehlversuchen?" Die ehrliche Antwort
war vorher: Kryptografie (256-Bit-Pairing-Secrets, EC-Signaturen) plus Admin-Freigabepflicht
schützen bereits zuverlässig vor echtem Zugriff, aber es gab weder eine aktive Warnung noch eine
eskalierende Sperre bei Dauerbeschuss - nur ein nicht-eskalierendes 60-Sekunden-Rate-Limit pro IP.

- Neuer `AbuseGuard` ([src/security/abuseGuard.ts](src/security/abuseGuard.ts)): zählt
  Fehlversuche pro IP über ein 5-Minuten-Fenster; ab einem konfigurierbaren Schwellwert (Standard
  10) wird die IP komplett blockiert (Standard 30 Minuten), nicht nur gedrosselt
- Greift auf `/pairing/claim`, `/auth/challenge`, `/auth/login`, `/auth/refresh` - zusätzlich zum
  bisherigen `RateLimiter`, der reines Anfragevolumen unabhängig vom Ausgang drosselt
- Eine neue Sperre erzeugt jetzt eine sichtbare `adapter.log.warn(...)`-Zeile im normalen
  Adapter-Log, nicht nur einen Eintrag im (bisher nur manuell einsehbaren) Audit-Log
- Ein Erfolg (bewiesen durch Secret/Signatur/gültigen Refresh-Token) löscht die
  Fehlversuchs-Historie der IP wieder - `/auth/challenge` bewusst ausgenommen, da ein Erfolg dort
  keine echte Identität beweist und Geräte-ID-Enumeration sonst den Zähler kostenlos zurücksetzen
  könnte
- Beide Schwellwerte konfigurierbar in den Instanz-Einstellungen (`abuseBlockThreshold`,
  `abuseBlockMinutes`)
- Neue Tests: `test/abuseGuard.test.ts` (isolierte Logik) sowie ein echter Ende-zu-Ende-Schritt
  in `test/integration/adapterStartup.ts`, der über die reale HTTP-Schnittstelle eine Sperre
  auslöst und verifiziert

## [0.0.14] - master, Testbuild

**Zwei echte Android-Bugs, live direkt nach 0.0.13 gefunden** (Nutzer bemängelte zu Recht, dass
ein abgelaufenes Pairing als rohes "HTTP 410" statt einer verständlichen Meldung angezeigt wurde):

1. `ApiErrorCode.kt` fehlten 7 der 19 Backend-Fehlercodes komplett (`PAIRING_INVALID`,
   `PAIRING_EXPIRED`, `CHALLENGE_INVALID`, `SIGNATURE_INVALID`, `NOT_FOUND`, `VALIDATION_ERROR`,
   `REPLAY_DETECTED`) - die fielen alle stillschweigend auf `UNKNOWN` zurück.
2. **Deutlich größerer Fund dabei**: `ErrorEnvelopeDto.kt` erwartete die Fehler-Antwort als
   verschachteltes `{"error": {"code": "...", "message": "..."}}`, das Backend schickt aber
   tatsächlich ein flaches `{"error": "CODE_STRING", "message": "..."}` (siehe
   `ApiError.toBody()` in `src/lib/errors.ts`). Durch diesen Typ-Mismatch ist das Deserialisieren
   des Fehler-Bodys seit der ursprünglichen Android-Implementierung bei **jeder einzelnen**
   Fehlerantwort des Servers stillschweigend fehlgeschlagen - die App hat den Fehlercode nie aus
   der Antwort gelesen, sondern immer nur grob aus dem rohen HTTP-Status geraten (und für Codes
   wie 400/410/428, die vorher gar nicht in dieser Rate-Fallback-Tabelle standen, landete das
   sogar direkt bei `UNKNOWN`).

Beide behoben: `ErrorEnvelopeDto` liest jetzt die echte Form, `mapHttpError` deckt alle Status ab,
und das Pairing zeigt jetzt echte deutsche Meldungen ("Die Kopplungsanfrage ist abgelaufen. Bitte
scanne den QR-Code erneut." usw. statt "HTTP 410"). Neue Tests: `ApiCallExecutorTest.kt`
(reale Backend-Fehlerform, Status-Fallback, unbekannter Code). **Erfordert einen neuen
Android-Build.**

## [0.0.13] - master, Testbuild

**Echter Bug, live beim Onboarding gefunden:** Die App zeigte bei jedem einzelnen Pairing-Versuch
"Achtung: der Server-Fingerabdruck stimmt nicht mit dem erwarteten Wert überein" - unabhängig vom
Netzwerk oder Reverse-Proxy-Zustand. Ursache: `ServerFingerprintChecker.kt` verglich den
`serverFingerprint` aus dem QR-Code gegen den SPKI-SHA256-Hash des **echten TLS-Zertifikats** aus
einem Live-Handshake. Dieser Adapter terminiert aber selbst kein TLS (ein VPN/Reverse-Proxy wird
davor erwartet, siehe `docs/MASTERKONZEPT.md` §4) - der `serverFingerprint`, den der Adapter in
jede QR-Einladung packt, ist tatsächlich ein Hash des adapter-eigenen JWT-Signierschlüssels, kein
Zertifikats-Hash. Zwei grundverschiedene Werte im selben String-Format (`sha256/BASE64`), die
niemals übereinstimmen konnten - weder über einfaches lokales HTTP (gar kein TLS-Handshake) noch
über einen HTTPS-Reverse-Proxy (dessen Zertifikat der Adapter gar nicht kennt). Das war kein
Netzwerkproblem, sondern ein Vertrags-Bug zwischen Backend und App seit der ursprünglichen
Android-Implementierung.

- Neuer, unauthentifizierter Endpunkt `GET /api/v1/server/info` liefert denselben Fingerprint,
  den auch jede QR-Einladung enthält
- `ServerFingerprintChecker.kt` holt sich diesen Wert jetzt live von dort und vergleicht direkt
  (kein TLS-Handshake mehr nötig) - ein Mismatch bedeutet weiterhin zuverlässig "das ist nicht der
  Server, der diesen QR-Code ausgestellt hat" (z.B. veralteter QR-Code nach einer
  Secret-Rotation), nur eben ohne echtes Zertifikats-Pinning vorzutäuschen
- `docs/openapi.yaml`, `docs/API-VERTRAG.md` und `android-app/README.md` entsprechend
  aktualisiert; neue Tests (`test/pairing.test.ts`, `test/integration/adapterStartup.ts`)
- **Erfordert einen neuen Android-Build** (APK aus der CI ziehen und auf dem Testgerät neu
  installieren) - der bisherige Fingerprint-Check bleibt sonst bestehen

## [0.0.12] - master, Testbuild

**Feature-Wunsch aus dem Livetest** (nicht mehr in "immer wieder ein neuer Bug"-Kategorie, sondern
echtes Feedback nach dem ersten funktionierenden Durchklicken): Der Objektbaum unter
"Objektfreigaben" war eine flache, auf 200 Treffer begrenzte Liste ohne jede Ordnerstruktur -
`browseObjectTree()` hat bisher nur `type: 'state'`-Objekte geliefert, nie die dazugehörigen
Channel-/Device-/Folder-Objekte mit ihren echten Namen. Außerdem ließ sich nur ein einzelner
Datenpunkt freigeben, obwohl `ExposureService` serverseitig `scope: 'channel'|'device'|'adapter'`
(Präfix-Match gegen alle Datenpunkte darunter) schon immer unterstützt hat - das war nur nie aus
der UI heraus erreichbar, weil `ExposureTab.tsx` beim Anlegen einer Regel `scope: 'state'` fest
verdrahtet hatte.

- `browseObjectTree()` liefert jetzt zusätzlich alle `channel`/`device`/`folder`/`adapter`/
  `instance`-Objekte, mit `kind: 'state' | 'container'` unterschieden
- Admin-Tab baut daraus einen echten, aufklappbaren Ordnerbaum (Standardansicht, wenn das
  Suchfeld leer ist); bei eingegebenem Suchbegriff bleibt die bisherige flache, durchsuchbare
  Liste erhalten
- "Freigeben" funktioniert jetzt auf jeder Ebene - auf einem Ordner erzeugt es automatisch eine
  `channel`-Scope-Regel, die alle Datenpunkte darunter abdeckt
- Neuer Test (`test/exposureTree.test.ts`) für die state/container-Unterscheidung und die
  Präfix-Match-Semantik einer Ordner-Regel

## [0.0.11] - master, Testbuild

**Echter Folgebug, live direkt nach dem socket.io-Fix gefunden:** Der Tab verbindet sich jetzt
sauber (kein Fehler-Popup mehr), aber jede Aktion darin - Rollen/Benutzer anlegen oder auch nur
auflisten, den Objektbaum für Freigaben durchsuchen, Sessions, Audit - tat scheinbar gar nichts,
ohne jede Fehlermeldung. Ursache: `io-package.json` hat nie `common.supportedMessages.custom`
deklariert (den modernen Ersatz für das veraltete `common.messagebox`-Flag). Ohne dieses Flag legt
js-controller für die Instanz gar keine Messagebox an und hat keinen Grund, auch nur einen der
`sendTo()`-Aufrufe des Admin-Tabs (`callAdapter(...)` in `admin/tab-src/src/connection.ts`, von
praktisch jedem Tab benutzt) an den Adapter zuzustellen - jeder einzelne wurde verworfen, bevor er
unseren `onMessage`-Handler in `main.ts` je erreichte. Bestätigt anhand von `@iobroker/types`s
`SupportedMessages`-Interface und dem echten, funktionierenden Custom-Tab von
`ioBroker.javascript` (kommuniziert genau wie unserer über `sendTo` und deklariert dasselbe Flag).
Jetzt in `io-package.json` ergänzt: `"supportedMessages": { "custom": true }`.

## [0.0.10] - master, Testbuild

**Echter Folgebug, live direkt nach dem adminTab-Fix gefunden:** Der Tab erschien nach [0.0.9]
korrekt in der Seitenleiste, aber beim Öffnen kam sofort ein Fehler-Popup: "Socket connection
could not be initialized: Error: Socket library could not be loaded!". Ursache: `Connection` aus
`@iobroker/adapter-react-v5` (bzw. `@iobroker/socket-client`) erwartet, dass die
socket.io-Client-Bibliothek bereits als globale Variable `window.io` vorhanden ist, wenn sie
konstruiert wird - sie lädt die Bibliothek selbst **nicht**, sondern wartet nur bis zu 3 Sekunden
darauf und wirft dann exakt diesen Fehler. Unser `tab.html` hat `socket.io.js` nie geladen. Fix:
ein einfaches `<script src="../../lib/js/socket.io.js">` (kein `type="module"`, läuft daher
garantiert vor unserem App-Bundle) vor dem eigentlichen Tab-Bundle in
`admin/tab-src/tab.html` ergänzt - derselbe relative Pfad, den auch der echte, funktionierende
Admin-Tab von `ioBroker.javascript` benutzt, und der auf den von js-controller für jeden Adapter
bereitgestellten gemeinsamen statischen Pfad zeigt.

## [0.0.9] - master, Testbuild

**Den echten Grund für den fehlenden Admin-Tab gefunden** (0.0.8 hat das Problem nicht vollständig
gelöst, wie der Nutzer live bestätigt hat: `adminUI.tab: "html"` war korrekt installiert,
`iobroker upload mobile-control` lief erfolgreich durch, aber der Tab tauchte weiterhin nicht in
der Seitenleiste auf). Ursache: `adminUI.tab` bestimmt nur, *wie* ein Tab gerendert wird (html vs.
json vs. materialize) - der eigentliche Seitenleisten-Eintrag wird über ein komplett separates
Feld `common.adminTab` registriert (siehe `@iobroker/types`s `AdapterCommon`-Interface), das in
unserer `io-package.json` schlicht nie existiert hat. Bestätigt durch Vergleich mit zwei echten,
funktionierenden Custom-Tab-Adaptern (`ioBroker.devices`, `ioBroker.javascript`), die beide ein
`adminTab`-Objekt (`singleton`, `name`) deklarieren, genau wie jetzt bei uns. Da unser Tab bereits
unter dem von `adminTab` erwarteten Standardpfad `admin/tab.html` liegt, war kein zusätzliches
`link`-Feld nötig.

## [0.0.8] - master, Testbuild

**Weiterer echter Bug, live beim Nutzer gefunden:** Der eigene Admin-Tab tauchte in der
ioBroker-Admin-Seitenleiste überhaupt nicht auf. Ursache: `io-package.json`s `adminUI.tab` stand
auf `"custom"` - laut echtem ioBroker-Schema (`@iobroker/types`) sind dort aber nur
`'html' | 'json' | 'materialize'` gültig. Mit dem ungültigen Wert registrierte die Admin-UI den
Tab still schweigend gar nicht erst. Das war seit Projektbeginn so und wurde nie bemerkt, weil
zuvor nur die gebauten `admin/tab.html`/`tab-assets`-Dateien direkt geprüft wurden, nie eine echte
ioBroker-Installation. Auf `"tab": "html"` korrigiert (unser Tab ist eine reine HTML-Datei, die
den React-Bundle lädt).

Außerdem: `@iobroker/testing`s offizieller Package-File-Validator (`validatePackageFiles`) lief
bisher gar nicht mit - jetzt als `test/packageFiles.test.ts` eingebunden (prüft u.a.
`package.json`/`io-package.json`-Konsistenz, gültige `adminUI.config`, Lizenzfelder; hätte den
`adminUI.tab`-Fehler selbst nicht gefangen, das ist trotzdem eine echte Lücke in der bisherigen
Testabdeckung).

## [0.0.7] - master, Testbuild

Nach dem ws-Fund in [0.0.6] gezielt nach derselben Fehlerklasse gesucht: "Fire-and-forget"
Async-Aufrufe (`void asyncFn()`), deren Ablehnung nirgendwo abgefangen wird und damit potenziell
den ganzen Adapter-Prozess crashen könnte (unhandled promise rejection), genau wie beim
ws-Bug. Acht Stellen gefunden und mit `.catch(...)` abgesichert (Session-/Geräte-Bookkeeping,
Kommando-Bestätigung/-Timeout, Status-States, Session-Widerruf, WebSocket-Nachrichtenverarbeitung).
Eine Stelle (Admin-Message-Handler) war bereits durch ein eigenes vollständiges try/catch sicher.

## [0.0.6] - master, Testbuild

**Echte Ursache des EADDRINUSE-Absturzes gefunden und behoben** (siehe [0.0.2] bis [0.0.5] für die
Fehlersuche): Die `ws`-Bibliothek registriert beim Erstellen von `new WebSocketServer({ server })`
selbst einen `'error'`-Listener auf dem übergebenen `http.Server` und reicht dessen Fehler an sich
selbst weiter (`wss.emit('error', err)`). Node.js wirft ein `'error'`-Event **synchron** als echte
ungefangene Exception, wenn dafür kein Listener registriert ist - da `RealtimeGateway` nie auf
`wss`s eigenes `'error'`-Event gehört hat, crashte **jeder** Listen-Fehler (nicht nur EADDRINUSE)
den ganzen Adapter-Prozess sofort, bevor die eigene Retry-/Port-Scan-/Fehlerbehandlung in
`main.ts` (aus den vorherigen Versionen) überhaupt eine Chance hatte zu laufen. Das war ein
latenter Bug seit Projektbeginn, der erst beim ersten echten Livetest gegen einen belegten Port
sichtbar wurde.

- `RealtimeGateway` hört jetzt auf `wss`s `'error'`-Event (loggt es nur, die eigentliche Behandlung
  bleibt in `main.ts`s `server.once('error', ...)`)
- Neuer Regressionstest (`test/realtime.test.ts`), der einen echten Port-Konflikt erzeugt und
  verifiziert, dass der Prozess dabei nicht abstürzt
- Temporäres, ausschließlich der Fehlersuche dienendes Diagnose-Logging aus [0.0.4]/[0.0.5] wieder
  entfernt

## [0.0.5] - master, Testbuild

Weitere temporäre Diagnose-Kontrollpunkte (`[diag] 1/6` bis `[diag] 6/6`) durch `onReady()` und
`startHttpServer()`, um beim Live-Test genau einzugrenzen, bis wohin der Code beim EADDRINUSE-Absturz
tatsächlich kommt - die vorherige Diagnose in `listenWithRetry` wurde auf der echten Installation nie
erreicht.

## [0.0.4] - master, Testbuild

Zwischenstand während der Live-Test-Fehlersuche (EADDRINUSE-Handling greift auf der echten
Installation nicht, Ursache noch offen):

- Temporäres, unbedingtes Diagnose-Logging in `listenWithRetry`/`onReady`, um zu sehen ob der
  neue Fehlerbehandlungscode zur Laufzeit überhaupt erreicht wird
- Dabei einen echten Regressions-Bug in der Diagnose selbst gefunden und gefixt: der
  `'ready'`-Event-Handler gab das `onReady()`-Promise nicht mehr zurück, wodurch der
  Integrationstest den Server manchmal noch nicht bereit vorfand (`ECONNREFUSED`)

## [0.0.3] - master, Testbuild

Gefunden beim ersten echten Livetest gegen eine reale ioBroker-Instanz:

- `package.json`s `files`-Liste schloss `src/` und beide `tsconfig*.json` aus. npm packt
  Git-Installs nach dem `prepare`-Skript gemäß dieser Liste - der allererste Install baute deshalb
  noch erfolgreich (voller, ungefilterter Klon), aber jeder spätere manuelle Rebuild **innerhalb**
  des bereits installierten Verzeichnisses schlug mit `tsconfig.build.json not found` fehl. Jetzt
  behoben, `src/`/beide tsconfigs sind Teil der `files`-Liste.

## [0.0.2] - master, Testbuild

### Backend
- Kamera-Snapshot-Endpoint (`GET /api/v1/objects/{id}/snapshot`), liest Data-URL- oder
  http(s)-URL-Kamera-States, proxied das Bild statt direktem Netzzugriff der App
- Rate Limiting auf `/auth/challenge`, `/auth/login`, `/auth/refresh`, `/pairing/claim` (vorher nur
  auf `/commands`) - Brute-Force-Lücke aus einer Konzept-Lückenanalyse
- Audit-Log für einzelne Freigabe-Änderungen und für Refresh-Token-Wiederverwendung
- Verbindungs-Info-Anzeige (lokale IP-Adressen, Port) im Admin-Tab für VPN/Reverse-Proxy-Einrichtung
- Klarere Fehlerbehandlung bei belegtem Port beim Start: automatischer Ausweich-Port-Scan, solange
  noch kein Gerät gekoppelt wurde; danach klare Fehlermeldung statt Absturz mit rohem Stacktrace
- Release-Automatisierung (`.github/workflows/release.yml`) und Versions-Konsistenzprüfung

### Android
- `CommandStatus.BLOCKED` wird jetzt tatsächlich für `LOCAL_ONLY`-Ablehnungen verwendet
- `minSdk` von 34 (Android 14) auf 26 (Android 8.0) gesenkt für Tests auf älterer Hardware
- CI baut bei jedem Push eine installierbare Debug-APK als Artefakt
- 90 JVM-Unit-Tests (vorher 39): neue ViewModel-Tests mit Fake-Repositories

## [0.0.1] - master, Testbuild

Erster vollständiger MVP-Stand auf `master`. Backend und Android-App sind funktional komplett
für die in [docs/TODO.md](docs/TODO.md) als erledigt markierten Punkte; noch kein offizieller
Release.

### Backend
- Adapter-Grundgerüst (TypeScript, State-basierte Persistenz, keine externe DB)
- Benutzer, Rollen, Geräte, Sessions, Audit-Log
- QR-Pairing mit Admin-Bestätigung, EC-P256-Challenge-Response-Auth
- Rotierende Refresh-Tokens mit Wiederverwendungserkennung
- Objektfreigaben (Rollen-/Nutzer-/Geräte-Priorität, `system.*` blockiert) inkl. wiederverwendbarer Freigabeprofile
- Gefilterter Objektkatalog mit Delta-Updates und effektiver Admin-Vorschau
- Aktorsteuerung mit Typ-/Wertebereichsprüfung, Rate-Limit, Replay-Schutz, Confirmation-Policies
- WebSocket-Realtime-Gateway, REST-API nach `docs/openapi.yaml`
- Verlaufs-API über konfigurierbare ioBroker-History-Instanz
- Versioniertes Migrations-Grundgerüst
- Custom React-Admin-Tab + native Einstellungsseite
- 56 Unit-Tests + echter Integrationstest gegen gemockte ioBroker-Umgebung

### Android-App
- QR-Pairing, Keystore-Schlüsselerzeugung, App-Sperre (PIN/Biometrie)
- Objektkatalog-Browser, Dashboards mit echtem Drag & Drop
- 10 Widget-Typen (Text, Temperatur, Feuchte, Status, Schalter, Taster, Slider, Rollladen,
  Thermostat, Verlauf) inkl. zentralem Bestätigungs-Gate für Confirmation-Policies
- Automatischer Command-Retry bei Timeout
- Offline-Cache mit zeitbasierter Bereinigung
- Drei Build-Varianten (debug/staging/release)

### Bekannte Lücken
Siehe [docs/TODO.md](docs/TODO.md) für die vollständige, ehrliche Liste. Wichtigste offene
Punkte: Kamera, Push-Benachrichtigungen, echtes Certificate-Pinning, Android-eigene Tests,
Alarm-Quittierung, Betrieb gegen eine echte (nicht gemockte) ioBroker-Instanz noch nicht verifiziert.
