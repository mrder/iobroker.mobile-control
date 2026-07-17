import React, { useEffect, useState } from 'react';
import { Grid, Paper, Typography, Button, Box, Alert, Chip } from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { callAdapter } from '../connection';
import type { ConnectionInfo, Overview } from '../types';

function StatCard({ label, value }: { label: string; value: number | string }): JSX.Element {
    return (
        <Paper sx={{ p: 2, minWidth: 160 }}>
            <Typography variant="body2" color="text.secondary">
                {label}
            </Typography>
            <Typography variant="h4">{value}</Typography>
        </Paper>
    );
}

export default function OverviewTab(): JSX.Element {
    const [overview, setOverview] = useState<Overview | null>(null);
    const [connection, setConnection] = useState<ConnectionInfo | null>(null);
    const [loading, setLoading] = useState(false);

    const load = (): void => {
        setLoading(true);
        Promise.all([callAdapter<Overview>('getOverview'), callAdapter<ConnectionInfo>('getConnectionInfo')])
            .then(([o, c]) => {
                setOverview(o);
                setConnection(c);
            })
            .finally(() => setLoading(false));
    };

    useEffect(() => {
        load();
        const interval = setInterval(load, 10_000);
        return () => clearInterval(interval);
    }, []);

    return (
        <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <Typography variant="h6" sx={{ flexGrow: 1 }}>
                    Übersicht
                </Typography>
                <Button startIcon={<RefreshIcon />} onClick={load} disabled={loading}>
                    Aktualisieren
                </Button>
            </Box>
            <Grid container spacing={2} sx={{ mb: 3 }}>
                <Grid item>
                    <StatCard label="Benutzer" value={overview?.users ?? '–'} />
                </Grid>
                <Grid item>
                    <StatCard label="Geräte" value={overview?.devices ?? '–'} />
                </Grid>
                <Grid item>
                    <StatCard label="Wartende Pairing-Anfragen" value={overview?.pendingClaims ?? '–'} />
                </Grid>
                <Grid item>
                    <StatCard label="Aktive Sessions" value={overview?.activeSessions ?? '–'} />
                </Grid>
                <Grid item>
                    <StatCard label="Verbundene Geräte (live)" value={overview?.connectedDevices ?? '–'} />
                </Grid>
            </Grid>

            <Paper sx={{ p: 2 }}>
                <Typography variant="h6" gutterBottom>
                    Verbindung & Fernzugriff
                </Typography>
                {connection && (
                    <>
                        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 2 }}>
                            <Chip label={`Port: ${connection.port}`} />
                            <Chip label={`Bind-Adresse: ${connection.bindAddress}`} />
                            <Chip
                                label={`Öffentliche URL: ${connection.publicUrl || '(nicht gesetzt)'}`}
                                color={connection.publicUrl ? 'default' : 'warning'}
                            />
                        </Box>
                        <Typography variant="body2" sx={{ mb: 1 }}>
                            Im lokalen Netz erreichbar unter:
                        </Typography>
                        <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', mb: 2 }}>
                            {connection.localAddresses.length === 0 && (
                                <Typography variant="body2" color="text.secondary">
                                    Keine nicht-lokale IPv4-Adresse gefunden.
                                </Typography>
                            )}
                            {connection.localAddresses.map((addr) => (
                                <Chip key={addr} size="small" label={`${addr}:${connection.port}`} variant="outlined" />
                            ))}
                        </Box>
                        <Alert severity="info" sx={{ mb: 1 }}>
                            Dieser Adapter terminiert selbst kein TLS und ist kein VPN/Reverse-Proxy. Für Zugriff von
                            unterwegs entweder (a) ein VPN zum Heimnetz nutzen (WireGuard, Tailscale, …) – dann bleibt
                            alles wie im lokalen Netz – oder (b) einen eigenen Reverse Proxy (nginx, Caddy, Traefik, …)
                            mit TLS davor stellen, der nur den obigen Port dieses Hosts nach <code>/api/v1</code> und{' '}
                            <code>/ws/v1</code> weiterleitet. In beiden Fällen die „Öffentliche Server-URL" in den
                            Einstellungen auf die von außen erreichbare Adresse setzen. Den Adapter-Port niemals direkt
                            per Portfreigabe am Router ins Internet stellen. Konkrete nginx/Caddy-Beispiele und eine
                            Firewall-Checkliste: <code>docs/DEPLOYMENT.md</code> im Repository.
                        </Alert>
                    </>
                )}
            </Paper>
        </Box>
    );
}
