# GESAMT-TODO

> Stand nach mehreren Umsetzungs-Sessions: Backend-MVP inkl. Freigabeprofilen, Katalog-Vorschau, History-API,
> Katalog-Delta-Updates, OpenAPI-Spezifikation, Migrations-Grundgerüst und Deployment-Doku (Phasen 1–7, 13) sowie
> Android-Kernfunktionen inkl. Drag&Drop, Retry-Regeln und echtem Verlauf-Widget (Phasen 8–14) sind als echter,
> getesteter Code vorhanden: 67 Backend-Unit-Tests plus ein echter Integrationstest, der den tatsächlich kompilierten
> Adapter gegen eine gemockte ioBroker-Umgebung end-to-end über echte HTTP-Requests durchspielt (Pairing → Admin-
> Bestätigung → Token → Katalog → Token-Rotation → Login), sowie 90 Android-JVM-Unit-Tests. CI baut/testet Backend,
> Admin-Tab und Android bei jedem Push und stellt außerdem eine installierbare Debug-APK als Artefakt bereit (siehe
> android-app/README.md). Eine Konzept-vs-Implementierung-Lückenanalyse hat zusätzliche, vorher nicht in dieser Liste
> sichtbare Lücken gefunden (u.a. fehlendes Rate Limiting auf Auth/Pairing - inzwischen gefixt - sowie mehrere
> funktionale Lücken, die jetzt einzeln unten stehen). Nicht abgehakte Punkte sind bewusst offen geblieben (siehe
> README) oder erfordern eine echte ioBroker-/Android-Laufzeitumgebung zur Verifikation, die hier nicht verfügbar war.

## Phase 1 – Grundlagen

- [x] Namen finalisieren
- [x] GitHub-Repositories erstellen (github.com/mrder/iobroker.mobile-control)
- [x] Lizenz festlegen
- [x] SECURITY.md
- [x] API-Versionierung
- [x] Bedrohungsmodell
- [x] Build- und Releaseprozess (CI baut/testet jeden Push; `.github/workflows/release.yml` erstellt bei einem `v*.*.*`-Tag automatisch ein GitHub Release aus CHANGELOG.md, siehe README „Release-Prozess")
- [ ] Testinstanz aufsetzen

## Phase 2 – Backend-Grundgerüst

- [x] offizielles ioBroker-Template
- [x] TypeScript
- [x] React Admin
- [x] Tests
- [x] CI
- [x] Konfigurationsmigration
- [x] Statusstates
- [x] Logging

## Phase 3 – Benutzer, Rollen, Geräte

- [x] Benutzer
- [x] Rollen
- [x] Geräte
- [x] Sessions
- [x] Sperren/Löschen
- [x] Audit

## Phase 4 – Objektfreigaben

- [x] Objektbrowser
- [x] Adapterfreigabe
- [x] Geräte-/Kanal-/State-Freigabe
- [x] Lesen/Schreiben getrennt
- [x] Wertebereiche
- [x] Vererbung
- [x] Freigabeprofile
- [x] effektive Vorschau
- [x] Systemstates blockieren
- [ ] Kombinierte Freigabe "Benutzer + Gerät" (MASTERKONZEPT.md §"Rechtestufen" nennt 5 Ebenen inkl. Kombination; `ExposureRule.validateSingleOwner` erzwingt aktuell genau einen Owner (Rolle XOR Benutzer XOR Gerät), das Datenmodell erlaubt keine Kombination - gefunden bei der Konzept-Lückenanalyse, nicht weiter dokumentierte Vereinfachung)
- [ ] "temporäre Sitzung" als eigene Rechte-Ebene (MASTERKONZEPT.md nennt sie unter den 5 Ebenen; `ExposureScope` kennt nur adapter/device/channel/state/group/alias/pattern)
- [ ] "scene"-Recht im Datenmodell (BACKEND-KONZEPT.md führt es in der Rechteliste; `ExposureRule` hat kein scene-Feld - anders als "Kamera" nirgends als bewusst zurückgestellt vermerkt)
- [ ] Generische Sperre für adapterinterne Config-/Credential-Namespaces (aktuell nur `system.`/`authentication.` als feste Präfix-Liste in `src/exposure/index.ts`, MASTERKONZEPT.md meint das breiter)

## Phase 5 – Objektkatalog

- [x] öffentliche UUIDs
- [x] Metadaten
- [x] Widgetvorschläge
- [x] Katalogversion
- [x] Delta-Updates (Backend: `GET /catalog?version=` – App nutzt es noch nicht aktiv, rückwärtskompatibel)
- [x] Rechteentzug
- [x] Original-State-IDs verbergen

## Phase 6 – Pairing und Auth

- [x] QR-Einladung
- [x] Ablaufzeit
- [x] Einmaligkeit
- [x] Adminbestätigung
- [x] Geräteschlüssel (EC P-256 statt Ed25519, siehe SECURITY.md-Begründung im Code)
- [x] Challenge-Response
- [x] Access Token
- [x] Refresh Token
- [x] Rotation
- [x] Wiederverwendungserkennung
- [x] Session-Widerruf
- [ ] Geräteschlüssel-Rotation (Re-Key eines bereits gepaarten Geräts; aktuell kein Endpoint dafür - nur initiales Pairing erzeugt ein Schlüsselpaar, siehe SECURITY.md "Geräteidentität")
- [ ] Optionales Pairing-Passwort / Vergleichscode (BACKEND-KONZEPT.md/SECURITY.md nennen das als zusätzliche Härtung neben der Admin-Bestätigung; nicht umgesetzt)

## Phase 7 – REST und WebSocket

- [x] API v1
- [x] OpenAPI (docs/openapi.yaml, validiert)
- [ ] WSS (Adapter terminiert selbst kein TLS - siehe Phase 16, DEPLOYMENT.md)
- [x] Subscription
- [x] Initialwerte
- [x] State-Updates
- [x] Heartbeat
- [x] Reconnect (App-seitig)
- [x] Rechteänderung
- [x] Session-Widerruf
- [x] Rate Limits (Kommandos pro Gerät UND jetzt auch pro IP auf /auth/challenge, /auth/login, /auth/refresh, /pairing/claim - die Auth/Pairing-Lücke fand die Konzept-Lückenanalyse und wurde gefixt)
- [ ] Dedizierter WS-Push für Alarmereignisse (BACKEND-KONZEPT.md/MASTERKONZEPT.md nennen "Alarmereignisse" als eigenen Event-Typ; `RealtimeGateway` kennt nur state_update/command_result/session_revoked/permissions_changed/heartbeat - Alarme werden clientseitig aus normalen State-Updates abgeleitet und nur erkannt, solange das Widget gerade beobachtet wird)
- [ ] WebSocket-Payload-Größenlimits/Backpressure (kein `maxPayload`, kein Backpressure-Handling in `src/realtime/index.ts`)

## Phase 8 – Android-Grundgerüst

- [x] Kotlin
- [x] Compose
- [x] minSdk 26 (ursprünglich 34/Android 14 geplant, bewusst abgesenkt für Tests auf älterer Hardware - siehe MASTERKONZEPT.md "Android-Version"; compileSdk/targetSdk bleiben 34)
- [x] Material 3
- [x] Room
- [x] DataStore
- [x] Keystore
- [x] Biometrie
- [x] REST Client
- [x] WebSocket Client
- [x] DI
- [ ] Tests (90 reine JVM-Unit-Tests: Domain-/Grid-/Alarm-Logik plus ViewModel-Tests mit Fake-Repositories für Lock/DashboardEditor/DashboardList/ObjectBrowser/Settings/Notifications/AppRoot/HistoryWidget; laufen automatisch in CI via `.github/workflows/ci.yml` android-Job und bauen dort auch eine herunterladbare Debug-APK - das deckte zwei echte, vorher nie kompilierte/ausgeführte Bugs auf: einen Kotlin-Typfehler in `CommandRepositoryImpl.kt` und einen hängenden Coroutine-Leak in `DashboardEditorViewModelTest.kt`, beide noch am selben Tag gefixt; OnboardingViewModel bewusst ausgelassen (braucht echten AndroidKeyStore-Provider); Repository-Ebene (Netzwerk/Retrofit) und UI-/Instrumentierungstests fehlen weiterhin)
- [x] Build Types (debug/staging/release, separate applicationIdSuffix, parallel installierbar)

## Phase 9 – Android Pairing

- [x] QR-Scanner
- [x] Serverprüfung
- [x] Fingerprint (vereinfacht, kein echtes Cert-Pinning - siehe Code-Kommentar)
- [x] Schlüsselpaar
- [x] Claim
- [x] Approval
- [x] Tokens
- [x] Fehlerzustände
- [x] Profil speichern

## Phase 10 – Objektbrowser

- [x] Katalog laden
- [x] Room Cache
- [x] Suche
- [x] Filter (Raum/Rolle/nur-schreibbar; ANDROID-APP-KONZEPT.md nennt zusätzlich "Adapter" und "Sensor/Aktor" als Filter - fehlen in `ObjectBrowserViewModel`)
- [x] Rechteanzeige
- [x] Live-Vorschau
- [x] Widgetvorschläge
- [ ] Favoriten (ANDROID-APP-KONZEPT.md/MASTERKONZEPT.md nennen Favoriten sowohl im Objektbrowser als auch für Dashboards; kein Favoriten-Feld im Datenmodell)

## Phase 11 – Dashboards

- [x] Dashboard-Datenmodell
- [x] Revision
- [x] Compact/Medium/Expanded
- [x] Dashboard-Liste
- [x] Editor
- [x] Drag & Drop (kollisionsbewusst, kein Swap bei Überlappung - siehe Code-Kommentar)
- [x] Größenänderung (weiterhin über +/− Buttons, kein Ecken-Ziehen)
- [x] Widget-Konfiguration
- [x] Speichern
- [x] Synchronisieren
- [x] Konflikte
- [ ] Rückgängig/Wiederholen im Editor (ANDROID-APP-KONZEPT.md nennt Undo/Redo für den Dashboard-Editor; nicht in `DashboardEditorViewModel` vorhanden)

## Phase 12 – Widgets

- [x] Text
- [x] Temperatur
- [x] Feuchte
- [x] Status
- [x] Schalter
- [x] Taster
- [x] Slider
- [x] Rollladen (Auf/Ab, "Stopp"-Button bewusst ohne Server-Befehl - siehe Code-Kommentar)
- [x] Thermostat
- [x] Verlauf (echtes Widget, Liste statt Sparkline/Chart - siehe android-app/README.md)
- [x] Alarm (Status-Icon + Quittieren, siehe Phase 15)
- [x] Kamera (Snapshot-Widget via GET /api/v1/objects/{id}/snapshot, siehe Phase 15)

## Phase 13 – Aktorsteuerung

- [x] Command API
- [x] Datentypprüfung
- [x] Werteprüfung
- [x] Confirmation Policy
- [x] Biometrie
- [x] Pending
- [x] Confirmed
- [x] Timeout
- [x] Retry-Regeln (ein automatischer Retry bei TIMEOUT mit neuer commandId, kein Retry bei REJECTED/BLOCKED)
- [x] Audit

## Phase 14 – Offline

- [x] Dashboards lokal
- [x] Katalog lokal
- [x] letzte Werte
- [x] Zeitstempel
- [x] Offlinebanner
- [x] Schreibfunktionen sperren
- [x] Cache-Limits (zeitbasierte Bereinigung, 14 Tage, kein größenbasiertes LRU)
- [x] Synchronisierung

## Phase 15 – Meldungen und Kamera

- [x] In-App-Meldungen (einfache Umsetzung)
- [x] Quittierung (lokal im Alarm-Widget, kein Server-Ack-State im Vertrag vorgesehen)
- [x] Entwarnung (automatische In-App-Meldung bei aktiv→inaktiv-Übergang)
- [x] Snapshot (Backend: `src/camera/index.ts` liest einen Data-URL- oder http(s)-URL-State und proxied das Bild; Android: CameraWidget)
- [x] Vollbild (einfacher Dialog beim Antippen des Bildes, kein Pinch-Zoom)
- [x] Fehlerstatus (eigener "Kein Snapshot verfügbar"-Zustand statt Absturz bei ungültigem Bild)
- [ ] später FCM
- [ ] später Livestream/PTZ (bewusst zurückgestellt, siehe MASTERKONZEPT.md §19 "Später")

## Phase 16 – Fernzugriff

- [x] lokal
- [ ] VPN (nicht konfiguriert/getestet - Infrastrukturaufgabe beim Deployment)
- [ ] Reverse Proxy (nicht konfiguriert/getestet)
- [ ] TLS (Adapter terminiert selbst kein TLS, siehe Code-Kommentar in main.ts)
- [ ] WebSocket Proxy
- [ ] Sicherheitsprüfung (interner Review-Durchgang fand & fixte 3 echte Lücken, siehe DEPLOYMENT.md; kein externes Review)
- [x] Dokumentation
- [x] Verbindungs-Info im Adapter (Settings-Hinweistext + Übersicht-Tab zeigt Port/Bind-Adresse/öffentliche URL/lokale IPs, erklärt VPN- vs. Reverse-Proxy-Optionen, ohne selbst VPN/Proxy zu implementieren)

## Phase 17 – Tests

- [x] Unit (Backend: 48 Tests für Auth/Authorization/Catalog/Commands/Sessions/Pairing/Profiles/Migrations)
- [x] Integration (test/integration/adapterStartup.ts – echter Adapter-Code gegen @iobroker/testing-Mock, echter HTTP-Server)
- [x] End-to-End (Pairing → Admin-Bestätigung → Token-Ausgabe → authentifizierter Katalog-Request → Token-Rotation → Challenge-Response-Login, alles über echte HTTP-Requests)
- [x] Pairing
- [x] Token-Rotation
- [x] Session-Widerruf
- [x] Rechteentzug
- [x] Replay
- [x] Rate Limit
- [x] falsche Alias-ID (OBJECT_NOT_FOUND)
- [ ] Offline
- [ ] Netzwechsel
- [ ] echte Geräte über den unterstützten Bereich (Android 8-14, minSdk jetzt 26) - u.a. ein Fire-OS-7.x-Tablet als geplantes Testgerät
- [ ] 7-Tage-Dauertest
- [x] OWASP API (Abgleich in DEPLOYMENT.md, kein externes Pentest)
- [x] OWASP MASVS (Abgleich in DEPLOYMENT.md, kein externes Pentest)

## Phase 18 – Release

- [ ] Adapter Alpha
- [ ] App Staging
- [ ] interne APK
- [ ] GitHub Releases (Automatisierung steht via `.github/workflows/release.yml`, aber es wurde noch kein Tag/Release erstellt)
- [x] Changelog (CHANGELOG.md)
- [x] Installationsanleitung
- [x] Updateprozess (dokumentiert in README „Release-Prozess": Admin-Tab „Update" bzw. erneutes `iobroker url`, da GitHub-Install statt npm-Registry)
- [ ] Beta
- [ ] Version 1.0
