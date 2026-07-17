# Mobile Control for ioBroker — Android App

Native Android-Gegenstück zum ioBroker "Mobile Control"-Adapter. Zeigt nie den echten
ioBroker-Objektbaum, sondern nur den vom Server gefilterten, freigegebenen Objektkatalog.

## Voraussetzungen

- Android Studio (aktuelle stabile Version, "Ladybug" oder neuer empfohlen)
- JDK 17 (wird von Android Studio i.d.R. mitgebracht; `compileOptions`/`kotlinOptions` sind auf 17 gesetzt)
- Android SDK Platform 34, minSdk 34 (App läuft nur auf Android 14+)
- Ein physisches Gerät oder Emulator mit Kamera-Unterstützung (für den QR-Scan) und
  Biometrie-Unterstützung (für den optionalen biometrischen App-Lock), falls diese Flows getestet werden sollen

## Bauen und Starten

1. Projekt in Android Studio öffnen: `File → Open…` und den Ordner `android-app/` auswählen
   (nicht das Repo-Root — die App ist ein eigenständiges Gradle-Projekt).
2. Gradle-Sync abwarten (lädt Compose BOM, Hilt, Room, CameraX, ML Kit etc. herunter).
3. `app`-Run-Konfiguration auf einem Gerät/Emulator mit API 34+ starten.
4. Alternativ per Kommandozeile: `./gradlew :app:assembleDebug` (Gradle Wrapper-JAR muss vorher
   einmal über Android Studio oder `gradle wrapper` erzeugt werden, da Binärdateien hier nicht
   eingecheckt sind).

Es ist keine echte Server-Instanz im Repo enthalten — die App spricht ausschließlich über den in
diesem Dokument beschriebenen REST/WebSocket-Vertrag mit einem ioBroker-Mobile-Control-Adapter.

### Fertige APK ohne Android Studio (z.B. für einen Live-Test auf dem eigenen Handy)

Jeder Push nach `master`/`main` baut in GitHub Actions eine installierbare Debug-APK und lädt sie
als Artefakt hoch (Job "Android (JVM unit tests)" in `.github/workflows/ci.yml`, Schritt
`gradle :app:assembleDebug`). Herunterladen:

```text
https://github.com/mrder/iobroker.mobile-control/actions → neuester "CI"-Lauf auf master
→ Artefakt "mobile-control-debug-apk" herunterladen (30 Tage aufbewahrt) → als ZIP entpacken
→ die .apk-Datei aufs Handy übertragen und installieren (Android fragt ggf. nach Erlaubnis für
"Installation aus unbekannten Quellen")
```

Die Debug-Variante (`applicationIdSuffix ".debug"`) ist parallel zu einer eventuell später
existierenden Staging-/Release-Installation installierbar.

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

90 reine JVM-Unit-Tests (kein Emulator nötig), laufen automatisch in CI (`.github/workflows/ci.yml`,
Job "android") bei jedem Push: Grid-Platzierung/Kollisionserkennung im Dashboard-Editor, die
Domain-Modelle (`WidgetType`, `CommandStatus`, `ApiErrorCode`, `Dashboard`/`SizeClass`,
`ObjectCatalogItem`) sowie ViewModel-Tests mit hand-geschriebenen Fake-Repositories für Lock,
DashboardEditor, DashboardList, ObjectBrowser, Settings, Notifications, AppRoot, HistoryWidget und
Alarm. `OnboardingViewModel` hat bewusst keinen Test - sein Konstruktor braucht einen echten
`AndroidKeyStore`-Provider, der auf einer reinen JVM (kein Emulator, kein Robolectric) nicht
existiert. Repository-Ebene (Netzwerk/Retrofit) und UI-/Instrumentierungstests fehlen weiterhin.

## Server-URL für Tests konfigurieren

Es gibt **keine** fest einkompilierte Server-URL. Die App erfährt die Server-Adresse ausschließlich
über den gescannten Pairing-QR-Code (siehe `PairingQrPayload` in
`domain/model/PairingQrPayload.kt`):

```json
{
  "version": 1,
  "serverUrl": "https://home.example.net/mobile-control",
  "pairingId": "random-id",
  "pairingSecret": "single-use-secret",
  "expiresAt": "2026-07-08T18:30:00+02:00",
  "serverFingerprint": "sha256/...",
  "instanceId": "instance-uuid"
}
```

Zum lokalen Testen ohne echten Server kann ein solcher QR-Code z.B. mit einem beliebigen
QR-Code-Generator aus obigem JSON erzeugt werden (`serverUrl` auf eine lokal erreichbare,
HTTPS-fähige Mock-/Testinstanz zeigen lassen — die App verlangt HTTPS für den Zertifikats-Fingerprint-Check).
`serverFingerprint` muss der SHA-256-SPKI-Fingerprint (Format `sha256/BASE64`) des von diesem Server
präsentierten TLS-Zertifikats sein; bei Abweichung zeigt die App eine Warnung, lässt aber (bewusst
vereinfacht, siehe unten) ein Fortfahren zu.

Die Basis-Pfade `/api/v1` und `/ws/v1` werden relativ zur gescannten `serverUrl` aufgelöst
(`data/remote/ServerConfigHolder.kt` + `DynamicBaseUrlInterceptor.kt`), da die Server-Adresse erst
zur Laufzeit bekannt ist und Retrofit/OkHttp trotzdem nur einmal beim App-Start aufgebaut werden.

## Architektur

- `ui/` — Jetpack Compose Screens + Hilt-ViewModels (MVVM), gegliedert nach Feature
  (`onboarding`, `lock`, `start`, `dashboards`, `objects`, `widgets`, `notifications`, `settings`)
- `domain/` — Domain-Models und Repository-Interfaces (kein Framework-Bezug)
- `data/` — Retrofit/OkHttp-REST-Client, OkHttp-WebSocket-Client, Room-Cache, DataStore/
  EncryptedSharedPreferences, Android-Keystore-Manager, Repository-Implementierungen
- `di/` — Hilt-Module (Network, Database, Crypto, Repository-Bindings)

Bewusst **kein** separates Use-Case-Layer: ViewModels rufen Repository-Interfaces direkt auf. Für
den Umfang dieser App hätte eine zusätzliche Use-Case-Schicht nur Boilerplate ohne echten Mehrwert
erzeugt (Single-Module-App, keine mehrfach wiederverwendete Geschäftslogik über Screens hinweg).

## Was funktioniert (MVP)

- Onboarding: QR-Scan (CameraX + ML Kit) → Server-Fingerprint-Check → EC-P256-Schlüsselerzeugung im
  Android Keystore → `pairing/claim` → Status-Polling → PIN-Einrichtung → Start
- Login/Session: Challenge-Response mit ECDSA/SHA256, automatisches Token-Refresh bei 401 über einen
  OkHttp-`Authenticator`, Behandlung von `DEVICE_REVOKED`/`SESSION_REVOKED` (auch per WebSocket
  `session_revoked`) mit Redirect auf einen "Zugriff widerrufen"-Screen und lokalem Token-Löschen
- App-Sperre: PIN (SETUP/VERIFY) + optionale BiometricPrompt-Integration, sperrt beim
  Verlassen des Vordergrunds (`ProcessLifecycleOwner`, nicht bei reinem Rotieren)
- Objektkatalog-Browser: Suche, Filter nach Raum/Rolle/nur-schreibbar, Room-Cache, Live-Werte-Vorschau
  per WebSocket-Subscribe solange der Screen sichtbar ist
- Dashboards: Liste, Erstellen/Löschen/Duplizieren/Umbenennen, Startdashboard wählen, Editor mit
  Widget hinzufügen/entfernen, echtem Drag&Drop (Ziehen auf dem Grid rastet auf die nächste
  Grid-Zelle ein, siehe unten) sowie +/−-Buttons für die Größe, Größenklassen
  compact/medium/expanded mit eigenem Layout-Array, Speichern inkl. Revision-Konflikt-Dialog
  (Überschreiben/Verwerfen)
- Widgets: Text/Value, Temperatur, Luftfeuchtigkeit, Boolean-Status, Schalter, Taster
  (Momentary-Button), Slider, Rollladen (Auf/Stopp/Ab), Thermostat (+/− um Zielwert mit
  Katalog-`step`), Verlauf (lädt die letzten Werte per `GET /api/v1/history` und zeigt sie als
  Liste, siehe unten), Alarm (Status + Quittieren/Entwarnung, siehe unten) — alle über einen
  gemeinsamen `WidgetState`-Sealed-Interface mit 9 visuell unterscheidbaren Zuständen.
  Schreibende Widgets (Schalter/Taster/Slider/Rollladen/Thermostat) laufen alle über ein
  gemeinsames Bestätigungs-Gate (`ui/widgets/ConfirmationGate.kt`), das
  `ObjectCatalogItem.confirmPolicy` auswertet, bevor `CommandRepository.sendCommand(...,
  confirmed=true)` aufgerufen wird
- Alarm-Widget: zeigt aktiv/inaktiv (rot/grün), pusht bei jedem Wechsel eine In-App-Meldung
  ("Alarm aktiv" / "Entwarnung") über dieselbe `NotificationRepository` wie Verbindungs-/
  Rechte-Ereignisse, "Quittieren" mutet die Alarm-Optik lokal bis zum nächsten neuen
  Alarm-Ereignis (`ui/widgets/AlarmWidgetViewModel.kt`, 8 Unit-Tests)
- Realtime: WebSocket-Repository mit Subscribe/Unsubscribe pro sichtbarem Screen,
  `StateFlow<Map<ObjectId, LiveValue>>`, Command-Timeout nach 15s mit genau einem automatischen
  Retry (neue commandId/nonce) bevor der Nutzer benachrichtigt wird — `REJECTED`/`BLOCKED` sind
  endgültige Server-Entscheidungen und werden nie erneut versucht, Reconnect mit exponentiellem
  Backoff, Heartbeat-Watchdog
- Offline-Verhalten: Dashboards/Katalog/letzte Werte aus Room auch ohne Verbindung, Offline-Badge,
  Schalter deaktiviert offline
- Einstellungen/Diagnose: Serverinfo, letzte Verbindung, Geräteabmeldung (lokal, best-effort),
  Cache leeren, App-/API-Version, rudimentäres Log (Verbindungs-/Auth-/State-Fehler, keine Secrets)
- Cache-Limits: `state_cache` (Room) wird beim Start von `StateRepositoryImpl` einmal pro
  Prozess/Session um Einträge bereinigt, deren `lastChange` älter als
  `STATE_CACHE_MAX_AGE_MS` (14 Tage) ist (`StateCacheDao.deleteOlderThan`) — einfache
  zeitbasierte Bereinigung, kein größenbasiertes LRU
- Build-Varianten: `debug` (`applicationIdSuffix = ".debug"`), `staging` (`.staging`-Suffix,
  minifiziert/shrunk wie Release, aber weiterhin debugfähig und über den Debug-Signing-Config
  installierbar) und `release` (kein Suffix, unsigniert, siehe TODO Signing-Pipeline) — dank der
  unterschiedlichen `applicationId`-Suffixe können alle drei parallel auf einem Gerät installiert sein
- Sicherheit: Privater Schlüssel verlässt nie den Keystore, Tokens nur in
  `EncryptedSharedPreferences`, keine Logcat-Ausgaben von Tokens/Signaturen (auch der
  `HttpLoggingInterceptor` läuft nur auf `BASIC`-Level, nie `BODY`/`HEADERS`), `FLAG_SECURE` auf
  Onboarding/Pairing- und Settings-Screens

## Bewusste Vereinfachungen

- **Certificate Pinning**: Statt eines dynamisch aus dem QR-Code aufgebauten OkHttp
  `CertificatePinner` (der den TLS-Handshake bei Mismatch hart abbricht) vergleicht
  `data/crypto/ServerFingerprintChecker.kt` den SPKI-SHA256-Fingerprint des Server-Zertifikats
  **nach** einem normalen Handshake gegen den Systemtrust-Store und zeigt dem Nutzer bei Abweichung
  eine explizite Warnung mit der Möglichkeit, trotzdem fortzufahren. Das ist schwächer als echtes
  Pinning (ein MITM mit CA-vertrautem Zertifikat würde nicht auffallen), erfüllt aber die im
  Lastenheft vorgesehene Fallback-Anforderung "warnen statt hart blockieren". Ein echter
  `CertificatePinner` ist ein guter Folgeschritt.
- **Drag&Drop-Kollisionsregel im Dashboard-Editor**: Wird ein Widget auf eine bereits belegte
  Grid-Zelle gezogen, wird der Drop verworfen (Widget springt auf seine letzte Position zurück,
  während des Ziehens zeigt ein roter Rahmen die ungültige Zielzelle an) statt die kollidierenden
  Widgets automatisch zu vertauschen — ein Tausch kann kaskadieren (das verdrängte Widget könnte
  wiederum ein drittes überlappen) und eine robuste, überraschungsfreie Auflösung dafür wäre
  deutlich aufwendiger. Die reine Positionsänderung geschieht per Ziehen; Größenänderung bleibt
  über die +/−-Buttons gelöst (kein Ziehen an einer Ecke).
- **Verlauf-Widget als einfache Liste statt Sparkline/Chart**: `HistoryWidget` zeigt die letzten
  Messwerte als "HH:mm Wert"-Liste, nicht als gezeichnete Kurve — beides ist für den MVP-Anspruch
  ausreichend, eine Liste kommt aber ohne Skalierungs-/Pixel-Mathematik aus (kein Chart-Framework
  im Projekt, siehe oben).
- **PIN-Hash**: unsalted SHA-256, da die PIN nur die lokale UI hinter einer bereits über den
  Keystore authentifizierten Session sperrt, nicht selbst ein Server-Credential ist.
- **Revision-Konflikt "Überschreiben"**: holt vor dem Force-Write die aktuelle Server-Revision und
  erhöht sie um 1 (der Server-Vertrag definiert keinen expliziten Force-Parameter).
- **Rollladen-Widget "Stopp"-Button**: Der API-Vertrag kennt nur das Setzen einer absoluten
  Position, keinen eigenen Stopp/Halt-Befehl. Der "Stopp"-Button existiert nur für das vertraute
  Drei-Tasten-Layout, sendet aber bewusst keinen Befehl an den Server (No-Op).
- **Bestätigungs-Policy `REAUTHENTICATE`/`LOCAL_NETWORK_ONLY`**: werden im MVP wie `DIALOG`
  behandelt (einfacher Ja/Abbrechen-Dialog) statt eines echten Re-Login-Flows bzw. einer
  Client-seitigen Netzwerk-Origin-Prüfung — beides wäre deutlich mehr Aufwand für einen Fall, den
  der Server ohnehin ablehnen/verlangen kann. `BLOCKED_ON_MOBILE` deaktiviert das jeweilige Widget
  stattdessen von vornherein (Titel-Suffix "nicht mobil steuerbar").
- **Slider-Drag-Verhalten**: `SliderWidget` hält eine lokale `sliderPosition`, die während des
  aktiven Ziehens nicht von Server-Value-Updates überschrieben wird (sonst würde der Thumb unter
  dem Finger zurückspringen); gesendet wird der Wert erst bei `onValueChangeFinished`, nicht bei
  jedem Drag-Tick, um kein Server-Rate-Limit zu strapazieren.
- **Alarm-"Quittieren" ist rein lokal**: Es gibt keinen Server-Schreibbefehl dafür — nicht jede
  Alarmquelle in ioBroker hat einen eigenen beschreibbaren Ack-State, und der API-Vertrag sieht
  keinen dafür vor. "Quittieren" mutet stattdessen nur die Alarm-Optik im Widget selbst (rot →
  gedämpft) bis zum nächsten echten Alarm-Ereignis (aktiv→inaktiv→aktiv). Ein serverseitiger
  Ack-Mechanismus wäre ein guter Folgeschritt, sobald das Freigabemodell einen solchen State
  vorsieht.

## Was bewusst nicht gebaut wurde (TODO)

- Kamera-Snapshot-Widget, Live-Stream
- FCM Push Notifications
- Echte History-Diagramme/Sparklines (aktuell eine einfache Werte-Liste, siehe "Bewusste Vereinfachungen")
- Undo/Redo im Editor
- Store-Release-Signing-Pipeline
- Echtes, dynamisches Certificate Pinning (siehe oben)
- WorkManager für Reconnect/Sync (Realtime-Reconnect läuft stattdessen In-App mit
  Exponential-Backoff im `RealtimeWebSocketClient`, solange die App im Vordergrund/Hintergrund
  läuft, nicht als eigener periodischer Hintergrund-Job)

## API-Vertrag

Der vollständige REST/WebSocket-Vertrag (Endpunkte, DTOs, Fehlercodes) ist 1:1 unter
`data/remote/ApiService.kt`, `data/remote/dto/`, `data/remote/RealtimeWebSocketClient.kt` und
`domain/model/ApiErrorCode.kt` abgebildet.
