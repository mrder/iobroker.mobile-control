# ANDROID-APP-KONZEPT

## 1. Plattform

```kotlin
minSdk = 34
```

Android 14 ist Mindestversion.

Technik:

- Kotlin
- Jetpack Compose
- Material 3
- Coroutines/Flow
- Navigation Compose
- Room
- DataStore
- Android Keystore
- BiometricPrompt
- Retrofit/OkHttp oder Ktor
- WebSocket
- WorkManager

---

## 2. Architektur

```text
UI Layer
↓
Domain Layer
↓
Data Layer
↓
REST / WebSocket / Room / Keystore
```

Repositories:

- AuthRepository
- PairingRepository
- ObjectCatalogRepository
- StateRepository
- DashboardRepository
- CommandRepository
- NotificationRepository
- SettingsRepository
- DiagnosticsRepository

---

## 3. Hauptbereiche

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
→ QR scannen
→ Server prüfen
→ Schlüssel erzeugen
→ Gerät freigeben
→ App-Sperre
→ Dashboard erstellen
```

---

## 4. Objektbrowser

Funktionen:

- Suche
- Filter Raum
- Filter Adapter
- Filter Sensor/Aktor
- Filter Rolle
- Favoriten
- nur schreibbare Objekte
- Live-Vorschau
- Rechte anzeigen
- Widgetvorschläge

---

## 5. Dashboard-Editor

Funktionen:

- mehrere Dashboards
- Widget hinzufügen
- Drag & Drop
- Skalierung
- Titel/Icon
- Löschen
- Duplizieren
- Rückgängig/Wiederholen
- Startdashboard
- Compact/Medium/Expanded
- lokale Entwürfe
- serverseitige Revisionen

---

## 6. Widget-Zustände

```text
LOADING
LIVE
STALE
OFFLINE
NO_PERMISSION
OBJECT_MISSING
COMMAND_PENDING
COMMAND_CONFIRMED
COMMAND_FAILED
```

---

## 7. Sicherheit

- privater Schlüssel im Keystore
- Tokens verschlüsselt
- App-Sperre
- Biometrie
- sensible Screens optional gegen Screenshots
- Task-Switcher-Vorschau schützen
- keine Secrets in Logs
- Session-Widerruf
- Rechteentzug live anwenden

---

## 8. Offline

Lokal speichern:

- Dashboards
- Widgets
- Objektkatalog
- letzte Werte
- Meldungen
- Einstellungen

Offline blockieren:

- Aktorbefehle
- Alarmquittierung
- Pairing
- kritische Aktionen

---

## 9. Build-Varianten

```text
debug
staging
release
```

Keine Produktionssecrets im APK.

---

## 10. App-MVP

- QR-Pairing
- Keystore-Schlüssel
- Challenge-Login
- App-Sperre
- Objektkatalog
- Dashboard-Editor
- Live-Werte
- Schalter
- Offline-Cache
- Diagnose
- Geräteabmeldung
