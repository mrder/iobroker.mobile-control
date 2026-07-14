# MASTERKONZEPT: ioBroker Mobile Control

## 1. Ziel

Das Projekt besteht aus zwei Produkten:

1. **ioBroker-Backend-Adapter**
2. **native Android-App**

Der Adapter stellt eine streng begrenzte, sichere Schnittstelle zu ioBroker bereit. Die App zeigt ausschließlich freigegebene Objekte und ermöglicht dem Benutzer, daraus vollständig eigene mobile Dashboards zu bauen.

Die zentrale Trennung lautet:

```text
ioBroker-Adapter
→ bestimmt, was ein Benutzer oder Gerät sehen und bedienen darf

Android-App
→ bestimmt, wie daraus persönliche Dashboards gebaut werden
```

Die App erhält niemals ungefilterten Zugriff auf den ioBroker-Objektbaum und niemals allgemeine Schreibrechte.

---

## 2. Produktbestandteile

### Backend-Adapter

Arbeitstitel:

```text
ioBroker.mobile-control
```

Aufgaben:

- Benutzer, Rollen und Geräte verwalten
- Objekte aus dem ioBroker-Objektbaum freigeben
- Lese-, Schreib- und Historienrechte definieren
- gefilterten Objektkatalog bereitstellen
- Geräte per QR-Code koppeln
- Geräte kryptografisch authentifizieren
- Tokens und Sessions verwalten
- REST- und WebSocket-API bereitstellen
- Aktorbefehle prüfen und ausführen
- persönliche Dashboards speichern und synchronisieren
- Audit- und Sicherheitsereignisse protokollieren

### Android-App

Arbeitstitel:

```text
Mobile Control for ioBroker
```

Aufgaben:

- QR-Pairing
- sichere Geräteanmeldung
- App-Sperre und Biometrie
- freigegebenen Objektkatalog anzeigen
- persönliche Dashboards erstellen
- Live-Werte darstellen
- freigegebene Aktoren bedienen
- Offline-Cache
- Meldungen und Diagnosen
- spätere Push-, Kamera- und Tablet-Funktionen

---

## 3. Technischer Gesamtaufbau

```text
┌─────────────────────────────┐
│ Android-App                 │
│ Kotlin / Jetpack Compose    │
│                             │
│ Pairing                     │
│ Secure Storage              │
│ Dashboard Editor            │
│ Widget Renderer             │
│ REST Client                 │
│ WebSocket Client            │
│ Offline Cache               │
└──────────────┬──────────────┘
               │ HTTPS / WSS
               │ Geräteschlüssel + Tokens
               ▼
┌─────────────────────────────┐
│ ioBroker.mobile-control     │
│                             │
│ Pairing Service             │
│ Authentication             │
│ Authorization              │
│ Object Exposure            │
│ Dashboard Sync             │
│ Command Gateway            │
│ Realtime Gateway           │
│ Audit / Security            │
└──────────────┬──────────────┘
               │ interne ioBroker-API
               ▼
┌─────────────────────────────┐
│ ioBroker                    │
│ Objekte / States / Adapter  │
└─────────────────────────────┘
```

---

## 4. Verbindungsvarianten

Unterstützte Betriebsarten:

```text
LOCAL_ONLY
VPN
REVERSE_PROXY
RELAY_FUTURE
```

Priorität:

1. lokales Netzwerk
2. VPN
3. eigener Reverse Proxy
4. später optionaler Relay-Dienst

DynDNS ist lediglich eine Adressauflösung und kein Sicherheitsmechanismus.

Nicht direkt ins Internet freigeben:

- ioBroker Admin
- Redis
- Simple API
- allgemeiner ioBroker-WebSocket
- beliebige Adapterports
- Object DB oder State DB

---

## 5. Pairing

### Ablauf

1. Administrator öffnet den Adapter.
2. Benutzer und Rolle werden gewählt.
3. Ein Freigabeprofil für Adapter, Geräte, Kanäle und States wird gewählt.
4. Adapter erzeugt kurzlebige Pairing-Einladung.
5. QR-Code wird angezeigt.
6. App scannt den QR-Code.
7. App prüft Server, TLS und Instanz.
8. App erzeugt ein asymmetrisches Geräteschlüsselpaar.
9. Öffentlicher Schlüssel wird an den Adapter übertragen.
10. Adapter prüft Einmal-Secret und Ablaufzeit.
11. Gerät erscheint als ausstehend.
12. Administrator bestätigt das Gerät optional manuell.
13. Gerät erhält individuelle Rechte und Session.
14. QR-Code wird endgültig ungültig.

### QR-Inhalt

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

Der QR-Code enthält keinen dauerhaft gültigen API-Key.

---

## 6. Geräteidentität

Nicht verwenden:

- MAC-Adresse
- IMEI
- Werbe-ID
- IP-Adresse
- Gerätename
- DynDNS-Name

Stattdessen:

```text
Privater Schlüssel
→ verbleibt im Android Keystore

Öffentlicher Schlüssel
→ wird im Adapter gespeichert
```

Jedes Gerät erhält:

- Geräte-UUID
- Benutzerzuordnung
- Rolle
- öffentlicher Schlüssel
- Schlüssel-Fingerprint
- letzte Verbindung
- letzte IP nur zur Diagnose
- aktive Sessions
- Freigaben
- Sperrstatus

---

## 7. Authentifizierung

### Challenge-Response

```text
App fordert Challenge an
→ Adapter sendet Nonce
→ App signiert Nonce
→ Adapter prüft Signatur
→ Access Token wird ausgegeben
```

### Token-Modell

Access Token:

- kurzlebig
- z. B. 5–15 Minuten
- geräte- und benutzergebunden
- sofort widerrufbar

Refresh Token:

- länger gültig
- rotierend
- nur gehasht gespeichert
- an Gerät und Session gebunden
- Wiederverwendung alter Tokens erkennen
- Token-Familie bei Verdacht sperren

Optional:

- Benutzerpasswort
- App-PIN
- Biometrie
- erneute Anmeldung für kritische Aktionen

---

## 8. Benutzer, Rollen und Geräte

### Rollen

```text
Administrator
Bediener
Betrachter
Gast
Benutzerdefiniert
```

### Rechte können gelten für

- Rolle
- Benutzer
- Gerät
- Benutzer + Gerät
- temporäre Sitzung

### Priorität

```text
explizites Verbot
→ explizite Gerätefreigabe
→ explizite Benutzerfreigabe
→ Rollenfreigabe
→ Standardverbot
```

Ein privates Smartphone kann mehr Rechte besitzen als ein Wandtablet desselben Benutzers.

---

## 9. Objektfreigaben

Der Adapter zeigt einen ioBroker-Objektbrowser und erlaubt Freigaben auf folgenden Ebenen:

```text
Adapterinstanz
Gerät
Kanal
Einzelstate
Objektgruppe
Alias
Musterregel
```

Beispiel:

```text
zigbee.0
├── Wohnzimmer Sensor
│   ├── temperature    Lesen
│   ├── humidity       Lesen
│   └── battery        nicht freigegeben
│
└── Wohnzimmer Licht
    └── state          Lesen + Schreiben
```

### Regeln

- Lesen und Schreiben getrennt
- History separat
- neue Objekte standardmäßig nicht automatisch schreibbar
- vererbte Leserechte möglich
- Schreibrechte möglichst explizit
- `system.*` grundsätzlich blockieren
- Konfigurations- und Secret-States blockieren
- keine allgemeinen `sendTo`-Methoden
- Wertebereiche und Datentypen serverseitig prüfen

---

## 10. Gefilterter Objektkatalog

Die App erhält nicht den echten Objektbaum, sondern einen öffentlichen Katalog:

```json
{
  "id": "public-object-uuid",
  "name": "Wohnzimmer Temperatur",
  "path": ["Wohnzimmer", "Klimasensor"],
  "role": "value.temperature",
  "type": "number",
  "unit": "°C",
  "read": true,
  "write": false,
  "history": true,
  "suggestedWidgets": ["temperature", "value", "chart"]
}
```

Die Original-State-ID kann verborgen bleiben.

---

## 11. Persönliche Dashboards

Dashboards werden vollständig in der App erstellt.

Der Nutzer kann:

- mehrere Dashboards erstellen
- Räume und Kategorien anlegen
- Widgets platzieren
- Widgets skalieren
- Titel und Icons anpassen
- Favoriten definieren
- Smartphone- und Tablet-Layouts pflegen
- Startdashboard wählen

Der Nutzer kann nur Objekte verwenden, die im gefilterten Objektkatalog vorhanden sind.

### Speicherung

Empfohlen:

- serverseitig im Adapter
- lokal in der App gecacht
- an Benutzer-ID gebunden
- zwischen Geräten synchronisierbar
- revisionsbasiert

---

## 12. Android-App

Technik:

```text
Kotlin
Jetpack Compose
Material 3
Coroutines / Flow
Navigation Compose
Room
DataStore
Android Keystore
BiometricPrompt
Retrofit/OkHttp oder Ktor
WebSocket
WorkManager
```

### Android-Version

```kotlin
minSdk = 34
```

Damit ist Android 14 Mindestversion.

`targetSdk` und `compileSdk` werden unabhängig davon auf dem bei Veröffentlichung aktuellen Stand gehalten.

---

## 13. App-Navigation

```text
Start
Dashboards
Objekte
Meldungen
Einstellungen
```

Ersteinrichtung:

```text
Willkommen
→ QR-Code scannen
→ Server prüfen
→ Geräteschlüssel erzeugen
→ Gerät bestätigen
→ App-Sperre einrichten
→ erstes Dashboard erstellen
```

---

## 14. Dashboard-Widgets

MVP:

- Textwert
- Temperatur
- Luftfeuchtigkeit
- Boolean-Status
- Schalter
- Taster
- Slider
- Rollladen
- Thermostat
- einfache Verlaufskurve
- Alarmkachel
- Kamera-Snapshot

Später:

- Farblicht
- Szenen
- Energieübersichten
- kombinierte Raumkarten
- Media Player
- komplexe Diagramme
- Türsprechstelle

---

## 15. Echtzeitkommunikation

REST für:

- Pairing
- Login
- Tokens
- Objektkatalog
- Dashboard-Synchronisierung
- History
- Geräteverwaltung
- einmalige Kommandos

WebSocket für:

- Live-State-Updates
- Alarmmeldungen
- Verbindungsstatus
- Kommandoergebnisse
- Rechteänderungen
- Session-Widerruf

Die App abonniert nur öffentliche Objekt-UUIDs, niemals beliebige State-IDs.

---

## 16. Aktorsteuerung

Ablauf:

```text
App sendet Kommando
→ Adapter prüft Session
→ Adapter prüft Benutzer
→ Adapter prüft Gerät
→ Adapter prüft Alias
→ Adapter prüft Schreibrecht
→ Adapter prüft Datentyp und Wertebereich
→ Adapter schreibt ioBroker-State
→ Adapter wartet optional auf Feedback
→ App erhält Ergebnis
```

Status:

```text
accepted
executed
confirmed
timeout
rejected
blocked
```

Kritische Aktionen:

```text
NONE
DIALOG
BIOMETRIC
REAUTHENTICATE
LOCAL_NETWORK_ONLY
BLOCKED_ON_MOBILE
```

---

## 17. Offline-Verhalten

Offline verfügbar:

- Dashboards
- Objektkatalog
- letzte Werte
- Zeitstempel
- Meldungsübersicht

Offline nicht erlaubt:

- Aktoren schalten
- Alarm quittieren
- Pairing
- kritische Befehle puffern

Widgets zeigen:

```text
Offline
Letzter Wert: 22,4 °C
Stand: 17:42 Uhr
```

---

## 18. Sicherheit

- TLS für Fernzugriff
- VPN bevorzugt
- keine globalen API-Keys
- keine MAC-Authentifizierung
- kurzlebige Pairing-Codes
- Geräteschlüssel
- kurzlebige Access Tokens
- rotierende Refresh Tokens
- serverseitige Autorisierung
- Rate Limiting
- Replay-Schutz
- Audit-Log
- Secret-Redaction
- sichere lokale Speicherung
- Geräte- und Session-Widerruf
- Rechteentzug wirkt sofort
- kritische Aktionen zusätzlich bestätigen

---

## 19. Kamera

MVP:

- Snapshot
- Zeitstempel
- Fehlerzustand
- Vollbild
- Aktualisieren
- optional Screenshot-Schutz

Später:

- Livestream
- PTZ
- Ereignisliste
- Gegensprechen

Kein ungeschützter RTSP-Zugriff direkt aus dem Internet.

---

## 20. Benachrichtigungen

MVP:

- In-App-Meldungen
- WebSocket-Alarme
- Quittierung
- Entwarnung

Später:

- FCM
- APNs
- Deep Links
- datensparsame Sperrbildschirmtexte
- vorhandene ioBroker-Nachrichtenadapter

---

## 21. Repositories

Empfohlen:

```text
ioBroker.mobile-control
mobile-control-android
```

Optional später:

```text
mobile-control-relay
mobile-control-protocol
```

Gemeinsame API-Modelle können in einer versionierten Spezifikation gepflegt werden.

---

## 22. MVP

### Backend

- Adaptergrundgerüst
- Benutzer und Rollen
- Objektfreigaben
- gefilterter Objektkatalog
- QR-Pairing
- Geräteschlüssel
- Geräteverwaltung
- Tokens und Sessions
- REST
- WebSocket
- Boolean-Aktoren
- Audit
- persönliche Dashboard-Speicherung

### Android-App

- Android 14+
- QR-Scanner
- Geräteschlüssel
- sichere Anmeldung
- App-Sperre
- Objektkatalog
- Dashboard-Editor
- Live-Werte
- Boolean-Schalter
- Offline-Cache
- Diagnose

---

## 23. Entwicklungsreihenfolge

1. Protokoll und Bedrohungsmodell
2. Backend-Grundgerüst
3. Objektfreigaben
4. Objektkatalog
5. Pairing
6. Geräteidentität
7. Token-System
8. Android-Grundgerüst
9. Objektbrowser
10. Dashboard-Datenmodell
11. Dashboard-Editor
12. Live-Werte
13. Aktorsteuerung
14. Audit und Widerruf
15. Offline-Modus
16. Kamera
17. Meldungen
18. Fernzugriff
19. interne Veröffentlichung
20. spätere Store-/Relay-Optionen

---

## 24. Definition of Done Version 1.0

- keine beliebigen State-Zugriffe
- keine globalen Dauer-API-Keys
- QR-Code kurzlebig und einmalig
- jedes Gerät besitzt eigenes Schlüsselpaar
- private Schlüssel verlassen das Gerät nicht
- Access Tokens kurzlebig
- Refresh Tokens rotierend
- Rechte serverseitig geprüft
- Schreibwerte serverseitig validiert
- persönliche Dashboards vollständig in der App
- nur freigegebene Objekte auswählbar
- Live-Werte stabil
- Offline-Zustände klar
- keine Offline-Aktorbefehle
- Geräte und Sessions sofort widerrufbar
- Audit vollständig
- Android-App läuft stabil
- Adapter läuft stabil
