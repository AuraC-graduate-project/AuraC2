import { useEffect, useMemo, useState } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Badge } from './ui/badge';
import { Button } from './ui/button';
import { Input } from './ui/input';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from './ui/dialog';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from './ui/table';
import { getAllSubmissions, getAllUsers, getProblemsByContest } from '../services/api';
import { ProblemResponse, SubmissionResponse, UserResponse } from '../types/api';
import { RefreshCw, Eye } from 'lucide-react';
import { toast } from 'sonner';

function verdictBadgeVariant(v: string): 'default' | 'secondary' | 'destructive' {
  const x = (v ?? '').toUpperCase();
  if (x === 'ACCEPTED' || x === 'OK') return 'default';
  if (x === 'PENDING' || x === 'RUNNING') return 'secondary';
  return 'destructive';
}

function formatDateTime(iso: string): string {
  if (!iso) return '-';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString();
}

export function SubmissionsView() {
  const [loading, setLoading] = useState(true);
  const [submissions, setSubmissions] = useState<SubmissionResponse[]>([]);

  const [users, setUsers] = useState<UserResponse[]>([]);
  const [problemMapByContest, setProblemMapByContest] = useState<Record<number, Record<number, ProblemResponse>>>({});

  const [query, setQuery] = useState('');
  const [verdictFilter, setVerdictFilter] = useState<string>('');

  const [codeOpen, setCodeOpen] = useState(false);
  const [codeItem, setCodeItem] = useState<SubmissionResponse | null>(null);

  const userMap = useMemo(() => {
    const map: Record<number, UserResponse> = {};
    for (const u of users) map[u.id] = u;
    return map;
  }, [users]);

  const load = async () => {
    setLoading(true);
    try {
      const [subs, u] = await Promise.all([
        getAllSubmissions(),
        getAllUsers().catch(() => [] as UserResponse[]),
      ]);

      setSubmissions(subs);
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
  }, []);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    const vf = verdictFilter.trim().toLowerCase();
    return submissions.filter(s => {
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
  }, [submissions, query, verdictFilter, userMap, problemMapByContest]);

  const uniqueVerdicts = useMemo(() => {
    const set = new Set<string>();
    for (const s of submissions) {
      if (s.verdict) set.add(String(s.verdict).toUpperCase());
    }
    return Array.from(set).sort();
  }, [submissions]);

  const openCode = (s: SubmissionResponse) => {
    setCodeItem(s);
    setCodeOpen(true);
  };

  return (
    <>
      <Card className="border border-gray-200 shadow-sm">
        <CardHeader className="bg-[#1E293B] text-white">
          <div className="flex items-center justify-between">
            <CardTitle>Submissions</CardTitle>
            <Button
              size="sm"
              variant="outline"
              className="gap-2 bg-white text-slate-900 hover:bg-slate-100"
              onClick={load}
            >
              <RefreshCw className="w-4 h-4" />
              Refresh
            </Button>
          </div>
        </CardHeader>

        <CardContent className="p-6">
          <div className="flex flex-col md:flex-row md:items-center gap-3 mb-4">
            <Input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search by id, username, verdict, language..."
              className="md:max-w-md"
            />

            <div className="flex items-center gap-2">
              <span className="text-sm text-slate-600">Verdict:</span>
              <select
                className="h-10 rounded-md border border-gray-200 bg-white px-3 text-sm"
                value={verdictFilter}
                onChange={(e) => setVerdictFilter(e.target.value)}
              >
                <option value="">All</option>
                {uniqueVerdicts.map(v => (
                  <option key={v} value={v.toLowerCase()}>{v}</option>
                ))}
              </select>
            </div>

            <div className="text-sm text-slate-600 md:ml-auto">
              Showing <b>{filtered.length}</b> of <b>{submissions.length}</b>
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
                          <Badge variant={verdictBadgeVariant(String(s.verdict))}>
                            {String(s.verdict)}
                          </Badge>
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

      <Dialog open={codeOpen} onOpenChange={setCodeOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>
              Submission Code {codeItem ? `#${codeItem.id}` : ''}
            </DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            {codeItem && (
              <div className="text-sm text-slate-600">
                Contest <b>{codeItem.contestId}</b> · Problem <b>{codeItem.problemId}</b> · User <b>{codeItem.userId}</b> · {String(codeItem.language)}
              </div>
            )}
            <div className="bg-slate-900 text-slate-100 rounded-lg p-4 overflow-auto max-h-[60vh]">
              <pre className="text-xs whitespace-pre-wrap">{codeItem?.code ?? ''}</pre>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
