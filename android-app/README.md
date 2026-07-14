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
  Widget hinzufügen/entfernen/Auf-Ab-Reihenfolge/Größe ändern, Größenklassen
  compact/medium/expanded mit eigenem Layout-Array, Speichern inkl. Revision-Konflikt-Dialog
  (Überschreiben/Verwerfen)
- Widgets: Text/Value, Temperatur, Luftfeuchtigkeit, Boolean-Status, Schalter (mit
  PENDING/CONFIRMED/FAILED-Overlay), Verlauf-Platzhalter — alle über einen gemeinsamen
  `WidgetState`-Sealed-Interface mit 9 visuell unterscheidbaren Zuständen
- Realtime: WebSocket-Repository mit Subscribe/Unsubscribe pro sichtbarem Screen,
  `StateFlow<Map<ObjectId, LiveValue>>`, Command-Timeout nach 15s, Reconnect mit exponentiellem
  Backoff, Heartbeat-Watchdog
- Offline-Verhalten: Dashboards/Katalog/letzte Werte aus Room auch ohne Verbindung, Offline-Badge,
  Schalter deaktiviert offline
- Einstellungen/Diagnose: Serverinfo, letzte Verbindung, Geräteabmeldung (lokal, best-effort),
  Cache leeren, App-/API-Version, rudimentäres Log (Verbindungs-/Auth-/State-Fehler, keine Secrets)
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
- **Grid-Layout im Dashboard-Editor**: `x`/`y`/`w`/`h` sind vollständig im Datenmodell vorhanden,
  werden aber nur über Auf/Ab-Buttons (Reihenfolge) und +/−-Buttons (Breite) bedient — kein
  Drag&Drop (siehe Scope-Ausschluss).
- **PIN-Hash**: unsalted SHA-256, da die PIN nur die lokale UI hinter einer bereits über den
  Keystore authentifizierten Session sperrt, nicht selbst ein Server-Credential ist.
- **Revision-Konflikt "Überschreiben"**: holt vor dem Force-Write die aktuelle Server-Revision und
  erhöht sie um 1 (der Server-Vertrag definiert keinen expliziten Force-Parameter).

## Was bewusst nicht gebaut wurde (TODO)

- Kamera-Snapshot-Widget, Live-Stream
- FCM Push Notifications
- Echtes Drag&Drop im Dashboard-Editor (Datenstruktur ist vorbereitet)
- History-Diagramme (nur Platzhalter-Kachel "Verlauf folgt")
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
