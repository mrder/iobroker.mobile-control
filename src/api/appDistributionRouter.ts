import { Router, type Request, type Response } from 'express';
import * as fs from 'node:fs';
import * as path from 'node:path';
import * as QRCode from 'qrcode';
import { createRateLimitMiddleware } from './middleware';
import type { RateLimiter } from '../security/rateLimiter';

export interface AppDistributionServices {
    adapter: ioBroker.Adapter;
    /** Same value used for the pairing QR payload (PairingConfig.publicUrl) - the one address
     *  this adapter already knows a phone/tablet can actually reach it at. */
    publicUrl: string;
    downloadRateLimiter: RateLimiter;
    /** Defaults to <repo-root>/app/mobile-control.apk - overridable for tests. */
    apkPath?: string;
}

/**
 * Resolves to <repo-root>/app/mobile-control.apk regardless of the process's cwd. This file
 * compiles to build/api/appDistributionRouter.js, so __dirname there is <repo-root>/build/api -
 * two levels up lands back at the repo root, where the bundled APK ships alongside build/ (see
 * package.json's "files" array and the release checklist in README.md).
 */
const DEFAULT_APK_PATH = path.join(__dirname, '..', '..', 'app', 'mobile-control.apk');
const PACKAGE_JSON_PATH = path.join(__dirname, '..', '..', 'package.json');

/** Exported so main.ts's getAppInfo admin message can check availability/build the same QR code
 *  the /app page itself shows, without duplicating the path-resolution logic. */
export function getDefaultApkPath(): string {
    return DEFAULT_APK_PATH;
}

export function readAdapterVersion(): string {
    try {
        const raw = fs.readFileSync(PACKAGE_JSON_PATH, 'utf8');
        return (JSON.parse(raw) as { version?: string }).version ?? 'unbekannt';
    } catch {
        return 'unbekannt';
    }
}

function escapeHtml(value: string): string {
    return value
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

function renderInstallPage(version: string, downloadUrl: string, qrDataUrl: string): string {
    return `<!doctype html>
<html lang="de">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Mobile Control App installieren</title>
<style>
  body { font-family: system-ui, sans-serif; max-width: 480px; margin: 40px auto; padding: 0 16px; text-align: center; color: #222; }
  img { max-width: 240px; width: 100%; height: auto; margin: 24px 0; }
  a.button { display: inline-block; padding: 12px 24px; background: #1565c0; color: #fff; text-decoration: none; border-radius: 6px; font-weight: 600; }
  p.hint { font-size: 0.9em; color: #666; text-align: left; }
</style>
</head>
<body>
<h1>Mobile Control</h1>
<p>Version ${escapeHtml(version)}</p>
<p>Mit der Tablet-/Handy-Kamera scannen, um die App direkt herunterzuladen:</p>
<img src="${qrDataUrl}" alt="QR-Code zum App-Download" width="240" height="240">
<p><a class="button" href="${downloadUrl}">APK direkt herunterladen</a></p>
<p class="hint">Hinweis: Da die App nicht über den Play Store installiert wird, muss Android beim
ersten Öffnen der heruntergeladenen Datei einmalig die Installation aus dieser Quelle erlauben
("Unbekannte Apps installieren"). Nach der Installation die App öffnen und über den QR-Code aus
der ioBroker-Adapterkonfiguration koppeln.</p>
</body>
</html>`;
}

/**
 * No device-level auth of its own (a brand-new, not-yet-paired device has no token yet and needs
 * to get the app itself before any pairing can happen - same trust level as an app store: the
 * binary itself carries no per-user secrets, it's identical for everyone). Since the portal-key
 * gate (see createPortalGateMiddleware) is mounted ahead of every route including this one, the
 * portal key is still required first - see main.ts's getAppInfo admin message / the "App-
 * Installation" admin tab for a QR code that already has it embedded. Rate-limited by IP purely to
 * stop the ~15-25MB file from being a cheap bandwidth-abuse target, not because the content itself
 * is sensitive.
 */
export function createAppDistributionRouter(services: AppDistributionServices): Router {
    const router = Router();
    const rateLimit = createRateLimitMiddleware(services.downloadRateLimiter);
    const downloadUrl = `${services.publicUrl.replace(/\/$/, '')}/app/download`;
    const apkPath = services.apkPath ?? DEFAULT_APK_PATH;

    router.get('/app', rateLimit, async (_req: Request, res: Response) => {
        if (!fs.existsSync(apkPath)) {
            res.status(503).send(
                'App-Download derzeit nicht verfügbar: Diese Adapterinstallation hat keine gebündelte APK (app/mobile-control.apk fehlt).',
            );
            return;
        }
        try {
            const qrDataUrl = await QRCode.toDataURL(downloadUrl);
            res.set('Content-Type', 'text/html; charset=utf-8').send(
                renderInstallPage(readAdapterVersion(), downloadUrl, qrDataUrl),
            );
        } catch (err) {
            services.adapter.log.error(`mobile-control: failed to render app install page: ${(err as Error).message}`);
            res.status(500).send('Interner Fehler beim Erzeugen der Installationsseite.');
        }
    });

    router.get('/app/download', rateLimit, (_req: Request, res: Response) => {
        if (!fs.existsSync(apkPath)) {
            res.status(404).json({ error: 'NOT_FOUND', message: 'no APK bundled with this adapter installation' });
            return;
        }
        res.download(apkPath, `mobile-control-${readAdapterVersion()}.apk`, (err) => {
            if (err) {
                services.adapter.log.warn(`mobile-control: APK download failed: ${err.message}`);
            }
        });
    });

    return router;
}
