# Changelog

Format angelehnt an [Keep a Changelog](https://keepachangelog.com/), Versionsschema siehe
[README.md "Branches & Versionierung"](README.md#branches--versionierung): `master` führt
Zwischenversionen `0.0.x`, ein Release auf `main` erhält `0.x.0`.

## [Unreleased]

Noch nichts nach `main` released.

## [0.0.57] - master, Testbuild

Nächster, live bestätigter Rückschritt behoben: die Verbindung baute sich zwar jedes Mal
erfolgreich auf, brach dann aber alle 15-20 Sekunden wieder ab und neu, dauerhaft in Dauerschleife.

- **Symptom**: Im Protokoll erfolgreiche Verbindungsaufbauten im Sekundentakt (~15-20s), die
  Anmeldung klappte jedes Mal, trotzdem kein stabiler Live-Betrieb - Schalterstatus blieb weiter
  unzuverlässig, „letzte Verbindung" sprang nie auf „gerade eben".
- **Ursache**: Zusätzlich zum eigenen, bereits funktionierenden Herzschlag-Mechanismus (JSON-
  Nachricht alle 30s) hatte der zugrunde liegende Netzwerk-Client noch eine eigene, niedrigstufige
  automatische Ping/Pong-Prüfung alle 20 Sekunden aktiv. Kam darauf keine rechtzeitige Antwort
  zurück (z. B. weil Mobilfunknetz/Router diese Kontrollpakete nicht zuverlässig durchreichen),
  hat der Netzwerk-Client die Verbindung selbst als gestört eingestuft und sofort gekappt - unab-
  hängig vom eigenen, tatsächlich funktionierenden Herzschlag.
- **Fix**: Die zusätzliche automatische Ping/Pong-Prüfung entfernt. Die App verlässt sich jetzt
  ausschließlich auf ihren eigenen, bereits bewährten Herzschlag-Mechanismus zur Erkennung toter
  Verbindungen.

## [0.0.56] - master, Testbuild

Echten, live bestätigten Rückschritt aus v0.0.55 behoben, plus die App-Versionsnummer endlich
sichtbar mitgeführt.

- **Symptom**: Nach dem Speicherleck-Fix aus v0.0.55 blieb die Live-Verbindung irgendwann
  dauerhaft tot hängen - „letzte Verbindung" in den Einstellungen stand 6 Stunden in der
  Vergangenheit, das Protokoll zeigte wiederholt „disconnected(willReconnect=true)", ohne dass
  sich je wieder etwas tat.
- **Ursache**: Öffnet sich eine Verbindung, bekommt aber nie irgendeine Antwort auf ihre
  Anmeldung (weder „auth_ok" noch einen Fehler), gab es dafür keine Zeitüberschreitung - nichts
  hat das je bemerkt. Vor dem Speicherleck-Fix wurde das zufällig überdeckt: Ein anderer,
  unkoordinierter `connect()`-Aufruf hat irgendwann eine zweite, funktionierende Verbindung
  daneben aufgebaut. Durch die neue Sperre gegen doppelte Verbindungen (v0.0.55) konnte das nicht
  mehr passieren - die Lücke wurde dadurch erst sichtbar.
- **Fix**: Neue 10-Sekunden-Zeitüberschreitung fürs Anmelden nach dem Verbindungsaufbau - bleibt
  die Antwort aus, gibt die App den Versuch auf und startet automatisch neu, genau wie bei jeder
  anderen Verbindungsstörung.
- **Außerdem**: Die App-Versionsnummer in den Einstellungen stand immer auf „0.1.0-debug", egal
  welcher Build installiert war - wird jetzt bei jedem Release mit der Adapter-Version
  mitgeführt.

## [0.0.55] - master, Testbuild

Echten, live bestätigten Fehler behoben: ein Verbindungs-Speicherleck bei der Live-Verbindung,
gefunden über die Logs auf dem Tablet während eines echten Schalter-Tests.

- **Symptom**: Schalter blieb nach dem Drücken dauerhaft bei „…" hängen, obwohl der Befehl
  tatsächlich funktioniert hatte (nach Neuladen der Seite korrekt sichtbar).
- **Ursache**: `connectRealtime()` wird von mehreren Stellen aus aufgerufen (nach PIN-Entsperrung,
  beim Kaltstart, vom Hintergrunddienst für Alarm-Benachrichtigungen) - ohne jede Sperre gegen
  doppelte Aufrufe. Jeder Aufruf hat bedingungslos eine komplett neue WebSocket-Verbindung
  geöffnet, ohne eine bestehende zu schließen. Im Log zeigte sich das als fast gleichzeitige,
  doppelte Verbindungsaufbauten. Nur die zuletzt geöffnete Verbindung wurde von der App noch
  referenziert - Live-Abonnements und Befehlsbestätigungen konnten dadurch an eine frühere,
  verwaiste Verbindung gebunden bleiben, die serverseitig zwar noch offen war, aber von der App
  nicht mehr beachtet wurde.
- **Fix**: `connect()` ist jetzt gegen Mehrfachaufrufe abgesichert (kein Effekt, wenn bereits eine
  Verbindung aktiv ist oder gerade neu aufgebaut wird), und eine übrig gebliebene Verbindung wird
  vor dem Öffnen einer neuen ausdrücklich geschlossen.

## [0.0.54] - master, Testbuild

Echten Fehler behoben, live direkt nach dem Signatur-Update gefunden (aus den echten Daten auf
dem Tablet bestätigt): Geräte-Ordner im Objektbaum landeten als lose Einzelelemente im falschen
übergeordneten Ordner.

- **Symptom**: Unter Ordnern wie „zigbee" tauchten einzelne Einträge auf (z. B. der Gerätename
  eines Bewegungsmelders), die eigentlich als eigener Geräte-Ordner erscheinen sollten - optisch
  vermischt mit den echten Messwerten anderer Geräte eine Ebene tiefer.
- **Ursache**: `effectiveCatalog()` im Backend hat Container-Objekte (Ordner/Geräte/Kanäle) zwar
  genutzt, um Ordnernamen aufzulösen, sie aber nie aus der Liste der echten Datenpunkte
  ausgeschlossen. Griff eine geräteweite Freigaberegel (die eigentlich nur die Kind-Objekte
  freigeben soll) zufällig auch auf das Container-Objekt selbst, lief dieses über denselben Pfad
  wie ein echter Messwert und verlor dabei sein eigenes ID-Segment aus dem Pfad.
- **Fix**: Container-Objekte werden jetzt explizit aus der Liste der Katalog-Objekte
  ausgeschlossen - sie dienen ausschließlich der Namens-Auflösung für `folderNames`.
- Neuer Regressionstest in `test/catalog.test.ts`, der genau dieses Freigabe-Szenario nachbildet.

## [0.0.53] - master, Testbuild

Große Sicherheitshärtung, live angefragt aus Sorge um Heimautomatisierungs-Sicherheit: Selbst mit
einem gestohlenen Zugriffs-Token sollte niemand einen Befehl per curl/Postman wiederholen oder
fälschen können.

- **Signatur pro Anfrage**: Jede authentifizierte Anfrage (REST und die WebSocket-Verbindung)
  erfordert jetzt zusätzlich eine Signatur mit dem hardwaregestützten Keystore-Schlüssel des
  Geräts. Ein gestohlener Bearer-Token allein reicht nicht mehr aus - jede Signatur ist außerdem
  nur einmal gültig (Zeitstempel + Einmal-Nummer + serverseitiger Wiederholungsschutz), eine
  mitgeschnittene Anfrage kann also nicht später erneut abgeschickt werden.
- **TLS-Zertifikats-Pinning**: Die App merkt sich beim Koppeln live das tatsächliche
  Server-Zertifikat und vertraut ab dann nur noch genau diesem - schließt die Lücke, die reines
  HTTPS nicht abdeckt (ein technisch vertrauenswürdiges, aber untergeschobenes Zertifikat). Siehe
  DEPLOYMENT.md-Abschnitt "Zertifikats-Pinning" für die nötige Reverse-Proxy-Einrichtung
  (`certbot --reuse-key`, sonst bricht die Kopplung bei jeder Zertifikatserneuerung).
- **Kompatibilität**: Bereits gekoppelte Geräte funktionieren bei der Signaturpflicht sofort
  weiter (ihr öffentlicher Schlüssel ist dem Server schon bekannt). Das Zertifikats-Pinning greift
  für ein bestehendes Gerät erst nach der nächsten Neu-Kopplung - bis dahin einfach inaktiv, keine
  bestehende Kopplung wird dadurch kaputt gemacht.
- **Wichtig**: Backend und App müssen zusammen aktualisiert werden. Eine alte App-Version gegen
  dieses Backend (oder umgekehrt) schlägt bei jeder authentifizierten Anfrage fehl.

## [0.0.52] - master, Testbuild

Echte Ursache dafür gefunden, warum die App nach einer Weile Inaktivität komplett tot wirkte
(leere Dashboards, leere Objektliste, keine Fehlermeldung) - live per Logcat bestätigt.

- **Kernfehler**: `GET /dashboards` → 401, danach `POST /auth/refresh` → ebenfalls 401. Der
  Zugriffs-Token war abgelaufen, und der Erneuerungs-Token wurde vom Server auch abgelehnt. Die
  App hat daraufhin einfach stillschweigend aufgegeben - für immer, ohne jede Möglichkeit zur
  Selbstheilung. Neu: Wird die Erneuerung abgelehnt, versucht die App jetzt automatisch eine
  vollständige Neuanmeldung mit dem bereits vorhandenen Sicherheitsschlüssel des Geräts (derselbe
  Mechanismus wie am Ende der Kopplung, nur ohne erneutes QR-Scannen). Ist das Gerät weiterhin
  berechtigt, erholt sich die Sitzung dadurch von selbst. Nur wenn auch das fehlschlägt, wird
  jetzt korrekt zur erneuten Kopplung aufgefordert.
- **Wahrscheinliche Ursache behoben**: Erneuerungsversuche liefen bisher unsynchronisiert - zwei
  etwa gleichzeitig fehlschlagende Anfragen konnten unabhängig voneinander mit demselben, noch
  nicht erneuerten Token erneuern wollen. Der eigene Wiederverwendungsschutz des Servers wertet
  das korrekterweise als gestohlenen Token und sperrt die ganze Sitzung dafür - vermutlich genau
  das, was hier passiert ist. Erneuerungsversuche laufen jetzt nacheinander ab.
- **Nebeneffekt behoben**: Die Datenbank-Änderung des letzten Updates (v0.0.51) hat den
  Dashboard-/Objekt-Zwischenspeicher auf dem Gerät gelöscht (Daten waren nie auf dem Server weg,
  nur die lokale Kopie). Die App löscht bei zukünftigen Updates keine lokalen Daten mehr
  automatisch - künftige Datenbankänderungen brauchen eine echte Migration.

## [0.0.51] - master, Testbuild

Echte Ursache hinter "nichts geht" / "Adapter nicht verbunden" / "Objektnamen fehlen" gefunden:
ein Protokollfehler zwischen App und Backend, keine wacklige Verbindung.

- **Kernfehler**: Die Live-Verbindung (WebSocket) der App hat sich über den Token als
  URL-Parameter angemeldet - das Backend liest diesen Parameter aber nie. Es erwartet
  stattdessen eine bestimmte Nachricht direkt nach Verbindungsaufbau und trennt die Verbindung
  zwangsweise, wenn sie nicht innerhalb von 5 Sekunden ankommt. Die App hat diese Nachricht nie
  gesendet - die Verbindung wirkte "offen", war aber nie wirklich authentifiziert. Dadurch wurden
  Live-Wert-Updates, Befehlsbestätigungen und Abonnierungen die ganze Zeit vom Server
  stillschweigend abgelehnt, und die Verbindungsanzeige des Adapters sprang deshalb immer wieder
  auf "nicht verbunden" zurück (sie zählt nur echt authentifizierte Verbindungen). Die App sendet
  jetzt die korrekte Anmelde-Nachricht und wartet auf die Bestätigung des Servers, bevor sie die
  Verbindung als bereit behandelt.
- **Verwandter Fehler**: Ordnernamen im Objektbaum wurden nur im Arbeitsspeicher gehalten und bei
  jedem App-Neustart geleert - ein einziger fehlgeschlagener Katalog-Abruf (z.B. wegen genau
  dieses Verbindungsproblems) hat sie für den Rest der Sitzung gelöscht. Das sah exakt wie ein
  Rückfall der bereits behobenen Objektbaum-Namen aus, obwohl die Lösung selbst nie kaputt war.
  Ordnernamen werden jetzt wie der Rest des Katalogs zwischengespeichert (neue Room-Tabelle) und
  überstehen einen fehlgeschlagenen Abruf.

## [0.0.50] - master, Testbuild

Sicherheitsfehler bei der PIN-Prüfung behoben, Sperre nach Fehlversuchen ergänzt, doppeltes
Zurück zum Beenden ergänzt, und einen echten "Schalter tut nichts"-Fehler behoben.

- **Sicherheitsfehler (live gefunden)**: Eine falsch eingegebene PIN konnte die App trotzdem
  kurzzeitig entsperren. Ursache war eine Race Condition: Die Entsperr-Navigation reagierte auf
  einen `wrongPin`-Zustand, der zum Zeitpunkt der Prüfung noch seinen alten (falschen) Wert
  `false` hatte, weil die eigentliche (asynchrone) PIN-Prüfung noch nicht fertig war. Die
  PIN-Prüfung wird jetzt direkt abgewartet (`suspend fun verifyPin(): Boolean`), entsperrt wird
  nur noch bei tatsächlich korrektem Ergebnis.
- **Neu**: PIN-Sperre nach 3 falschen Versuchen in Folge für 10 Minuten, mit Live-Countdown auf
  dem Sperrbildschirm. Die Sperre wird verschlüsselt gespeichert (nicht nur im Speicher), damit
  ein erzwungenes Schließen und Neuöffnen der App sie nicht umgeht.
- **Neu**: Doppeltes Zurück-Drücken erforderlich, um die App vom Startbildschirm aus zu beenden
  (mit kurzem Hinweis-Toast beim ersten Drücken).
- **Fehler behoben**: Ein Schalter-Widget konnte beim Drücken scheinbar komplett wirkungslos
  bleiben (kein "…", kein "✗", einfach nichts). Ursache: Schlug die anfängliche Sende-Anfrage an
  den Server fehl (z. B. direkt nach dem Entsperren, während gerade ein Token erneuert wurde),
  wurde der Fehlschlag intern zwar als "abgelehnt" vermerkt, aber die zugehörige Befehls-ID nie
  an das Widget zurückgegeben - das Widget konnte den Fehlschlag dadurch nie anzeigen und blieb
  einfach im alten Zustand hängen.

## [0.0.49] - master, Testbuild

Echten, live gefundenen Fehlalarm behoben: Schalter-Widget zeigte "fehlgeschlagen" für einen
Befehl, der tatsächlich funktioniert hatte.

- **Symptom**: Ein Zigbee-Aktor-Schalter blieb nach dem Antippen bei "…" (Bearbeiten) hängen,
  zeigte danach ein rotes "✗" (fehlgeschlagen) - beim Neuladen der Seite stand der Schalter
  aber korrekt auf "EIN". Der Befehl hatte also funktioniert, nur die Rückmeldung kam zu spät.
- **Ursache**: Das Backend wartet nach dem Senden eines Befehls auf die eigene Bestätigung des
  Geräts (`ack: true`), bevor es "confirmed" meldet - bisher nur 10 Sekunden, danach "timeout".
  Ein Zigbee-Mesh-Gerät (Funk-Hop-Wiederholung, Aufwachen aus dem Schlafmodus) kann dafür
  durchaus länger brauchen, auch wenn der Befehl selbst erfolgreich ankam.
- **Fix**: Bestätigungswartezeit im Backend (`CommandsService.CONFIRMATION_TIMEOUT_MS`) von
  10s auf 25s angehoben. Die App-eigene lokale Wartezeit (`CommandRepositoryImpl.COMMAND_TIMEOUT_MS`)
  passend von 15s auf 30s erhöht, damit sie nicht vorher selbst aufgibt und einen unnötigen
  Zweitbefehl sendet, bevor die korrekte Rückmeldung vom Backend überhaupt ankommen konnte.

## [0.0.48] - master, Testbuild

Aufräumen, keine funktionale Änderung.

- Die temporäre Diagnose-Log-Zeile aus `effectiveCatalog()` (seit v0.0.44) wieder entfernt -
  mit v0.0.47 live bestätigt, dass der Objektbaum-Fix funktioniert: 13033 Container gefunden
  und korrekt benannt (vorher 0), z.B. `zigbee.0.00124b0024510164` → "SNZB-03 Bewegungsmelder
  Briefkasten".

## [0.0.47] - master, Testbuild

Echten, live gefundenen Bug behoben: Ordner-/Gerätenamen fehlten im Objektbaum.

- **Ursache**, über zwei Runden Live-Diagnose gefunden: `getForeignObjectsAsync('*')` liefert
  auf dieser Installation ohne explizite Typ-Angabe stillschweigend nur `state`-Objekte
  zurück - 38604 Objekte, alle vom Typ `state`, 0 Container, obwohl Channel-/Device-/
  Ordner-Objekte definitiv existieren (bestätigt am Zigbee-Gerät selbst, Typ `device`, mit
  korrekt gesetztem Namen). Dieses Verhalten ist nirgends dokumentiert.
- **Fix**: `ExposureService.browseObjectTree()` ruft `getForeignObjectsAsync('*', type)` jetzt
  einmal pro Objekttyp auf (`state`, `channel`, `device`, `folder`, `adapter`, `instance`) und
  führt die Ergebnisse zusammen, statt sich auf den ungetypten "alle Typen"-Aufruf zu
  verlassen.
- Regressionstest ergänzt, der genau dieses reale Verhalten nachstellt (ein Mock, der bei
  einem Aufruf ohne Typ-Angabe nur States liefert), damit das nicht unbemerkt wieder
  kaputtgeht.
- Betrifft alle drei Stellen, die vorher nur rohe IDs zeigten: Admin-Tab "Objektfreigaben",
  der Objektkatalog der App, und der "Widget hinzufügen"-Dialog im Dashboard.

## [0.0.46] - master, Testbuild

Reiner Diagnose-Build, Folge-Untersuchung zu v0.0.44/v0.0.45.

- Die erste Diagnose-Zeile (v0.0.44) zeigte live: `browseObjectTree()` fand 37514 Objekte
  insgesamt, aber **0 Container** (Ordner/Geräte) - trotz live bestätigt vorhandener Ordner
  mit echt vergebenen Namen in der App.
- Neue Log-Zeile `mobile-control: DIAG browseObjectTree:` in `ExposureService.browseObjectTree()`
  zählt jetzt die rohen ioBroker-Objekttypen (`obj.type`) auf, bevor irgendeine Filterung
  passiert, um zu sehen ob Channel-/Device-/Ordner-Objekte im Rohergebnis von
  `getForeignObjectsAsync('*')` wirklich fehlen oder nur beim Parsen scheitern.
- Nebenbei: die neue Zähl-Schleife läuft jetzt pro Eintrag isoliert (try/catch), damit ein
  einzelnes fehlerhaftes Objekt beim Zählen nicht dieselbe Klasse von Bug reproduziert, die
  `browseObjectTree()` selbst schon behebt (siehe Regressionstest
  "a single malformed object is skipped").

## [0.0.45] - master, Testbuild

Echten, live gefundenen Deadlock behoben.

- **Ursache**: Sobald der Zugriffstoken eines Widgets abgelaufen war, hat `TokenAuthenticator`
  ihn per `runBlocking` über denselben geteilten `OkHttpClient` erneuert, der auch die
  dauerhafte Live-WebSocket-Verbindung hält. OkHttp begrenzt gleichzeitige Anfragen pro Host
  (Standard: 5) über alle Aufrufe eines Clients, auch offene WebSockets - war das Limit
  erreicht, brauchte der blockierte Refresh-Aufruf einen Verbindungsplatz, den nur sein
  eigener (blockierter) Thread hätte freigeben können. Echter Deadlock, kein Timeout.
- **Symptome, die dadurch erklärt sind**: ein Web-Seite-Widget blieb dauerhaft bei "Lädt…"
  hängen; das Speichern im Dashboard-Editor blieb scheinbar wirkungslos (derselbe Deadlock,
  ausgelöst durch den `PUT /dashboards`-Call).
- **Fix**: `TokenAuthenticator` nutzt jetzt einen komplett eigenen `OkHttpClient` (eigener
  Verbindungspool, kein Authenticator auf sich selbst, kein Bearer-Header nötig) für den
  `auth/refresh`-Aufruf - kann nie mehr mit dem WebSocket um einen Slot konkurrieren.
- Live bestätigt: dasselbe Widget, das vorher endlos hing, scheitert jetzt sofort mit einem
  echten, protokollierten 401 statt zu hängen (die zugehörige Sitzung dieses Testgeräts war
  durch die vielen Testunterbrechungen der letzten Tage inzwischen selbst ungültig geworden -
  ein separates, erwartetes Sitzungsproblem, kein neuer Bug; das Gerät muss neu gekoppelt
  werden).
- Zusätzlich behebt v0.0.43's Tunnel-Timeout-Fix jetzt sauber: `WebViewProxyOverride`s
  Plattform-Callback ist jetzt selbst mit 5s Timeout abgesichert (live beobachtet: kann auf
  manchen WebView-Provider-Builds ausbleiben), und der Tunnel-Aufbau blockiert die
  Seitenanzeige nicht mehr - beides zusätzliche Härtung, nicht die Hauptursache.

## [0.0.44] - master, Testbuild

Reiner Diagnose-Build, kein für Nutzer sichtbares Verhalten geändert.

- Temporäre Log-Zeile in `CatalogService.effectiveCatalog()`: meldet Anzahl der von
  `browseObjectTree()` gefundenen Container-Objekte (Ordner/Geräte) plus eine Stichprobe -
  zur Fehlersuche bei einem live gemeldeten Bug: die neue `folderNames`-Zuordnung (v0.0.43)
  kommt trotz bestätigtem `common.name` am echten Zigbee-Geräteobjekt leer zurück (bestätigt
  über die rohe `/catalog`-Antwort, geloggt via Android-OkHttp BODY-Level-Logging).
- Wird nach Diagnose wieder entfernt.

## [0.0.43] - master, Testbuild

Zwei live gemeldete Folgepunkte zum Tunnel-Feature und zum Objektbaum.

- **Tunnel-Performance**: Eine getunnelte Seite mit eigener Live-Verbindung (Long-Polling) hat
  immer wieder "Verbindung verloren - verbinde neu" gemeldet. Ursache: Der Backend-Fetch-Timeout
  (`FETCH_TIMEOUT_MS`, bisher 10s) und der Android-OkHttp-`callTimeout` (bisher 15s) waren beide
  zu knapp für eine Anfrage, die eine Live-Verbindung berechtigterweise länger offen hält, während
  sie auf das nächste Ereignis wartet. Backend-Timeout auf 45s angehoben; Android nutzt jetzt
  `readTimeout`(50s)/`connectTimeout`(10s)/`writeTimeout`(15s) statt eines pauschalen
  `callTimeout`, der die gesamte Wartezeit begrenzt hätte.
- Bekannte Grenze weiterhin bestehend: eine Zielseite mit echter WebSocket-Verbindung (statt
  Long-Polling) funktioniert nicht durch den Tunnel - `TunnelProxyServer`/`forwardTunnelRequest`
  sind reines Request/Response-HTTP, kein Byte-Relay. Dokumentiert in `docs/TODO.md`.
- **Objektbaum-Namen**: Admin-Tab "Objektfreigaben", der App-eigene Objektkatalog-Bildschirm und
  der "Widget hinzufügen"-Dialog zeigten Ordner (z.B. ein Zigbee-Gerät) bisher nur mit ihrer
  rohen ID an, nicht mit ihrem echten Namen - der an die App ausgelieferte, serverseitig
  gefilterte Katalog enthielt nur einzelne Datenpunkte, nie die Container-Objekte, auf denen der
  Name eigentlich steht. `CatalogService.effectiveCatalog()` liefert jetzt zusätzlich
  `folderNames` (Ordner-ID -> Name) für jeden Ordner mit mindestens einem sichtbaren Objekt
  darin - Ordner ganz ohne Freigabe verraten ihren Namen weiterhin nicht.

## [0.0.42] - master, Testbuild

Neues Feature, live angefragt: alte, nicht mehr genutzte gekoppelte Geräte lassen sich jetzt
im Admin-Tab endgültig löschen.

- Neuer "Löschen"-Button je Gerät (mit `window.confirm()`-Bestätigung, da nicht rückgängig
  machbar) - bisher gab es nur "Widerrufen", was das Gerät für immer in der Liste beließ.
- `DevicesService.delete()` entfernt den Datensatz; der neue `deleteDevice`-Admin-Handler
  in `main.ts` widerruft davor Sitzungen/Tunnel und räumt alle Objekt- (`ExposureRule`) und
  URL-Einbettungs-Zugriffsregeln (`UrlEmbedAccessRule`) auf, die noch auf das Gerät verwiesen
  haben, damit keine verwaisten Regeln zurückbleiben. Audit-Eintrag `device.deleted`.
- `AuditEvent.actorDeviceId` (reines Verlaufsprotokoll) bleibt unangetastet.
- Neuer Integrationstest deckt den vollen Lösch-Ablauf inkl. Regel-Aufräumung ab, neue
  Unit-Tests für `DevicesService.delete()`.

## [0.0.41] - master, Testbuild

Echten Bug behoben, live beim allerersten Test des neuen Tunnel-Modus gefunden: Die
getunnelte Seite zeigte erst weiß, dann wirren/binär aussehenden Text ("Hieroglyphen")
statt des eigentlichen Inhalts.

- **Ursache**: WebView schickt bei jeder Anfrage automatisch `Accept-Encoding: gzip, deflate,
  br`, und der lokale Tunnel-Proxy hat das unverändert an den Aufruf zum Adapter
  weitergereicht. OkHttp entpackt eine gzip-Antwort aber nur automatisch, wenn der Aufrufer
  `Accept-Encoding` **nicht selbst** gesetzt hat - das Weiterreichen von WebViews Header hat
  diese automatische Entpackung also still abgeschaltet. Komprimiert dann irgendetwas
  zwischen Handy und Ziel die Antwort (z.B. ein Reverse-Proxy vor einem öffentlich
  erreichbaren Adapter - der übliche Fall), landen die rohen komprimierten Bytes ohne
  erklärenden `Content-Encoding`-Header direkt bei WebView - und werden als Datenmüll
  angezeigt statt als Seite.
- **Fix**: Für den Hop vom lokalen Proxy zum Adapter wird jetzt explizit
  `Accept-Encoding: identity` (gar keine Komprimierung) angefordert, statt sich auf OkHttps
  rein gzip-beschränkte automatische Entpackung zu verlassen.
- **Zusätzlich**: OkHttp-Logging auf BASIC-Stufe für diesen Client ergänzt (nur
  Methode/URL/Status, keine Header/Body - das Tunnel-Token darf nie ins Logcat), damit eine
  laufende Tunnel-Sitzung beim nächsten Mal per `adb logcat` diagnostizierbar ist.

## [0.0.40] - master, Testbuild

Die "Mini-VPN"-Idee aus dem letzten Release, diesmal wirklich gebaut ("das wollen wir doch
jetzt auch direkt gleich mit haben... möglichst hohe Sicherheitsfeatures... einen
zusätzlichen separaten API-Key"): ein **Tunnel-Modus für Web-Seite-Widgets**, damit eine
lokale Geräte-Oberfläche (plain `http://`, z.B. die Weboberfläche eines Zigbee-Aktors) über
die App erreichbar bleibt, ohne dass das Handy im selben Netz oder per VPN verbunden sein
muss.

- **Backend, separates Zugangsmittel**: `TunnelService` (`src/tunnel/`) erzeugt ein
  kurzlebiges (~10 Minuten), eng auf genau eine vom Admin freigegebene URL-Einbettung
  beschränktes Token (`X-Tunnel-Token`) - bewusst getrennt vom normalen Bearer-Token, wie
  live gewünscht. `forwardTunnelRequest` leitet jede Anfrage an das echte Ziel weiter; der
  Ziel-Origin wird dabei **immer serverseitig aus dem Token abgeleitet, nie vom Client**
  akzeptiert - die zentrale Anti-SSRF-Eigenschaft. Neue Endpunkte `POST /tunnel-token/{id}`
  und `/tunnel/proxy` (vor dem globalen JSON-Body-Parser gemountet, damit jeder
  Content-Type unverändert durchgereicht wird).
- **Android, lokaler Proxy**: ein kleiner selbstgeschriebener HTTP/1.1-Proxy-Server läuft in
  der App (nur Loopback, `127.0.0.1`), `androidx.webkit.ProxyController` leitet das WebView
  für die Dauer einer Tunnel-Sitzung dadurch - nicht nur der erste Seitenaufruf, jede
  Unteranfrage der Seite geht durch den Tunnel.
- **Aktivierung pro Widget**: neuer Schalter "Tunnel (auch ohne WLAN/VPN)" im
  Bearbeiten-Dialog eines Web-Seite-Widgets.
- **Bewusste Scope-Grenze**: nur `http://`-Ziele (ein `https://`-Ziel bräuchte einen rohen
  CONNECT/TLS-Tunnel - ein deutlich größeres Feature, siehe `docs/TODO.md`); kein chunked
  Transfer-Encoding für Request-Bodies (Content-Length reicht für typische kleine
  Formular-/Toggle-Anfragen); Cookies laufen transparent über WebViews eigenes
  CookieManager, kein serverseitiger Cookie-Jar nötig.
- Backend Ende-zu-Ende getestet (echter HTTP-Test-Server im Integrationstest, echter
  Token-Issue-und-Proxy-Durchlauf); die WebView-/ProxyController-Integration selbst braucht
  noch einen echten Test an einem echten lokalen Ziel.

## [0.0.39] - master, Testbuild

Echten, bisher unentdeckten Backend-Bug live gefunden: nach dem Freigeben einer
Channel-Freigabe mit Schreibrecht (ein ganzer Zigbee-Ordner, zum Testen von Aktoren)
erschienen die freigegebenen Objekte überhaupt nicht im Katalog der App - nicht nur nicht
schreibbar, komplett unsichtbar.

- **Ursache lag nicht am Schreibrecht** (geprüft: kein Codepfad in `matchesScope`,
  `AuthorizationService.resolve` oder `CatalogService.effectiveCatalog` behandelt `write`
  anders als `read` für die Aufnahme in den Katalog).
- **Tatsächliches Risiko**: ein einzelnes fehlerhaft geformtes Objekt irgendwo im
  ioBroker-Objektbaum (manche Adapter, darunter zigbee, liefern beobachtet ungewöhnliche
  Objektformen) konnte beim Baumaufbau oder bei der Rechteauflösung eine Exception auslösen -
  ohne Isolierung pro Eintrag riss das den **kompletten Katalog** für diese Anfrage mit, nicht
  nur das eine fehlerhafte Objekt.
- **Fix**: `ExposureService.browseObjectTree()` und `CatalogService.effectiveCatalog()`
  isolieren jetzt jedes Objekt/jeden Eintrag in einem eigenen try/catch - ein fehlerhafter
  Eintrag wird übersprungen und mit seiner genauen Objekt-Id im Adapter-Log protokolliert,
  statt jede andere Kategorie mit runterzureißen.
- **Architekturfrage beantwortet** (live gestellt: "wie wäre es mit einer Art Mini-VPN"): als
  konkrete, sinnvoll abgegrenzte Idee in `docs/TODO.md` (Phase 16) dokumentiert - ein
  Transport-Tunnel über den bestehenden authentifizierten Kanal statt Content-Rewriting, mit
  demselben Freigabemodell wie jetzt. Diesmal nicht umgesetzt.

## [0.0.38] - master, Testbuild

Echten, bisher unentdeckten Bug live gefunden: ein Web-Seite-Widget zu einem lokalen Gerät per
`http://192.168.178.87:8097` scheiterte mit `net::ERR_CLEARTEXT_NOT_PERMITTED`.

- **Ursache größer als das eine Widget**: Android blockiert ab API 28 standardmäßig jeglichen
  unverschlüsselten `http://`-Verkehr, und die App hatte dafür keine
  Netzwerksicherheits-Konfiguration. Das hätte theoretisch auch Pairing/Login/die komplette App
  kaputt gemacht, sobald sich jemand direkt mit einem plain-http-Adapter im LAN verbindet, ohne
  Reverse-Proxy davor (ein vollständig unterstütztes, dokumentiertes Setup laut README "im
  lokalen Netz erreichbar") - ist bisher nur nicht aufgefallen, weil hier gegen eine per HTTPS
  erreichbare Instanz getestet wird.
- **Fix**: Neue `network_security_config.xml`, die Cleartext-Verkehr app-weit erlaubt -
  passend zum tatsächlichen Vertrauensmodell dieser App (selbst gehostetes LAN-Tool, vom Admin
  freigegebene Ziele, kein Store-Vertrieb).
- **Zusätzlich**: echte Fehlererkennung beim WebView-Laden ergänzt - eine gescheiterte Seite
  zeigte bisher nur ein leeres/kaputtes Bild statt "Seite nicht verfügbar".
- **Klarstellung zur Architektur** (auf Nachfrage, ob der Adapter als Proxy dient): "URL-Bild"
  läuft bereits über das Backend als Proxy und funktioniert von überall. "Web-Seite" navigiert
  das WebView des Handys bewusst direkt zur LAN-URL (eine ganze Website durch einen
  Einzelressourcen-Proxy zu schleusen ist nicht realistisch umsetzbar) - braucht deshalb auch
  mit diesem Fix weiterhin echte Netzwerk-Erreichbarkeit (gleiches Netz oder VPN, siehe
  "Verbindung & Fernzugriff" im Admin-Tab).

## [0.0.37] - master, Testbuild

Echten Bedienbarkeits-Bug behoben, live gefunden: "ich habe ein neuen URL einbetten wollen,
aber ich könnte nur URL Bild auswählen und nix anderes...".

- **Ursache**: Der Widget-Typ-Auswähler zeigte beim Anlegen eines Widgets aus einer
  URL-Einbettung alle 15 Widget-Typen im Dropdown, inklusive reiner Objekt-Typen (Temperatur,
  Schalter, ...), die für eine URL-Einbettung gar nicht funktionieren. "Web-Seite" ging dabei
  fast am Ende der Liste unter und wurde leicht übersehen - man landete beim vorausgewählten
  "URL-Bild", was dann bei einer echten Website "Kein Bild verfügbar" zeigt.
- **Fix**: Das Dropdown ist jetzt kontextabhängig gefiltert - nur "URL-Bild"/"Web-Seite" bei
  einer URL-Einbettung, nur die objekt-kompatiblen Typen bei einem ioBroker-Objekt.
- Zusätzlich ein Dropdown-Pfeil-Symbol am Typ-Chip ergänzt, damit klar erkennbar ist, dass es
  eine Mehrfachauswahl ist und kein statisches Label.
- **Dokumentiert, nicht umgesetzt**: konkreter Plan in `docs/TODO.md` (Phase 18) für eine
  spätere "App direkt vom Adapter installieren"-Seite (QR-Code, GitHub-Release-Asset statt
  APK im Git eingebettet, Release-Signing als offene Voraussetzung) - live besprochene
  Praxis-Idee, bewusst als Vormerkung statt sofortiger Umsetzung.

## [0.0.36] - master, Testbuild

Echtes UX-Problem behoben, direkt nach den URL-Einbettungs-Freigaben live gefunden: eine ganze
externe Website (nicht eine kleine lokale Geräte-Oberfläche) als "Web-Seite"-Widget einzubetten
rendert bisher immer eine live laufende Mini-WebView in der kleinen Dashboard-Kachel - bei der
Größe meist unlesbar, und lädt die Seite bei jedem Dashboard-Rendern neu ("ich wollte das ja
als Button drin haben, wo ich drauf klicke und die Website öffnen kann").

- **Neuer Schalter "Live-Vorschau in der Kachel"** im Bearbeiten-Dialog eines Widgets, nur bei
  Web-Seite-Widgets sichtbar. Ausgeschaltet zeigt die Kachel nur noch einen "Öffnen"-Button,
  der die Seite erst beim Antippen lädt - in derselben Vollbildansicht, die es im
  Live-Vorschau-Modus schon gab (kein doppelter WebView-Code).
- Bestehende Web-Seite-Widgets behalten die Live-Vorschau standardmäßig bei.
- **Hinweis**: ein bereits als "URL-Bild" angelegtes Widget (das bei einer echten Website
  "Kein Bild verfügbar" zeigt) lässt sich nicht nachträglich auf "Web-Seite" umstellen - dafür
  das Widget löschen und über den zweiten Reiter im Widget-Picker mit dem Typ "Web-Seite" neu
  anlegen.

## [0.0.35] - master, Testbuild

Drei Sicherheits-Sichtbarkeits-Folgepunkte aus einer Live-Nachfrage direkt nach den
URL-Einbettungs-Freigaben ("wo sehen wir nun die verbundenen IPs... damit ggf. Angriffe
sicher gemacht werden").

- **Echter Bugfix, kein neues Feature**: `req.ip` war für **jede** Anfrage falsch, sobald ein
  Reverse-Proxy vor dem Adapter steht (z.B. Docker-Setup mit nginx/Caddy davor, wie beim
  Nutzer selbst) - Express hat ohne `trust proxy`-Konfiguration die Adresse des Proxys
  aufgezeichnet statt die des echten Clients. Das hat still Audit-Log, AbuseGuard und
  `device.lastIp` verfälscht. Jetzt vertraut der Adapter `X-Forwarded-For` nur, wenn die
  direkt verbundene Gegenstelle selbst eine Loopback-/private Adresse ist (Docker-Bridge,
  lokaler Proxy) - ein Angreifer direkt aus dem Internet kann das nicht fälschen, nur ein
  bereits lokal vertrauter Proxy kann eine IP weiterreichen.
- **Audit-Log aufräumen**: neue Buttons "Alles löschen" und "Nur letzte N Tage behalten" im
  Audit-Tab (bisher nur die harte 5000-Einträge-Obergrenze als einzige Begrenzung), dazu eine
  IP-Spalte in der Tabelle - das Feld wurde schon erfasst, war nur nicht sichtbar.
- **Neue Kennzahlen in der Übersicht**: "Gesperrte IPs (live)" und "Fehlversuche (5 Min)",
  direkt aus demselben AbuseGuard-Snapshot berechnet, den das "Auffällige IPs"-Panel darunter
  schon zeigt - deckt weiterhin nur die vier überwachten Auth-/Pairing-Endpunkte ab, keine
  allgemeinen Autorisierungs-Ablehnungen auf der übrigen API.

## [0.0.34] - master, Testbuild

URL-Einbettungen bekommen dasselbe Freigabemodell wie Objektfreigaben, live angefragt ("da
müssten wir das noch ähnlich machen wie bei den Objektfreigaben, was wird für wen
freigegeben"). Bisher war eine vom Admin angelegte URL-Einbettung automatisch für jedes
gekoppelte Gerät sichtbar - anders als Datenpunkte, die standardmäßig verboten sind und eine
explizite Freigabe brauchen.

- **Neuer `UrlEmbedAccessRule`-Typ**: dieselbe Rolle/Benutzer/Gerät-Eigentümerschaft und
  derselbe Verbot-gewinnt-Vorrang (explizites Verbot > Gerät > Benutzer > Rolle >
  Standardverbot) wie `ExposureRule` - ohne die Lesen/Schreiben/Historie/Min/Max-Felder, die
  für eine URL keinen Sinn ergeben (sie ist sichtbar oder nicht, Lesen/Schreiben spielt hier
  keine Rolle).
- **`GET /api/v1/url-embeds`** liefert jetzt nur noch Einbettungen, für die das anfragende
  Gerät eine Freigabe hat; `/content` und `/resolve` prüfen dieselbe Freigabe pro Aufruf
  (`READ_FORBIDDEN`, wenn nicht freigegeben).
- **Admin-Tab**: neuer Bereich "Zugriff freigeben" unter URL-Einbettungen, gleiches
  Rolle/Benutzer/Gerät-Auswahlmuster wie bei den Objektfreigaben, plus eine Tabelle der
  aktiven Freigaben zum Widerrufen.
- **Achtung**: Bestehende URL-Einbettungen haben noch keine Freigaben und sind ab diesem
  Update für kein Gerät mehr sichtbar, bis im Admin-Tab eine Freigabe angelegt wird.

## [0.0.33] - master, Testbuild

Drei Widget-Wünsche aus dem Livetest ("wenn man ein Widget einrichtet, sollte man auch den
Namen drüber ändern können... und vor allem Einheiten... und am Widget größer und kleiner
machen mit dem -/+ müssen wir noch etwas arbeiten").

- **Widget bearbeiten**: Neues Zahnrad-Symbol an jedem Widget im Bearbeiten-Modus öffnet einen
  Dialog zum Umbenennen und (bei Wert-Widgets: Textwert, Temperatur, Feuchte, Verlauf,
  Schieberegler, Thermostat) zum Setzen einer eigenen Einheit - bisher nur einmalig beim
  Anlegen aus der Katalog-Einheit übernehmbar, jetzt jederzeit änderbar.
- **Neuer Widget-Typ "Überschrift"**: reine Textbeschriftung ohne Datenpunkt, zum optischen
  Gruppieren/Abgrenzen von Widget-Gruppen auf einem Dashboard. Über einen dritten Reiter im
  Widget-Picker anlegbar.
- **Feinere Größenänderung**: Die alten +/- Buttons haben Breite und Höhe immer gemeinsam in
  festen 1er-Schritten verändert, wodurch z.B. ein breites, flaches Widget kaum zu erreichen
  war. Breite und Höhe sind jetzt getrennte Stepper im neuen Bearbeiten-Dialog, und das Raster
  wurde von 4 auf 8 Spalten/Zeilen verbreitert, damit ein einzelner Schritt weniger stark ins
  Gewicht fällt. Bestehende Dashboards werden beim nächsten Öffnen automatisch verbreitert
  (Widgets behalten ihre bisherige Position/Größe, können aber jetzt weiter wachsen).

## [0.0.32] - master, Testbuild

Echtes Logo, ersetzt das seit Projektbeginn als "nicht im MVP-Umfang" markierte
Platzhalter-Haus/Schalter-Symbol ("wir müssten dem Mobile Control noch ein schickes Logo
verpassen").

- **Design**: Telefon-Silhouette mit einem Aktor-Schalter auf dem Bildschirm (passend zur
  tatsächlichen App-Funktion: Schalter/Aktoren steuern) und einem kleinen Funksignal-Bogen, im
  bereits bestehenden Markenblau `#0B5FA5`/-türkis `#4FC3F7` (dieselben Farben, die `Theme.kt`
  schon für `BluePrimary`/`Teal` verwendet - keine neue Farbpalette nötig).
- **Verwendung an beiden Stellen**: `admin/mobile-control.png` (Adapter-Icon in ioBroker, 128x128)
  und der Android-Launcher-Icon (`ic_launcher_foreground.xml`, Adaptive Icon) - dasselbe Design.
- **Sicherheitszonen-Check**: Vor dem Ausliefern gegen Android's 66dp-Safe-Zone unter sowohl
  kreisförmigen als auch abgerundeten Launcher-Masken gerendert und geprüft, dass nichts
  Wichtiges abgeschnitten wird (ein erster Entwurf mit zwei Signalbögen wurde dabei verworfen -
  der äußere Bogen wurde von beiden Maskenformen sichtbar abgeschnitten).

## [0.0.31] - master, Testbuild

CI-Testfehler aus dem vorherigen Push behoben: Zwei `AlarmMonitorTest`-Fälle (`backgroundScope.launch`
+ `advanceUntilIdle` gegen ein endloses `Flow.collect()`) scheiterten in CI mit einem bloßen
`AssertionError`, trotz desselben Musters, das an anderer Stelle im Code bereits funktioniert.
Entfernt - die geprüfte Logik (`newlyActiveAlarms`) bleibt durch vier deterministische
Pure-Function-Tests abgedeckt. Kein Produktionscode geändert.

## [0.0.30] - master, Testbuild

Neues Feature, live angefragt: Alarm-Push-Benachrichtigungen ("man sollte im ioBroker eine
Pushnachricht an die App schicken können, sodass wir dann sozusagen Alarme schicken oder
ähnliches"). Bewusst **nicht** auf Firebase/FCM aufgebaut, nachdem in der Diskussion klar wurde:
(a) das eigene Testgerät (Fire HD 8) hat gar keine Google Play Services, FCM würde dort also gar
nicht funktionieren, und (b) der Adapter soll später in die Community-Base und soll niemandem
einen Google-Account aufzwingen. UnifiedPush wäre die self-hosted-Alternative gewesen, wurde aber
wegen der zusätzlichen Distributor-App-Einrichtung (Reibung für Durchschnittsnutzer) vorerst
zurückgestellt zugunsten eines dritten Wegs ganz ohne neue Infrastruktur.

1. **Backend**: Neuer `AlarmEventsService` (`src/alarms/index.ts`) abonniert bei Adapterstart
   alle Objekte mit Alarm-Rolle (`role.includes('alarm')`, dieselbe Konvention wie
   `suggestWidgets`) - unabhängig davon, ob gerade ein Client zusieht, anders als
   `RealtimeGateway`s dynamische Pro-Verbindung-Subscriptions. Jeder "Alarm wurde aktiv"-Übergang
   wird persistiert. Neuer Endpunkt `GET /alarm-events?since=` lässt ein Gerät nachholen, was seit
   dem letzten Mal passiert ist (Autorisierung wird pro Ereignis geprüft, nicht beim Sammeln).
2. **Android**: Neuer Opt-in-Schalter "Live-Benachrichtigungen" in den Einstellungen (Standard:
   aus, da Mehrverbrauch). Aktiviert einen Foreground-Service (`PushConnectionService`), der die
   ohnehin vorhandene, App-weite WebSocket-Verbindung (`RealtimeWebSocketClient`/
   `StateRepository`) auch ohne offenen Bildschirm am Leben hält, plus `AlarmMonitor`, der bei
   einem live eintreffenden Alarm eine echte System-Benachrichtigung zeigt und beim Start verpasste
   Alarme über den Nachhol-Endpunkt abruft.

## [0.0.29] - master, Testbuild

Echtes UX-Problem behoben, live in der Instanzübersicht entdeckt: "Verbunden mit Gerät oder
Dienst" stand rot, obwohl die App gerade gekoppelt und benutzt war.

**Ursache**: `info.connection` hing bisher rein daran, ob `RealtimeGateway.connectedDeviceCount`
gerade > 0 ist - also ob aktuell ein offener WebSocket existiert. Android hält aber keine
dauerhafte Hintergrundverbindung offen; das Socket lebt nur, während die App aktiv im Vordergrund
auf einem Live-Daten-Bildschirm ist. Sobald der Bildschirm ausgeht oder man auf einen anderen
Tab wechselt, kippt die Anzeige sofort auf Rot, obwohl das Gerät ganz normal gekoppelt und
Sekunden zuvor noch aktiv war.

**Fix**: Neue `DevicesService.hasRecentlyActiveDevice(windowMs)` prüft, ob ein freigegebenes
Gerät innerhalb der letzten 5 Minuten eine authentifizierte HTTP-Anfrage gestellt hat (jeder
`requireAuth`-Request aktualisiert bereits `device.lastSeenAt` über `DevicesService.touch()`,
das war schon vorhanden, wurde nur noch nicht für diese Anzeige genutzt). `info.connection` ist
jetzt `true`, wenn entweder ein Live-WebSocket offen ist ODER ein Gerät kürzlich aktiv war -
weiterhin korrekt Rot, wenn wirklich seit Längerem nichts mehr von der App kam.

## [0.0.28] - master, Testbuild

Folgepunkt zu den URL-Einbettungen aus 0.0.27, live angefragt: "manches wäre ja im Vollbild
sinnvoller sich anzuschauen". Das "Web-Seite"-Widget wurde bisher nur innerhalb seiner
Dashboard-Zelle gerendert. Hat jetzt einen kleinen, halbtransparent hinterlegten Vollbild-Button
oben rechts über dem WebView (das WebView selbst bleibt für Scroll/Interaktion der Seite
klickbar, kann also nicht selbst der Tap-Ziel sein) - Öffnen zeigt dieselbe freigegebene URL in
einem eigenen, bildschirmfüllenden Dialog mit Titelzeile + Schließen-Button, exakt das Muster,
das Kamera- und URL-Bild-Widgets für ihr Tap-für-Vollbild bereits verwenden.

## [0.0.27] - master, Testbuild

Neues Feature, live angefragt: URL-Einbettungen ("wie schaut das aus mit URL-Einbindungen? kriegen
wir das indirekt vom lokal per Proxy dann auch in die App im besten Fall?"), umgesetzt als bewusst
**allowlist-basierter** Ansatz statt eines generischen Proxys (Nutzerentscheidung aus einer
früheren Rückfrage).

1. **Backend**: Neuer `UrlEmbedsService` (`src/urlEmbeds/index.ts`) verwaltet eine
   admin-gepflegte Liste erlaubter URLs. Drei neue Endpunkte: `GET /url-embeds` (liefert nur
   `{id, name}`, nie die URL selbst), `GET /url-embeds/{id}/content` (serverseitig geholt +
   gecacht + Fallback-bei-Fehler, dasselbe Muster wie `CameraService`), `GET
   /url-embeds/{id}/resolve` (liefert die echte URL für WebView-Einbettungen, da eine ganze
   Webseite mit eigenen relativen Unterressourcen sich nicht sinnvoll durch einen
   Einzelressourcen-Proxy schleusen lässt). Ein Client kennt nie eine URL, die der Admin nicht
   vorher freigegeben hat.
2. **Admin-Tab**: Neuer Reiter "URL-Einbettungen" zum Anlegen/Löschen von Allowlist-Einträgen.
3. **Android**: Zwei neue Widget-Typen im Dashboard - "URL-Bild" (Bild aus dem Proxy, wie das
   Kamera-Widget) und "Web-Seite" (echtes `WebView`, JavaScript aktiviert, keine Bridge zum
   App-Code). Auswahl über einen neuen zweiten Reiter im Widget-Picker-Dialog neben dem
   bestehenden Objektbaum.

## [0.0.26] - master, Testbuild

Drei Folgepunkte aus dem Livetest auf dem Tablet:

1. **Größenklassen-Wähler entfernt**: Der Dashboard-Editor hatte einen
   Kompakt/Medium/Erweitert-Segmented-Button oben im Bildschirm, der Platz für das eigentliche
   Dashboard weggenommen hat - und da die App durchgehend nur die Größenklasse "kompakt" nutzt
   (jedes Widget landete dort), waren die anderen beiden Layouts immer leer. Wähler entfernt, die
   App bleibt intern fest auf "kompakt" (keine Datenmigration nötig, war schon immer der Default).
2. **+/- Buttons ändern jetzt Breite UND Höhe**: Die Größenänderung eines Widgets im Editier-Modus
   hat bisher `resizeWidget(id, dw, 0)` aufgerufen - die Höhe war fest verdrahtet auf "keine
   Änderung". Beide Buttons wachsen/schrumpfen jetzt Breite und Höhe gemeinsam.
3. **Theme-Umschalter in den Einstellungen**: Neue Hell/Dunkel/System-Auswahl (persistiert über
   DataStore, `ThemeMode`-Enum) - bisher folgte die App nur `isSystemInDarkTheme()` ohne
   In-App-Override.

## [0.0.25] - master, Testbuild

Feature-Wunsch aus Live-Feedback: Der "Widget hinzufügen"-Dialog im Dashboard-Editor war eine
flache, ungefilterte Liste ("total unübersichtlich und bescheiden mit der Suche, da Kategorien,
Ordner usw. dort nicht angezeigt werden").

1. **Ordnerbaum statt Flachliste**: Der Picker zeigt jetzt standardmäßig denselben aufklappbaren
   Ordnerbaum wie der Objektkatalog (`ObjectTreeNode`/`buildObjectTree`), fällt bei aktiver Suche
   oder Filter auf eine flache, gefilterte Liste zurück (auf 200 Einträge gekappt).
2. **Filter nach Werttyp**: Neue Filter-Chips (Alle/Bool/Zahl/Text/JSON) über der Suche, nutzt den
   in 0.0.24 ergänzten `ValueType.JSON`.
3. **Widget-Typ manuell wählbar**: Nach Auswahl eines Objekts wird weiterhin ein passender
   Widget-Typ vorgeschlagen, kann jetzt aber über ein Dropdown manuell überschrieben werden statt
   die Vermutung immer zu übernehmen.

## [0.0.24] - master, Testbuild

Drei Punkte aus Live-Feedback:

1. **Direkte Steuerung im Objektkatalog**: Ein Bool mit Schreibrecht bekommt jetzt einen Schalter
   direkt in der Listenzeile ("können wir da im Zweifel auch direkt schalten"). Ein Tap auf ein
   beliebiges schreibbares Objekt öffnet einen Dialog zum Senden eines neuen Werts (Zahlen/Text)
   oder Umschalten (Bool) - über dieselbe `ConfirmationGate`-Logik wie Dashboard-Widgets
   (`BLOCKED_ON_MOBILE` deaktiviert die Steuerung komplett, andere Policies zeigen wie gewohnt
   einen Bestätigungsdialog/Biometrie).
2. **Echter `json`-Werttyp**: Bisher fiel ein ioBroker-State mit `common.type: "json"` in den
   generischen `mixed`-Topf. Jetzt ein eigener Wert end-to-end (Backend-`mapValueType` +
   Android-`ValueType`) - Grundlage für eine spätere Filterung des Dashboard-Widget-Pickers nach
   Werttyp.
3. **Kamera-Snapshot-Caching + Fallback**: `CameraService` cacht jetzt den letzten erfolgreich
   abgerufenen Schnappschuss für ein paar Sekunden (weniger Last auf der Kamera-Quelle bei
   häufigem Dashboard-Refresh) und liefert bei einem fehlgeschlagenen frischen Abruf den letzten
   bekannt guten Schnappschuss statt eines Fehlers - kein kaputtes Bild mehr bei einem kurzen
   Aussetzer. Echte Bildinhaltsanalyse ("ist das Bild schwarz/weiß") ist weiterhin nicht enthalten,
   das würde eine echte Bildverarbeitung brauchen.

## [0.0.23] - master, Testbuild

Die 120-Zeichen-Kürzung aus [0.0.22] hat den Absturz **reduziert, aber nicht behoben** - live
bestätigt: Das Breiten-Defizit schrumpfte von -250 auf -90, nicht auf >= 0. Dichter Text wie JSON
hat kaum Umbruchstellen, wodurch selbst ein "kurzer" 120-Zeichen-String noch eine große
zusammenhängende intrinsische Breite haben kann - es gibt keine Zeichenanzahl, die für jeden
denkbaren Wert sicher ist.

Diesmal mit zwei unabhängigen Ebenen behoben (Vorschlag aus dem Livetest: kurze Vorschau + Tap
für volle Länge im Popup):

- Vorschau in der Liste jetzt deutlich kürzer (24 Zeichen, `MAX_VALUE_PREVIEW_LENGTH`)
- **Zusätzlich** eine harte Breitenbegrenzung via `Modifier.widthIn(max = 90.dp)` direkt am
  Vorschau-Text - begrenzt Composes intrinsische Messung strukturell, unabhängig davon wie dicht
  der (bereits gekürzte) Text ist
- Ein Tap auf einen Datenpunkt öffnet jetzt einen Dialog mit dem vollständigen, ungekürzten,
  markierbaren Wert - dort unproblematisch, weil ein Dialog gegen eine bereits begrenzte Breite
  misst statt eine unbeschränkte intrinsische Messung zu brauchen (das war die eigentliche
  Ursache in `ListItem`s dreispaltigem Layout)

## [0.0.22] - master, Testbuild

**Echter Absturz, live direkt nach [0.0.21] gefunden - die Einrückungs-Obergrenze war nicht die
eigentliche Ursache:** Das Öffnen eines JSON-Datenpunkts (Ereignisprotokoll, mehrere KB groß)
stürzte mit demselben Fehlerbild ab:

```
java.lang.IllegalArgumentException: maxWidth(-250) must be >= than minWidth(0)
    at androidx.compose.material3.ListItemMeasurePolicy.measure-3p2s80s(ListItem.kt:234)
```

Ursache: Die Objektlisten-Zeile hat den Live-Wert direkt und unbegrenzt im `ListItem`-Trailing-Text
angezeigt. Compose misst bei einem `Text` zuerst die *intrinsische* (unbeschränkte) Breite, bevor
es umbricht/abschneidet - bei einem mehrere KB großen, zusammenhängenden String führte das zu
einer negativen verbleibenden Breite für den Rest der Zeile.

- Neue Funktion `formatLiveValueForDisplay()`
  ([LiveValueFormatting.kt](android-app/app/src/main/java/com/mobilecontrol/app/domain/model/LiveValueFormatting.kt)):
  kürzt den angezeigten Wert **vor** der Anzeige hart auf 120 Zeichen - nicht nur visuell mit
  Auslassungspunkten, das würde weiterhin erfordern, zuerst den vollständigen String zu vermessen
- Neue Tests (`LiveValueFormattingTest.kt`), inkl. eines Regressionstests mit einem echten,
  mehrere KB großen JSON-Blob wie dem, der live abgestürzt ist

## [0.0.21] - master, Testbuild

**Echter Absturz, live beim Scrollen im Objektbaum gefunden** (per Logcat vom echten Testgerät
diagnostiziert):

```
java.lang.IllegalArgumentException: maxWidth(-250) must be >= than minWidth(0)
    at androidx.compose.material3.ListItemMeasurePolicy.measure-3p2s80s(ListItem.kt:234)
```

Ursache: Jede Baumebene wurde um `depth * 16dp` eingerückt, ohne Obergrenze. Manche echten
Kataloge (z.B. growmanagers `database.group-<id>.<subgroup>`-Struktur) verschachteln tief genug,
dass die Einrückung mehr Breite verbraucht hat als die Zeile zur Verfügung hatte - die für
`ListItem` verbleibende Breite ging ins Negative, was Compose beim nächsten Scroll-Remeasure hart
mit einer `IllegalArgumentException` quittiert (kein sanftes Clipping).

Fix: Einrückung wächst jetzt nicht mehr über 8 Ebenen (128dp) hinaus - tiefer verschachtelte
Objekte werden mit derselben Einrückung wie Ebene 8 dargestellt statt die Breite weiter zu
verringern.

## [0.0.20] - master, Testbuild

CI-Compile-Fehler aus [0.0.19] behoben: unnötiger expliziter Import von `Modifier.weight` löste
sich auf ein internes, nicht zugängliches Symbol auf statt auf das `RowScope`-Member, das
innerhalb eines `Row {}`-Lambdas ohnehin implizit verfügbar ist (kein Import nötig). Keine
funktionale Änderung.

## [0.0.19] - master, Testbuild

Live-Feedback zum Objektkatalog: auf dem Tablet blieb nur ein winziger sichtbarer Streifen für die
eigentliche Liste/den Baum übrig ("2,5 Zeilen"), weil Suchfeld, Raum-Chip, Rollen-Chip und die
"Nur schreibbar"-Checkbox jeweils eine eigene volle Zeile belegten - gestapelt über der Liste und
unter der unteren Navigationsleiste. Raum/Rolle/Nur-schreibbar sind jetzt in einem einzigen
Filter-Symbol neben dem Suchfeld zusammengefasst (mit Badge für die Anzahl aktiver Filter), das
einen kompakten Dialog öffnet statt permanent Platz zu belegen - gewinnt rund zwei Zeilen
vertikalen Platz zurück.

## [0.0.18] - master, Testbuild

Der vorgeschlagene Gerätename beim Onboarding war `Build.MODEL` - live am echten Testgerät
bestätigt, dass das auf einem Amazon-Fire-Tablet `"KFONWI"` liefert (interner Hardware-Codename),
nicht `"Fire Tablet"`. Neuer `DeviceNameProvider`
([DeviceNameProvider.kt](android-app/app/src/main/java/com/mobilecontrol/app/data/local/DeviceNameProvider.kt)):
liest zuerst den vom Nutzer selbst gesetzten Bluetooth-Namen (`Settings.Secure`
`"bluetooth_name"`, keine besondere Berechtigung nötig, anders als
`BluetoothAdapter.name` ab API 31), fällt nur auf `Build.MODEL` zurück falls der nicht gesetzt
ist. Bleibt weiterhin nur ein Vorschlag im Textfeld, frei änderbar vor dem Koppeln.

## [0.0.17] - master, Testbuild

**Zwei Folgepunkte aus Live-Feedback:**

1. **Echter CI-Bug behoben:** Jeder Debug-APK-Build in CI wurde mit einem anderen, zufällig
   automatisch erzeugten Debug-Keystore signiert (jede GitHub-Actions-VM startet leer, nichts
   bleibt zwischen Läufen erhalten - Gradle erzeugt ohne expliziten `signingConfigs.debug` bei
   Bedarf einen eigenen `~/.android/debug.keystore`). Dadurch war jedes einzelne in dieser Session
   installierte APK-Update anders signiert als das vorherige, was ein Deinstallieren erzwang
   (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`) - und damit den Keystore-Schlüssel und das gekoppelte
   Geräteprofil löschte, sodass nach jedem App-Update ein komplett neues Pairing per QR-Code nötig
   war. Fix: fester, mit eingecheckter Debug-Keystore
   (`android-app/debug.keystore`, unsensibel, nie für Release-Signing genutzt) plus expliziter
   `signingConfigs.debug` in `build.gradle.kts` - jeder `debug`/`staging`-Build ist jetzt
   reproduzierbar gleich signiert, `adb install -r` funktioniert, Pairing übersteht Updates.
2. **Sichtbarkeit für AbuseGuard** ([0.0.15]): Neues Panel „Auffällige IPs (Pairing/Login)" in der
   Admin-Tab-Übersicht zeigt live jede IP mit aktuell laufenden Fehlversuchen oder aktiver Sperre -
   Anzahl der Fehlversuche, zuletzt versuchter Endpunkt, Sperrstatus/-ende. Vorher gab es nur die
   einmalige Log-Zeile beim Auslösen einer neuen Sperre.
   - `AbuseGuard.snapshot()`/`getFailureCount()` neu, neuer sendTo-Befehl `listAbuseState`
   - Log-Warnung enthält jetzt auch die tatsächliche Anzahl Fehlversuche, nicht nur den Grund
   - Neue Tests (`test/abuseGuard.test.ts` erweitert, echter Ende-zu-Ende-Check in
     `test/integration/adapterStartup.ts`)

## [0.0.16] - master, Testbuild

**Feature-Wunsch:** Der Objektbrowser in der Android-App (Hauptnavigation "Objekte") war eine
flache, unstrukturierte Liste aller freigegebenen Datenpunkte - auf einem Gerät mit vielen
Objekten mühsam zu durchsuchen. Baut jetzt denselben Ansatz wie der Freigabe-Baum im Admin-Tab
([0.0.12]): ein echter, aufklappbarer Ordnerbaum, gruppiert nach `path` (rein clientseitig
synthetisiert, da die App - anders als der Admin-Tab - nie echte Channel-/Device-Objekte sieht,
nur den bereits gefilterten flachen Katalog).

- Neues `ObjectTreeNode`-Modell + `buildObjectTree()`/`visibleLeafIds()`
  ([ObjectTreeNode.kt](android-app/app/src/main/java/com/mobilecontrol/app/domain/model/ObjectTreeNode.kt))
- Ohne aktiven Suchbegriff/Filter: aufklappbarer Ordnerbaum (Standardansicht). Mit Suchbegriff
  oder Filter (Raum/Rolle/nur schreibbar): bisherige flache, durchsuchbare Liste bleibt erhalten
- Nur Objekte in tatsächlich aufgeklappten Ordnern werden live abonniert (`visibleLeafIds`) -
  vermeidet, Hunderte verborgener States gleichzeitig zu abonnieren
- Neue Tests: `ObjectTreeNodeTest.kt`, Erweiterungen in `ObjectBrowserViewModelTest.kt`
- **Erfordert einen neuen Android-Build.**

## [0.0.15] - master, Testbuild

**Sicherheits-Härtung**, ausgelöst durch eine berechtigte Live-Nachfrage: "ist das Pairing sicher,
gibt es eine Warnung oder ein Blacklisting bei wiederholten Fehlversuchen?" Die ehrliche Antwort
war vorher: Kryptografie (256-Bit-Pairing-Secrets, EC-Signaturen) plus Admin-Freigabepflicht
schützen bereits zuverlässig vor echtem Zugriff, aber es gab weder eine aktive Warnung noch eine
eskalierende Sperre bei Dauerbeschuss - nur ein nicht-eskalierendes 60-Sekunden-Rate-Limit pro IP.

- Neuer `AbuseGuard` ([src/security/abuseGuard.ts](src/security/abuseGuard.ts)): zählt
  Fehlversuche pro IP über ein 5-Minuten-Fenster; ab einem konfigurierbaren Schwellwert (Standard
  10) wird die IP komplett blockiert (Standard 30 Minuten), nicht nur gedrosselt
- Greift auf `/pairing/claim`, `/auth/challenge`, `/auth/login`, `/auth/refresh` - zusätzlich zum
  bisherigen `RateLimiter`, der reines Anfragevolumen unabhängig vom Ausgang drosselt
- Eine neue Sperre erzeugt jetzt eine sichtbare `adapter.log.warn(...)`-Zeile im normalen
  Adapter-Log, nicht nur einen Eintrag im (bisher nur manuell einsehbaren) Audit-Log
- Ein Erfolg (bewiesen durch Secret/Signatur/gültigen Refresh-Token) löscht die
  Fehlversuchs-Historie der IP wieder - `/auth/challenge` bewusst ausgenommen, da ein Erfolg dort
  keine echte Identität beweist und Geräte-ID-Enumeration sonst den Zähler kostenlos zurücksetzen
  könnte
- Beide Schwellwerte konfigurierbar in den Instanz-Einstellungen (`abuseBlockThreshold`,
  `abuseBlockMinutes`)
- Neue Tests: `test/abuseGuard.test.ts` (isolierte Logik) sowie ein echter Ende-zu-Ende-Schritt
  in `test/integration/adapterStartup.ts`, der über die reale HTTP-Schnittstelle eine Sperre
  auslöst und verifiziert

## [0.0.14] - master, Testbuild

**Zwei echte Android-Bugs, live direkt nach 0.0.13 gefunden** (Nutzer bemängelte zu Recht, dass
ein abgelaufenes Pairing als rohes "HTTP 410" statt einer verständlichen Meldung angezeigt wurde):

1. `ApiErrorCode.kt` fehlten 7 der 19 Backend-Fehlercodes komplett (`PAIRING_INVALID`,
   `PAIRING_EXPIRED`, `CHALLENGE_INVALID`, `SIGNATURE_INVALID`, `NOT_FOUND`, `VALIDATION_ERROR`,
   `REPLAY_DETECTED`) - die fielen alle stillschweigend auf `UNKNOWN` zurück.
2. **Deutlich größerer Fund dabei**: `ErrorEnvelopeDto.kt` erwartete die Fehler-Antwort als
   verschachteltes `{"error": {"code": "...", "message": "..."}}`, das Backend schickt aber
   tatsächlich ein flaches `{"error": "CODE_STRING", "message": "..."}` (siehe
   `ApiError.toBody()` in `src/lib/errors.ts`). Durch diesen Typ-Mismatch ist das Deserialisieren
   des Fehler-Bodys seit der ursprünglichen Android-Implementierung bei **jeder einzelnen**
   Fehlerantwort des Servers stillschweigend fehlgeschlagen - die App hat den Fehlercode nie aus
   der Antwort gelesen, sondern immer nur grob aus dem rohen HTTP-Status geraten (und für Codes
   wie 400/410/428, die vorher gar nicht in dieser Rate-Fallback-Tabelle standen, landete das
   sogar direkt bei `UNKNOWN`).

Beide behoben: `ErrorEnvelopeDto` liest jetzt die echte Form, `mapHttpError` deckt alle Status ab,
und das Pairing zeigt jetzt echte deutsche Meldungen ("Die Kopplungsanfrage ist abgelaufen. Bitte
scanne den QR-Code erneut." usw. statt "HTTP 410"). Neue Tests: `ApiCallExecutorTest.kt`
(reale Backend-Fehlerform, Status-Fallback, unbekannter Code). **Erfordert einen neuen
Android-Build.**

## [0.0.13] - master, Testbuild

**Echter Bug, live beim Onboarding gefunden:** Die App zeigte bei jedem einzelnen Pairing-Versuch
"Achtung: der Server-Fingerabdruck stimmt nicht mit dem erwarteten Wert überein" - unabhängig vom
Netzwerk oder Reverse-Proxy-Zustand. Ursache: `ServerFingerprintChecker.kt` verglich den
`serverFingerprint` aus dem QR-Code gegen den SPKI-SHA256-Hash des **echten TLS-Zertifikats** aus
einem Live-Handshake. Dieser Adapter terminiert aber selbst kein TLS (ein VPN/Reverse-Proxy wird
davor erwartet, siehe `docs/MASTERKONZEPT.md` §4) - der `serverFingerprint`, den der Adapter in
jede QR-Einladung packt, ist tatsächlich ein Hash des adapter-eigenen JWT-Signierschlüssels, kein
Zertifikats-Hash. Zwei grundverschiedene Werte im selben String-Format (`sha256/BASE64`), die
niemals übereinstimmen konnten - weder über einfaches lokales HTTP (gar kein TLS-Handshake) noch
über einen HTTPS-Reverse-Proxy (dessen Zertifikat der Adapter gar nicht kennt). Das war kein
Netzwerkproblem, sondern ein Vertrags-Bug zwischen Backend und App seit der ursprünglichen
Android-Implementierung.

- Neuer, unauthentifizierter Endpunkt `GET /api/v1/server/info` liefert denselben Fingerprint,
  den auch jede QR-Einladung enthält
- `ServerFingerprintChecker.kt` holt sich diesen Wert jetzt live von dort und vergleicht direkt
  (kein TLS-Handshake mehr nötig) - ein Mismatch bedeutet weiterhin zuverlässig "das ist nicht der
  Server, der diesen QR-Code ausgestellt hat" (z.B. veralteter QR-Code nach einer
  Secret-Rotation), nur eben ohne echtes Zertifikats-Pinning vorzutäuschen
- `docs/openapi.yaml`, `docs/API-VERTRAG.md` und `android-app/README.md` entsprechend
  aktualisiert; neue Tests (`test/pairing.test.ts`, `test/integration/adapterStartup.ts`)
- **Erfordert einen neuen Android-Build** (APK aus der CI ziehen und auf dem Testgerät neu
  installieren) - der bisherige Fingerprint-Check bleibt sonst bestehen

## [0.0.12] - master, Testbuild

**Feature-Wunsch aus dem Livetest** (nicht mehr in "immer wieder ein neuer Bug"-Kategorie, sondern
echtes Feedback nach dem ersten funktionierenden Durchklicken): Der Objektbaum unter
"Objektfreigaben" war eine flache, auf 200 Treffer begrenzte Liste ohne jede Ordnerstruktur -
`browseObjectTree()` hat bisher nur `type: 'state'`-Objekte geliefert, nie die dazugehörigen
Channel-/Device-/Folder-Objekte mit ihren echten Namen. Außerdem ließ sich nur ein einzelner
Datenpunkt freigeben, obwohl `ExposureService` serverseitig `scope: 'channel'|'device'|'adapter'`
(Präfix-Match gegen alle Datenpunkte darunter) schon immer unterstützt hat - das war nur nie aus
der UI heraus erreichbar, weil `ExposureTab.tsx` beim Anlegen einer Regel `scope: 'state'` fest
verdrahtet hatte.

- `browseObjectTree()` liefert jetzt zusätzlich alle `channel`/`device`/`folder`/`adapter`/
  `instance`-Objekte, mit `kind: 'state' | 'container'` unterschieden
- Admin-Tab baut daraus einen echten, aufklappbaren Ordnerbaum (Standardansicht, wenn das
  Suchfeld leer ist); bei eingegebenem Suchbegriff bleibt die bisherige flache, durchsuchbare
  Liste erhalten
- "Freigeben" funktioniert jetzt auf jeder Ebene - auf einem Ordner erzeugt es automatisch eine
  `channel`-Scope-Regel, die alle Datenpunkte darunter abdeckt
- Neuer Test (`test/exposureTree.test.ts`) für die state/container-Unterscheidung und die
  Präfix-Match-Semantik einer Ordner-Regel

## [0.0.11] - master, Testbuild

**Echter Folgebug, live direkt nach dem socket.io-Fix gefunden:** Der Tab verbindet sich jetzt
sauber (kein Fehler-Popup mehr), aber jede Aktion darin - Rollen/Benutzer anlegen oder auch nur
auflisten, den Objektbaum für Freigaben durchsuchen, Sessions, Audit - tat scheinbar gar nichts,
ohne jede Fehlermeldung. Ursache: `io-package.json` hat nie `common.supportedMessages.custom`
deklariert (den modernen Ersatz für das veraltete `common.messagebox`-Flag). Ohne dieses Flag legt
js-controller für die Instanz gar keine Messagebox an und hat keinen Grund, auch nur einen der
`sendTo()`-Aufrufe des Admin-Tabs (`callAdapter(...)` in `admin/tab-src/src/connection.ts`, von
praktisch jedem Tab benutzt) an den Adapter zuzustellen - jeder einzelne wurde verworfen, bevor er
unseren `onMessage`-Handler in `main.ts` je erreichte. Bestätigt anhand von `@iobroker/types`s
`SupportedMessages`-Interface und dem echten, funktionierenden Custom-Tab von
`ioBroker.javascript` (kommuniziert genau wie unserer über `sendTo` und deklariert dasselbe Flag).
Jetzt in `io-package.json` ergänzt: `"supportedMessages": { "custom": true }`.

## [0.0.10] - master, Testbuild

**Echter Folgebug, live direkt nach dem adminTab-Fix gefunden:** Der Tab erschien nach [0.0.9]
korrekt in der Seitenleiste, aber beim Öffnen kam sofort ein Fehler-Popup: "Socket connection
could not be initialized: Error: Socket library could not be loaded!". Ursache: `Connection` aus
`@iobroker/adapter-react-v5` (bzw. `@iobroker/socket-client`) erwartet, dass die
socket.io-Client-Bibliothek bereits als globale Variable `window.io` vorhanden ist, wenn sie
konstruiert wird - sie lädt die Bibliothek selbst **nicht**, sondern wartet nur bis zu 3 Sekunden
darauf und wirft dann exakt diesen Fehler. Unser `tab.html` hat `socket.io.js` nie geladen. Fix:
ein einfaches `<script src="../../lib/js/socket.io.js">` (kein `type="module"`, läuft daher
garantiert vor unserem App-Bundle) vor dem eigentlichen Tab-Bundle in
`admin/tab-src/tab.html` ergänzt - derselbe relative Pfad, den auch der echte, funktionierende
Admin-Tab von `ioBroker.javascript` benutzt, und der auf den von js-controller für jeden Adapter
bereitgestellten gemeinsamen statischen Pfad zeigt.

## [0.0.9] - master, Testbuild

**Den echten Grund für den fehlenden Admin-Tab gefunden** (0.0.8 hat das Problem nicht vollständig
gelöst, wie der Nutzer live bestätigt hat: `adminUI.tab: "html"` war korrekt installiert,
`iobroker upload mobile-control` lief erfolgreich durch, aber der Tab tauchte weiterhin nicht in
der Seitenleiste auf). Ursache: `adminUI.tab` bestimmt nur, *wie* ein Tab gerendert wird (html vs.
json vs. materialize) - der eigentliche Seitenleisten-Eintrag wird über ein komplett separates
Feld `common.adminTab` registriert (siehe `@iobroker/types`s `AdapterCommon`-Interface), das in
unserer `io-package.json` schlicht nie existiert hat. Bestätigt durch Vergleich mit zwei echten,
funktionierenden Custom-Tab-Adaptern (`ioBroker.devices`, `ioBroker.javascript`), die beide ein
`adminTab`-Objekt (`singleton`, `name`) deklarieren, genau wie jetzt bei uns. Da unser Tab bereits
unter dem von `adminTab` erwarteten Standardpfad `admin/tab.html` liegt, war kein zusätzliches
`link`-Feld nötig.

## [0.0.8] - master, Testbuild

**Weiterer echter Bug, live beim Nutzer gefunden:** Der eigene Admin-Tab tauchte in der
ioBroker-Admin-Seitenleiste überhaupt nicht auf. Ursache: `io-package.json`s `adminUI.tab` stand
auf `"custom"` - laut echtem ioBroker-Schema (`@iobroker/types`) sind dort aber nur
`'html' | 'json' | 'materialize'` gültig. Mit dem ungültigen Wert registrierte die Admin-UI den
Tab still schweigend gar nicht erst. Das war seit Projektbeginn so und wurde nie bemerkt, weil
zuvor nur die gebauten `admin/tab.html`/`tab-assets`-Dateien direkt geprüft wurden, nie eine echte
ioBroker-Installation. Auf `"tab": "html"` korrigiert (unser Tab ist eine reine HTML-Datei, die
den React-Bundle lädt).

Außerdem: `@iobroker/testing`s offizieller Package-File-Validator (`validatePackageFiles`) lief
bisher gar nicht mit - jetzt als `test/packageFiles.test.ts` eingebunden (prüft u.a.
`package.json`/`io-package.json`-Konsistenz, gültige `adminUI.config`, Lizenzfelder; hätte den
`adminUI.tab`-Fehler selbst nicht gefangen, das ist trotzdem eine echte Lücke in der bisherigen
Testabdeckung).

## [0.0.7] - master, Testbuild

Nach dem ws-Fund in [0.0.6] gezielt nach derselben Fehlerklasse gesucht: "Fire-and-forget"
Async-Aufrufe (`void asyncFn()`), deren Ablehnung nirgendwo abgefangen wird und damit potenziell
den ganzen Adapter-Prozess crashen könnte (unhandled promise rejection), genau wie beim
ws-Bug. Acht Stellen gefunden und mit `.catch(...)` abgesichert (Session-/Geräte-Bookkeeping,
Kommando-Bestätigung/-Timeout, Status-States, Session-Widerruf, WebSocket-Nachrichtenverarbeitung).
Eine Stelle (Admin-Message-Handler) war bereits durch ein eigenes vollständiges try/catch sicher.

## [0.0.6] - master, Testbuild

**Echte Ursache des EADDRINUSE-Absturzes gefunden und behoben** (siehe [0.0.2] bis [0.0.5] für die
Fehlersuche): Die `ws`-Bibliothek registriert beim Erstellen von `new WebSocketServer({ server })`
selbst einen `'error'`-Listener auf dem übergebenen `http.Server` und reicht dessen Fehler an sich
selbst weiter (`wss.emit('error', err)`). Node.js wirft ein `'error'`-Event **synchron** als echte
ungefangene Exception, wenn dafür kein Listener registriert ist - da `RealtimeGateway` nie auf
`wss`s eigenes `'error'`-Event gehört hat, crashte **jeder** Listen-Fehler (nicht nur EADDRINUSE)
den ganzen Adapter-Prozess sofort, bevor die eigene Retry-/Port-Scan-/Fehlerbehandlung in
`main.ts` (aus den vorherigen Versionen) überhaupt eine Chance hatte zu laufen. Das war ein
latenter Bug seit Projektbeginn, der erst beim ersten echten Livetest gegen einen belegten Port
sichtbar wurde.

- `RealtimeGateway` hört jetzt auf `wss`s `'error'`-Event (loggt es nur, die eigentliche Behandlung
  bleibt in `main.ts`s `server.once('error', ...)`)
- Neuer Regressionstest (`test/realtime.test.ts`), der einen echten Port-Konflikt erzeugt und
  verifiziert, dass der Prozess dabei nicht abstürzt
- Temporäres, ausschließlich der Fehlersuche dienendes Diagnose-Logging aus [0.0.4]/[0.0.5] wieder
  entfernt

## [0.0.5] - master, Testbuild

Weitere temporäre Diagnose-Kontrollpunkte (`[diag] 1/6` bis `[diag] 6/6`) durch `onReady()` und
`startHttpServer()`, um beim Live-Test genau einzugrenzen, bis wohin der Code beim EADDRINUSE-Absturz
tatsächlich kommt - die vorherige Diagnose in `listenWithRetry` wurde auf der echten Installation nie
erreicht.

## [0.0.4] - master, Testbuild

Zwischenstand während der Live-Test-Fehlersuche (EADDRINUSE-Handling greift auf der echten
Installation nicht, Ursache noch offen):

- Temporäres, unbedingtes Diagnose-Logging in `listenWithRetry`/`onReady`, um zu sehen ob der
  neue Fehlerbehandlungscode zur Laufzeit überhaupt erreicht wird
- Dabei einen echten Regressions-Bug in der Diagnose selbst gefunden und gefixt: der
  `'ready'`-Event-Handler gab das `onReady()`-Promise nicht mehr zurück, wodurch der
  Integrationstest den Server manchmal noch nicht bereit vorfand (`ECONNREFUSED`)

## [0.0.3] - master, Testbuild

Gefunden beim ersten echten Livetest gegen eine reale ioBroker-Instanz:

- `package.json`s `files`-Liste schloss `src/` und beide `tsconfig*.json` aus. npm packt
  Git-Installs nach dem `prepare`-Skript gemäß dieser Liste - der allererste Install baute deshalb
  noch erfolgreich (voller, ungefilterter Klon), aber jeder spätere manuelle Rebuild **innerhalb**
  des bereits installierten Verzeichnisses schlug mit `tsconfig.build.json not found` fehl. Jetzt
  behoben, `src/`/beide tsconfigs sind Teil der `files`-Liste.

## [0.0.2] - master, Testbuild

### Backend
- Kamera-Snapshot-Endpoint (`GET /api/v1/objects/{id}/snapshot`), liest Data-URL- oder
  http(s)-URL-Kamera-States, proxied das Bild statt direktem Netzzugriff der App
- Rate Limiting auf `/auth/challenge`, `/auth/login`, `/auth/refresh`, `/pairing/claim` (vorher nur
  auf `/commands`) - Brute-Force-Lücke aus einer Konzept-Lückenanalyse
- Audit-Log für einzelne Freigabe-Änderungen und für Refresh-Token-Wiederverwendung
- Verbindungs-Info-Anzeige (lokale IP-Adressen, Port) im Admin-Tab für VPN/Reverse-Proxy-Einrichtung
- Klarere Fehlerbehandlung bei belegtem Port beim Start: automatischer Ausweich-Port-Scan, solange
  noch kein Gerät gekoppelt wurde; danach klare Fehlermeldung statt Absturz mit rohem Stacktrace
- Release-Automatisierung (`.github/workflows/release.yml`) und Versions-Konsistenzprüfung

### Android
- `CommandStatus.BLOCKED` wird jetzt tatsächlich für `LOCAL_ONLY`-Ablehnungen verwendet
- `minSdk` von 34 (Android 14) auf 26 (Android 8.0) gesenkt für Tests auf älterer Hardware
- CI baut bei jedem Push eine installierbare Debug-APK als Artefakt
- 90 JVM-Unit-Tests (vorher 39): neue ViewModel-Tests mit Fake-Repositories

## [0.0.1] - master, Testbuild

Erster vollständiger MVP-Stand auf `master`. Backend und Android-App sind funktional komplett
für die in [docs/TODO.md](docs/TODO.md) als erledigt markierten Punkte; noch kein offizieller
Release.

### Backend
- Adapter-Grundgerüst (TypeScript, State-basierte Persistenz, keine externe DB)
- Benutzer, Rollen, Geräte, Sessions, Audit-Log
- QR-Pairing mit Admin-Bestätigung, EC-P256-Challenge-Response-Auth
- Rotierende Refresh-Tokens mit Wiederverwendungserkennung
- Objektfreigaben (Rollen-/Nutzer-/Geräte-Priorität, `system.*` blockiert) inkl. wiederverwendbarer Freigabeprofile
- Gefilterter Objektkatalog mit Delta-Updates und effektiver Admin-Vorschau
- Aktorsteuerung mit Typ-/Wertebereichsprüfung, Rate-Limit, Replay-Schutz, Confirmation-Policies
- WebSocket-Realtime-Gateway, REST-API nach `docs/openapi.yaml`
- Verlaufs-API über konfigurierbare ioBroker-History-Instanz
- Versioniertes Migrations-Grundgerüst
- Custom React-Admin-Tab + native Einstellungsseite
- 56 Unit-Tests + echter Integrationstest gegen gemockte ioBroker-Umgebung

### Android-App
- QR-Pairing, Keystore-Schlüsselerzeugung, App-Sperre (PIN/Biometrie)
- Objektkatalog-Browser, Dashboards mit echtem Drag & Drop
- 10 Widget-Typen (Text, Temperatur, Feuchte, Status, Schalter, Taster, Slider, Rollladen,
  Thermostat, Verlauf) inkl. zentralem Bestätigungs-Gate für Confirmation-Policies
- Automatischer Command-Retry bei Timeout
- Offline-Cache mit zeitbasierter Bereinigung
- Drei Build-Varianten (debug/staging/release)

### Bekannte Lücken
Siehe [docs/TODO.md](docs/TODO.md) für die vollständige, ehrliche Liste. Wichtigste offene
Punkte: Kamera, Push-Benachrichtigungen, echtes Certificate-Pinning, Android-eigene Tests,
Alarm-Quittierung, Betrieb gegen eine echte (nicht gemockte) ioBroker-Instanz noch nicht verifiziert.
