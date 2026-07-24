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
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { callAdapter } from '../connection';
import type { ExposureRule, ObjectTreeEntry, Role, User, Device } from '../types';

type OwnerType = 'role' | 'user' | 'device';

interface TreeNode {
    id: string;
    name: string;
    children: TreeNode[];
    /** The real backed object at this exact id, if any (states always have one; not every
     *  intermediate path segment does, e.g. an adapter's bare instance number). */
    entry?: ObjectTreeEntry;
}

/** Synthesizes a granting target for a path segment that has no matching ioBroker object of its
 *  own - exposure rules match by id prefix (see ExposureService.matchesScope), so this still
 *  works correctly even though the id itself was never a real "container" object. */
function syntheticEntry(node: TreeNode): ObjectTreeEntry {
    return { id: node.id, name: node.name, role: '', type: 'folder', unit: null, path: node.id.split('.'), kind: 'container' };
}

function buildTree(entries: ObjectTreeEntry[]): TreeNode[] {
    const byId = new Map<string, TreeNode>();
    const roots: TreeNode[] = [];

    const getOrCreate = (path: string[]): TreeNode => {
        const id = path.join('.');
        const existing = byId.get(id);
        if (existing) return existing;
        const node: TreeNode = { id, name: path[path.length - 1], children: [] };
        byId.set(id, node);
        if (path.length === 1) {
            roots.push(node);
        } else {
            getOrCreate(path.slice(0, -1)).children.push(node);
        }
        return node;
    };

    for (const entry of entries) {
        const node = getOrCreate(entry.path);
        node.entry = entry;
        if (entry.name) node.name = entry.name;
    }

    const isFolder = (n: TreeNode): boolean => n.children.length > 0 || n.entry?.kind !== 'state';
    const sortRec = (nodes: TreeNode[]): void => {
        nodes.sort((a, b) => {
            const af = isFolder(a);
            const bf = isFolder(b);
            return af === bf ? a.name.localeCompare(b.name) : af ? -1 : 1;
        });
        nodes.forEach((n) => sortRec(n.children));
    };
    sortRec(roots);
    return roots;
}

function TreeRow({
    node,
    depth,
    expanded,
    onToggle,
    onGrant,
}: {
    node: TreeNode;
    depth: number;
    expanded: Set<string>;
    onToggle: (id: string) => void;
    onGrant: (entry: ObjectTreeEntry) => void;
}): JSX.Element {
    const isOpen = expanded.has(node.id);
    const hasChildren = node.children.length > 0;

    return (
        <>
            <TableRow hover>
                <TableCell sx={{ pl: depth * 2.5 + 1, whiteSpace: 'nowrap' }}>
                    {hasChildren ? (
                        <IconButton size="small" onClick={() => onToggle(node.id)} sx={{ mr: 0.5, p: 0.25 }}>
                            {isOpen ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
                        </IconButton>
                    ) : (
                        <Box component="span" sx={{ display: 'inline-block', width: 28 }} />
                    )}
                    <Box component="span" sx={{ fontFamily: 'monospace', fontSize: 11, color: 'text.secondary' }}>
                        {node.id}
                    </Box>
                    {node.name && node.name !== node.id.split('.').pop() && (
                        <Box component="span"> – {node.name}</Box>
                    )}
                </TableCell>
                <TableCell>{node.entry?.kind === 'state' ? node.entry.role : hasChildren ? 'Ordner' : ''}</TableCell>
                <TableCell align="right">
                    <Button size="small" onClick={() => onGrant(node.entry ?? syntheticEntry(node))}>
                        Freigeben
                    </Button>
                </TableCell>
            </TableRow>
            {isOpen && node.children.map((child) => (
                <TreeRow key={child.id} node={child} depth={depth + 1} expanded={expanded} onToggle={onToggle} onGrant={onGrant} />
            ))}
        </>
    );
}

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
    const [expanded, setExpanded] = useState<Set<string>>(new Set());

    const loadAll = (): void => {
        void callAdapter<ObjectTreeEntry[]>('browseObjectTree').then(setTree);
        void callAdapter<ExposureRule[]>('listExposureRules').then(setRules);
        void callAdapter<Role[]>('listRoles').then(setRoles);
        void callAdapter<User[]>('listUsers').then(setUsers);
        void callAdapter<Device[]>('listDevices').then(setDevices);
    };

    useEffect(loadAll, []);

    const rootNodes = useMemo(() => buildTree(tree), [tree]);

    const toggleExpanded = (id: string): void => {
        setExpanded((prev) => {
            const next = new Set(prev);
            if (next.has(id)) next.delete(id);
            else next.add(id);
            return next;
        });
    };

    const filtered = useMemo(() => {
        const term = search.trim().toLowerCase();
        if (!term) return [];
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
            // A container target (folder/channel/device/adapter) is prefix-matched against every
            // state underneath it (see ExposureService.matchesScope) - 'channel' covers all of
            // those identically, there's no need for a finer-grained scope here.
            scope: dialogTarget.kind === 'state' ? 'state' : 'channel',
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
                                <TableCell>ID – Name</TableCell>
                                <TableCell>Rolle</TableCell>
                                <TableCell />
                            </TableRow>
                        </TableHead>
                        <TableBody>
                            {search.trim()
                                ? filtered.map((entry) => (
                                      <TableRow key={entry.id} hover>
                                          <TableCell>
                                              <Box component="span" sx={{ fontFamily: 'monospace', fontSize: 12, color: 'text.secondary' }}>
                                                  {entry.id}
                                              </Box>
                                              {entry.name && entry.name !== entry.id.split('.').pop() && (
                                                  <Box component="span"> – {entry.name}</Box>
                                              )}
                                          </TableCell>
                                          <TableCell>{entry.kind === 'state' ? entry.role : 'Ordner'}</TableCell>
                                          <TableCell align="right">
                                              <Button size="small" onClick={() => openDialog(entry)}>
                                                  Freigeben
                                              </Button>
                                          </TableCell>
                                      </TableRow>
                                  ))
                                : rootNodes.map((node) => (
                                      <TreeRow
                                          key={node.id}
                                          node={node}
                                          depth={0}
                                          expanded={expanded}
                                          onToggle={toggleExpanded}
                                          onGrant={openDialog}
                                      />
                                  ))}
                        </TableBody>
                    </Table>
                    {search.trim() && tree.length > filtered.length && (
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
                                    <TableCell sx={{ fontFamily: 'monospace', fontSize: 11 }}>
                                        {rule.target}
                                        {rule.scope !== 'state' && (
                                            <Typography component="span" variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                                                (Ordner, {rule.scope})
                                            </Typography>
                                        )}
                                    </TableCell>
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
                <DialogTitle>{dialogTarget?.kind === 'state' ? 'Datenpunkt freigeben' : 'Ganzen Ordner freigeben'}</DialogTitle>
                <DialogContent>
                    <Typography variant="body2" sx={{ mb: 1, fontFamily: 'monospace' }}>
                        {dialogTarget?.id}
                    </Typography>
                    {dialogTarget?.kind !== 'state' && (
                        <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 2 }}>
                            Gilt für alle Datenpunkte unterhalb dieses Ordners.
                        </Typography>
                    )}
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
