import React, { useEffect, useState } from 'react';
import { Box, Paper, Typography, Alert, Link, Divider, Button } from '@mui/material';
import GitHubIcon from '@mui/icons-material/GitHub';
import DownloadIcon from '@mui/icons-material/Download';
import { callAdapter } from '../connection';
import type { AppInfo } from '../types';

const GITHUB_URL = 'https://github.com/mrder/iobroker.mobile-control';

/**
 * Live-requested (2026-07-30): the app-install QR code previously only existed on the /app page
 * itself, which - like every other route since the portal-key gate landed - needs the portal key
 * just to open. Nobody found it. This tab surfaces the same QR code directly inside the already-
 * authenticated admin UI, plus the GitHub link and license/copyright info the user asked for.
 */
export default function InfoTab(): JSX.Element {
    const [appInfo, setAppInfo] = useState<AppInfo | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        callAdapter<AppInfo>('getAppInfo')
            .then(setAppInfo)
            .catch((err: Error) => setError(err.message))
            .finally(() => setLoading(false));
    }, []);

    return (
        <Box>
            <Typography variant="h6" gutterBottom>
                App-Installation
            </Typography>
            <Paper sx={{ p: 2, mb: 3 }}>
                {loading && (
                    <Typography variant="body2" color="text.secondary">
                        Lade…
                    </Typography>
                )}
                {error && <Alert severity="error">{error}</Alert>}
                {appInfo && !appInfo.available && (
                    <Alert severity="warning">
                        Diese Adapterinstallation enthält keine gebündelte App (app/mobile-control.apk fehlt) - Download
                        derzeit nicht möglich.
                    </Alert>
                )}
                {appInfo?.available && (
                    <>
                        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                            Mit der Tablet-/Handy-Kamera scannen, um die Mobile-Control-App (Version {appInfo.version}) direkt
                            zu installieren - der Portal-Schlüssel ist im QR-Code bereits enthalten, keine manuelle Eingabe
                            nötig.
                        </Typography>
                        <Box sx={{ display: 'flex', gap: 3, flexWrap: 'wrap', alignItems: 'flex-start' }}>
                            {appInfo.qrDataUrl && (
                                <Box component="img" src={appInfo.qrDataUrl} alt="QR-Code zur App-Installation" sx={{ width: 220, height: 220 }} />
                            )}
                            <Box sx={{ flex: 1, minWidth: 240 }}>
                                <Typography variant="body2" sx={{ mb: 1 }}>
                                    Oder auf einem Gerät im selben Netz öffnen:
                                </Typography>
                                <Typography variant="body2" sx={{ fontFamily: 'monospace', mb: 2, wordBreak: 'break-all' }}>
                                    {appInfo.installUrl}
                                </Typography>
                                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                                    Beim manuellen Öffnen fragt der Browser nach dem Portal-Schlüssel (siehe Tab „Übersicht").
                                </Typography>
                                <Button
                                    variant="outlined"
                                    startIcon={<DownloadIcon />}
                                    href={appInfo.installUrl}
                                    target="_blank"
                                    rel="noreferrer"
                                >
                                    Installationsseite öffnen
                                </Button>
                            </Box>
                        </Box>
                    </>
                )}
            </Paper>

            <Typography variant="h6" gutterBottom>
                Projekt
            </Typography>
            <Paper sx={{ p: 2 }}>
                <Button startIcon={<GitHubIcon />} href={GITHUB_URL} target="_blank" rel="noreferrer" sx={{ mb: 2 }}>
                    Quellcode auf GitHub
                </Button>
                <Divider sx={{ mb: 2 }} />
                <Typography variant="body2" color="text.secondary">
                    Mobile Control für ioBroker · MIT-Lizenz · © mrder
                </Typography>
                <Typography variant="body2" color="text.secondary">
                    Quellcode, Lizenztext und Dokumentation:{' '}
                    <Link href={GITHUB_URL} target="_blank" rel="noreferrer">
                        {GITHUB_URL}
                    </Link>
                </Typography>
            </Paper>
        </Box>
    );
}
