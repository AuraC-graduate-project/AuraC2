import { useEffect, useMemo, useState, type FormEvent } from "react";
import { CheckCircle2, Eye, EyeOff, KeyRound, RefreshCw, ShieldCheck, UserPlus, Users } from "lucide-react";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "./ui/table";
import { registerUser, getAllUsers } from "../services/api";
import { RegisterRequest, UserResponse } from "../types/api";
import { StatusBadge } from "../../components/StatusBadge";
import { toast } from "sonner";

function isTeam(user: UserResponse) {
  return user.role === "TEAM";
}

export function TeamAccountsPage() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [loading, setLoading] = useState(true);
  const [users, setUsers] = useState<UserResponse[]>([]);
  const [createdTeam, setCreatedTeam] = useState<string | null>(null);

  const teams = useMemo(() => users.filter(isTeam), [users]);

  const loadUsers = async () => {
    setLoading(true);
    try {
      setUsers(await getAllUsers());
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to load users");
      setUsers([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadUsers();
  }, []);

  const resetForm = () => {
    setUsername("");
    setPassword("");
    setConfirmPassword("");
  };

  const handleCreateTeam = async (event: FormEvent) => {
    event.preventDefault();
    const normalizedUsername = username.trim();

    if (!normalizedUsername) {
      toast.error("Team username is required");
      return;
    }

    if (normalizedUsername.length < 3) {
      toast.error("Team username must be at least 3 characters");
      return;
    }

    if (password.length < 6) {
      toast.error("Password must be at least 6 characters");
      return;
    }

    if (password !== confirmPassword) {
      toast.error("Passwords do not match");
      return;
    }

    setSubmitting(true);
    setCreatedTeam(null);
    try {
      const payload: RegisterRequest = {
        username: normalizedUsername,
        password,
        role: "TEAM",
      };
      await registerUser(payload);
      setCreatedTeam(normalizedUsername);
      toast.success(`Team account "${normalizedUsername}" created`);
      resetForm();
      await loadUsers();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to create team account");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="space-y-6">
      <section className="rounded-lg border border-slate-200 bg-white shadow-sm">
        <div className="border-b border-slate-200 bg-slate-50 px-6 py-5">
          <p className="text-sm font-semibold uppercase tracking-wide text-blue-700">Admin only</p>
          <h1 className="mt-1 text-2xl font-semibold text-slate-950">Create Team Accounts</h1>
          <p className="mt-1 text-sm text-slate-600">
            Create student contest accounts. The backend endpoint always creates TEAM users and now requires an ADMIN token.
          </p>
        </div>

        <div className="grid gap-6 p-6 xl:grid-cols-[0.85fr_1.15fr]">
          <form onSubmit={handleCreateTeam} className="rounded-lg border border-slate-200 bg-white p-5">
            <div className="mb-5 flex items-start gap-3">
              <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-blue-50 text-blue-700">
                <UserPlus className="h-5 w-5" />
              </div>
              <div>
                <h2 className="font-semibold text-slate-950">New team login</h2>
                <p className="mt-1 text-sm text-slate-500">Share these credentials with one team only.</p>
              </div>
            </div>

            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="team-username">Team Username</Label>
                <Input
                  id="team-username"
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                  placeholder="team-alpha"
                  className="h-10"
                  disabled={submitting}
                  autoComplete="off"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="team-password">Password</Label>
                <div className="relative">
                  <KeyRound className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                  <Input
                    id="team-password"
                    type={showPassword ? "text" : "password"}
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    placeholder="Minimum 6 characters"
                    className="h-10 pl-9 pr-10"
                    disabled={submitting}
                    autoComplete="new-password"
                  />
                  <button
                    type="button"
                    onClick={() => setShowPassword((value) => !value)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-slate-400 hover:text-slate-700"
                    aria-label={showPassword ? "Hide password" : "Show password"}
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="team-confirm-password">Confirm Password</Label>
                <Input
                  id="team-confirm-password"
                  type={showPassword ? "text" : "password"}
                  value={confirmPassword}
                  onChange={(event) => setConfirmPassword(event.target.value)}
                  placeholder="Repeat password"
                  className="h-10"
                  disabled={submitting}
                  autoComplete="new-password"
                />
              </div>

              <div className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-600">
                Role is fixed as <span className="font-semibold text-slate-900">TEAM</span>. Admin accounts are not created from this page.
              </div>

              {createdTeam && (
                <div className="flex gap-2 rounded-lg border border-emerald-200 bg-emerald-50 p-3 text-sm text-emerald-700">
                  <CheckCircle2 className="mt-0.5 h-4 w-4 shrink-0" />
                  Team account "{createdTeam}" was created successfully.
                </div>
              )}

              <Button type="submit" className="w-full gap-2 bg-blue-700 hover:bg-blue-800" disabled={submitting}>
                <UserPlus className="h-4 w-4" />
                {submitting ? "Creating..." : "Create Team Account"}
              </Button>
            </div>
          </form>

          <aside className="rounded-lg border border-slate-200 bg-white">
            <div className="flex flex-col gap-4 border-b border-slate-200 bg-slate-50 p-5 md:flex-row md:items-center md:justify-between">
              <div className="flex items-start gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-blue-700">
                  <Users className="h-5 w-5" />
                </div>
                <div>
                  <h2 className="font-semibold text-slate-950">Existing Teams</h2>
                  <p className="mt-1 text-sm text-slate-500">{teams.length} team account(s)</p>
                </div>
              </div>
              <Button variant="outline" className="gap-2 bg-white" onClick={loadUsers} disabled={loading}>
                <RefreshCw className="h-4 w-4" />
                Refresh
              </Button>
            </div>

            <div className="overflow-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-[90px]">ID</TableHead>
                    <TableHead>Username</TableHead>
                    <TableHead className="w-[140px]">Role</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {loading ? (
                    <TableRow>
                      <TableCell colSpan={3} className="py-8 text-center text-slate-500">
                        Loading team accounts...
                      </TableCell>
                    </TableRow>
                  ) : teams.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={3} className="py-8 text-center text-slate-500">
                        No team accounts yet.
                      </TableCell>
                    </TableRow>
                  ) : (
                    teams.map((team) => (
                      <TableRow key={team.id}>
                        <TableCell className="font-mono text-sm">{team.id}</TableCell>
                        <TableCell>
                          <div className="flex items-center gap-2 font-medium text-slate-900">
                            <ShieldCheck className="h-4 w-4 text-blue-700" />
                            {team.username}
                          </div>
                        </TableCell>
                        <TableCell>
                          <StatusBadge kind="neutral" value={team.role} />
                        </TableCell>
                      </TableRow>
                    ))
                  )}
                </TableBody>
              </Table>
            </div>
          </aside>
        </div>
      </section>
    </div>
  );
}
