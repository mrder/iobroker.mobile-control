# BACKEND-KONZEPT: ioBroker.mobile-control

## 1. Verantwortlichkeiten

Der Adapter ist die einzige autoritative Sicherheits- und Zugriffsschicht zwischen App und ioBroker.

Er übernimmt:

- Benutzer
- Rollen
- Geräte
- Pairing
- Authentifizierung
- Sessions
- Objektfreigaben
- Objektkatalog
- Dashboard-Synchronisierung
- REST
- WebSocket
- Aktorkommandos
- History-Zugriff
- Kamera-Snapshots
- Audit
- Sicherheitsereignisse

---

## 2. Module

```text
src/
├── main.ts
├── config/
├── users/
├── roles/
├── devices/
├── pairing/
├── auth/
├── sessions/
├── authorization/
├── exposure/
├── catalog/
├── dashboards/
├── commands/
├── realtime/
├── history/
├── camera/
├── notifications/
├── audit/
├── security/
├── api/
└── migrations/
```

---

## 3. Objektfreigaben

Freigaben gelten für:

- Adapterinstanz
- Gerät
- Kanal
- State
- Gruppe
- Alias
- Muster

Rechte:

- read
- write
- history
- camera
- scene
- confirm-policy
- local-only
- min/max/step
- allowed-values

Standard:

```text
deny by default
```

---

## 4. Objektkatalog

Der Adapter berechnet pro Benutzer/Gerät einen effektiven Katalog.

Katalogeigenschaften:

- öffentliche UUID
- Anzeigename
- Pfad
- Rolle
- Datentyp
- Einheit
- Rechte
- Wertebereich
- Widgetvorschläge
- History-Verfügbarkeit
- Metadatenversion

Original-State-IDs bleiben intern.

---

## 5. API-Gateway

Der Adapter stellt keine allgemeine ioBroker-API bereit.

Erlaubt:

- definierte REST-Endpunkte
- definierte WebSocket-Nachrichten
- öffentliche Objekt-UUIDs
- validierte Kommandos

Nicht erlaubt:

- beliebige `getState`
- beliebige `setState`
- allgemeine `sendTo`
- Objektbaum komplett lesen
- Adaptermethoden frei aufrufen

---

## 6. Pairing

Pairing-Einladungen:

- hohe Entropie
- kurzlebig
- einmalig
- serverseitig gehasht
- Audit
- optional Passwort
- optional Vergleichscode
- optional Adminbestätigung

---

## 7. Sessions

Session enthält:

- Session-ID
- Benutzer-ID
- Geräte-ID
- Rolle
- Token-Familie
- Erstellungszeit
- letzte Aktivität
- Ablaufzeit
- Widerrufsstatus
- letzte IP
- User-Agent/App-Version

---

## 8. WebSocket

Funktionen:

- Authentifizierung
- Subscribe auf öffentliche Objekt-UUIDs
- Unsubscribe
- Initialwerte
- State-Updates
- Kommandoergebnisse
- Alarmereignisse
- Rechteänderungen
- Session-Widerruf
- Heartbeat
- Backpressure
- Größenlimits

---

## 9. Dashboard-Speicherung

Dashboards sind persönliche Benutzerdaten.

Speichern:

- Dashboard-ID
- Benutzer-ID
- Serverprofil
- Revision
- Layouts
- Widgets
- Erstellungszeit
- Änderungszeit

Der Adapter prüft beim Speichern, ob alle referenzierten Objekt-UUIDs für den Benutzer sichtbar sind.

---

## 10. Aktorbefehle

Jedes Kommando enthält:

```json
{
  "commandId": "uuid",
  "objectId": "public-object-uuid",
  "value": true,
  "timestamp": "2026-07-08T18:00:00+02:00",
  "nonce": "random"
}
```

Prüfungen:

- Session aktiv
- Gerät aktiv
- Benutzer aktiv
- Objekt erlaubt
- write erlaubt
- Datentyp korrekt
- Wert erlaubt
- Rate Limit
- Confirmation Policy
- Replay-Schutz
- Local-only-Regel
- Audit

---

## 11. Audit

Ereignisse:

- Pairing erstellt
- Pairing fehlgeschlagen
- Gerät registriert
- Gerät bestätigt
- Login
- Token erneuert
- Token-Wiederverwendung
- Session widerrufen
- Gerät gesperrt
- Freigabe geändert
- Dashboard geändert
- Aktor geschaltet
- Kommando abgelehnt
- Rate Limit
- Sicherheitswarnung

---

## 12. Admin-Oberfläche

Bereiche:

- Übersicht
- Benutzer
- Rollen
- Geräte
- Pairing
- Objektfreigaben
- Freigabeprofile
- Dashboards-Metadaten
- Sessions
- Audit
- API/Verbindung
- Sicherheit
- Diagnose
- Import/Export

---

## 13. State-Struktur

```text
mobile-control.0
├── info
│   ├── connection
│   ├── apiStatus
│   ├── websocketStatus
│   ├── activeDevices
│   ├── activeSessions
│   └── securityStatus
├── control
│   ├── enabled
│   ├── remoteAccessEnabled
│   └── revokeAllSessions
├── devices
├── users
├── alarms
└── audit
```

Konfigurationsdetails liegen nicht als frei beschreibbare öffentliche States vor.

---

## 14. Backend-MVP

- Benutzer
- Rollen
- Geräte
- Pairing
- Geräteschlüssel
- Sessionverwaltung
- Objektfreigaben
- Objektkatalog
- REST
- WebSocket
- persönliche Dashboards
- Boolean-Aktoren
- Audit
- lokale/VPN-Verbindung
