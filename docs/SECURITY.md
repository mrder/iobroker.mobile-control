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
- Größenlimits
- sichere Fehlertexte

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
