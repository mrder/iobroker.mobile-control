import React, { useEffect, useState } from 'react';
import { Box, Typography, Table, TableHead, TableRow, TableCell, TableBody, Button, Chip } from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { callAdapter } from '../connection';
import type { AuditEvent } from '../types';

export default function AuditTab(): JSX.Element {
    const [events, setEvents] = useState<AuditEvent[]>([]);

    const load = (): void => {
        void callAdapter<AuditEvent[]>('listAudit', { limit: 200 }).then(setEvents);
    };

    useEffect(load, []);

    return (
        <Box>
            <Box sx={{ display: 'flex', alignItems: 'center', mb: 2 }}>
                <Typography variant="h6" sx={{ flexGrow: 1 }}>
                    Audit-Log
                </Typography>
                <Button startIcon={<RefreshIcon />} onClick={load}>
                    Aktualisieren
                </Button>
            </Box>
            <Table size="small">
                <TableHead>
                    <TableRow>
                        <TableCell>Zeit</TableCell>
                        <TableCell>Aktion</TableCell>
                        <TableCell>Ergebnis</TableCell>
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
                            <TableCell sx={{ fontFamily: 'monospace', fontSize: 11 }}>{event.objectId ?? '–'}</TableCell>
                            <TableCell>{event.detail ?? '–'}</TableCell>
                        </TableRow>
                    ))}
                </TableBody>
            </Table>
        </Box>
    );
}
