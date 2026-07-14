import React, { useEffect, useState } from 'react';
import { Grid, Paper, Typography, Button, Box } from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { callAdapter } from '../connection';
import type { Overview } from '../types';

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
    const [loading, setLoading] = useState(false);

    const load = (): void => {
        setLoading(true);
        callAdapter<Overview>('getOverview')
            .then(setOverview)
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
            <Grid container spacing={2}>
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
        </Box>
    );
}
