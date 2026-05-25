import { useCallback, useEffect, useMemo, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Checkbox } from './ui/checkbox';
import {
  Dialog,
  DialogContent,
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
import {
  getAllSubmissions,
  getAllUsers,
  getProblemsByContest,
  rejudgeSubmissions,
  forceRejudgeSubmissions,
} from '../services/api';
import { ProblemResponse, RejudgeResponse, SubmissionResponse, UserResponse } from '../types/api';
import {
  contestLabel,
  ContestOption,
  loadContestOptions,
} from '../utils/contestOptions';
import { RefreshCw, Eye, Search, RotateCcw } from 'lucide-react';
import { toast } from 'sonner';
import { StatusBadge, formatStatusText, normalizeVerdict } from '../../components/StatusBadge';
import { useSubmissionStream } from '../../hooks/useSubmissionStream';
import { AdminHelpTooltip } from './AdminHelpTooltip';

function formatDateTime(iso: string): string {
  if (!iso) return '-';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString();
}

const ALL_CONTESTS = 'all';

function contestFilterFromUrl(): string {
  try {
    return new URLSearchParams(window.location.search).get('contestId') ?? ALL_CONTESTS;
  } catch {
    return ALL_CONTESTS;
  }
}

function syncContestFilterToUrl(value: string) {
  try {
    const url = new URL(window.location.href);
    if (value === ALL_CONTESTS) {
      url.searchParams.delete('contestId');
    } else {
      url.searchParams.set('contestId', value);
    }
    window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`);
  } catch {
    // URL persistence is a convenience; filtering still works in memory.
  }
}

function compareSubmissionsNewestFirst(a: SubmissionResponse, b: SubmissionResponse): number {
  const aTime = Date.parse(a.createdAt);
  const bTime = Date.parse(b.createdAt);
  const safeATime = Number.isNaN(aTime) ? 0 : aTime;
  const safeBTime = Number.isNaN(bTime) ? 0 : bTime;
  return safeBTime - safeATime || b.id - a.id;
}

export function SubmissionsView() {
  const [loading, setLoading] = useState(true);
  const [submissions, setSubmissions] = useState<SubmissionResponse[]>([]);
  const [refreshKey, setRefreshKey] = useState(0);

  const [users, setUsers] = useState<UserResponse[]>([]);
  const [problemMapByContest, setProblemMapByContest] = useState<Record<number, Record<number, ProblemResponse>>>({});
  const [contestOptions, setContestOptions] = useState<ContestOption[]>([]);
  const [contestFilter, setContestFilter] = useState<string>(() => contestFilterFromUrl());
  const [loadingContests, setLoadingContests] = useState(false);

  const [query, setQuery] = useState('');
  const [verdictFilter, setVerdictFilter] = useState<string>('');

  const [codeOpen, setCodeOpen] = useState(false);
  const [codeItem, setCodeItem] = useState<SubmissionResponse | null>(null);

  // Selection & rejudge state
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [rejudging, setRejudging] = useState(false);
  const [forceDialogOpen, setForceDialogOpen] = useState(false);

  // Live submission updates via SSE — any verdict change triggers a safe refetch.
  useSubmissionStream({
    role: "ADMIN",
    enabled: true,
    onEvent: () => {
      setRefreshKey((k) => k + 1);
    },
  });

  const userMap = useMemo(() => {
    const map: Record<number, UserResponse> = {};
    for (const u of users) map[u.id] = u;
    return map;
  }, [users]);

  useEffect(() => {
    let mounted = true;
    setLoadingContests(true);
    loadContestOptions()
      .then((options) => {
        if (mounted) setContestOptions(options);
      })
      .catch(() => {
        if (mounted) setContestOptions([]);
      })
      .finally(() => {
        if (mounted) setLoadingContests(false);
      });

    return () => {
      mounted = false;
    };
  }, []);

  useEffect(() => {
    syncContestFilterToUrl(contestFilter);
    setSelected(new Set());
  }, [contestFilter]);

  const load = async () => {
    setLoading(true);
    try {
      const selectedContestId =
        contestFilter === ALL_CONTESTS ? null : Number(contestFilter);
      const [subs, u] = await Promise.all([
        getAllSubmissions(
          selectedContestId && Number.isFinite(selectedContestId)
            ? selectedContestId
            : null
        ),
        getAllUsers().catch(() => [] as UserResponse[]),
      ]);

      setSubmissions([...subs].sort(compareSubmissionsNewestFirst));
      setUsers(u);

      // Build (contestId -> problemId -> ProblemResponse) map for nicer display.
      const contestIds = Array.from(new Set(subs.map(s => s.contestId).filter((x): x is number => typeof x === 'number')));

      const entries: Array<[number, Record<number, ProblemResponse>]> = [];
      for (const cid of contestIds) {
        try {
          const list = await getProblemsByContest(cid);
          const m: Record<number, ProblemResponse> = {};
          for (const p of list) m[p.id] = p;
          entries.push([cid, m]);
        } catch {
          // Contest may have no problems or endpoint might fail; keep partial UI working.
          entries.push([cid, {}]);
        }
      }
      setProblemMapByContest(Object.fromEntries(entries));
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to load submissions');
      setSubmissions([]);
      setUsers([]);
      setProblemMapByContest({});
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    load();
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [refreshKey, contestFilter]);

  const orderedSubmissions = useMemo(
    () => [...submissions].sort(compareSubmissionsNewestFirst),
    [submissions]
  );

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const vf = verdictFilter.trim().toLowerCase();
    return orderedSubmissions.filter(s => {
      const verdictOk = !vf || String(s.verdict ?? '').toLowerCase() === vf;
      if (!verdictOk) return false;

      if (!q) return true;

      const user = userMap[s.userId];
      const username = user?.username ?? '';
      const problemTitle = problemMapByContest[s.contestId]?.[s.problemId]?.title ?? '';

      return (
        String(s.id).includes(q) ||
        String(s.contestId).includes(q) ||
        String(s.problemId).includes(q) ||
        String(s.userId).includes(q) ||
        username.toLowerCase().includes(q) ||
        String(s.language ?? '').toLowerCase().includes(q) ||
        String(s.verdict ?? '').toLowerCase().includes(q) ||
        problemTitle.toLowerCase().includes(q)
      );
    });
  }, [orderedSubmissions, query, verdictFilter, userMap, problemMapByContest]);

  const uniqueVerdicts = useMemo(() => {
    const set = new Set<string>();
    for (const s of orderedSubmissions) {
      if (s.verdict) set.add(String(s.verdict).toUpperCase());
    }
    return Array.from(set).sort();
  }, [orderedSubmissions]);

  const openCode = (s: SubmissionResponse) => {
    setCodeItem(s);
    setCodeOpen(true);
  };

  // ─── Selection helpers ───────────────────────────────────────────────────────

  const toggleSelect = useCallback((id: number) => {
    setSelected(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }, []);

  const toggleSelectAll = useCallback(() => {
    if (selected.size === filtered.length) {
      setSelected(new Set());
    } else {
      setSelected(new Set(filtered.map(s => s.id)));
    }
  }, [filtered, selected.size]);

  const showRejudgeResult = (res: RejudgeResponse, force: boolean) => {
    const label = force ? 'Force rejudge' : 'Rejudge';
    toast.success(
      `${label}: ${res.queuedCount} queued, ${res.skippedCount} skipped` +
        (res.missingSubmissionIds.length > 0
          ? `, ${res.missingSubmissionIds.length} missing`
          : '')
    );
    setSelected(new Set());
    load();
  };

  const handleRejudge = async () => {
    if (selected.size === 0) return;
    setRejudging(true);
    try {
      const res = await rejudgeSubmissions(Array.from(selected));
      showRejudgeResult(res, false);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Rejudge failed');
    } finally {
      setRejudging(false);
    }
  };

  const handleForceRejudge = async () => {
    if (selected.size === 0) return;
    setRejudging(true);
    setForceDialogOpen(false);
    try {
      const res = await forceRejudgeSubmissions(Array.from(selected));
      showRejudgeResult(res, true);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Force rejudge failed');
    } finally {
      setRejudging(false);
    }
  };

  return (
    <>
      <Card className="border border-gray-200 shadow-sm">
        <CardHeader className="border-b border-slate-200 bg-slate-50">
          <div className="flex flex-col gap-4 md:flex-row md:items-center md:justify-between">
            <div>
              <div className="flex items-center gap-2">
                <CardTitle className="text-2xl text-slate-950">Submissions</CardTitle>
                <AdminHelpTooltip
                  label="Submissions help"
                  content="Select submissions to rejudge after test, checker, or language configuration changes."
                />
              </div>
              <p className="mt-1 text-sm text-slate-600">Review aggregate verdicts and submitted source code.</p>
            </div>
            <div className="flex items-center gap-2">
              {selected.size > 0 && (
                <>
                  <Button
                    size="sm"
                    variant="outline"
                    className="gap-2 bg-white"
                    disabled={rejudging}
                    onClick={handleRejudge}
                  >
                    <RotateCcw className="w-4 h-4" />
                    {rejudging ? 'Rejudging...' : `Rejudge (${selected.size})`}
                  </Button>
                  <Button
                    size="sm"
                    variant="destructive"
                    className="gap-2"
                    disabled={rejudging}
                    onClick={() => setForceDialogOpen(true)}
                  >
                    {rejudging ? 'Rejudging...' : `Force Rejudge (${selected.size})`}
                  </Button>
                </>
              )}
              <Button
                size="sm"
                variant="outline"
                className="gap-2 bg-white"
                onClick={load}
              >
                <RefreshCw className="w-4 h-4" />
                Refresh
              </Button>
            </div>
          </div>
        </CardHeader>

        <CardContent className="p-6">
          <div className="flex flex-col md:flex-row md:items-center gap-3 mb-4">
            <div className="flex items-center gap-2">
              <span className="text-sm text-slate-600">Contest:</span>
              <select
                className="h-10 min-w-[220px] rounded-md border border-gray-200 bg-white px-3 text-sm"
                value={contestFilter}
                onChange={(e) => setContestFilter(e.target.value)}
                disabled={loadingContests}
              >
                <option value={ALL_CONTESTS}>All contests</option>
                {contestOptions.map((contest) => (
                  <option key={contest.id} value={String(contest.id)}>
                    {contestLabel(contest)} - {contest.bucket}
                  </option>
                ))}
              </select>
            </div>

            <div className="relative w-full md:max-w-md">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              <Input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search by ID, username, verdict, language..."
                className="h-10 pl-9"
              />
            </div>

            <div className="flex items-center gap-2">
              <span className="text-sm text-slate-600">Verdict:</span>
              <select
                className="h-10 rounded-md border border-gray-200 bg-white px-3 text-sm"
                value={verdictFilter}
                onChange={(e) => setVerdictFilter(e.target.value)}
              >
                <option value="">All</option>
                {uniqueVerdicts.map(v => (
                  <option key={v} value={v.toLowerCase()}>{formatStatusText(normalizeVerdict(v))}</option>
                ))}
              </select>
            </div>

            <div className="text-sm text-slate-600 md:ml-auto">
              Showing <b>{filtered.length}</b> of <b>{orderedSubmissions.length}</b>
            </div>
          </div>

          {loading ? (
            <p className="text-slate-600 py-8 text-center">Loading submissions...</p>
          ) : filtered.length === 0 ? (
            <p className="text-slate-600 py-8 text-center">No submissions found.</p>
          ) : (
            <div className="border border-gray-200 rounded-lg overflow-hidden">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-[50px]">
                      <Checkbox
                        checked={filtered.length > 0 && selected.size === filtered.length}
                        onCheckedChange={toggleSelectAll}
                      />
                    </TableHead>
                    <TableHead className="w-[90px]">ID</TableHead>
                    <TableHead className="w-[110px]">Contest</TableHead>
                    <TableHead>Problem</TableHead>
                    <TableHead>User</TableHead>
                    <TableHead className="w-[140px]">Language</TableHead>
                    <TableHead className="w-[180px]">Verdict</TableHead>
                    <TableHead className="w-[140px]">Exec</TableHead>
                    <TableHead className="w-[140px]">Memory</TableHead>
                    <TableHead className="w-[220px]">Time</TableHead>
                    <TableHead className="text-right w-[90px]">Code</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filtered.map((s) => {
                    const user = userMap[s.userId];
                    const p = problemMapByContest[s.contestId]?.[s.problemId];
                    return (
                      <TableRow key={s.id}>
                        <TableCell>
                          <Checkbox
                            checked={selected.has(s.id)}
                            onCheckedChange={() => toggleSelect(s.id)}
                          />
                        </TableCell>
                        <TableCell className="font-mono text-sm">{s.id}</TableCell>
                        <TableCell className="font-mono text-sm">{s.contestId}</TableCell>
                        <TableCell>
                          <div className="flex flex-col">
                            <span className="text-sm text-slate-900">
                              {p?.title ?? `#${s.problemId}`}
                            </span>
                            <span className="text-xs text-slate-500 font-mono">{s.problemId}</span>
                          </div>
                        </TableCell>
                        <TableCell>
                          <div className="flex flex-col">
                            <span className="text-sm text-slate-900">
                              {user?.username ?? `#${s.userId}`}
                            </span>
                            <span className="text-xs text-slate-500 font-mono">{s.userId}</span>
                          </div>
                        </TableCell>
                        <TableCell className="font-mono text-sm">{s.language}</TableCell>
                        <TableCell>
                          <StatusBadge kind="verdict" value={String(s.verdict)} />
                        </TableCell>
                        <TableCell className="text-sm">
                          {s.executionTime == null ? '-' : `${s.executionTime} ms`}
                        </TableCell>
                        <TableCell className="text-sm">
                          {s.memoryUsage == null ? '-' : `${s.memoryUsage} MB`}
                        </TableCell>
                        <TableCell className="text-sm">{formatDateTime(s.createdAt)}</TableCell>
                        <TableCell className="text-right">
                          <Button
                            size="sm"
                            variant="outline"
                            className="gap-2"
                            onClick={() => openCode(s)}
                          >
                            <Eye className="w-4 h-4" />
                            Details
                          </Button>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Force Rejudge Confirmation Dialog */}
      <AlertDialog open={forceDialogOpen} onOpenChange={setForceDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Force Rejudge {selected.size} submission{selected.size !== 1 ? 's' : ''}?</AlertDialogTitle>
            <AlertDialogDescription>
              Force rejudge includes submissions that are currently pending, running, or awaiting rejudge.
              It logically cancels any in-progress judging by advancing the run ID — old Judge0 callbacks
              will be discarded. External Judge0 jobs are not physically stopped. This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={rejudging}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-rose-600 text-white hover:bg-rose-700"
              disabled={rejudging}
              onClick={(e) => {
                e.preventDefault();
                handleForceRejudge();
              }}
            >
              {rejudging ? 'Force Rejudging...' : 'Force Rejudge'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      <Dialog open={codeOpen} onOpenChange={setCodeOpen}>
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>
              Submission Details {codeItem ? `#${codeItem.id}` : ''}
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            {codeItem && (
              <div className="grid gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm md:grid-cols-3">
                <div>
                  <p className="text-xs font-semibold uppercase text-slate-500">Team</p>
                  <p className="mt-1 font-medium text-slate-900">{userMap[codeItem.userId]?.username ?? `#${codeItem.userId}`}</p>
                </div>
                <div>
                  <p className="text-xs font-semibold uppercase text-slate-500">Problem</p>
                  <p className="mt-1 font-medium text-slate-900">
                    {problemMapByContest[codeItem.contestId]?.[codeItem.problemId]?.title ?? `#${codeItem.problemId}`}
                  </p>
                </div>
                <div>
                  <p className="text-xs font-semibold uppercase text-slate-500">Verdict</p>
                  <div className="mt-1">
                    <StatusBadge kind="verdict" value={String(codeItem.verdict)} />
                  </div>
                </div>
                <div>
                  <p className="text-xs font-semibold uppercase text-slate-500">Language</p>
                  <p className="mt-1 font-mono text-slate-900">{String(codeItem.language)}</p>
                </div>
                <div>
                  <p className="text-xs font-semibold uppercase text-slate-500">Execution</p>
                  <p className="mt-1 text-slate-900">{codeItem.executionTime == null ? '-' : `${codeItem.executionTime} ms`}</p>
                </div>
                <div>
                  <p className="text-xs font-semibold uppercase text-slate-500">Memory</p>
                  <p className="mt-1 text-slate-900">{codeItem.memoryUsage == null ? '-' : `${codeItem.memoryUsage} MB`}</p>
                </div>
                <div className="md:col-span-3">
                  <p className="text-xs font-semibold uppercase text-slate-500">Submitted at</p>
                  <p className="mt-1 text-slate-900">{formatDateTime(codeItem.createdAt)}</p>
                </div>
              </div>
            )}
            <div className="bg-slate-950 text-slate-100 rounded-lg p-4 overflow-auto max-h-[60vh]">
              <pre className="text-xs whitespace-pre-wrap">{codeItem?.code ?? ''}</pre>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
