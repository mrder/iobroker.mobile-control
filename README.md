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

Sobald das Repo auf GitHub liegt:

```text
iobroker url https://github.com/YOUR_GITHUB_USER/iobroker.mobile-control
```

oder über die Admin-Oberfläche → Adapter → benutzerdefinierte URL.

## Entwicklung (Adapter)

```bash
npm install
npm run build
npm test
```

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

## Status

MVP in aktiver Entwicklung. Aktueller Umsetzungsstand siehe [docs/TODO.md](docs/TODO.md).

## Lizenz

MIT, siehe [LICENSE](LICENSE).
