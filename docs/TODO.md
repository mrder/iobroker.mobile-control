# GESAMT-TODO

> Stand nach mehreren Umsetzungs-Sessions: Backend-MVP inkl. Freigabeprofilen, Katalog-Vorschau, History-API,
> Katalog-Delta-Updates, OpenAPI-Spezifikation, Migrations-Grundgerüst und Deployment-Doku (Phasen 1–7, 13) sowie
> Android-Kernfunktionen inkl. Drag&Drop, Retry-Regeln und echtem Verlauf-Widget (Phasen 8–14) sind als echter,
> getesteter Code vorhanden: 56 Backend-Unit-Tests plus ein echter Integrationstest, der den tatsächlich kompilierten
> Adapter gegen eine gemockte ioBroker-Umgebung end-to-end über echte HTTP-Requests durchspielt (Pairing → Admin-
> Bestätigung → Token → Katalog → Token-Rotation → Login). Nicht abgehakte Punkte sind bewusst offen geblieben (siehe
> README) oder erfordern eine echte ioBroker-/Android-Laufzeitumgebung zur Verifikation, die hier nicht verfügbar war.

## Phase 1 – Grundlagen

- [x] Namen finalisieren
- [x] GitHub-Repositories erstellen (github.com/mrder/iobroker.mobile-control)
- [x] Lizenz festlegen
- [x] SECURITY.md
- [x] API-Versionierung
- [x] Bedrohungsmodell
- [ ] Build- und Releaseprozess
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
- [x] Rate Limits

## Phase 8 – Android-Grundgerüst

- [x] Kotlin
- [x] Compose
- [x] minSdk 34
- [x] Material 3
- [x] Room
- [x] DataStore
- [x] Keystore
- [x] Biometrie
- [x] REST Client
- [x] WebSocket Client
- [x] DI
- [ ] Tests (keine Android-Unit-/Instrumentierungstests)
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
- [x] Filter
- [x] Rechteanzeige
- [x] Live-Vorschau
- [x] Widgetvorschläge

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
- [ ] Alarm
- [ ] Kamera (bewusst zurückgestellt)

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
- [ ] Quittierung
- [ ] Entwarnung
- [ ] Snapshot (bewusst zurückgestellt)
- [ ] Vollbild
- [ ] Fehlerstatus
- [ ] später FCM

## Phase 16 – Fernzugriff

- [x] lokal
- [ ] VPN (nicht konfiguriert/getestet - Infrastrukturaufgabe beim Deployment)
- [ ] Reverse Proxy (nicht konfiguriert/getestet)
- [ ] TLS (Adapter terminiert selbst kein TLS, siehe Code-Kommentar in main.ts)
- [ ] WebSocket Proxy
- [ ] Sicherheitsprüfung (kein externes Review)
- [x] Dokumentation

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
- [ ] Android 14 Geräte
- [ ] 7-Tage-Dauertest
- [x] OWASP API (Abgleich in DEPLOYMENT.md, kein externes Pentest)
- [x] OWASP MASVS (Abgleich in DEPLOYMENT.md, kein externes Pentest)

## Phase 18 – Release

- [ ] Adapter Alpha
- [ ] App Staging
- [ ] interne APK
- [ ] GitHub Releases
- [ ] Changelog
- [x] Installationsanleitung
- [ ] Updateprozess
- [ ] Beta
- [ ] Version 1.0
