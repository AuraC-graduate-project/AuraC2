import { useCallback, useEffect, useMemo, useState } from 'react';
import { AlertTriangle, FileCode, RefreshCw, RotateCcw, Search, Trophy, Zap } from 'lucide-react';
import { toast } from 'sonner';
import {
  forceRejudgeContest,
  forceRejudgeProblem,
  getProblemsByContest,
  rejudgeContest,
  rejudgeProblem,
} from '../services/api';
import { ProblemResponse, RejudgeResponse } from '../types/api';
import {
  contestLabel,
  ContestOption,
  loadContestOptions as loadContestOptionsList,
} from '../utils/contestOptions';
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
import { Badge } from './ui/badge';
import { Button } from './ui/button';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Input } from './ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from './ui/select';

type ForceTarget =
  | { kind: 'problem'; id: number; label: string }
  | { kind: 'contest'; id: number; label: string }
  | null;
type ActionInFlight = 'problem' | 'contest' | 'force-problem' | 'force-contest' | null;

type RejudgeViewProps = {
  initialContestId?: number | null;
};

function problemLetter(index: number): string {
  if (index >= 0 && index < 26) {
    return String.fromCharCode(65 + index);
  }
  return String(index + 1);
}

function difficultyClass(difficulty: ProblemResponse['difficulty']): string {
  switch (difficulty) {
    case 'EASY':
      return 'border-emerald-200 bg-emerald-50 text-emerald-700';
    case 'MEDIUM':
      return 'border-amber-200 bg-amber-50 text-amber-700';
    case 'HARD':
      return 'border-rose-200 bg-rose-50 text-rose-700';
    default:
      return 'border-slate-200 bg-slate-50 text-slate-700';
  }
}

export function RejudgeView({ initialContestId = null }: RejudgeViewProps) {
  const [contestOptions, setContestOptions] = useState<ContestOption[]>([]);
  const [selectedContestId, setSelectedContestId] = useState('');
  const [problems, setProblems] = useState<ProblemResponse[]>([]);
  const [selectedProblemId, setSelectedProblemId] = useState('');
  const [problemSearch, setProblemSearch] = useState('');
  const [loadingContests, setLoadingContests] = useState(false);
  const [loadingProblems, setLoadingProblems] = useState(false);
  const [contestError, setContestError] = useState<string | null>(null);
  const [problemError, setProblemError] = useState<string | null>(null);
  const [actionInFlight, setActionInFlight] = useState<ActionInFlight>(null);
  const [forceTarget, setForceTarget] = useState<ForceTarget>(null);

  const selectedContest = useMemo(
    () => contestOptions.find((contest) => String(contest.id) === selectedContestId) ?? null,
    [contestOptions, selectedContestId]
  );

  const selectedProblem = useMemo(
    () => problems.find((problem) => String(problem.id) === selectedProblemId) ?? null,
    [problems, selectedProblemId]
  );

  const problemIndexById = useMemo(() => {
    const indexes = new Map<number, number>();
    problems.forEach((problem, index) => indexes.set(problem.id, index));
    return indexes;
  }, [problems]);

  const filteredProblems = useMemo(() => {
    const query = problemSearch.trim().toLowerCase();
    if (!query) return problems;

    return problems.filter((problem, index) => {
      const code = problemLetter(index).toLowerCase();
      return (
        problem.title.toLowerCase().includes(query) ||
        problem.difficulty.toLowerCase().includes(query) ||
        String(problem.id).includes(query) ||
        code.includes(query)
      );
    });
  }, [problemSearch, problems]);

  const isWorking = actionInFlight !== null;
  const problemActionDisabled = isWorking || loadingProblems || selectedProblem === null;
  const contestActionDisabled = isWorking || selectedContest === null;

  const showResult = (res: RejudgeResponse, force: boolean) => {
    const label = force ? 'Force rejudge' : 'Rejudge';
    toast.success(
      `${label} (${res.scope}): ${res.queuedCount} queued, ${res.skippedCount} skipped` +
        (res.missingSubmissionIds.length > 0
          ? `, ${res.missingSubmissionIds.length} missing`
          : '')
    );
  };

  const loadContestOptions = useCallback(async () => {
    setLoadingContests(true);
    setContestError(null);

    try {
      const next = await loadContestOptionsList();

      setContestOptions(next);
      setSelectedContestId((previous) => {
        if (previous && next.some((contest) => String(contest.id) === previous)) {
          return previous;
        }
        if (
          initialContestId != null &&
          next.some((contest) => contest.id === initialContestId)
        ) {
          return String(initialContestId);
        }
        return next[0] ? String(next[0].id) : '';
      });

      if (next.length === 0) {
        setContestError('No contests were found for rejudge selection.');
      }
    } catch (error) {
      setContestOptions([]);
      setSelectedContestId('');
      setContestError(error instanceof Error ? error.message : 'Failed to load contests.');
    } finally {
      setLoadingContests(false);
    }
  }, [initialContestId]);

  const loadProblems = useCallback(async (contestId: number) => {
    setLoadingProblems(true);
    setProblemError(null);
    try {
      const list = await getProblemsByContest(contestId);
      setProblems(list);
      setSelectedProblemId((previous) => {
        if (previous && list.some((problem) => String(problem.id) === previous)) {
          return previous;
        }
        return list[0] ? String(list[0].id) : '';
      });
      if (list.length === 0) {
        setProblemError('This contest does not have problems yet.');
      }
    } catch (error) {
      setProblems([]);
      setSelectedProblemId('');
      setProblemError(error instanceof Error ? error.message : 'Failed to load problems.');
    } finally {
      setLoadingProblems(false);
    }
  }, []);

  useEffect(() => {
    loadContestOptions();
  }, [loadContestOptions]);

  useEffect(() => {
    const contestId = Number(selectedContestId);
    setProblemSearch('');

    if (!contestId || contestId <= 0) {
      setProblems([]);
      setSelectedProblemId('');
      return;
    }

    loadProblems(contestId);
  }, [loadProblems, selectedContestId]);

  const handleRejudgeProblem = async () => {
    if (!selectedProblem) {
      toast.error('Choose a problem first.');
      return;
    }

    setActionInFlight('problem');
    try {
      const res = await rejudgeProblem(selectedProblem.id);
      showResult(res, false);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Rejudge problem failed');
    } finally {
      setActionInFlight(null);
    }
  };

  const handleRejudgeContest = async () => {
    if (!selectedContest) {
      toast.error('Choose a contest first.');
      return;
    }

    setActionInFlight('contest');
    try {
      const res = await rejudgeContest(selectedContest.id);
      showResult(res, false);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Rejudge contest failed');
    } finally {
      setActionInFlight(null);
    }
  };

  const openForceDialog = (kind: 'problem' | 'contest') => {
    if (kind === 'problem') {
      if (!selectedProblem) {
        toast.error('Choose a problem first.');
        return;
      }
      const index = problemIndexById.get(selectedProblem.id) ?? 0;
      setForceTarget({
        kind,
        id: selectedProblem.id,
        label: `Problem ${problemLetter(index)} - ${selectedProblem.title}`,
      });
      return;
    }

    if (!selectedContest) {
      toast.error('Choose a contest first.');
      return;
    }

    setForceTarget({
      kind,
      id: selectedContest.id,
      label: selectedContest.title || `Contest #${selectedContest.id}`,
    });
  };

  const handleForceRejudge = async () => {
    if (!forceTarget) return;

    const target = forceTarget;
    setForceTarget(null);
    setActionInFlight(target.kind === 'problem' ? 'force-problem' : 'force-contest');

    try {
      const res =
        target.kind === 'problem'
          ? await forceRejudgeProblem(target.id)
          : await forceRejudgeContest(target.id);
      showResult(res, true);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Force rejudge failed');
    } finally {
      setActionInFlight(null);
    }
  };

  return (
    <>
      <Card className="border border-slate-200 shadow-sm">
        <CardHeader className="border-b border-slate-200 bg-slate-50">
          <CardTitle className="text-2xl text-slate-950">Rejudge</CardTitle>
          <p className="mt-1 text-sm text-slate-600">
            Select a contest, choose a problem by name, then requeue normal or force rejudge jobs.
          </p>
        </CardHeader>

        <CardContent className="space-y-6 p-6">
          <section className="rounded-md border border-slate-200 bg-white p-4">
            <div className="mb-4 flex flex-col gap-3 lg:flex-row lg:items-end">
              <div className="flex-1">
                <label className="mb-1 block text-sm font-medium text-slate-700">
                  Contest
                </label>
                <Select
                  value={selectedContestId}
                  onValueChange={setSelectedContestId}
                  disabled={loadingContests || contestOptions.length === 0}
                >
                  <SelectTrigger className="h-10 bg-white">
                    <SelectValue
                      placeholder={loadingContests ? 'Loading contests...' : 'Select contest'}
                    />
                  </SelectTrigger>
                  <SelectContent className="max-h-72">
                    {contestOptions.map((contest) => (
                      <SelectItem key={contest.id} value={String(contest.id)}>
                        {contestLabel(contest)} - {contest.bucket}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <Button
                type="button"
                variant="outline"
                className="h-10 gap-2"
                disabled={loadingContests}
                onClick={loadContestOptions}
              >
                <RefreshCw className="h-4 w-4" />
                Refresh
              </Button>
            </div>

            {contestError ? (
              <div className="flex items-center gap-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                <AlertTriangle className="h-4 w-4" />
                {contestError}
              </div>
            ) : null}

            {selectedContest ? (
              <div className="grid gap-3 rounded-md border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700 md:grid-cols-4">
                <div>
                  <span className="block text-xs font-medium uppercase text-slate-500">Selected</span>
                  <span className="font-semibold text-slate-900">{selectedContest.title}</span>
                </div>
                <div>
                  <span className="block text-xs font-medium uppercase text-slate-500">Contest ID</span>
                  #{selectedContest.id}
                </div>
                <div>
                  <span className="block text-xs font-medium uppercase text-slate-500">State</span>
                  {selectedContest.effectiveState ?? selectedContest.status}
                </div>
                <div>
                  <span className="block text-xs font-medium uppercase text-slate-500">Problems</span>
                  {loadingProblems ? 'Loading...' : problems.length}
                </div>
              </div>
            ) : null}
          </section>

          <section className="rounded-md border border-slate-200 bg-white p-4">
            <div className="mb-4 flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
              <h3 className="flex items-center gap-2 text-lg font-semibold text-slate-900">
                <FileCode className="h-5 w-5 text-slate-600" />
                Problem Rejudge
              </h3>
              <div className="relative w-full md:max-w-sm">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <Input
                  value={problemSearch}
                  onChange={(event) => setProblemSearch(event.target.value)}
                  placeholder="Search problem name, ID, or difficulty"
                  className="h-10 bg-white pl-9"
                  disabled={loadingProblems || problems.length === 0}
                />
              </div>
            </div>

            {problemError ? (
              <div className="mb-4 flex items-center gap-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                <AlertTriangle className="h-4 w-4" />
                {problemError}
              </div>
            ) : null}

            <div className="grid gap-4 lg:grid-cols-[minmax(0,1.15fr)_minmax(280px,0.85fr)]">
              <div className="max-h-80 overflow-y-auto rounded-md border border-slate-200">
                {loadingProblems ? (
                  <div className="p-4 text-sm text-slate-600">Loading problems...</div>
                ) : filteredProblems.length === 0 ? (
                  <div className="p-4 text-sm text-slate-600">
                    {problems.length === 0 ? 'No problems available.' : 'No problems match this search.'}
                  </div>
                ) : (
                  filteredProblems.map((problem) => {
                    const index = problemIndexById.get(problem.id) ?? 0;
                    const selected = selectedProblem?.id === problem.id;

                    return (
                      <button
                        key={problem.id}
                        type="button"
                        className={
                          'flex w-full items-start justify-between gap-4 border-b border-slate-100 px-4 py-3 text-left transition last:border-b-0 hover:bg-slate-50 ' +
                          (selected ? 'bg-blue-50 ring-1 ring-inset ring-blue-200' : 'bg-white')
                        }
                        onClick={() => setSelectedProblemId(String(problem.id))}
                      >
                        <span className="min-w-0">
                          <span className="block text-sm font-semibold text-slate-950">
                            Problem {problemLetter(index)} - {problem.title}
                          </span>
                          <span className="mt-1 block text-xs text-slate-600">
                            ID #{problem.id} - {problem.timeLimit} ms - {problem.memoryLimit} MB
                          </span>
                        </span>
                        <Badge
                          variant="outline"
                          className={difficultyClass(problem.difficulty)}
                        >
                          {problem.difficulty}
                        </Badge>
                      </button>
                    );
                  })
                )}
              </div>

              <div className="rounded-md border border-slate-200 bg-slate-50 p-4">
                <h4 className="text-sm font-semibold text-slate-900">Selected Problem</h4>
                {selectedProblem ? (
                  <div className="mt-3 space-y-3 text-sm">
                    <div>
                      <span className="block text-xs font-medium uppercase text-slate-500">
                        Problem name
                      </span>
                      <span className="font-semibold text-slate-950">{selectedProblem.title}</span>
                    </div>
                    <div className="grid grid-cols-2 gap-3 text-slate-700">
                      <div>
                        <span className="block text-xs font-medium uppercase text-slate-500">Problem ID</span>
                        #{selectedProblem.id}
                      </div>
                      <div>
                        <span className="block text-xs font-medium uppercase text-slate-500">Contest ID</span>
                        #{selectedProblem.contestId}
                      </div>
                      <div>
                        <span className="block text-xs font-medium uppercase text-slate-500">Time</span>
                        {selectedProblem.timeLimit} ms
                      </div>
                      <div>
                        <span className="block text-xs font-medium uppercase text-slate-500">Memory</span>
                        {selectedProblem.memoryLimit} MB
                      </div>
                    </div>
                    <Badge
                      variant="outline"
                      className={difficultyClass(selectedProblem.difficulty)}
                    >
                      {selectedProblem.difficulty}
                    </Badge>
                  </div>
                ) : (
                  <p className="mt-3 text-sm text-slate-600">
                    Select a problem from the list to rejudge it.
                  </p>
                )}

                <div className="mt-5 flex flex-col gap-2 sm:flex-row">
                  <Button
                    size="sm"
                    variant="outline"
                    className="h-10 flex-1 gap-2"
                    disabled={problemActionDisabled}
                    onClick={handleRejudgeProblem}
                  >
                    <RotateCcw className="h-4 w-4" />
                    {actionInFlight === 'problem' ? 'Working...' : 'Rejudge'}
                  </Button>
                  <Button
                    size="sm"
                    variant="destructive"
                    className="h-10 flex-1 gap-2"
                    disabled={problemActionDisabled}
                    onClick={() => openForceDialog('problem')}
                  >
                    <Zap className="h-4 w-4" />
                    {actionInFlight === 'force-problem' ? 'Working...' : 'Force Rejudge'}
                  </Button>
                </div>
              </div>
            </div>
          </section>

          <section className="rounded-md border border-slate-200 bg-white p-4">
            <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <h3 className="flex items-center gap-2 text-lg font-semibold text-slate-900">
                  <Trophy className="h-5 w-5 text-slate-600" />
                  Contest Rejudge
                </h3>
                <p className="mt-1 text-sm text-slate-600">
                  {selectedContest
                    ? `${selectedContest.title} (#${selectedContest.id})`
                    : 'No contest selected'}
                </p>
              </div>
              <div className="flex flex-col gap-2 sm:flex-row">
                <Button
                  size="sm"
                  variant="outline"
                  className="h-10 gap-2"
                  disabled={contestActionDisabled}
                  onClick={handleRejudgeContest}
                >
                  <RotateCcw className="h-4 w-4" />
                  {actionInFlight === 'contest' ? 'Working...' : 'Rejudge Contest'}
                </Button>
                <Button
                  size="sm"
                  variant="destructive"
                  className="h-10 gap-2"
                  disabled={contestActionDisabled}
                  onClick={() => openForceDialog('contest')}
                >
                  <Zap className="h-4 w-4" />
                  {actionInFlight === 'force-contest' ? 'Working...' : 'Force Contest'}
                </Button>
              </div>
            </div>
          </section>
        </CardContent>
      </Card>

      <AlertDialog
        open={forceTarget !== null}
        onOpenChange={(open) => {
          if (!open) setForceTarget(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              Force Rejudge {forceTarget?.kind === 'problem' ? 'Problem' : 'Contest'}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {forceTarget
                ? `${forceTarget.label} will include pending, pending rejudge, and running submissions. Old Judge0 callbacks become stale by advancing the judge run ID, but external Judge0 jobs are not physically stopped.`
                : ''}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isWorking}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-rose-600 text-white hover:bg-rose-700"
              disabled={isWorking}
              onClick={(event) => {
                event.preventDefault();
                handleForceRejudge();
              }}
            >
              {isWorking ? 'Force Rejudging...' : 'Force Rejudge'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
