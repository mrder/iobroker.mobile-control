# GESAMT-TODO

> Stand nach der ersten Umsetzungs-Session: Backend-MVP (Phasen 1–7, 13) und Android-Kernfunktionen (Phasen 8–12, 14) sind
> als echter, getesteter Code vorhanden. Nicht abgehakte Punkte sind bewusst offen geblieben (siehe README) oder erfordern
> eine echte ioBroker-/Android-Laufzeitumgebung zur Verifikation, die in dieser Session nicht verfügbar war.

## Phase 1 – Grundlagen

- [x] Namen finalisieren
- [ ] GitHub-Repositories erstellen
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
- [ ] Konfigurationsmigration
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
- [ ] Freigabeprofile (nur einzelne Regeln, keine wiederverwendbaren Profile)
- [ ] effektive Vorschau (kein "Vorschau für Benutzer X" Admin-Screen)
- [x] Systemstates blockieren

## Phase 5 – Objektkatalog

- [x] öffentliche UUIDs
- [x] Metadaten
- [x] Widgetvorschläge
- [x] Katalogversion
- [ ] Delta-Updates (Katalog wird immer vollständig geliefert)
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
- [ ] OpenAPI (kein maschinenlesbares Schema erzeugt)
- [ ] WSS (Adapter terminiert selbst kein TLS - siehe Phase 16)
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
- [ ] Build Types (kein separates debug/staging/release-Flavor-Setup)

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
- [ ] Drag & Drop (bewusst zurückgestellt, Datenmodell ist vorbereitet)
- [x] Größenänderung (über Buttons, kein Drag&Drop)
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
- [ ] Taster
- [ ] Slider
- [ ] Rollladen
- [ ] Thermostat
- [ ] Verlauf (nur Platzhalterkachel, keine echten Daten)
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
- [ ] Retry-Regeln
- [x] Audit

## Phase 14 – Offline

- [x] Dashboards lokal
- [x] Katalog lokal
- [x] letzte Werte
- [x] Zeitstempel
- [x] Offlinebanner
- [x] Schreibfunktionen sperren
- [ ] Cache-Limits
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

- [x] Unit (Backend: 27 Tests für Auth/Authorization/Commands/Sessions)
- [ ] Integration
- [ ] End-to-End
- [ ] Pairing (nur Backend-Logik ungetestet als eigener Testfall)
- [x] Token-Rotation
- [x] Session-Widerruf
- [ ] Rechteentzug (als eigener Testfall)
- [x] Replay
- [x] Rate Limit
- [ ] falsche Alias-ID
- [ ] Offline
- [ ] Netzwechsel
- [ ] Android 14 Geräte
- [ ] 7-Tage-Dauertest
- [ ] OWASP API
- [ ] OWASP MASVS

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
