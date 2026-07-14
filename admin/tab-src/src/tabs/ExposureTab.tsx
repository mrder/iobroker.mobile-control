import React, { useEffect, useMemo, useState } from 'react';
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
    Dialog,
    DialogTitle,
    DialogContent,
    DialogActions,
    Select,
    MenuItem,
    FormControlLabel,
    Checkbox,
    IconButton,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import { callAdapter } from '../connection';
import type { ExposureRule, ObjectTreeEntry, Role, User, Device } from '../types';

type OwnerType = 'role' | 'user' | 'device';

export default function ExposureTab(): JSX.Element {
    const [tree, setTree] = useState<ObjectTreeEntry[]>([]);
    const [rules, setRules] = useState<ExposureRule[]>([]);
    const [roles, setRoles] = useState<Role[]>([]);
    const [users, setUsers] = useState<User[]>([]);
    const [devices, setDevices] = useState<Device[]>([]);
    const [search, setSearch] = useState('');
    const [dialogTarget, setDialogTarget] = useState<ObjectTreeEntry | null>(null);
    const [ownerType, setOwnerType] = useState<OwnerType>('role');
    const [ownerId, setOwnerId] = useState('');
    const [read, setRead] = useState(true);
    const [write, setWrite] = useState(false);
    const [history, setHistory] = useState(false);

    const loadAll = (): void => {
        void callAdapter<ObjectTreeEntry[]>('browseObjectTree').then(setTree);
        void callAdapter<ExposureRule[]>('listExposureRules').then(setRules);
        void callAdapter<Role[]>('listRoles').then(setRoles);
        void callAdapter<User[]>('listUsers').then(setUsers);
        void callAdapter<Device[]>('listDevices').then(setDevices);
    };

    useEffect(loadAll, []);

    const filtered = useMemo(() => {
        const term = search.trim().toLowerCase();
        if (!term) return tree.slice(0, 200);
        return tree.filter((e) => e.id.toLowerCase().includes(term) || e.name.toLowerCase().includes(term)).slice(0, 200);
    }, [tree, search]);

    const openDialog = (entry: ObjectTreeEntry): void => {
        setDialogTarget(entry);
        setOwnerType('role');
        setOwnerId('');
        setRead(true);
        setWrite(false);
        setHistory(false);
    };

    const saveRule = async (): Promise<void> => {
        if (!dialogTarget || !ownerId) return;
        await callAdapter('createExposureRule', {
            scope: 'state',
            target: dialogTarget.id,
            roleId: ownerType === 'role' ? ownerId : null,
            userId: ownerType === 'user' ? ownerId : null,
            deviceId: ownerType === 'device' ? ownerId : null,
            deny: false,
            read,
            write,
            history,
            min: null,
            max: null,
            step: null,
            allowedValues: null,
            localOnly: false,
            confirmPolicy: 'NONE',
            displayName: null,
            suggestedWidgets: null,
        });
        setDialogTarget(null);
        loadAll();
    };

    const deleteRule = async (id: string): Promise<void> => {
        await callAdapter('deleteExposureRule', { id });
        loadAll();
    };

    const ownerLabel = (rule: ExposureRule): string => {
        if (rule.deviceId) return `Gerät: ${devices.find((d) => d.id === rule.deviceId)?.name ?? rule.deviceId}`;
        if (rule.userId) return `Benutzer: ${users.find((u) => u.id === rule.userId)?.name ?? rule.userId}`;
        if (rule.roleId) return `Rolle: ${roles.find((r) => r.id === rule.roleId)?.name ?? rule.roleId}`;
        return '–';
    };

    const ownerOptions = ownerType === 'role' ? roles : ownerType === 'user' ? users : devices;

    return (
        <Grid container spacing={3}>
            <Grid item xs={12} md={7}>
                <Paper sx={{ p: 2 }}>
                    <Typography variant="h6" gutterBottom>
                        Objektbaum
                    </Typography>
                    <TextField
                        fullWidth
                        size="small"
                        placeholder="Suche nach Name oder ID…"
                        value={search}
                        onChange={(e) => setSearch(e.target.value)}
                        sx={{ mb: 2 }}
                    />
                    <Table size="small">
                        <TableHead>
                            <TableRow>
                                <TableCell>ID</TableCell>
                                <TableCell>Name</TableCell>
                                <TableCell>Rolle</TableCell>
                                <TableCell />
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {filtered.map((entry) => (
                                <TableRow key={entry.id}>
                                    <TableCell sx={{ fontFamily: 'monospace', fontSize: 12 }}>{entry.id}</TableCell>
                                    <TableCell>{entry.name}</TableCell>
                                    <TableCell>{entry.role}</TableCell>
                                    <TableCell align="right">
                                        <Button size="small" onClick={() => openDialog(entry)}>
                                            Freigeben
                                        </Button>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                    {tree.length > filtered.length && (
                        <Typography variant="caption" color="text.secondary">
                            {filtered.length} von {tree.length} Objekten angezeigt – weiter eingrenzen mit der Suche.
                        </Typography>
                    )}
                </Paper>
            </Grid>

            <Grid item xs={12} md={5}>
                <Paper sx={{ p: 2 }}>
                    <Typography variant="h6" gutterBottom>
                        Aktive Freigaben
                    </Typography>
                    <Table size="small">
                        <TableHead>
                            <TableRow>
                                <TableCell>Objekt</TableCell>
                                <TableCell>Für</TableCell>
                                <TableCell>Rechte</TableCell>
                                <TableCell />
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {rules.map((rule) => (
                                <TableRow key={rule.id}>
                                    <TableCell sx={{ fontFamily: 'monospace', fontSize: 11 }}>{rule.target}</TableCell>
                                    <TableCell>{ownerLabel(rule)}</TableCell>
                                    <TableCell>
                                        {rule.deny ? 'verboten' : [rule.read && 'R', rule.write && 'W', rule.history && 'H'].filter(Boolean).join('')}
                                    </TableCell>
                                    <TableCell align="right">
                                        <IconButton size="small" onClick={() => deleteRule(rule.id)}>
                                            <DeleteIcon fontSize="small" />
                                        </IconButton>
                                    </TableCell>
                                </TableRow>
                            ))}
                        </TableBody>
                    </Table>
                </Paper>
            </Grid>

            <Dialog open={!!dialogTarget} onClose={() => setDialogTarget(null)} maxWidth="xs" fullWidth>
                <DialogTitle>Objekt freigeben</DialogTitle>
                <DialogContent>
                    <Typography variant="body2" sx={{ mb: 2, fontFamily: 'monospace' }}>
                        {dialogTarget?.id}
                    </Typography>
                    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
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
                        <FormControlLabel control={<Checkbox checked={read} onChange={(e) => setRead(e.target.checked)} />} label="Lesen" />
                        <FormControlLabel control={<Checkbox checked={write} onChange={(e) => setWrite(e.target.checked)} />} label="Schreiben" />
                        <FormControlLabel
                            control={<Checkbox checked={history} onChange={(e) => setHistory(e.target.checked)} />}
                            label="Historie"
                        />
                    </Box>
                </DialogContent>
                <DialogActions>
                    <Button onClick={() => setDialogTarget(null)}>Abbrechen</Button>
                    <Button variant="contained" onClick={saveRule} disabled={!ownerId}>
                        Speichern
                    </Button>
                </DialogActions>
            </Dialog>
        </Grid>
    );
}
