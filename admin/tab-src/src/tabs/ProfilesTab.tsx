import React, { useEffect, useState } from 'react';
import {
    Box,
    Typography,
    Grid,
    Paper,
    TextField,
    Table,
    TableHead,
    TableRow,
    TableCell,
    TableBody,
    Button,
    Select,
    MenuItem,
    IconButton,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import { callAdapter } from '../connection';
import type { ExposureProfile, Role, User, Device } from '../types';

type OwnerType = 'role' | 'user' | 'device';

export default function ProfilesTab(): JSX.Element {
    const [profiles, setProfiles] = useState<ExposureProfile[]>([]);
    const [roles, setRoles] = useState<Role[]>([]);
    const [users, setUsers] = useState<User[]>([]);
    const [devices, setDevices] = useState<Device[]>([]);

    const [newName, setNewName] = useState('');
    const [sourceType, setSourceType] = useState<OwnerType>('role');
    const [sourceId, setSourceId] = useState('');

    const [selectedProfile, setSelectedProfile] = useState('');
    const [targetType, setTargetType] = useState<OwnerType>('user');
    const [targetId, setTargetId] = useState('');

    const loadAll = (): void => {
        void callAdapter<ExposureProfile[]>('listExposureProfiles').then(setProfiles);
        void callAdapter<Role[]>('listRoles').then(setRoles);
        void callAdapter<User[]>('listUsers').then(setUsers);
        void callAdapter<Device[]>('listDevices').then(setDevices);
    };

    useEffect(loadAll, []);

    const optionsFor = (type: OwnerType): { id: string; name: string }[] => {
        if (type === 'role') return roles;
        if (type === 'user') return users;
        return devices;
    };

    const createProfile = async (): Promise<void> => {
        if (!newName.trim() || !sourceId) return;
        await callAdapter('createExposureProfileFromOwner', { name: newName.trim(), ownerType: sourceType, ownerId: sourceId });
        setNewName('');
        setSourceId('');
        loadAll();
    };

    const deleteProfile = async (id: string): Promise<void> => {
        await callAdapter('deleteExposureProfile', { id });
        loadAll();
    };

    const applyProfile = async (): Promise<void> => {
        if (!selectedProfile || !targetId) return;
        await callAdapter('applyExposureProfile', { profileId: selectedProfile, ownerType: targetType, ownerId: targetId });
        loadAll();
    };

    return (
        <Grid container spacing={3}>
            <Grid item xs={12} md={5}>
                <Paper sx={{ p: 2 }}>
                    <Typography variant="h6" gutterBottom>
                        Neues Profil aus bestehenden Freigaben
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                        Übernimmt alle aktuellen Freigabe-Regeln der gewählten Rolle/des Benutzers/Geräts als
                        wiederverwendbare Vorlage (ohne den ursprünglichen Besitzer).
                    </Typography>
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                        <TextField size="small" label="Profilname" value={newName} onChange={(e) => setNewName(e.target.value)} />
                        <Select
                            size="small"
                            value={sourceType}
                            onChange={(e) => {
                                setSourceType(e.target.value as OwnerType);
                                setSourceId('');
                            }}
                        >
                            <MenuItem value="role">Aus Rolle</MenuItem>
                            <MenuItem value="user">Aus Benutzer</MenuItem>
                            <MenuItem value="device">Aus Gerät</MenuItem>
                        </Select>
                        <Select size="small" displayEmpty value={sourceId} onChange={(e) => setSourceId(e.target.value)}>
                            <MenuItem value="" disabled>
                                Quelle wählen…
                            </MenuItem>
                            {optionsFor(sourceType).map((o) => (
                                <MenuItem key={o.id} value={o.id}>
                                    {o.name}
                                </MenuItem>
                            ))}
                        </Select>
                        <Button variant="contained" onClick={createProfile} disabled={!newName.trim() || !sourceId}>
                            Profil erstellen
                        </Button>
                    </Box>
                </Paper>

                <Paper sx={{ p: 2, mt: 3 }}>
                    <Typography variant="h6" gutterBottom>
                        Profil anwenden
                    </Typography>
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                        <Select size="small" displayEmpty value={selectedProfile} onChange={(e) => setSelectedProfile(e.target.value)}>
                            <MenuItem value="" disabled>
                                Profil wählen…
                            </MenuItem>
                            {profiles.map((p) => (
                                <MenuItem key={p.id} value={p.id}>
                                    {p.name} ({p.rules.length} Regeln)
                                </MenuItem>
                            ))}
                        </Select>
                        <Select
                            size="small"
                            value={targetType}
                            onChange={(e) => {
                                setTargetType(e.target.value as OwnerType);
                                setTargetId('');
                            }}
                        >
                            <MenuItem value="role">Auf Rolle</MenuItem>
                            <MenuItem value="user">Auf Benutzer</MenuItem>
                            <MenuItem value="device">Auf Gerät</MenuItem>
                        </Select>
                        <Select size="small" displayEmpty value={targetId} onChange={(e) => setTargetId(e.target.value)}>
                            <MenuItem value="" disabled>
                                Ziel wählen…
                            </MenuItem>
                            {optionsFor(targetType).map((o) => (
                                <MenuItem key={o.id} value={o.id}>
                                    {o.name}
                                </MenuItem>
                            ))}
                        </Select>
                        <Button variant="contained" onClick={applyProfile} disabled={!selectedProfile || !targetId}>
                            Anwenden (erzeugt neue Regeln)
                        </Button>
                    </Box>
                </Paper>
            </Grid>

            <Grid item xs={12} md={7}>
                <Paper sx={{ p: 2 }}>
                    <Typography variant="h6" gutterBottom>
                        Vorhandene Profile
                    </Typography>
                    <Table size="small">
                        <TableHead>
                            <TableRow>
                                <TableCell>Name</TableCell>
                                <TableCell>Beschreibung</TableCell>
                                <TableCell>Regeln</TableCell>
                                <TableCell />
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {profiles.map((p) => (
                                <TableRow key={p.id}>
                                    <TableCell>{p.name}</TableCell>
                                    <TableCell>{p.description ?? '–'}</TableCell>
                                    <TableCell>{p.rules.length}</TableCell>
                                    <TableCell align="right">
                                        <IconButton size="small" onClick={() => deleteProfile(p.id)}>
                                            <DeleteIcon fontSize="small" />
                                        </IconButton>
                                    </TableCell>
                                </TableRow>
                            ))}
                            {profiles.length === 0 && (
                                <TableRow>
                                    <TableCell colSpan={4}>Noch keine Profile angelegt.</TableCell>
                                </TableRow>
                            )}
                        </TableBody>
                    </Table>
                </Paper>
            </Grid>
        </Grid>
    );
}
