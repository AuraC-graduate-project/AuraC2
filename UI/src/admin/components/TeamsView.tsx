import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Button } from './ui/button';
import { ClipboardList, Copy, Download, UserPlus, Pencil, KeyRound, Trash2, RefreshCw, Search, ShieldCheck } from 'lucide-react';
import { RegisterModal } from './RegisterModal';
import { Input } from './ui/input';
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
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
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from './ui/table';
import { toast } from 'sonner';
import { deleteUser, generateTeamAccounts, getAllUsers, updateUserName, updateUserPassword } from '../services/api';
import { GeneratedTeamCredentialResponse, UserResponse } from '../types/api';
import { StatusBadge } from '../../components/StatusBadge';
import { AdminHelpTooltip } from './AdminHelpTooltip';
import {
  buildCredentialRows,
  credentialsCsv,
  credentialsPlainText,
  credentialsXlsx,
} from '../utils/teamCredentialExport';

export function TeamsView() {
  const [registerModalOpen, setRegisterModalOpen] = useState(false);

  const [users, setUsers] = useState<UserResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [query, setQuery] = useState('');
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

  const selectedTitle = useMemo(() => {
    if (!selectedUser) return '';
    return `${selectedUser.username} (#${selectedUser.id})`;
  }, [selectedUser]);

  const isAdminUser = (user: UserResponse) => user.role === 'ADMIN';

  const filteredUsers = useMemo(() => {
    const q = query.trim().toLowerCase();
    if (!q) return users;
    return users.filter((user) => (
      String(user.id).includes(q) ||
      user.username.toLowerCase().includes(q) ||
      user.role.toLowerCase().includes(q)
    ));
  }, [users, query]);

  const loadUsers = async () => {
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
  };

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

  useEffect(() => {
    loadUsers();
  }, []);

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

  return (
    <>
      <Card className="border border-gray-200 shadow-sm">
        <CardHeader className="border-b border-slate-200 bg-slate-50">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <CardTitle className="text-2xl text-slate-950">Teams & Accounts</CardTitle>
              <p className="mt-1 text-sm text-slate-600">Create team logins and maintain contest user access from one place.</p>
            </div>
            <div className="flex items-center gap-2">
              <Button
                size="sm"
                variant="outline"
                className="gap-2 bg-white"
                onClick={loadUsers}
              >
                <RefreshCw className="w-4 h-4" />
                Refresh
              </Button>
              <Button
                size="sm"
                className="gap-2 bg-blue-700 hover:bg-blue-800"
                onClick={() => setRegisterModalOpen(true)}
              >
                <UserPlus className="w-4 h-4" />
                Create Team Account
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent className="p-6">
          <form
            onSubmit={handleGenerateTeams}
            className="mb-6 rounded-lg border border-slate-200 bg-slate-50 p-4"
          >
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
                <p className="mt-1 text-sm text-slate-600">
                  Generated passwords are shown only once after creation. They are hashed on the server and cannot be recovered later.
                </p>
              </div>

              <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-[9rem_8rem_8rem_9rem_auto]">
                <div className="space-y-1.5">
                  <label htmlFor="bulk-prefix" className="text-xs font-semibold uppercase text-slate-500">
                    Prefix
                  </label>
                  <Input
                    id="bulk-prefix"
                    value={bulkPrefix}
                    onChange={(event) => setBulkPrefix(event.target.value)}
                    className="h-10 bg-white"
                    disabled={bulkGenerating}
                  />
                </div>
                <div className="space-y-1.5">
                  <label htmlFor="bulk-start" className="text-xs font-semibold uppercase text-slate-500">
                    Start
                  </label>
                  <Input
                    id="bulk-start"
                    type="number"
                    min={1}
                    value={bulkStartNumber}
                    onChange={(event) => setBulkStartNumber(Number(event.target.value))}
                    className="h-10 bg-white"
                    disabled={bulkGenerating}
                  />
                </div>
                <div className="space-y-1.5">
                  <label htmlFor="bulk-end" className="text-xs font-semibold uppercase text-slate-500">
                    End
                  </label>
                  <Input
                    id="bulk-end"
                    type="number"
                    min={1}
                    value={bulkEndNumber}
                    onChange={(event) => setBulkEndNumber(Number(event.target.value))}
                    className="h-10 bg-white"
                    disabled={bulkGenerating}
                  />
                </div>
                <div className="space-y-1.5">
                  <label htmlFor="bulk-password-length" className="text-xs font-semibold uppercase text-slate-500">
                    Password length
                  </label>
                  <Input
                    id="bulk-password-length"
                    type="number"
                    min={8}
                    value={bulkPasswordLength}
                    onChange={(event) => setBulkPasswordLength(Number(event.target.value))}
                    className="h-10 bg-white"
                    disabled={bulkGenerating}
                  />
                </div>
                <Button
                  type="submit"
                  className="h-10 gap-2 bg-blue-700 hover:bg-blue-800 sm:col-span-2 xl:col-span-1"
                  disabled={bulkGenerating}
                >
                  <UserPlus className="h-4 w-4" />
                  {bulkGenerating ? 'Generating...' : 'Generate'}
                </Button>
              </div>
            </div>
          </form>

          {generatedCredentials.length > 0 && (
            <div className="mb-6 overflow-hidden rounded-lg border border-slate-200 bg-white">
              <div className="flex flex-col gap-3 border-b border-slate-200 bg-slate-50 px-4 py-3 lg:flex-row lg:items-center lg:justify-between">
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="font-semibold text-slate-950">Generated Credentials</h3>
                    <AdminHelpTooltip
                      label="Generated credentials help"
                      content="Plaintext passwords are visible only in this result. Store the CSV securely and restrict who can access it."
                    />
                  </div>
                  <p className="mt-1 text-sm text-slate-600">
                    Store these securely now. Plaintext passwords will not be available after you leave this result.
                    <span className="mt-1 block text-xs font-medium text-slate-500">
                      XLSX is recommended for Excel because it preserves passwords that begin with symbols such as +, -, =, or @.
                    </span>
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    className="gap-2 bg-white"
                    onClick={() => copyText(credentialsPlainText(credentialRows()), 'All generated credentials copied')}
                  >
                    <Copy className="h-4 w-4" />
                    Copy All
                  </Button>
                  <Button
                    type="button"
                    className="gap-2 bg-blue-700 hover:bg-blue-800"
                    onClick={downloadCredentialsXlsx}
                  >
                    <Download className="h-4 w-4" />
                    Download XLSX
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    className="gap-2 bg-white"
                    onClick={downloadCredentialsCsv}
                  >
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
                  {generatedCredentials.map((credential) => (
                    <TableRow key={credential.username}>
                      <TableCell className="font-medium text-slate-900">{credential.username}</TableCell>
                      <TableCell className="font-mono text-sm text-slate-900">{credential.password}</TableCell>
                      <TableCell>
                        <StatusBadge kind="neutral" value={credential.role} />
                      </TableCell>
                      <TableCell className="text-right">
                        <Button
                          type="button"
                          size="sm"
                          variant="outline"
                          className="gap-2 bg-white"
                          onClick={() => copyText(`${credential.username},${credential.password}`, `${credential.username} copied`)}
                        >
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
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search by ID, username, or role..."
                className="h-10 pl-9"
              />
            </div>
            <p className="text-sm text-slate-500">
              Showing <span className="font-semibold text-slate-800">{filteredUsers.length}</span> of{" "}
              <span className="font-semibold text-slate-800">{users.length}</span>
            </p>
          </div>

          {loading ? (
            <div className="rounded-lg border border-slate-200 bg-slate-50 py-10 text-center text-sm text-slate-600">
              Loading team and user accounts...
            </div>
          ) : users.length === 0 ? (
            <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-10 text-center">
              <p className="font-medium text-slate-800">No user accounts found.</p>
              <p className="mt-1 text-sm text-slate-500">Create the first team account to start contest access setup.</p>
            </div>
          ) : filteredUsers.length === 0 ? (
            <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-10 text-center text-sm text-slate-500">
              No accounts match your search.
            </div>
          ) : (
            <div className="border border-gray-200 rounded-lg overflow-hidden">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-[80px]">ID</TableHead>
                    <TableHead>Username</TableHead>
                    <TableHead className="w-[140px]">Role</TableHead>
                    <TableHead className="text-right w-[220px]">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filteredUsers.map((u) => (
                    <TableRow key={u.id}>
                      <TableCell className="font-mono text-sm">{u.id}</TableCell>
                      <TableCell>
                        <div className="font-medium text-slate-900">{u.username}</div>
                        {isAdminUser(u) && (
                          <div className="mt-1 inline-flex items-center gap-1 text-xs text-slate-500">
                            <ShieldCheck className="h-3 w-3" />
                            Protected account
                          </div>
                        )}
                      </TableCell>
                      <TableCell>
                        <StatusBadge kind="neutral" value={u.role} />
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex items-center justify-end gap-2">
                          <Button
                            size="sm"
                            variant="outline"
                            className="gap-2"
                            onClick={() => openEditName(u)}
                          >
                            <Pencil className="w-4 h-4" />
                            Name
                          </Button>
                          <Button
                            size="sm"
                            variant="outline"
                            className="gap-2"
                            onClick={() => openEditPassword(u)}
                          >
                            <KeyRound className="w-4 h-4" />
                            Password
                          </Button>
                          <Button
                            size="sm"
                            variant="destructive"
                            className="gap-2"
                            onClick={() => openDelete(u)}
                            disabled={isAdminUser(u)}
                            title={isAdminUser(u) ? 'Admin account cannot be deleted' : undefined}
                          >
                            <Trash2 className="w-4 h-4" />
                            {isAdminUser(u) ? 'Protected' : 'Delete'}
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

      <RegisterModal 
        open={registerModalOpen}
        onOpenChange={(v) => {
          setRegisterModalOpen(v);
          // If user closed modal, refresh list (in case they registered successfully)
          if (!v) loadUsers();
        }}
      />

      {/* Edit Username */}
      <Dialog open={editNameOpen} onOpenChange={setEditNameOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Update Username</DialogTitle>
          </DialogHeader>
          <div className="space-y-2">
            <div className="text-sm text-slate-600">User: {selectedTitle}</div>
            <Input
              value={newUsername}
              onChange={(e) => setNewUsername(e.target.value)}
              placeholder="New username"
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditNameOpen(false)}>
              Cancel
            </Button>
            <Button className="bg-[#1E293B] hover:bg-[#334155]" onClick={handleUpdateName}>
              Save
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Edit Password */}
      <Dialog open={editPasswordOpen} onOpenChange={setEditPasswordOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Update Password</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <div className="text-sm text-slate-600">User: {selectedTitle}</div>
            <Input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              placeholder="New password"
            />
            <Input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder="Confirm new password"
            />
            <div className="text-xs text-slate-500">
              Password is sent to the server; it is not stored in the browser.
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditPasswordOpen(false)}>
              Cancel
            </Button>
            <Button className="bg-[#1E293B] hover:bg-[#334155]" onClick={handleUpdatePassword}>
              Save
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation */}
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
    </>
  );
}
