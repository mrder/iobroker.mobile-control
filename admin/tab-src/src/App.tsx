import React, { useEffect, useState } from 'react';
import { AppBar, Toolbar, Typography, Tabs, Tab, Box, Alert, CircularProgress } from '@mui/material';
import { getConnection } from './connection';
import OverviewTab from './tabs/OverviewTab';
import UsersRolesTab from './tabs/UsersRolesTab';
import DevicesPairingTab from './tabs/DevicesPairingTab';
import ExposureTab from './tabs/ExposureTab';
import ProfilesTab from './tabs/ProfilesTab';
import SessionsTab from './tabs/SessionsTab';
import AuditTab from './tabs/AuditTab';

const TAB_LABELS = ['Übersicht', 'Benutzer & Rollen', 'Geräte & Pairing', 'Objektfreigaben', 'Freigabeprofile', 'Sessions', 'Audit'];

export default function App(): JSX.Element {
    const [tab, setTab] = useState(0);
    const [status, setStatus] = useState<'connecting' | 'ready' | 'error'>('connecting');
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let cancelled = false;
        getConnection()
            .then(() => {
                if (!cancelled) setStatus('ready');
            })
            .catch((err: Error) => {
                if (!cancelled) {
                    setStatus('error');
                    setError(err.message);
                }
            });
        return () => {
            cancelled = true;
        };
    }, []);

    return (
        <Box sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
            <AppBar position="static" color="default" elevation={1}>
                <Toolbar variant="dense">
                    <Typography variant="h6" sx={{ flexGrow: 1 }}>
                        Mobile Control
                    </Typography>
                </Toolbar>
                <Tabs value={tab} onChange={(_, v) => setTab(v)} variant="scrollable" scrollButtons="auto">
                    {TAB_LABELS.map((label) => (
                        <Tab key={label} label={label} />
                    ))}
                </Tabs>
            </AppBar>

            <Box sx={{ p: 2, flex: 1, overflow: 'auto' }}>
                {status === 'connecting' && (
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                        <CircularProgress size={20} />
                        <Typography>Verbinde mit dem Adapter…</Typography>
                    </Box>
                )}
                {status === 'error' && (
                    <Alert severity="error">
                        Verbindung zum Adapter fehlgeschlagen: {error}. Bitte Seite neu laden oder prüfen, ob die Adapterinstanz läuft.
                    </Alert>
                )}
                {status === 'ready' && (
                    <>
                        {tab === 0 && <OverviewTab />}
                        {tab === 1 && <UsersRolesTab />}
                        {tab === 2 && <DevicesPairingTab />}
                        {tab === 3 && <ExposureTab />}
                        {tab === 4 && <ProfilesTab />}
                        {tab === 5 && <SessionsTab />}
                        {tab === 6 && <AuditTab />}
                    </>
                )}
            </Box>
        </Box>
    );
}
