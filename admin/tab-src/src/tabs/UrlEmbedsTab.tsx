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
    Grid,
    Select,
    MenuItem,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import { callAdapter } from '../connection';
import type { UrlEmbed, UrlEmbedAccessRule, Role, User, Device } from '../types';

type OwnerType = 'role' | 'user' | 'device';

export default function UrlEmbedsTab(): JSX.Element {
    const [embeds, setEmbeds] = useState<UrlEmbed[]>([]);
    const [accessRules, setAccessRules] = useState<UrlEmbedAccessRule[]>([]);
    const [roles, setRoles] = useState<Role[]>([]);
    const [users, setUsers] = useState<User[]>([]);
    const [devices, setDevices] = useState<Device[]>([]);
    const [newName, setNewName] = useState('');
    const [newUrl, setNewUrl] = useState('');
    const [error, setError] = useState<string | null>(null);

    const [grantEmbedId, setGrantEmbedId] = useState('');
    const [ownerType, setOwnerType] = useState<OwnerType>('role');
    const [ownerId, setOwnerId] = useState('');

    const load = (): void => {
        void callAdapter<UrlEmbed[]>('listUrlEmbeds').then(setEmbeds);
        void callAdapter<UrlEmbedAccessRule[]>('listUrlEmbedAccessRules').then(setAccessRules);
        void callAdapter<Role[]>('listRoles').then(setRoles);
        void callAdapter<User[]>('listUsers').then(setUsers);
        void callAdapter<Device[]>('listDevices').then(setDevices);
    };

    useEffect(load, []);

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

    const grantAccess = async (): Promise<void> => {
        if (!grantEmbedId || !ownerId) return;
        setError(null);
        try {
            await callAdapter('createUrlEmbedAccessRule', {
                urlEmbedId: grantEmbedId,
                roleId: ownerType === 'role' ? ownerId : null,
                userId: ownerType === 'user' ? ownerId : null,
                deviceId: ownerType === 'device' ? ownerId : null,
                deny: false,
            });
            setOwnerId('');
            load();
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Unbekannter Fehler');
        }
    };

    const revokeAccess = async (id: string): Promise<void> => {
        await callAdapter('deleteUrlEmbedAccessRule', { id });
        load();
    };

    const embedName = (id: string): string => embeds.find((e) => e.id === id)?.name ?? id;
    const ownerLabel = (rule: UrlEmbedAccessRule): string => {
        if (rule.deviceId) return `Gerät: ${devices.find((d) => d.id === rule.deviceId)?.name ?? rule.deviceId}`;
        if (rule.userId) return `Benutzer: ${users.find((u) => u.id === rule.userId)?.name ?? rule.userId}`;
        if (rule.roleId) return `Rolle: ${roles.find((r) => r.id === rule.roleId)?.name ?? rule.roleId}`;
        return '–';
    };
    const ownerOptions = ownerType === 'role' ? roles : ownerType === 'user' ? users : devices;

    return (
        <Grid container spacing={3}>
            <Grid item xs={12}>
                <Paper sx={{ p: 2 }}>
                    <Typography variant="h6" gutterBottom>
                        URL-Einbettungen
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                        Erlaubt der App, ausgewählte externe URLs (z.B. den Schnappschuss-Link einer Kamera oder die
                        lokale Web-Oberfläche eines anderen Geräts) als Widget einzubinden - bewusst nur als Allowlist:
                        ein Gerät sieht nie eine beliebige URL, sondern kann nur diese hier freigegebenen Einträge
                        abrufen. Wer welchen Eintrag überhaupt sehen darf, wird unten wie bei den Objektfreigaben
                        einzeln je Rolle/Benutzer/Gerät festgelegt - ein neu angelegter Eintrag ist zunächst für
                        niemanden sichtbar.
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
            </Grid>

            <Grid item xs={12} md={6}>
                <Paper sx={{ p: 2 }}>
                    <Typography variant="h6" gutterBottom>
                        Zugriff freigeben
                    </Typography>
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                        <Select size="small" displayEmpty value={grantEmbedId} onChange={(e) => setGrantEmbedId(e.target.value)}>
                            <MenuItem value="" disabled>
                                Einbettung auswählen…
                            </MenuItem>
                            {embeds.map((embed) => (
                                <MenuItem key={embed.id} value={embed.id}>
                                    {embed.name}
                                </MenuItem>
                            ))}
                        </Select>
                        <Select
                            size="small"
                            value={ownerType}
                            onChange={(e) => {
                                setOwnerType(e.target.value as OwnerType);
                                setOwnerId('');
                            }}
                        >
                            <MenuItem value="role">Rolle</MenuItem>
                            <MenuItem value="user">Benutzer</MenuItem>
                            <MenuItem value="device">Gerät</MenuItem>
                        </Select>
                        <Select size="small" displayEmpty value={ownerId} onChange={(e) => setOwnerId(e.target.value)}>
                            <MenuItem value="" disabled>
                                Auswählen…
                            </MenuItem>
                            {ownerOptions.map((o) => (
                                <MenuItem key={o.id} value={o.id}>
                                    {o.name}
                                </MenuItem>
                            ))}
                        </Select>
                        <Button variant="contained" onClick={grantAccess} disabled={!grantEmbedId || !ownerId}>
                            Freigeben
                        </Button>
                    </Box>
                </Paper>
            </Grid>

            <Grid item xs={12} md={6}>
                <Paper sx={{ p: 2 }}>
                    <Typography variant="h6" gutterBottom>
                        Aktive Freigaben
                    </Typography>
                    <Table size="small">
                        <TableHead>
                            <TableRow>
                                <TableCell>Einbettung</TableCell>
                                <TableCell>Für</TableCell>
                                <TableCell />
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {accessRules.map((rule) => (
                                <TableRow key={rule.id}>
                                    <TableCell>{embedName(rule.urlEmbedId)}</TableCell>
                                    <TableCell>{ownerLabel(rule)}</TableCell>
                                    <TableCell align="right">
                                        <IconButton size="small" onClick={() => revokeAccess(rule.id)}>
                                            <DeleteIcon fontSize="small" />
                                        </IconButton>
                                    </TableCell>
                                </TableRow>
                            ))}
                            {accessRules.length === 0 && (
                                <TableRow>
                                    <TableCell colSpan={3}>Noch keine Freigaben - Einbettungen sind bis dahin für niemanden sichtbar.</TableCell>
                                </TableRow>
                            )}
                        </TableBody>
                    </Table>
                </Paper>
            </Grid>
        </Grid>
    );
}
