# Changelog

Format angelehnt an [Keep a Changelog](https://keepachangelog.com/), Versionsschema siehe
[README.md "Branches & Versionierung"](README.md#branches--versionierung): `master` führt
Zwischenversionen `0.0.x`, ein Release auf `main` erhält `0.x.0`.

## [Unreleased]

Noch nichts nach `main` released.

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
