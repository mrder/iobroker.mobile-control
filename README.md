# ioBroker.mobile-control

Sichere Schnittstelle zwischen [ioBroker](https://www.iobroker.net/) und der zugehörigen nativen Android-App **Mobile Control**. Der Adapter bestimmt, was ein Benutzer oder Gerät sehen und bedienen darf; die App baut daraus persönliche Dashboards.

```text
ioBroker-Adapter  → definiert Freigaben und Sicherheit
Android-App       → baut daraus persönliche Dashboards
```

Die App erhält niemals ungefilterten Zugriff auf den ioBroker-Objektbaum und niemals allgemeine Schreibrechte. Details siehe [docs/MASTERKONZEPT.md](docs/MASTERKONZEPT.md) und [docs/SECURITY.md](docs/SECURITY.md).

## Repo-Aufbau

```text
.                     ioBroker-Adapter (installierbar via "iobroker url <repo>")
├── src/              Adapter-Quellcode (TypeScript)
├── admin/            Custom Admin-UI (React)
├── test/             Unit-Tests
├── android-app/       native Android-App (Kotlin/Jetpack Compose, eigenständiges Gradle-Projekt)
└── docs/              vollständige Konzeptdokumente
```

## Adapter installieren

```text
iobroker url https://github.com/mrder/iobroker.mobile-control
```

oder über die Admin-Oberfläche → Adapter → benutzerdefinierte URL. Das installiert immer den
aktuellen `master`-Stand (Testversion) – siehe Versionierung unten. Für einen stabilen Release
sobald verfügbar: `iobroker url https://github.com/mrder/iobroker.mobile-control#main`.

## Branches & Versionierung

```text
master   laufende Entwicklung, Zwischen-/Testversionen  0.0.x   (z. B. 0.0.1, 0.0.2, …)
main     veröffentlichte Releases                       0.x.0   (z. B. 0.1.0, 0.2.0, … 1.0.0)
```

Entwickelt wird auf `master`, jede sinnvolle Zwischenstufe erhöht die Patch-Version (0.0.x).
Sobald ein Meilenstein aus [docs/ROADMAP.md](docs/ROADMAP.md) erreicht ist, wird `master` nach
`main` gemergt und die Version auf die nächste Minor-Stufe (0.x.0) angehoben – `main` enthält
dann nur getestete, freigegebene Stände. `main` existiert erst ab dem ersten Release.

## Entwicklung (Adapter)

```bash
npm install
npm run build
npm test
```

`npm test` baut den Adapter, führt die 56 Unit-Tests aus und startet danach den echten
kompilierten Adapter gegen eine gemockte ioBroker-Umgebung (`@iobroker/testing`) für einen
vollständigen End-to-End-Durchlauf (Pairing → Admin-Bestätigung → Login → Token-Rotation) über
echte HTTP-Requests – siehe [test/integration/adapterStartup.ts](test/integration/adapterStartup.ts).

## Android-App

Siehe [android-app/README.md](android-app/README.md) – eigenständiges Android-Studio-Projekt, minSdk 34.

## Dokumentation

- [docs/MASTERKONZEPT.md](docs/MASTERKONZEPT.md) – vollständiges Gesamtbild
- [docs/BACKEND-KONZEPT.md](docs/BACKEND-KONZEPT.md) – Adapter-Architektur
- [docs/ANDROID-APP-KONZEPT.md](docs/ANDROID-APP-KONZEPT.md) – App-Architektur
- [docs/API-VERTRAG.md](docs/API-VERTRAG.md) – REST-/WebSocket-Vertrag
- [docs/openapi.yaml](docs/openapi.yaml) – maschinenlesbare OpenAPI-3.0-Spezifikation des REST-Teils
- [docs/SECURITY.md](docs/SECURITY.md) – Sicherheitsmodell
- [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) – Reverse-Proxy/VPN/TLS-Anleitung, OWASP-Abgleich
- [docs/ROADMAP.md](docs/ROADMAP.md) – Versionen und Meilensteine
- [docs/TODO.md](docs/TODO.md) – Entwicklungsstand
- [CHANGELOG.md](CHANGELOG.md) – Änderungshistorie

## Status

MVP in aktiver Entwicklung. Aktueller Umsetzungsstand siehe [docs/TODO.md](docs/TODO.md).

## Lizenz

MIT, siehe [LICENSE](LICENSE).
