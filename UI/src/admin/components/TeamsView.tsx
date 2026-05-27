import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import {
  ChevronDown,
  ClipboardList,
  Copy,
  Download,
  Eye,
  EyeOff,
  KeyRound,
  MoreHorizontal,
  Pencil,
  RefreshCw,
  Search,
  ShieldAlert,
  ShieldCheck,
  Trash2,
  UserPlus,
} from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Button } from './ui/button';
import { RegisterModal } from './RegisterModal';
import { Input } from './ui/input';
import { Textarea } from './ui/textarea';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogTrigger,
} from './ui/dialog';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from './ui/alert-dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from './ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from './ui/tabs';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from './ui/table';
import { toast } from 'sonner';
import {
  deleteUser,
  exportModerationLogsCsv,
  generateTeamAccounts,
  getAllUsers,
  getContestTeamModerations,
  getModerationLogs,
  moderateContestTeam,
  updateUserName,
  updateUserPassword,
} from '../services/api';
import {
  ContestTeamModerationResponse,
  GeneratedTeamCredentialResponse,
  ModerationActionType,
  ModerationAuditLogResponse,
  ModerationLogFilters,
  UserResponse,
} from '../types/api';
import { StatusBadge } from '../../components/StatusBadge';
import { AdminHelpTooltip } from './AdminHelpTooltip';
import {
  buildCredentialRows,
  credentialsCsv,
  credentialsPlainText,
  credentialsXlsx,
} from '../utils/teamCredentialExport';
import { ContestOption, contestLabel, loadContestOptions } from '../utils/contestOptions';

type TeamsTab = 'accounts' | 'moderation' | 'logs';

type PendingModerationAction = {
  row: ContestTeamModerationResponse;
  actionType: ModerationActionType;
};

const ACTION_LABELS: Record<ModerationActionType, string> = {
  HIDE_FROM_SCOREBOARD: 'Hide from Scoreboard',
  SHOW_ON_SCOREBOARD: 'Show on Scoreboard',
  DISQUALIFY_TEAM: 'Disqualify Team',
  RESTORE_TEAM: 'Restore Team',
  DISABLE_SUBMIT: 'Disable Submit',
  ENABLE_SUBMIT: 'Enable Submit',
  DISABLE_RUN: 'Disable Run',
  ENABLE_RUN: 'Enable Run',
  ADMIN_RUN_LAB_EXECUTION: 'Admin Run Lab Execution',
};

const REASON_REQUIRED = new Set<ModerationActionType>([
  'HIDE_FROM_SCOREBOARD',
  'DISQUALIFY_TEAM',
  'DISABLE_SUBMIT',
  'DISABLE_RUN',
]);

const DESTRUCTIVE_ACTIONS = new Set<ModerationActionType>([
  'DISQUALIFY_TEAM',
  'DISABLE_SUBMIT',
  'DISABLE_RUN',
]);

const ACTION_OPTIONS: ModerationActionType[] = [
  'HIDE_FROM_SCOREBOARD',
  'SHOW_ON_SCOREBOARD',
  'DISQUALIFY_TEAM',
  'RESTORE_TEAM',
  'DISABLE_SUBMIT',
  'ENABLE_SUBMIT',
  'DISABLE_RUN',
  'ENABLE_RUN',
  'ADMIN_RUN_LAB_EXECUTION',
];

function formatDateTime(value: string | null | undefined): string {
  if (!value) return '-';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function shortJson(value: string | null | undefined): string {
  if (!value) return '-';
  return value.length > 96 ? `${value.slice(0, 96)}...` : value;
}

export function TeamsView() {
  const [activeTab, setActiveTab] = useState<TeamsTab>('accounts');
  const [registerModalOpen, setRegisterModalOpen] = useState(false);

  const [users, setUsers] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
  const [bulkOpen, setBulkOpen] = useState(false);
  const [bulkPrefix, setBulkPrefix] = useState('team');
  const [bulkStartNumber, setBulkStartNumber] = useState(1);
  const [bulkEndNumber, setBulkEndNumber] = useState(20);
  const [bulkPasswordLength, setBulkPasswordLength] = useState(10);
  const [bulkGenerating, setBulkGenerating] = useState(false);
  const [generatedCredentials, setGeneratedCredentials] = useState<GeneratedTeamCredentialResponse[]>([]);
  const [generatedCredentialsAt, setGeneratedCredentialsAt] = useState<string | null>(null);

  const [editNameOpen, setEditNameOpen] = useState(false);
  const [editPasswordOpen, setEditPasswordOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [selectedUser, setSelectedUser] = useState<UserResponse | null>(null);

  const [newUsername, setNewUsername] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');

  const [contestOptions, setContestOptions] = useState<ContestOption[]>([]);
  const [selectedContestId, setSelectedContestId] = useState<string>('');
  const [moderationRows, setModerationRows] = useState<ContestTeamModerationResponse[]>([]);
  const [moderationLoading, setModerationLoading] = useState(false);
  const [pendingAction, setPendingAction] = useState<PendingModerationAction | null>(null);
  const [moderationReason, setModerationReason] = useState('');
  const [moderationSaving, setModerationSaving] = useState(false);

  const [logs, setLogs] = useState<ModerationAuditLogResponse[]>([]);
  const [logsLoading, setLogsLoading] = useState(false);
  const [logContestId, setLogContestId] = useState('all');
  const [logTeamId, setLogTeamId] = useState('all');
  const [logAdminId, setLogAdminId] = useState('all');
  const [logActionType, setLogActionType] = useState<'all' | ModerationActionType>('all');
  const [logFrom, setLogFrom] = useState('');
  const [logTo, setLogTo] = useState('');

  const selectedTitle = useMemo(() => {
    if (!selectedUser) return '';
    return `${selectedUser.username} (#${selectedUser.id})`;
  }, [selectedUser]);

  const isAdminUser = (user: UserResponse) => user.role === 'ADMIN';
  const teamUsers = useMemo(() => users.filter((user) => user.role === 'TEAM'), [users]);
  const adminUsers = useMemo(() => users.filter((user) => user.role === 'ADMIN'), [users]);

  const selectedContestNumber = selectedContestId ? Number(selectedContestId) : null;
  const selectedContest = useMemo(
      () => contestOptions.find((contest) => String(contest.id) === selectedContestId) ?? null,
      [contestOptions, selectedContestId],
  );

  const filteredUsers = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return users;
    return users.filter((user) => (
        String(user.id).includes(q) ||
        user.username.toLowerCase().includes(q) ||
        user.role.toLowerCase().includes(q)
    ));
  }, [users, query]);

  const loadUsers = useCallback(async () => {
    setLoading(true);
    try {
      const list = await getAllUsers();
      setUsers(list);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to load users');
      setUsers([]);
    } finally {
      setLoading(false);
    }
  }, []);

  const loadContests = useCallback(async () => {
    try {
      const options = await loadContestOptions();
      setContestOptions(options);
      setSelectedContestId((current) => current || (options[0] ? String(options[0].id) : ''));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to load contests');
      setContestOptions([]);
    }
  }, []);

  const loadModerationRows = useCallback(async () => {
    if (selectedContestNumber == null) {
      setModerationRows([]);
      return;
    }
    setModerationLoading(true);
    try {
      setModerationRows(await getContestTeamModerations(selectedContestNumber));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to load team moderation');
      setModerationRows([]);
    } finally {
      setModerationLoading(false);
    }
  }, [selectedContestNumber]);

  const currentLogFilters = useCallback((): ModerationLogFilters => ({
    contestId: logContestId === 'all' ? null : Number(logContestId),
    teamId: logTeamId === 'all' ? null : Number(logTeamId),
    adminId: logAdminId === 'all' ? null : Number(logAdminId),
    actionType: logActionType === 'all' ? null : logActionType,
    from: logFrom || null,
    to: logTo || null,
  }), [logActionType, logAdminId, logContestId, logFrom, logTeamId, logTo]);

  const loadLogs = useCallback(async () => {
    setLogsLoading(true);
    try {
      setLogs(await getModerationLogs(currentLogFilters()));
    } catch {
      toast.error('Failed to load moderation logs.');
      setLogs([]);
    } finally {
      setLogsLoading(false);
    }
  }, [currentLogFilters]);

  useEffect(() => {
    loadUsers();
    loadContests();
  }, [loadContests, loadUsers]);

  useEffect(() => {
    if (activeTab === 'moderation') loadModerationRows();
  }, [activeTab, loadModerationRows]);

  useEffect(() => {
    if (activeTab === 'logs') loadLogs();
  }, [activeTab, loadLogs]);

  const credentialRows = () => buildCredentialRows(generatedCredentials, generatedCredentialsAt ?? new Date().toISOString());

  const copyText = async (value: string, successMessage: string) => {
    try {
      await navigator.clipboard.writeText(value);
      toast.success(successMessage);
    } catch {
      toast.error('Copy failed. Please copy the credentials manually.');
    }
  };

  const downloadBlob = (blob: Blob, filename: string, successMessage: string) => {
    const href = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = href;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(href);
    toast.success(successMessage);
  };

  const credentialsFilename = (extension: 'csv' | 'xlsx') =>
      `team-credentials-${new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-')}.${extension}`;

  const downloadCredentialsCsv = () => {
    if (generatedCredentials.length === 0) return;
    const blob = new Blob([credentialsCsv(credentialRows())], { type: 'text/csv;charset=utf-8' });
    downloadBlob(blob, credentialsFilename('csv'), 'Spreadsheet-safe credentials CSV downloaded');
  };

  const downloadCredentialsXlsx = () => {
    if (generatedCredentials.length === 0) return;
    downloadBlob(credentialsXlsx(credentialRows()), credentialsFilename('xlsx'), 'Credentials XLSX downloaded');
  };

  const handleGenerateTeams = async (event: FormEvent) => {
    event.preventDefault();
    const prefix = bulkPrefix.trim();

    if (!prefix) {
      toast.error('Prefix cannot be blank');
      return;
    }
    if (bulkStartNumber > bulkEndNumber) {
      toast.error('Start number must be less than or equal to end number');
      return;
    }
    if (bulkPasswordLength < 8) {
      toast.error('Password length must be at least 8 characters');
      return;
    }

    setBulkGenerating(true);
    try {
      const credentials = await generateTeamAccounts({
        prefix,
        startNumber: bulkStartNumber,
        endNumber: bulkEndNumber,
        passwordLength: bulkPasswordLength,
      });
      setGeneratedCredentials(credentials);
      setGeneratedCredentialsAt(new Date().toISOString());
      toast.success(`${credentials.length} team account${credentials.length === 1 ? '' : 's'} generated`);
      await loadUsers();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to generate team accounts');
    } finally {
      setBulkGenerating(false);
    }
  };

  const openEditName = (user: UserResponse) => {
    setSelectedUser(user);
    setNewUsername(user.username ?? '');
    setEditNameOpen(true);
  };

  const openEditPassword = (user: UserResponse) => {
    setSelectedUser(user);
    setNewPassword('');
    setConfirmPassword('');
    setEditPasswordOpen(true);
  };

  const openDelete = (user: UserResponse) => {
    if (isAdminUser(user)) {
      toast.error('Admin account cannot be deleted');
      return;
    }
    setSelectedUser(user);
    setDeleteOpen(true);
  };

  const handleUpdateName = async () => {
    if (!selectedUser) return;
    const trimmed = newUsername.trim();
    if (!trimmed) {
      toast.error('Username cannot be empty');
      return;
    }
    try {
      await updateUserName(selectedUser.id, trimmed);
      toast.success('Username updated');
      setEditNameOpen(false);
      await loadUsers();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to update username');
    }
  };

  const handleUpdatePassword = async () => {
    if (!selectedUser) return;
    if (!newPassword || newPassword.length < 6) {
      toast.error('Password must be at least 6 characters');
      return;
    }
    if (newPassword !== confirmPassword) {
      toast.error('Passwords do not match');
      return;
    }
    try {
      await updateUserPassword(selectedUser.id, newPassword);
      toast.success('Password updated');
      setEditPasswordOpen(false);
      await loadUsers();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to update password');
    }
  };

  const handleDelete = async () => {
    if (!selectedUser) return;
    if (isAdminUser(selectedUser)) {
      toast.error('Admin account cannot be deleted');
      setDeleteOpen(false);
      return;
    }
    try {
      await deleteUser(selectedUser.id);
      toast.success('User deleted');
      setDeleteOpen(false);
      await loadUsers();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to delete user');
    }
  };

  const openModerationAction = (row: ContestTeamModerationResponse, actionType: ModerationActionType) => {
    setPendingAction({ row, actionType });
    setModerationReason('');
  };

  const handleConfirmModeration = async () => {
    if (!pendingAction) return;
    if (REASON_REQUIRED.has(pendingAction.actionType) && !moderationReason.trim()) {
      toast.error('Reason is required for this action');
      return;
    }

    setModerationSaving(true);
    try {
      const updated = await moderateContestTeam(
          pendingAction.row.contestId,
          pendingAction.row.teamId,
          { actionType: pendingAction.actionType, reason: moderationReason.trim() || null },
      );
      setModerationRows((current) => current.map((row) => row.teamId === updated.teamId ? updated : row));
      setPendingAction(null);
      toast.success(`${ACTION_LABELS[pendingAction.actionType]} applied`);
      if (activeTab === 'logs') await loadLogs();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Moderation action failed');
    } finally {
      setModerationSaving(false);
    }
  };

  const handleExportLogs = async () => {
    try {
      const { blob, filename } = await exportModerationLogsCsv(currentLogFilters());
      downloadBlob(blob, filename ?? 'moderation-logs.csv', 'Moderation logs CSV downloaded');
    } catch {
      toast.error('Failed to export moderation logs.');
    }
  };

  return (
      <>
        <div className="space-y-5">
          <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
            <div>
              <h1 className="text-3xl font-semibold text-slate-950">Teams</h1>
              <p className="mt-1 text-sm text-slate-600">
                Manage global team accounts and contest-scoped moderation separately.
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button size="sm" variant="outline" className="gap-2 bg-white" onClick={loadUsers}>
                <RefreshCw className="h-4 w-4" />
                Refresh Accounts
              </Button>
              <Button
                  size="sm"
                  className="gap-2 bg-blue-700 hover:bg-blue-800"
                  onClick={() => setRegisterModalOpen(true)}
              >
                <UserPlus className="h-4 w-4" />
                Create Team Account
              </Button>
            </div>
          </div>

          <Tabs value={activeTab} onValueChange={(value) => setActiveTab(value as TeamsTab)} className="space-y-4">
            <TabsList className="grid w-full grid-cols-3 rounded-lg bg-slate-100 p-1 md:w-auto md:min-w-[520px]">
              <TabsTrigger value="accounts">Accounts</TabsTrigger>
              <TabsTrigger value="moderation">Contest Moderation</TabsTrigger>
              <TabsTrigger value="logs">Moderation Logs</TabsTrigger>
            </TabsList>

            <TabsContent value="accounts" className="m-0">
              <AccountsTab
                  users={users}
                  loading={loading}
                  query={query}
                  filteredUsers={filteredUsers}
                  bulkOpen={bulkOpen}
                  setBulkOpen={setBulkOpen}
                  bulkPrefix={bulkPrefix}
                  setBulkPrefix={setBulkPrefix}
                  bulkStartNumber={bulkStartNumber}
                  setBulkStartNumber={setBulkStartNumber}
                  bulkEndNumber={bulkEndNumber}
                  setBulkEndNumber={setBulkEndNumber}
                  bulkPasswordLength={bulkPasswordLength}
                  setBulkPasswordLength={setBulkPasswordLength}
                  bulkGenerating={bulkGenerating}
                  generatedCredentials={generatedCredentials}
                  setQuery={setQuery}
                  handleGenerateTeams={handleGenerateTeams}
                  credentialRows={credentialRows}
                  copyText={copyText}
                  downloadCredentialsCsv={downloadCredentialsCsv}
                  downloadCredentialsXlsx={downloadCredentialsXlsx}
                  openEditName={openEditName}
                  openEditPassword={openEditPassword}
                  openDelete={openDelete}
              />
            </TabsContent>

            <TabsContent value="moderation" className="m-0">
              <ContestModerationTab
                  contestOptions={contestOptions}
                  selectedContestId={selectedContestId}
                  setSelectedContestId={setSelectedContestId}
                  selectedContest={selectedContest}
                  rows={moderationRows}
                  loading={moderationLoading}
                  onRefresh={loadModerationRows}
                  onOpenAction={openModerationAction}
              />
            </TabsContent>

            <TabsContent value="logs" className="m-0">
              <ModerationLogsTab
                  contestOptions={contestOptions}
                  teamUsers={teamUsers}
                  adminUsers={adminUsers}
                  logs={logs}
                  loading={logsLoading}
                  logContestId={logContestId}
                  setLogContestId={setLogContestId}
                  logTeamId={logTeamId}
                  setLogTeamId={setLogTeamId}
                  logAdminId={logAdminId}
                  setLogAdminId={setLogAdminId}
                  logActionType={logActionType}
                  setLogActionType={setLogActionType}
                  logFrom={logFrom}
                  setLogFrom={setLogFrom}
                  logTo={logTo}
                  setLogTo={setLogTo}
                  onApply={loadLogs}
                  onExport={handleExportLogs}
              />
            </TabsContent>
          </Tabs>
        </div>

        <RegisterModal
            open={registerModalOpen}
            onOpenChange={(v) => {
              setRegisterModalOpen(v);
              if (!v) loadUsers();
            }}
        />

        <Dialog open={editNameOpen} onOpenChange={setEditNameOpen}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Update Username</DialogTitle>
            </DialogHeader>
            <div className="space-y-2">
              <div className="text-sm text-slate-600">User: {selectedTitle}</div>
              <Input value={newUsername} onChange={(e) => setNewUsername(e.target.value)} placeholder="New username" />
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setEditNameOpen(false)}>Cancel</Button>
              <Button className="bg-[#1E293B] hover:bg-[#334155]" onClick={handleUpdateName}>Save</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        <Dialog open={editPasswordOpen} onOpenChange={setEditPasswordOpen}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>Update Password</DialogTitle>
            </DialogHeader>
            <div className="space-y-3">
              <div className="text-sm text-slate-600">User: {selectedTitle}</div>
              <Input type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} placeholder="New password" />
              <Input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)} placeholder="Confirm new password" />
              <div className="text-xs text-slate-500">Password is sent to the server; it is not stored in the browser.</div>
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setEditPasswordOpen(false)}>Cancel</Button>
              <Button className="bg-[#1E293B] hover:bg-[#334155]" onClick={handleUpdatePassword}>Save</Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        <AlertDialog open={deleteOpen} onOpenChange={setDeleteOpen}>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Delete User?</AlertDialogTitle>
              <AlertDialogDescription>
                This will permanently delete <b>{selectedTitle}</b>. This action cannot be undone.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Cancel</AlertDialogCancel>
              <AlertDialogAction onClick={handleDelete} className="bg-red-600 hover:bg-red-700">
                Delete
              </AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>

        <Dialog open={pendingAction !== null} onOpenChange={(open) => !open && setPendingAction(null)}>
          <DialogContent>
            <DialogHeader>
              <DialogTitle>{pendingAction ? ACTION_LABELS[pendingAction.actionType] : 'Moderation Action'}</DialogTitle>
            </DialogHeader>
            {pendingAction && (
                <div className="space-y-4">
                  <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">
                    <div className="font-semibold text-slate-950">{pendingAction.row.teamUsername}</div>
                    <div>Contest: {pendingAction.row.contestTitle}</div>
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-xs font-semibold uppercase text-slate-500">
                      Reason {REASON_REQUIRED.has(pendingAction.actionType) ? '(required)' : '(optional)'}
                    </label>
                    <Textarea
                        value={moderationReason}
                        onChange={(event) => setModerationReason(event.target.value)}
                        placeholder="Add a moderation note for the audit log..."
                        className="min-h-28"
                    />
                  </div>
                </div>
            )}
            <DialogFooter>
              <Button variant="outline" onClick={() => setPendingAction(null)}>Cancel</Button>
              <Button
                  onClick={handleConfirmModeration}
                  disabled={moderationSaving || (pendingAction ? REASON_REQUIRED.has(pendingAction.actionType) && !moderationReason.trim() : false)}
                  className={pendingAction && DESTRUCTIVE_ACTIONS.has(pendingAction.actionType) ? 'bg-red-600 hover:bg-red-700' : 'bg-blue-700 hover:bg-blue-800'}
              >
                {moderationSaving ? 'Saving...' : 'Confirm'}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>
      </>
  );
}

function AccountsTab(props: {
  users: UserResponse[];
  loading: boolean;
  query: string;
  filteredUsers: UserResponse[];
  bulkOpen: boolean;
  setBulkOpen: (value: boolean) => void;
  bulkPrefix: string;
  setBulkPrefix: (value: string) => void;
  bulkStartNumber: number;
  setBulkStartNumber: (value: number) => void;
  bulkEndNumber: number;
  setBulkEndNumber: (value: number) => void;
  bulkPasswordLength: number;
  setBulkPasswordLength: (value: number) => void;
  bulkGenerating: boolean;
  generatedCredentials: GeneratedTeamCredentialResponse[];
  setQuery: (value: string) => void;
  handleGenerateTeams: (event: FormEvent) => void;
  credentialRows: () => ReturnType<typeof buildCredentialRows>;
  copyText: (value: string, successMessage: string) => Promise<void>;
  downloadCredentialsCsv: () => void;
  downloadCredentialsXlsx: () => void;
  openEditName: (user: UserResponse) => void;
  openEditPassword: (user: UserResponse) => void;
  openDelete: (user: UserResponse) => void;
}) {
  const isAdminUser = (user: UserResponse) => user.role === 'ADMIN';

  return (
      <Card className="border border-gray-200 shadow-sm">
        <CardHeader className="border-b border-slate-200 bg-slate-50">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <CardTitle className="text-xl text-slate-950">Accounts</CardTitle>
              <p className="mt-1 text-sm text-slate-600">Global login accounts for teams and administrators.</p>
            </div>
            <Button
                type="button"
                variant="outline"
                className="h-9 gap-2 bg-white"
                onClick={() => props.setBulkOpen(!props.bulkOpen)}
            >
              <ClipboardList className="h-4 w-4" />
              Bulk Team Generation
              <ChevronDown className={`h-4 w-4 transition ${props.bulkOpen ? 'rotate-180' : ''}`} />
            </Button>
          </div>
        </CardHeader>
        <CardContent className="p-5">
          {props.bulkOpen && (
              <form onSubmit={props.handleGenerateTeams} className="mb-5 rounded-lg border border-slate-200 bg-slate-50 p-4">
                <div className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between">
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <ClipboardList className="h-5 w-5 text-blue-700" />
                      <h3 className="font-semibold text-slate-950">Bulk Team Generation</h3>
                      <AdminHelpTooltip
                          label="Bulk team generation help"
                          content="Generated passwords are returned once. Copy or download the CSV before leaving this result."
                      />
                    </div>
                    <p className="mt-1 text-sm text-slate-600">Generated passwords are shown only once after creation.</p>
                  </div>

                  <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-[9rem_8rem_8rem_9rem_auto]">
                    <Field label="Prefix">
                      <Input value={props.bulkPrefix} onChange={(event) => props.setBulkPrefix(event.target.value)} className="h-10 bg-white" disabled={props.bulkGenerating} />
                    </Field>
                    <Field label="Start">
                      <Input type="number" min={1} value={props.bulkStartNumber} onChange={(event) => props.setBulkStartNumber(Number(event.target.value))} className="h-10 bg-white" disabled={props.bulkGenerating} />
                    </Field>
                    <Field label="End">
                      <Input type="number" min={1} value={props.bulkEndNumber} onChange={(event) => props.setBulkEndNumber(Number(event.target.value))} className="h-10 bg-white" disabled={props.bulkGenerating} />
                    </Field>
                    <Field label="Password length">
                      <Input type="number" min={8} value={props.bulkPasswordLength} onChange={(event) => props.setBulkPasswordLength(Number(event.target.value))} className="h-10 bg-white" disabled={props.bulkGenerating} />
                    </Field>
                    <Button type="submit" className="h-10 gap-2 bg-blue-700 hover:bg-blue-800 sm:col-span-2 xl:col-span-1" disabled={props.bulkGenerating}>
                      <UserPlus className="h-4 w-4" />
                      {props.bulkGenerating ? 'Generating...' : 'Generate'}
                    </Button>
                  </div>
                </div>
              </form>
          )}

          {props.generatedCredentials.length > 0 && (
              <div className="mb-5 overflow-hidden rounded-lg border border-slate-200 bg-white">
                <div className="flex flex-col gap-3 border-b border-slate-200 bg-slate-50 px-4 py-3 lg:flex-row lg:items-center lg:justify-between">
                  <div>
                    <div className="flex items-center gap-2">
                      <h3 className="font-semibold text-slate-950">Generated Credentials</h3>
                      <AdminHelpTooltip
                          label="Generated credentials help"
                          content="Plaintext passwords are visible only in this result. Store the CSV securely and restrict who can access it."
                      />
                    </div>
                    <p className="mt-1 text-sm text-slate-600">Store these securely now. Plaintext passwords will not be available later.</p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Button type="button" variant="outline" className="gap-2 bg-white" onClick={() => props.copyText(credentialsPlainText(props.credentialRows()), 'All generated credentials copied')}>
                      <Copy className="h-4 w-4" />
                      Copy All
                    </Button>
                    <Button type="button" className="gap-2 bg-blue-700 hover:bg-blue-800" onClick={props.downloadCredentialsXlsx}>
                      <Download className="h-4 w-4" />
                      Download XLSX
                    </Button>
                    <Button type="button" variant="outline" className="gap-2 bg-white" onClick={props.downloadCredentialsCsv}>
                      <Download className="h-4 w-4" />
                      Download CSV
                    </Button>
                  </div>
                </div>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Username</TableHead>
                      <TableHead>Password</TableHead>
                      <TableHead className="w-[120px]">Role</TableHead>
                      <TableHead className="w-[110px] text-right">Copy</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {props.generatedCredentials.map((credential) => (
                        <TableRow key={credential.username}>
                          <TableCell className="font-medium text-slate-900">{credential.username}</TableCell>
                          <TableCell className="font-mono text-sm text-slate-900">{credential.password}</TableCell>
                          <TableCell><StatusBadge kind="neutral" value={credential.role} /></TableCell>
                          <TableCell className="text-right">
                            <Button type="button" size="sm" variant="outline" className="gap-2 bg-white" onClick={() => props.copyText(`${credential.username},${credential.password}`, `${credential.username} copied`)}>
                              <Copy className="h-3.5 w-3.5" />
                              Copy
                            </Button>
                          </TableCell>
                        </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
          )}

          <div className="mb-5 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div className="relative w-full md:max-w-md">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              <Input
                  value={props.query}
                  onChange={(e) => props.setQuery(e.target.value)}
                  placeholder="Search by ID, username, or role..."
                  className="h-10 pl-9"
              />
            </div>
            <p className="text-sm text-slate-500">
              Showing <span className="font-semibold text-slate-800">{props.filteredUsers.length}</span> of{' '}
              <span className="font-semibold text-slate-800">{props.users.length}</span>
            </p>
          </div>

          {props.loading ? (
              <EmptyState>Loading team and user accounts...</EmptyState>
          ) : props.users.length === 0 ? (
              <EmptyState>No user accounts found.</EmptyState>
          ) : props.filteredUsers.length === 0 ? (
              <EmptyState>No accounts match your search.</EmptyState>
          ) : (
              <div className="overflow-hidden rounded-lg border border-gray-200">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-[80px]">ID</TableHead>
                      <TableHead>Username</TableHead>
                      <TableHead className="w-[140px]">Role</TableHead>
                      <TableHead className="w-[220px] text-right">Actions</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {props.filteredUsers.map((user) => (
                        <TableRow key={user.id}>
                          <TableCell className="font-mono text-sm">{user.id}</TableCell>
                          <TableCell>
                            <div className="font-medium text-slate-900">{user.username}</div>
                            {isAdminUser(user) && (
                                <div className="mt-1 inline-flex items-center gap-1 text-xs text-slate-500">
                                  <ShieldCheck className="h-3 w-3" />
                                  Protected account
                                </div>
                            )}
                          </TableCell>
                          <TableCell><StatusBadge kind="neutral" value={user.role} /></TableCell>
                          <TableCell className="text-right">
                            <div className="flex items-center justify-end gap-2">
                              <Button size="sm" variant="outline" className="gap-2" onClick={() => props.openEditName(user)}>
                                <Pencil className="h-4 w-4" />
                                Name
                              </Button>
                              <Button size="sm" variant="outline" className="gap-2" onClick={() => props.openEditPassword(user)}>
                                <KeyRound className="h-4 w-4" />
                                Password
                              </Button>
                              <Button size="sm" variant="destructive" className="gap-2" onClick={() => props.openDelete(user)} disabled={isAdminUser(user)} title={isAdminUser(user) ? 'Admin account cannot be deleted' : undefined}>
                                <Trash2 className="h-4 w-4" />
                                {isAdminUser(user) ? 'Protected' : 'Delete'}
                              </Button>
                            </div>
                          </TableCell>
                        </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
          )}
        </CardContent>
      </Card>
  );
}

function ContestModerationTab({
                                contestOptions,
                                selectedContestId,
                                setSelectedContestId,
                                selectedContest,
                                rows,
                                loading,
                                onRefresh,
                                onOpenAction,
                              }: {
  contestOptions: ContestOption[];
  selectedContestId: string;
  setSelectedContestId: (value: string) => void;
  selectedContest: ContestOption | null;
  rows: ContestTeamModerationResponse[];
  loading: boolean;
  onRefresh: () => void;
  onOpenAction: (row: ContestTeamModerationResponse, actionType: ModerationActionType) => void;
}) {
  return (
      <Card className="border border-gray-200 shadow-sm">
        <CardHeader className="border-b border-slate-200 bg-slate-50">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <CardTitle className="text-xl text-slate-950">Contest Moderation</CardTitle>
              <p className="mt-1 text-sm text-slate-600">Restrictions here apply only to the selected contest.</p>
            </div>
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
              <Select value={selectedContestId} onValueChange={setSelectedContestId}>
                <SelectTrigger className="h-10 min-w-[260px] bg-white">
                  <SelectValue placeholder="Select contest" />
                </SelectTrigger>
                <SelectContent>
                  {contestOptions.map((contest) => (
                      <SelectItem key={contest.id} value={String(contest.id)}>
                        {contestLabel(contest)} - {contest.bucket}
                      </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <Button type="button" variant="outline" className="h-10 gap-2 bg-white" onClick={onRefresh} disabled={!selectedContestId || loading}>
                <RefreshCw className="h-4 w-4" />
                Refresh
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent className="p-5">
          {!selectedContest ? (
              <EmptyState>No contest selected.</EmptyState>
          ) : loading ? (
              <EmptyState>Loading moderation state...</EmptyState>
          ) : rows.length === 0 ? (
              <EmptyState>No team accounts are available for this contest.</EmptyState>
          ) : (
              <div className="overflow-visible rounded-lg border border-slate-200">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Team</TableHead>
                      <TableHead>Status</TableHead>
                      <TableHead>Scoreboard</TableHead>
                      <TableHead>Submit</TableHead>
                      <TableHead>Run</TableHead>
                      <TableHead>Last Updated</TableHead>
                      <TableHead className="w-[110px] text-right">Moderation</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {rows.map((row) => (
                        <TableRow key={row.teamId}>
                          <TableCell>
                            <div className="font-medium text-slate-900">{row.teamUsername}</div>
                            <div className="text-xs text-slate-500">#{row.teamId}</div>
                          </TableCell>
                          <TableCell><ModerationBadge type={row.status === 'DISQUALIFIED' ? 'danger' : 'success'} label={row.status} /></TableCell>
                          <TableCell>
                            {row.hiddenFromScoreboard || row.status === 'DISQUALIFIED'
                                ? <ModerationBadge type="warning" label="Hidden" />
                                : <ModerationBadge type="neutral" label="Visible" />}
                          </TableCell>
                          <TableCell>
                            {row.submitEnabled && row.status !== 'DISQUALIFIED'
                                ? <ModerationBadge type="success" label="Enabled" />
                                : <ModerationBadge type="danger" label="Disabled" />}
                          </TableCell>
                          <TableCell>
                            {row.runEnabled && row.status !== 'DISQUALIFIED'
                                ? <ModerationBadge type="success" label="Enabled" />
                                : <ModerationBadge type="danger" label="Disabled" />}
                          </TableCell>
                          <TableCell>
                            <div className="text-sm text-slate-700">{formatDateTime(row.updatedAt)}</div>
                            <div className="text-xs text-slate-500">{row.updatedByAdminUsername ?? 'Default state'}</div>
                          </TableCell>
                          <TableCell className="text-right">
                            <ModerationMenu row={row} onOpenAction={onOpenAction} />
                          </TableCell>
                        </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
          )}
        </CardContent>
      </Card>
  );
}

function ModerationMenu({
                          row,
                          onOpenAction,
                        }: {
  row: ContestTeamModerationResponse;
  onOpenAction: (row: ContestTeamModerationResponse, actionType: ModerationActionType) => void;
}) {
  const [open, setOpen] = useState(false);
  const actions: ModerationActionType[] = [
    row.hiddenFromScoreboard ? 'SHOW_ON_SCOREBOARD' : 'HIDE_FROM_SCOREBOARD',
    row.status === 'DISQUALIFIED' ? 'RESTORE_TEAM' : 'DISQUALIFY_TEAM',
    row.submitEnabled ? 'DISABLE_SUBMIT' : 'ENABLE_SUBMIT',
    row.runEnabled ? 'DISABLE_RUN' : 'ENABLE_RUN',
  ];

  return (
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogTrigger asChild>
          <Button
              size="sm"
              variant="outline"
              className="relative gap-2 bg-white dark:border-[#384352] dark:bg-[#111827] dark:text-slate-100"
          >
            <MoreHorizontal className="h-4 w-4" />
            Actions
          </Button>
        </DialogTrigger>
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>Moderation Actions</DialogTitle>
            <DialogDescription>
              Select an action for team <strong>{row.teamUsername}</strong>
            </DialogDescription>
          </DialogHeader>
          <div className="flex flex-col gap-2 pt-2 pb-4">
            {actions.map((action) => (
                <Button
                    key={action}
                    variant={DESTRUCTIVE_ACTIONS.has(action) ? 'destructive' : 'outline'}
                    className="justify-start w-full gap-2"
                    onClick={() => {
                      setOpen(false);
                      onOpenAction(row, action);
                    }}
                >
                  {action === 'HIDE_FROM_SCOREBOARD' ? <EyeOff className="h-4 w-4" /> : null}
                  {action === 'SHOW_ON_SCOREBOARD' ? <Eye className="h-4 w-4" /> : null}
                  {action === 'DISQUALIFY_TEAM' ? <ShieldAlert className="h-4 w-4" /> : null}
                  {action === 'RESTORE_TEAM' ? <ShieldCheck className="h-4 w-4" /> : null}
                  {ACTION_LABELS[action]}
                </Button>
            ))}
          </div>
        </DialogContent>
      </Dialog>
  );
}

function ModerationLogsTab({
                             contestOptions,
                             teamUsers,
                             adminUsers,
                             logs,
                             loading,
                             logContestId,
                             setLogContestId,
                             logTeamId,
                             setLogTeamId,
                             logAdminId,
                             setLogAdminId,
                             logActionType,
                             setLogActionType,
                             logFrom,
                             setLogFrom,
                             logTo,
                             setLogTo,
                             onApply,
                             onExport,
                           }: {
  contestOptions: ContestOption[];
  teamUsers: UserResponse[];
  adminUsers: UserResponse[];
  logs: ModerationAuditLogResponse[];
  loading: boolean;
  logContestId: string;
  setLogContestId: (value: string) => void;
  logTeamId: string;
  setLogTeamId: (value: string) => void;
  logAdminId: string;
  setLogAdminId: (value: string) => void;
  logActionType: 'all' | ModerationActionType;
  setLogActionType: (value: 'all' | ModerationActionType) => void;
  logFrom: string;
  setLogFrom: (value: string) => void;
  logTo: string;
  setLogTo: (value: string) => void;
  onApply: () => void;
  onExport: () => void;
}) {
  return (
      <Card className="border border-gray-200 shadow-sm">
        <CardHeader className="border-b border-slate-200 bg-slate-50">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <CardTitle className="text-xl text-slate-950">Moderation Logs</CardTitle>
              <p className="mt-1 text-sm text-slate-600">Audit records for contest team moderation actions.</p>
            </div>
            <Button type="button" className="h-10 gap-2 bg-blue-700 hover:bg-blue-800" onClick={onExport}>
              <Download className="h-4 w-4" />
              Export CSV
            </Button>
          </div>
        </CardHeader>
        <CardContent className="p-5">
          <div className="mb-5 grid gap-3 md:grid-cols-2 xl:grid-cols-6">
            <Select value={logContestId} onValueChange={setLogContestId}>
              <SelectTrigger className="h-10 bg-white"><SelectValue placeholder="Contest" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All contests</SelectItem>
                {contestOptions.map((contest) => (
                    <SelectItem key={contest.id} value={String(contest.id)}>{contestLabel(contest)}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={logTeamId} onValueChange={setLogTeamId}>
              <SelectTrigger className="h-10 bg-white"><SelectValue placeholder="Team" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All teams</SelectItem>
                {teamUsers.map((team) => (
                    <SelectItem key={team.id} value={String(team.id)}>{team.username}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={logAdminId} onValueChange={setLogAdminId}>
              <SelectTrigger className="h-10 bg-white"><SelectValue placeholder="Admin" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All admins</SelectItem>
                {adminUsers.map((admin) => (
                    <SelectItem key={admin.id} value={String(admin.id)}>{admin.username}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select value={logActionType} onValueChange={(value) => setLogActionType(value as 'all' | ModerationActionType)}>
              <SelectTrigger className="h-10 bg-white"><SelectValue placeholder="Action" /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All actions</SelectItem>
                {ACTION_OPTIONS.map((action) => (
                    <SelectItem key={action} value={action}>{ACTION_LABELS[action]}</SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Input type="datetime-local" value={logFrom} onChange={(event) => setLogFrom(event.target.value)} className="h-10 bg-white" />
            <div className="flex gap-2">
              <Input type="datetime-local" value={logTo} onChange={(event) => setLogTo(event.target.value)} className="h-10 bg-white" />
              <Button type="button" variant="outline" className="h-10 bg-white" onClick={onApply} disabled={loading}>
                Apply
              </Button>
            </div>
          </div>

          {loading ? (
              <EmptyState>Loading moderation logs...</EmptyState>
          ) : logs.length === 0 ? (
              <EmptyState>No moderation logs match the current filters.</EmptyState>
          ) : (
              <div className="overflow-x-auto rounded-lg border border-slate-200">
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Timestamp</TableHead>
                      <TableHead>Contest</TableHead>
                      <TableHead>Team</TableHead>
                      <TableHead>Admin</TableHead>
                      <TableHead>Problem / Context</TableHead>
                      <TableHead>Action</TableHead>
                      <TableHead>Reason</TableHead>
                      <TableHead>Old Value</TableHead>
                      <TableHead>New Value</TableHead>
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {logs.map((log) => (
                        <TableRow key={log.id}>
                          <TableCell className="whitespace-nowrap text-sm text-slate-700">{formatDateTime(log.createdAt)}</TableCell>
                          <TableCell>
                            <div className="font-medium text-slate-900">{log.contestTitle}</div>
                            <div className="text-xs text-slate-500">#{log.contestId}</div>
                          </TableCell>
                          <TableCell>{log.teamUsername ?? '-'}</TableCell>
                          <TableCell>{log.adminUsername}</TableCell>
                          <TableCell>
                            <div className="text-sm text-slate-800">{log.problemTitle ?? '-'}</div>
                            <div className="text-xs text-slate-500">
                              {log.executionMode ? `${log.executionMode.replaceAll('_', ' ')}${log.languageId ? ` · Judge0 #${log.languageId}` : ''}` : ''}
                            </div>
                          </TableCell>
                          <TableCell><StatusBadge kind="neutral" value={ACTION_LABELS[log.actionType]} /></TableCell>
                          <TableCell className="max-w-[220px] text-sm text-slate-700">{log.reason ?? '-'}</TableCell>
                          <TableCell className="max-w-[260px] font-mono text-xs text-slate-600">{shortJson(log.oldValueJson)}</TableCell>
                          <TableCell className="max-w-[260px] font-mono text-xs text-slate-600">{shortJson(log.newValueJson)}</TableCell>
                        </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </div>
          )}
        </CardContent>
      </Card>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
      <div className="space-y-1.5">
        <label className="text-xs font-semibold uppercase text-slate-500">{label}</label>
        {children}
      </div>
  );
}

function EmptyState({ children }: { children: ReactNode }) {
  return (
      <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 px-4 py-5 text-center text-sm text-slate-500">
        {children}
      </div>
  );
}

function ModerationBadge({ type, label }: { type: 'success' | 'danger' | 'warning' | 'neutral'; label: string }) {
  const classes = {
    success: 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-[#315040] dark:bg-[#172820] dark:text-[#a9c7b8]',
    danger: 'border-rose-200 bg-rose-50 text-rose-700 dark:border-[#57363b] dark:bg-[#2b1d20] dark:text-[#d4b0b5]',
    warning: 'border-amber-200 bg-amber-50 text-amber-800 dark:border-[#5a4b2d] dark:bg-[#2a2418] dark:text-[#d1c09a]',
    neutral: 'border-slate-200 bg-slate-50 text-slate-700 dark:border-[#384352] dark:bg-[#1b2431] dark:text-[#c4ccd8]',
  }[type];

  return (
      <span className={`inline-flex rounded-full border px-2.5 py-1 text-xs font-semibold leading-none ${classes}`}>
      {label.replaceAll('_', ' ')}
    </span>
  );
}
