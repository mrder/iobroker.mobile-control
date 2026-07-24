import React, { useEffect, useState } from 'react';
import { Box, Typography, Table, TableHead, TableRow, TableCell, TableBody, Button, Chip, TextField, Alert } from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { callAdapter } from '../connection';
import type { AuditEvent } from '../types';

export default function AuditTab(): JSX.Element {
    const [events, setEvents] = useState<AuditEvent[]>([]);
    const [keepDays, setKeepDays] = useState('7');
    const [info, setInfo] = useState<string | null>(null);

    const load = (): void => {
        void callAdapter<AuditEvent[]>('listAudit', { limit: 200 }).then(setEvents);
    };

    useEffect(load, []);

    const clearAll = async (): Promise<void> => {
        if (!window.confirm('Wirklich das gesamte Audit-Log unwiderruflich löschen?')) return;
        const result = await callAdapter<{ removed: number }>('clearAudit');
        setInfo(`${result.removed} Einträge gelöscht.`);
        load();
    };

    const clearOlderThan = async (): Promise<void> => {
        const days = Number(keepDays);
        if (!Number.isFinite(days) || days <= 0) return;
        if (!window.confirm(`Alle Einträge älter als ${days} Tage unwiderruflich löschen?`)) return;
        const result = await callAdapter<{ removed: number }>('clearAuditOlderThan', { days });
        setInfo(`${result.removed} Einträge gelöscht, die letzten ${days} Tage bleiben erhalten.`);
        load();
    };

    return (
        <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mb: 2, flexWrap: 'wrap' }}>
                <Typography variant="h6" sx={{ flexGrow: 1 }}>
                    Audit-Log
                </Typography>
                <TextField
                    size="small"
                    label="Tage behalten"
                    type="number"
                    value={keepDays}
                    onChange={(e) => setKeepDays(e.target.value)}
                    sx={{ width: 130 }}
                />
                <Button color="error" variant="outlined" onClick={clearOlderThan}>
                    Nur letzte {keepDays || 'N'} Tage behalten
                </Button>
                <Button color="error" variant="outlined" onClick={clearAll}>
                    Alles löschen
                </Button>
                <Button startIcon={<RefreshIcon />} onClick={load}>
                    Aktualisieren
                </Button>
            </Box>
            {info && (
                <Alert severity="info" sx={{ mb: 2 }} onClose={() => setInfo(null)}>
                    {info}
                </Alert>
            )}
            <Table size="small">
                <TableHead>
                    <TableRow>
                        <TableCell>Zeit</TableCell>
                        <TableCell>Aktion</TableCell>
                        <TableCell>Ergebnis</TableCell>
                        <TableCell>IP</TableCell>
                        <TableCell>Objekt</TableCell>
                        <TableCell>Details</TableCell>
                    </TableRow>
                </TableHead>
                <TableBody>
                    {events.map((event) => (
                        <TableRow key={event.id}>
                            <TableCell>{new Date(event.timestamp).toLocaleString()}</TableCell>
                            <TableCell>{event.action}</TableCell>
                            <TableCell>
                                <Chip
                                    size="small"
                                    label={event.result}
                                    color={event.result === 'success' ? 'success' : 'error'}
                                />
                            </TableCell>
                            <TableCell sx={{ fontFamily: 'monospace', fontSize: 11 }}>{event.ip ?? '–'}</TableCell>
                            <TableCell sx={{ fontFamily: 'monospace', fontSize: 11 }}>{event.objectId ?? '–'}</TableCell>
                            <TableCell>{event.detail ?? '–'}</TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </Box>
    );
}
