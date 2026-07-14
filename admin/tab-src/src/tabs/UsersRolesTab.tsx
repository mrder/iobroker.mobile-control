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
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import { callAdapter } from '../connection';
import type { Role, User } from '../types';

export default function UsersRolesTab(): JSX.Element {
    const [roles, setRoles] = useState<Role[]>([]);
    const [users, setUsers] = useState<User[]>([]);
    const [newRoleName, setNewRoleName] = useState('');
    const [newUserName, setNewUserName] = useState('');
    const [newUserRole, setNewUserRole] = useState('');

    const loadRoles = (): void => {
        void callAdapter<Role[]>('listRoles').then(setRoles);
    };
    const loadUsers = (): void => {
        void callAdapter<User[]>('listUsers').then(setUsers);
    };

    useEffect(() => {
        loadRoles();
        loadUsers();
    }, []);

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
        </Grid>
    );
}
