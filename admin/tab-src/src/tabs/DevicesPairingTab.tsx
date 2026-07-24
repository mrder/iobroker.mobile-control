import React, { useEffect, useState } from 'react';
import {
    Box,
    Typography,
    Grid,
    Paper,
    Select,
    MenuItem,
    Button,
    Table,
    TableHead,
    TableRow,
    TableCell,
    TableBody,
    Chip,
} from '@mui/material';
import { callAdapter } from '../connection';
import type { CreatedInvite, Device, PairingClaim, Role, User } from '../types';

const STATUS_COLOR: Record<Device['status'], 'default' | 'success' | 'error' | 'warning'> = {
    pending: 'warning',
    approved: 'success',
    rejected: 'error',
    revoked: 'error',
};

export default function DevicesPairingTab(): JSX.Element {
    const [users, setUsers] = useState<User[]>([]);
    const [roles, setRoles] = useState<Role[]>([]);
    const [selectedUser, setSelectedUser] = useState('');
    const [selectedRole, setSelectedRole] = useState('');
    const [invite, setInvite] = useState<CreatedInvite | null>(null);
    const [claims, setClaims] = useState<PairingClaim[]>([]);
    const [devices, setDevices] = useState<Device[]>([]);

    const loadAll = (): void => {
        void callAdapter<User[]>('listUsers').then(setUsers);
        void callAdapter<Role[]>('listRoles').then(setRoles);
        void callAdapter<PairingClaim[]>('listPendingClaims').then(setClaims);
        void callAdapter<Device[]>('listDevices').then(setDevices);
    };

    useEffect(() => {
        loadAll();
        const interval = setInterval(loadAll, 5_000);
        return () => clearInterval(interval);
    }, []);

    const createInvite = async (): Promise<void> => {
        if (!selectedUser || !selectedRole) return;
        const created = await callAdapter<CreatedInvite>('createPairingInvite', { userId: selectedUser, roleId: selectedRole });
        setInvite(created);
    };

    const approveClaim = async (claimId: string): Promise<void> => {
        await callAdapter('approveClaim', { claimId });
        loadAll();
    };
    const rejectClaim = async (claimId: string): Promise<void> => {
        await callAdapter('rejectClaim', { claimId });
        loadAll();
    };
    const revokeDevice = async (id: string): Promise<void> => {
        await callAdapter('revokeDevice', { id });
        loadAll();
    };
    const deleteDevice = async (id: string, name: string): Promise<void> => {
        if (!window.confirm(`"${name}" endgültig löschen? Freigaben für dieses Gerät gehen dabei ebenfalls verloren.`)) return;
        await callAdapter('deleteDevice', { id });
        loadAll();
    };

    const userName = (id: string): string => users.find((u) => u.id === id)?.name ?? id;

    return (
        <Grid container spacing={3}>
            <Grid item xs={12} md={4}>
                <Paper sx={{ p: 2 }}>
                    <Typography variant="h6" gutterBottom>
                        Neues Gerät koppeln
                    </Typography>
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                        <Select size="small" displayEmpty value={selectedUser} onChange={(e) => setSelectedUser(e.target.value)}>
                            <MenuItem value="" disabled>
                                Benutzer wählen
                            </MenuItem>
                            {users.map((u) => (
                                <MenuItem key={u.id} value={u.id}>
                                    {u.name}
                                </MenuItem>
                            ))}
                        </Select>
                        <Select size="small" displayEmpty value={selectedRole} onChange={(e) => setSelectedRole(e.target.value)}>
                            <MenuItem value="" disabled>
                                Rolle wählen
                            </MenuItem>
                            {roles.map((r) => (
                                <MenuItem key={r.id} value={r.id}>
                                    {r.name}
                                </MenuItem>
                            ))}
                        </Select>
                        <Button variant="contained" onClick={createInvite} disabled={!selectedUser || !selectedRole}>
                            QR-Einladung erzeugen
                        </Button>
                    </Box>

                    {invite && (
                        <Box sx={{ mt: 2, textAlign: 'center' }}>
                            <img
                                src={invite.qrPngDataUrl}
                                alt="Pairing-QR-Code"
                                style={{ width: '100%', maxWidth: 260 }}
                            />
                            <Typography variant="caption" display="block" sx={{ mt: 1 }}>
                                Gültig bis {new Date(invite.invite.expiresAt).toLocaleTimeString()} – nur einmal verwendbar.
                            </Typography>
                        </Box>
                    )}
                </Paper>
            </Grid>

            <Grid item xs={12} md={8}>
                <Paper sx={{ p: 2, mb: 3 }}>
                    <Typography variant="h6" gutterBottom>
                        Wartende Pairing-Anfragen
                    </Typography>
                    <Table size="small">
                        <TableHead>
                            <TableRow>
                                <TableCell>Gerät</TableCell>
                                <TableCell>Plattform</TableCell>
                                <TableCell>Erstellt</TableCell>
                                <TableCell />
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {claims.map((claim) => (
                                <TableRow key={claim.id}>
                                    <TableCell>{claim.deviceName}</TableCell>
                                    <TableCell>
                                        {claim.platform} {claim.appVersion}
                                    </TableCell>
                                    <TableCell>{new Date(claim.createdAt).toLocaleString()}</TableCell>
                                    <TableCell align="right">
                                        <Button size="small" onClick={() => approveClaim(claim.id)}>
                                            Bestätigen
                                        </Button>
                                        <Button size="small" color="error" onClick={() => rejectClaim(claim.id)}>
                                            Ablehnen
                                        </Button>
                                    </TableCell>
                                </TableRow>
                            ))}
                            {claims.length === 0 && (
                                <TableRow>
                                    <TableCell colSpan={4}>Keine wartenden Anfragen.</TableCell>
                                </TableRow>
                            )}
                        </TableBody>
                    </Table>
                </Paper>

                <Paper sx={{ p: 2 }}>
                    <Typography variant="h6" gutterBottom>
                        Geräte
                    </Typography>
                    <Table size="small">
                        <TableHead>
                            <TableRow>
                                <TableCell>Gerät</TableCell>
                                <TableCell>Benutzer</TableCell>
                                <TableCell>Status</TableCell>
                                <TableCell>Zuletzt gesehen</TableCell>
                                <TableCell />
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {devices.map((device) => (
                                <TableRow key={device.id}>
                                    <TableCell>{device.name}</TableCell>
                                    <TableCell>{userName(device.userId)}</TableCell>
                                    <TableCell>
                                        <Chip size="small" label={device.status} color={STATUS_COLOR[device.status]} />
                                    </TableCell>
                                    <TableCell>{device.lastSeenAt ? new Date(device.lastSeenAt).toLocaleString() : '–'}</TableCell>
                                    <TableCell align="right">
                                        {device.status !== 'revoked' && (
                                            <Button size="small" color="error" onClick={() => revokeDevice(device.id)}>
                                                Widerrufen
                                            </Button>
                                        )}
                                        <Button size="small" color="error" onClick={() => deleteDevice(device.id, device.name)}>
                                            Löschen
                                        </Button>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </Paper>
            </Grid>
        </Grid>
    );
}
