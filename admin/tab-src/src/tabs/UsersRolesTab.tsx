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
    Select,
    MenuItem,
    Switch,
    IconButton,
    Grid,
    Paper,
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Chip,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import VisibilityIcon from '@mui/icons-material/Visibility';
import { callAdapter } from '../connection';
import type { Role, User, Device, EffectiveCatalog } from '../types';

export default function UsersRolesTab(): JSX.Element {
    const [roles, setRoles] = useState<Role[]>([]);
    const [users, setUsers] = useState<User[]>([]);
    const [devices, setDevices] = useState<Device[]>([]);
    const [newRoleName, setNewRoleName] = useState('');
    const [newUserName, setNewUserName] = useState('');
    const [newUserRole, setNewUserRole] = useState('');

    const [previewUser, setPreviewUser] = useState<User | null>(null);
    const [previewDeviceId, setPreviewDeviceId] = useState('');
    const [previewCatalog, setPreviewCatalog] = useState<EffectiveCatalog | null>(null);
    const [previewLoading, setPreviewLoading] = useState(false);

    const loadRoles = (): void => {
        void callAdapter<Role[]>('listRoles').then(setRoles);
    };
    const loadUsers = (): void => {
        void callAdapter<User[]>('listUsers').then(setUsers);
    };
    const loadDevices = (): void => {
        void callAdapter<Device[]>('listDevices').then(setDevices);
    };

    useEffect(() => {
        loadRoles();
        loadUsers();
        loadDevices();
    }, []);

    const fetchPreview = (userId: string, deviceId: string): void => {
        setPreviewLoading(true);
        callAdapter<EffectiveCatalog>('previewCatalog', { userId, deviceId: deviceId || undefined })
            .then(setPreviewCatalog)
            .finally(() => setPreviewLoading(false));
    };

    const openPreview = (user: User): void => {
        setPreviewUser(user);
        setPreviewDeviceId('');
        setPreviewCatalog(null);
        fetchPreview(user.id, '');
    };

    const runPreview = (deviceId: string): void => {
        if (!previewUser) return;
        setPreviewDeviceId(deviceId);
        fetchPreview(previewUser.id, deviceId);
    };

    const createRole = async (): Promise<void> => {
        if (!newRoleName.trim()) return;
        await callAdapter('createRole', { name: newRoleName.trim() });
        setNewRoleName('');
        loadRoles();
    };

    const deleteRole = async (id: string): Promise<void> => {
        await callAdapter('deleteRole', { id });
        loadRoles();
    };

    const createUser = async (): Promise<void> => {
        if (!newUserName.trim() || !newUserRole) return;
        await callAdapter('createUser', { name: newUserName.trim(), roleId: newUserRole });
        setNewUserName('');
        loadUsers();
    };

    const setUserRole = async (id: string, roleId: string): Promise<void> => {
        await callAdapter('setUserRole', { id, roleId });
        loadUsers();
    };

    const setUserDisabled = async (id: string, disabled: boolean): Promise<void> => {
        await callAdapter('setUserDisabled', { id, disabled });
        loadUsers();
    };

    const deleteUser = async (id: string): Promise<void> => {
        await callAdapter('deleteUser', { id });
        loadUsers();
    };

    return (
        <Grid container spacing={3}>
            <Grid item xs={12} md={5}>
                <Paper sx={{ p: 2 }}>
                    <Typography variant="h6" gutterBottom>
                        Rollen
                    </Typography>
                    <Box sx={{ display: 'flex', gap: 1, mb: 2 }}>
                        <TextField
                            size="small"
                            label="Neue Rolle"
                            value={newRoleName}
                            onChange={(e) => setNewRoleName(e.target.value)}
                        />
                        <Button variant="contained" onClick={createRole}>
                            Anlegen
                        </Button>
                    </Box>
                    <Table size="small">
                        <TableHead>
                            <TableRow>
                                <TableCell>Name</TableCell>
                                <TableCell>Typ</TableCell>
                                <TableCell />
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {roles.map((role) => (
                                <TableRow key={role.id}>
                                    <TableCell>{role.name}</TableCell>
                                    <TableCell>{role.builtIn ? 'fest' : 'benutzerdefiniert'}</TableCell>
                                    <TableCell align="right">
                                        {!role.builtIn && (
                                            <IconButton size="small" onClick={() => deleteRole(role.id)}>
                                                <DeleteIcon fontSize="small" />
                                            </IconButton>
                                        )}
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </Paper>
            </Grid>

            <Grid item xs={12} md={7}>
                <Paper sx={{ p: 2 }}>
                    <Typography variant="h6" gutterBottom>
                        Benutzer
                    </Typography>
                    <Box sx={{ display: 'flex', gap: 1, mb: 2, flexWrap: 'wrap' }}>
                        <TextField
                            size="small"
                            label="Name"
                            value={newUserName}
                            onChange={(e) => setNewUserName(e.target.value)}
                        />
                        <Select size="small" displayEmpty value={newUserRole} onChange={(e) => setNewUserRole(e.target.value)}>
                            <MenuItem value="" disabled>
                                Rolle wählen
                            </MenuItem>
                            {roles.map((role) => (
                                <MenuItem key={role.id} value={role.id}>
                                    {role.name}
                                </MenuItem>
                            ))}
                        </Select>
                        <Button variant="contained" onClick={createUser}>
                            Anlegen
                        </Button>
                    </Box>
                    <Table size="small">
                        <TableHead>
                            <TableRow>
                                <TableCell>Name</TableCell>
                                <TableCell>Rolle</TableCell>
                                <TableCell>Aktiv</TableCell>
                                <TableCell />
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {users.map((user) => (
                                <TableRow key={user.id}>
                                    <TableCell>{user.name}</TableCell>
                                    <TableCell>
                                        <Select size="small" value={user.roleId} onChange={(e) => setUserRole(user.id, e.target.value)}>
                                            {roles.map((role) => (
                                                <MenuItem key={role.id} value={role.id}>
                                                    {role.name}
                                                </MenuItem>
                                            ))}
                                        </Select>
                                    </TableCell>
                                    <TableCell>
                                        <Switch
                                            checked={!user.disabled}
                                            onChange={(e) => setUserDisabled(user.id, !e.target.checked)}
                                        />
                                    </TableCell>
                                    <TableCell align="right">
                                        <IconButton size="small" onClick={() => openPreview(user)} title="Effektive Freigaben ansehen">
                                            <VisibilityIcon fontSize="small" />
                                        </IconButton>
                                        <IconButton size="small" onClick={() => deleteUser(user.id)}>
                                            <DeleteIcon fontSize="small" />
                                        </IconButton>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </Paper>
            </Grid>

            <Dialog open={!!previewUser} onClose={() => setPreviewUser(null)} maxWidth="sm" fullWidth>
                <DialogTitle>Effektive Freigaben – {previewUser?.name}</DialogTitle>
                <DialogContent>
                    <Select
                        size="small"
                        fullWidth
                        value={previewDeviceId}
                        onChange={(e) => runPreview(e.target.value)}
                        sx={{ mb: 2 }}
                    >
                        <MenuItem value="">Rollenbasiert (kein bestimmtes Gerät)</MenuItem>
                        {devices
                            .filter((d) => d.userId === previewUser?.id)
                            .map((d) => (
                                <MenuItem key={d.id} value={d.id}>
                                    {d.name}
                                </MenuItem>
                            ))}
                    </Select>
                    {previewLoading && <Typography variant="body2">Lädt…</Typography>}
                    {!previewLoading && previewCatalog && (
                        <Table size="small">
                            <TableHead>
                                <TableRow>
                                    <TableCell>Objekt</TableCell>
                                    <TableCell>Rechte</TableCell>
                                </TableRow>
                            </TableHead>
                            <TableBody>
                                {previewCatalog.objects.map((o) => (
                                    <TableRow key={o.id}>
                                        <TableCell>{o.name}</TableCell>
                                        <TableCell>
                                            <Chip size="small" label={[o.read && 'R', o.write && 'W', o.history && 'H'].filter(Boolean).join('') || '–'} />
                                        </TableCell>
                                    </TableRow>
                                ))}
                                {previewCatalog.objects.length === 0 && (
                                    <TableRow>
                                        <TableCell colSpan={2}>Keine sichtbaren Objekte.</TableCell>
                                    </TableRow>
                                )}
                            </TableBody>
                        </Table>
                    )}
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setPreviewUser(null)}>Schließen</Button>
                </DialogActions>
            </Dialog>
        </Grid>
    );
}
