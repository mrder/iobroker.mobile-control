import React, { useEffect, useState } from 'react';
import { Box, Typography, Table, TableHead, TableRow, TableCell, TableBody, Button, Chip } from '@mui/material';
import { callAdapter } from '../connection';
import type { Session } from '../types';

export default function SessionsTab(): JSX.Element {
    const [sessions, setSessions] = useState<Session[]>([]);

    const load = (): void => {
        void callAdapter<Session[]>('listSessions').then(setSessions);
    };

    useEffect(() => {
        load();
        const interval = setInterval(load, 5_000);
        return () => clearInterval(interval);
    }, []);

    const revoke = async (id: string): Promise<void> => {
        await callAdapter('revokeSession', { id });
        load();
    };

    const revokeAll = async (): Promise<void> => {
        await callAdapter('revokeAllSessions');
        load();
    };

    return (
        <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <Typography variant="h6" sx={{ flexGrow: 1 }}>
                    Sessions
                </Typography>
                <Button color="error" variant="outlined" onClick={revokeAll}>
                    Alle Sessions widerrufen
                </Button>
            </Box>
            <Table size="small">
                <TableHead>
                    <TableRow>
                        <TableCell>Session</TableCell>
                        <TableCell>Gerät</TableCell>
                        <TableCell>Letzte Aktivität</TableCell>
                        <TableCell>Läuft ab</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell />
                    </TableRow>
                </TableHead>
                <TableBody>
                    {sessions.map((s) => (
                        <TableRow key={s.id}>
                            <TableCell sx={{ fontFamily: 'monospace', fontSize: 11 }}>{s.id.slice(0, 8)}…</TableCell>
                            <TableCell sx={{ fontFamily: 'monospace', fontSize: 11 }}>{s.deviceId.slice(0, 8)}…</TableCell>
                            <TableCell>{new Date(s.lastActivityAt).toLocaleString()}</TableCell>
                            <TableCell>{new Date(s.expiresAt).toLocaleString()}</TableCell>
                            <TableCell>
                                <Chip
                                    size="small"
                                    label={s.revoked ? 'widerrufen' : 'aktiv'}
                                    color={s.revoked ? 'error' : 'success'}
                                />
                            </TableCell>
                            <TableCell align="right">
                                {!s.revoked && (
                                    <Button size="small" color="error" onClick={() => revoke(s.id)}>
                                        Widerrufen
                                    </Button>
                                )}
                            </TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </Box>
    );
}
