# DEPLOYMENT

Betriebsanleitung für Fernzugriff (Reverse Proxy/TLS, VPN) und ein Abgleich der Umsetzung gegen
OWASP API Security Top 10 und OWASP MASVS. Ergänzt [SECURITY.md](SECURITY.md) (Bedrohungsmodell)
um die konkrete Netzwerk-Konfiguration.

## 1. Verbindungsvarianten (siehe MASTERKONZEPT.md §4)

Priorität, empfohlen in dieser Reihenfolge:

1. **LOCAL_ONLY** – Adapter nur im lokalen Netz erreichbar, keine Portfreigabe im Router. Für die
   meisten Haushalte ausreichend; die App funktioniert dann nur im WLAN zu Hause.
2. **VPN** – Smartphone verbindet sich per VPN (z. B. WireGuard, Tailscale, ioBroker-VPN-Adapter)
   ins Heimnetz, danach wie LOCAL_ONLY. Kein Port muss am Router geöffnet werden.
3. **REVERSE_PROXY** – Adapter bleibt intern auf `localhost`/LAN, ein Reverse Proxy (nginx, Caddy,
   Traefik) terminiert TLS und leitet nur `/api/v1/*` und `/ws/v1` weiter.
4. **RELAY_FUTURE** – noch nicht implementiert (siehe TODO.md/ROADMAP.md).

**Nicht direkt ins Internet freigeben:** ioBroker Admin, Redis, Simple API, allgemeiner
ioBroker-WebSocket, beliebige Adapterports, Object-/State-DB. Nur der `mobile-control`-Port
(Standard `8095`, konfigurierbar) darf – falls Variante 3 gewählt wird – überhaupt von außen
erreichbar sein, und auch dann nur über den Reverse Proxy, nie direkt.

## 2. Reverse Proxy (Variante 3)

Der Adapter selbst terminiert kein TLS (siehe Kommentar in `src/main.ts`, `onReady()`). Das ist
bewusst so gelöst: TLS-Zertifikatsverwaltung (Let's Encrypt-Renewal etc.) ist Aufgabe eines
etablierten Reverse Proxys, nicht des Adapters.

Wichtig: `/ws/v1` ist ein WebSocket-Upgrade – der Proxy muss `Upgrade`/`Connection`-Header
durchreichen, sonst funktionieren Live-Werte nicht.

### nginx

```nginx
server {
    listen 443 ssl http2;
    server_name home.example.net;

    ssl_certificate     /etc/letsencrypt/live/home.example.net/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/home.example.net/privkey.pem;

    location /mobile-control/api/ {
        proxy_pass http://127.0.0.1:8095/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /mobile-control/ws/ {
        proxy_pass http://127.0.0.1:8095/ws/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 3600s;   # keep long-lived WS connections open
    }
}
```

Passe `publicUrl` in den Adapter-Einstellungen entsprechend an, z. B.
`https://home.example.net/mobile-control` – dieser Wert wird 1:1 in den Pairing-QR-Code
eingebettet (siehe `PairingService`), damit die App weiß, wohin sie sich verbinden soll.

### Caddy (automatisches TLS)

```caddyfile
home.example.net {
    handle /mobile-control/api/* {
        uri strip_prefix /mobile-control
        reverse_proxy 127.0.0.1:8095
    }
    handle /mobile-control/ws/* {
        uri strip_prefix /mobile-control
        reverse_proxy 127.0.0.1:8095
    }
}
```

Caddy reicht WebSocket-Upgrades standardmäßig durch, keine Sonderkonfiguration nötig.

### Rate Limiting auf Proxy-Ebene

Der Adapter hat bereits ein eigenes Rate-Limit pro Gerät für Kommandos (`rateLimitPerMinute` in
den Einstellungen). Zusätzlich empfiehlt sich ein IP-basiertes Rate-Limit auf Proxy-Ebene gegen
Login-/Pairing-Brute-Force, z. B. mit nginx' `limit_req_zone` auf `/api/v1/auth/` und
`/api/v1/pairing/`.

## 3. VPN (Variante 2)

Kein spezielles Adapter-Setup nötig – die App verbindet sich, sobald das Gerät im VPN ist, wie im
lokalen Netz (`serverUrl` im QR-Code zeigt auf die interne Adresse). Empfehlenswert:
WireGuard (z. B. über den ioBroker-Host selbst oder einen separaten Router/Gateway) oder
Tailscale/Headscale für einfaches Zero-Config-Mesh-VPN ohne Portfreigabe.

## 4. Firewall-Checkliste

- [ ] Adapter-Port (`8095` Standard) ist **nicht** direkt am Router auf das Internet weitergeleitet
- [ ] Falls Reverse Proxy: nur Port 443 (TLS) des Proxys ist von außen erreichbar
- [ ] ioBroker-Admin-Port (meist 8081), Simple-API, Redis, roher ioBroker-WebSocket sind **nicht**
      von außen erreichbar
- [ ] `requireAdminApproval` bleibt aktiviert (Standard), außer in kontrollierten Testumgebungen
- [ ] `publicUrl` in den Adaptereinstellungen entspricht der tatsächlich erreichbaren Adresse

## 5. OWASP API Security Top 10 (2023) – Abgleich

| # | Risiko | Umsetzung |
|---|---|---|
| API1 | Broken Object Level Authorization | Jede Objekt-Referenz läuft über `CatalogService.resolveAuthorized()` – öffentliche UUID → interne State-ID nur nach Rechteprüfung, für REST, WebSocket-Subscribe und Commands gleichermaßen (siehe `test/catalog.test.ts`) |
| API2 | Broken Authentication | Geräte-Schlüsselpaar (EC P-256) + Challenge-Response, kurzlebige Access-Tokens (JWT), rotierende Refresh-Tokens mit Wiederverwendungserkennung (siehe `test/auth.test.ts`, `test/sessions.test.ts`) |
| API3 | Broken Object Property Level Authorization | Read/Write pro Objekt getrennt, Wertebereiche (`min`/`max`/`step`/`allowedValues`) serverseitig geprüft (`CommandsService`) |
| API4 | Unrestricted Resource Consumption | Rate-Limiting pro Gerät (`RateLimiter`), Payload-Größenlimit (`express.json({limit:'256kb'})`), WebSocket-Heartbeat mit Timeout |
| API5 | Broken Function Level Authorization | Admin-Funktionen (Benutzer/Rollen/Freigaben/Sessions) laufen ausschließlich über den separaten ioBroker-Admin-Message-Kanal, nicht über die App-REST-API – unterschiedliche Vertrauensgrenzen bewusst getrennt |
| API6 | Unrestricted Access to Sensitive Business Flows | Pairing erfordert kurzlebiges Einmal-Secret + (standardmäßig) Admin-Bestätigung; kein Self-Service-Zugriff auf beliebige Objekte |
| API7 | Server Side Request Forgery | Adapter nimmt keine vom Client kontrollierten URLs entgegen, die er selbst abruft – kein SSRF-Angriffsfläche identifiziert |
| API8 | Security Misconfiguration | `system.*` und weitere Namespaces sind hart blockiert (`isBlockedStateId`), Secrets nie in Klartext-Config, JWT-Secret wird generiert statt fest im Code |
| API9 | Improper Inventory Management | `/api/v1`, `/ws/v1` API-Versionierung von Anfang an, `docs/openapi.yaml` als maschinenlesbare Referenz |
| API10 | Unsafe Consumption of APIs | Adapter konsumiert nur die eigene ioBroker-Instanz, keine externen APIs im MVP |

Ein gezielter interner Review-Durchgang (kein Ersatz für ein externes Pentest) hat drei reale
Lücken gefunden und behoben: fehlende zeitkonstante Vergleiche bei Pairing-Secret/Refresh-Token-
Hashes (`safeEqualHex`, war implementiert aber nicht überall verwendet), Wertprüfung akzeptierte
stillschweigend beliebige Objekte bei State-Typen außerhalb boolean/number/string, und WebSocket-
Sessions wurden nach der initialen Authentifizierung nicht mehr auf Ablauf geprüft (nur explizite
Widerrufe wurden aktiv gepusht). Alle drei sind behoben und getestet.

**Bekannte Lücken (siehe TODO.md):** kein automatisiertes Penetrationstesting/externes Security-Review
durchgeführt; kein IP-basiertes Rate-Limiting auf Adapterebene (nur pro Gerät) – auf Proxy-Ebene
nachrüsten (siehe oben).

## 6. OWASP MASVS – Android-App

| Kategorie | Umsetzung |
|---|---|
| MASVS-STORAGE | Tokens in `EncryptedSharedPreferences`, privater Schlüssel nie exportierbar im Android Keystore |
| MASVS-CRYPTO | EC P-256/ECDSA-SHA256 über den Android Keystore, kein selbst-implementiertes Krypto-Primitive |
| MASVS-AUTH | Challenge-Response pro Login, kurzlebige Access-Tokens, automatisches Refresh, App-Sperre (PIN/Biometrie) |
| MASVS-NETWORK | HTTPS erzwungen (Pairing verlangt `https://` in `serverUrl`); Certificate Pinning ist **vereinfacht** (Post-Handshake-Fingerprint-Vergleich mit Warnung statt hartem `CertificatePinner` – siehe `ServerFingerprintChecker.kt` und `android-app/README.md`) |
| MASVS-PLATFORM | `FLAG_SECURE` auf Pairing-/Settings-Screens, keine sensiblen Daten in Logcat (HTTP-Logging nur `BASIC`-Level) |
| MASVS-CODE | ProGuard/R8 für Release-Builds vorbereitet (`proguard-rules.pro`), keine hartkodierten Secrets im APK |
| MASVS-RESILIENCE | Kein Root-Detection/Tamper-Detection implementiert (bewusst außerhalb des MVP-Scopes) |

**Bekannte Lücken:** kein echtes Certificate Pinning, keine Root-/Emulator-Erkennung, keine
Android-Instrumentierungstests gegen die Sicherheitsmechanismen (nur Backend hat automatisierte
Tests, siehe TODO.md Phase 17).

## 7. Verlauf (optional)

`GET /api/v1/history` liefert historische Werte, wenn in den Adapter-Einstellungen eine
History-Adapter-Instanz eingetragen ist (z. B. `history.0`, `sql.0`, `influxdb.0`). Der Adapter
nutzt dafür die in der ioBroker-Welt übliche `sendTo(instanz, 'getHistory', {...})`-Konvention,
die diese Adapter implementieren. Ohne konfigurierte Instanz ist die Verlaufsfunktion einfach
deaktiviert (kein Fehler) – zusätzlich muss die jeweilige Objektfreigabe `history: true` gesetzt
haben, sonst liefert die API `READ_FORBIDDEN`.

## 8. Vor dem produktiven Betrieb

1. ~~`YOUR_GITHUB_USER`-Platzhalter ersetzen~~ erledigt (Repo: github.com/mrder/iobroker.mobile-control)
2. Adapter-Einstellungen prüfen: `publicUrl`, `requireAdminApproval`, TTLs, Rate-Limit, optional `historyInstance`
3. Reverse Proxy/VPN gemäß Abschnitt 2/3 einrichten und testen
4. Erstes Pairing über die Admin-Oberfläche durchspielen, Rechte-Vorschau (Tab "Benutzer & Rollen"
   → Lupe-Symbol) nutzen um zu verifizieren, was der neue Nutzer tatsächlich sieht
5. Audit-Log (Tab "Audit") nach dem ersten Testlauf stichprobenartig prüfen

## 9. Fehlersuche beim Livetest (ohne SSH-Zugriff auf den ioBroker-Host)

Falls der ioBroker-Host nur lokal erreichbar ist und absichtlich nicht per SSH nach außen
freigegeben wird, lassen sich Backend- und App-Logs trotzdem einfach als Text kopieren:

**Backend-Logs (ioBroker):**
1. Admin-Oberfläche → Instanz "mobile-control" → Zahnrad → Log-Level für den Testzeitraum auf
   `debug` stellen (Standard ist `info`, siehe `io-package.json` `loglevel`)
2. Admin-Oberfläche → Tab "Log" (oder `iobroker logs mobile-control.0` direkt am Host) → nach
   "mobile-control" filtern → relevanten Ausschnitt kopieren

**App-Logs (Android), ohne Android Studio:**
1. Nur "Android SDK Platform-Tools" herunterladen (ZIP, enthält `adb.exe`, keine 10 GB wie die volle
   IDE): https://developer.android.com/tools/releases/platform-tools
2. Am Handy: Einstellungen → Über das Telefon → 7× auf "Build-Nummer" tippen (aktiviert
   Entwickleroptionen) → Entwickleroptionen → USB-Debugging aktivieren
3. Handy per USB anschließen, am Gerät den Debugging-Popup bestätigen
4. `adb logcat -v time` im entpackten platform-tools-Ordner ausführen; die Debug-APK loggt
   OkHttp-Requests (Methode/URL/Status, Tag `OkHttp`) automatisch, unbehandelte Abstürze erscheinen
   unter dem Tag `AndroidRuntime` - relevanten Ausschnitt kopieren
5. Zum Eingrenzen: `adb logcat -v time | findstr /I "mobilecontrol OkHttp AndroidRuntime"`
   (PowerShell/cmd) filtert auf die relevanten Zeilen
