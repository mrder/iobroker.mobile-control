# SECURITY-KONZEPT

## Grundsatz

Die App ist nicht vertrauenswürdig genug, um selbst Rechte durchzusetzen. Jede Berechtigung wird im Adapter erneut geprüft.

## Nicht verwenden

- MAC-Adresse
- IMEI
- IP-Adresse als Identität
- globaler API-Key
- dauerhaft gültiger QR-Code
- direkte State-IDs als Sicherheitsgrenze
- clientseitig versteckte Buttons als Schutz

## Pairing

- kurzlebig
- einmalig
- hohe Entropie
- gehasht speichern
- keine Secrets in Logs
- optional Adminbestätigung
- optional Vergleichscode

## Geräteidentität

- asymmetrisches Schlüsselpaar
- privater Schlüssel im Android Keystore
- öffentlicher Schlüssel im Adapter
- Challenge-Response
- Schlüsselrotation
- Gerätewiderruf

## Tokens

- Access Token kurzlebig
- Refresh Token rotierend
- Refresh Token gehasht
- Wiederverwendungserkennung
- Token-Familie sperren
- Session sofort widerrufbar

## Autorisierung

Prüfen:

- Benutzer
- Rolle
- Gerät
- Session
- Objekt
- read/write
- Datentyp
- Wertebereich
- Rate Limit
- Confirmation Policy
- Local-only
- Replay

## Netzwerk

- HTTPS/WSS
- VPN bevorzugt
- Reverse Proxy möglich
- kein direkter Adminzugriff
- keine allgemeine ioBroker-API
- Rate Limiting
- temporäre IP-Sperre bei wiederholten Fehlversuchen (AbuseGuard, unabhängig vom reinen Rate-Limit)
- Größenlimits
- sichere Fehlertexte
- **Portal-Schlüssel** (2026-07-30, live-requested): gemeinsames Geheimnis per HTTP Basic Auth vor
  jeder Anfrage an den Server, noch vor Pairing/Login - ein Angreifer/Scanner ohne diesen Schlüssel
  bekommt für jeden Pfad dieselbe generische 401-Antwort, keine Unterscheidung zwischen gültigem
  und ungültigem Pfad möglich. Eigener AbuseGuard (getrennt vom Pairing/Login-Tracker, siehe
  `portalAbuseGuard` in `main.ts` - ein gemeinsamer Zähler hätte jeden gültigen Portal-Schlüssel-
  Check die Fehlversuchszählung für Pairing/Login zurücksetzen lassen, live über den
  Integrationstest gefunden). Wird beim Pairing automatisch per QR-Code übertragen; ein bereits
  gekoppeltes Gerät ohne Schlüssel holt ihn sich einmalig über den eigens dafür von dieser Sperre
  ausgenommenen, aber weiterhin normal geräte-authentifizierten Endpunkt `GET /api/v1/portal-key`
  nach. **Bewusste Einschränkung:** Nach einem manuellen Neu-Erzeugen des Schlüssels im Admin-Tab
  gibt es keinen automatischen Übergang - jedes bereits gekoppelte Gerät braucht den neuen
  Schlüssel von Hand (aktuell nur durch den Admin manuell weitergegeben, keine In-App-Eingabe
  dafür vorhanden). Schützt nur gegen Bruteforce/Scanning auf Anwendungsebene, nicht gegen echtes
  volumenbasiertes DDoS (braucht Schutz auf Netzwerkebene).

## Lokale App-Sicherheit

- Keystore
- BiometricPrompt
- verschlüsselte Tokens
- keine Secrets in normalen Preferences
- keine Secrets in Logs
- optional Screenshot-Schutz
- Remote-Logout
- Cache löschen bei Profilentfernung

## Audit

Keine Secrets protokollieren.

Protokollieren:

- wer
- welches Gerät
- welche Aktion
- welches Objekt
- Ergebnis
- Zeitpunkt
- IP zur Diagnose
- Session-ID
