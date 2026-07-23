import React, { useEffect, useState } from 'react';
import {
    Box,
    Typography,
    Table,
    TableHead,
    TableRow,
    TableCell,
    TableBody,
    TextField,
    Button,
    IconButton,
    Paper,
    Alert,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import { callAdapter } from '../connection';
import type { UrlEmbed } from '../types';

export default function UrlEmbedsTab(): JSX.Element {
    const [embeds, setEmbeds] = useState<UrlEmbed[]>([]);
    const [newName, setNewName] = useState('');
    const [newUrl, setNewUrl] = useState('');
    const [error, setError] = useState<string | null>(null);

    const load = (): void => {
        void callAdapter<UrlEmbed[]>('listUrlEmbeds').then(setEmbeds);
    };

    useEffect(() => {
        load();
    }, []);

    const create = async (): Promise<void> => {
        if (!newName.trim() || !newUrl.trim()) return;
        setError(null);
        try {
            await callAdapter('createUrlEmbed', { name: newName.trim(), url: newUrl.trim() });
            setNewName('');
            setNewUrl('');
            load();
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Unbekannter Fehler');
        }
    };

    const remove = async (id: string): Promise<void> => {
        await callAdapter('deleteUrlEmbed', { id });
        load();
    };

    return (
        <Box sx={{ maxWidth: 900 }}>
            <Paper sx={{ p: 2 }}>
                <Typography variant="h6" gutterBottom>
                    URL-Einbettungen
                </Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                    Erlaubt der App, ausgewählte externe URLs (z.B. den Schnappschuss-Link einer Kamera oder die
                    lokale Web-Oberfläche eines anderen Geräts) als Widget einzubinden - bewusst nur als Allowlist:
                    ein Gerät sieht nie eine beliebige URL, sondern kann nur diese hier freigegebenen Einträge
                    abrufen.
                </Typography>
                {error && (
                    <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
                        {error}
                    </Alert>
                )}
                <Box sx={{ display: 'flex', gap: 1, mb: 2, flexWrap: 'wrap' }}>
                    <TextField size="small" label="Name" value={newName} onChange={(e) => setNewName(e.target.value)} />
                    <TextField
                        size="small"
                        label="URL"
                        placeholder="http://192.168.1.40/status"
                        value={newUrl}
                        onChange={(e) => setNewUrl(e.target.value)}
                        sx={{ minWidth: 320 }}
                    />
                    <Button variant="contained" onClick={create}>
                        Anlegen
                    </Button>
                </Box>
                <Table size="small">
                    <TableHead>
                        <TableRow>
                            <TableCell>Name</TableCell>
                            <TableCell>URL</TableCell>
                            <TableCell />
                        </TableRow>
                    </TableHead>
                    <TableBody>
                        {embeds.map((embed) => (
                            <TableRow key={embed.id}>
                                <TableCell>{embed.name}</TableCell>
                                <TableCell sx={{ wordBreak: 'break-all' }}>{embed.url}</TableCell>
                                <TableCell align="right">
                                    <IconButton size="small" onClick={() => remove(embed.id)}>
                                        <DeleteIcon fontSize="small" />
                                    </IconButton>
                                </TableCell>
                            </TableRow>
                        ))}
                        {embeds.length === 0 && (
                            <TableRow>
                                <TableCell colSpan={3}>Keine URL-Einbettungen konfiguriert.</TableCell>
                            </TableRow>
                        )}
                    </TableBody>
                </Table>
            </Paper>
        </Box>
    );
}
