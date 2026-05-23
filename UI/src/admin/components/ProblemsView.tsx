import { useState, useEffect, useCallback, useMemo } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Plus, ChevronRight, Pencil, Search, Timer, Database, Trash2, AlertTriangle, RefreshCw, RotateCcw } from 'lucide-react';
import { CreateProblemModal } from './CreateProblemModal';
import { TestCasesPanel } from './TestCasesPanel';
import { EditProblemModal } from './EditProblemModal';
import { OraclePanel } from './OraclePanel';
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
import { deleteProblem, getProblem, getProblemsByContest } from '../services/api';
import { ProblemResponse } from '../types/api';
import { toast } from 'sonner';
import { StatusBadge } from '../../components/StatusBadge';
import { RichTextContent } from '../../components/RichTextContent';
import {
  contestLabel,
  ContestOption,
  loadContestOptions,
} from '../utils/contestOptions';

interface ProblemsViewProps {
  contestId: number | null;
}

function contestIdFromUrl(): string {
  try {
    return new URLSearchParams(window.location.search).get('contestId') ?? '';
  } catch {
    return '';
  }
}

function syncContestIdToUrl(value: string) {
  try {
    const url = new URL(window.location.href);
    if (value) {
      url.searchParams.set('contestId', value);
    } else {
      url.searchParams.delete('contestId');
    }
    window.history.replaceState({}, '', `${url.pathname}${url.search}${url.hash}`);
  } catch {
    // URL persistence is a convenience; the in-memory selector still works.
  }
}

function navigateToRejudge(contestId: number) {
  try {
    window.history.pushState({}, '', `/admin/rejudge?contestId=${contestId}`);
    window.dispatchEvent(new PopStateEvent('popstate'));
  } catch {
    window.location.href = `/admin/rejudge?contestId=${contestId}`;
  }
}

export function ProblemsView({ contestId }: ProblemsViewProps) {
  const [contestOptions, setContestOptions] = useState<ContestOption[]>([]);
  const [selectedContestId, setSelectedContestId] = useState(() =>
    contestIdFromUrl() || (contestId != null ? String(contestId) : '')
  );
  const [isLoadingContests, setIsLoadingContests] = useState(false);
  const [contestError, setContestError] = useState<string | null>(null);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [problems, setProblems] = useState<ProblemResponse[]>([]);
  const [selectedProblem, setSelectedProblem] = useState<ProblemResponse | null>(null);
  const [isLoadingProblem, setIsLoadingProblem] = useState(false);
  const [isLoadingList, setIsLoadingList] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [problemToDelete, setProblemToDelete] = useState<ProblemResponse | null>(null);
  const [isDeletingProblem, setIsDeletingProblem] = useState(false);
  const [query, setQuery] = useState('');

  const selectedContest = useMemo(
    () => contestOptions.find((contest) => String(contest.id) === selectedContestId) ?? null,
    [contestOptions, selectedContestId]
  );
  const selectedContestNumericId = useMemo(() => {
    const numeric = Number(selectedContestId);
    return selectedContestId && Number.isFinite(numeric) && numeric > 0 ? numeric : null;
  }, [selectedContestId]);
  const selectedContestState = selectedContest?.effectiveState ?? selectedContest?.status;
  const isEndedContest = selectedContestState === 'ENDED';

  const loadContestChoices = useCallback(async () => {
    setIsLoadingContests(true);
    setContestError(null);
    try {
      const options = await loadContestOptions();
      setContestOptions(options);
      setSelectedContestId((previous) => {
        const urlContestId = contestIdFromUrl();
        const fallbackContestId = contestId != null ? String(contestId) : '';
        const candidates = [previous, urlContestId, fallbackContestId];
        const match = candidates.find(
          (candidate) => candidate && options.some((contest) => String(contest.id) === candidate)
        );

        return match ?? (options[0] ? String(options[0].id) : '');
      });

      if (options.length === 0) {
        setContestError('No contests were found. Create a contest before adding problems.');
      }
    } catch (error) {
      setContestOptions([]);
      setSelectedContestId('');
      setContestError(error instanceof Error ? error.message : 'Failed to load contests.');
    } finally {
      setIsLoadingContests(false);
    }
  }, [contestId]);

  const loadProblems = useCallback(async () => {
    if (!selectedContestNumericId) {
      setProblems([]);
      setSelectedProblem(null);
      return;
    }

    setIsLoadingList(true);
    try {
      const list = await getProblemsByContest(selectedContestNumericId);
      setProblems(list);
      setSelectedProblem((prev) => {
        if (!prev) return null;
        return list.find((p) => p.id === prev.id) ?? null;
      });
    } catch (error) {
      console.error('Failed to load problems list:', error);
      toast.error('Failed to load problems list');
      setProblems([]);
      setSelectedProblem(null);
    } finally {
      setIsLoadingList(false);
    }
  }, [selectedContestNumericId]);

  useEffect(() => {
    loadContestChoices();
  }, [loadContestChoices]);

  useEffect(() => {
    syncContestIdToUrl(selectedContestId);
    setQuery('');
    setSelectedProblem(null);
    setProblems([]);
  }, [selectedContestId]);

  useEffect(() => {
    loadProblems();
  }, [loadProblems]);

  // Load problem details when selected
  const handleProblemSelect = async (problem: ProblemResponse) => {
    setIsLoadingProblem(true);
    try {
      // Fetch fresh problem data from backend
      const problemData = await getProblem(problem.id);
      setSelectedProblem(problemData);
    } catch (error) {
      console.error('Failed to load problem:', error);
      toast.error('Failed to load problem details');
      setSelectedProblem(problem); // Fallback to cached data
    } finally {
      setIsLoadingProblem(false);
    }
  };

  const handleProblemCreated = async (newProblem: ProblemResponse) => {
    // Auto-select newly created problem, then reload list from backend
    setSelectedProblem(newProblem);
    await loadProblems();
  };

  const handleProblemUpdated = async (updatedProblem: ProblemResponse) => {
    setSelectedProblem(updatedProblem);
    await loadProblems();
  };

  const handleDeleteProblem = async () => {
    if (!problemToDelete) return;

    setIsDeletingProblem(true);
    try {
      await deleteProblem(problemToDelete.id);
      toast.success('Problem deleted successfully');
      if (selectedProblem?.id === problemToDelete.id) {
        setSelectedProblem(null);
      }
      setProblemToDelete(null);
      await loadProblems();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to delete problem');
    } finally {
      setIsDeletingProblem(false);
    }
  };

  const filteredProblems = problems.filter((problem) => {
    const q = query.trim().toLowerCase();
    if (!q) return true;
    return (
      String(problem.id).includes(q) ||
      problem.title.toLowerCase().includes(q) ||
      problem.difficulty.toLowerCase().includes(q)
    );
  });

  return (
    <>
      <div className="mb-6 rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="text-sm font-semibold uppercase tracking-wide text-blue-700">
              {selectedContest ? `${selectedContest.bucket} contest #${selectedContest.id}` : 'Contest selection'}
            </p>
            <h1 className="mt-1 text-2xl font-semibold text-slate-950">Problems Management</h1>
            <p className="mt-1 text-sm text-slate-600">Create problem statements and manage public/private judge tests.</p>
          </div>
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
            <select
              className="h-10 min-w-[260px] rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-900"
              value={selectedContestId}
              onChange={(event) => setSelectedContestId(event.target.value)}
              disabled={isLoadingContests || contestOptions.length === 0}
            >
              <option value="">{isLoadingContests ? 'Loading contests...' : 'Select contest'}</option>
              {contestOptions.map((contest) => (
                <option key={contest.id} value={String(contest.id)}>
                  {contestLabel(contest)} - {contest.bucket}
                </option>
              ))}
            </select>
            <Button
              type="button"
              variant="outline"
              className="gap-2 bg-white"
              disabled={isLoadingContests}
              onClick={loadContestChoices}
            >
              <RefreshCw className="h-4 w-4" />
              Refresh
            </Button>
            <Button
              className="gap-2 bg-blue-700 hover:bg-blue-800"
              disabled={!selectedContestNumericId}
              onClick={() => setCreateModalOpen(true)}
            >
              <Plus className="h-4 w-4" />
              Create Problem
            </Button>
          </div>
        </div>
      </div>

      {contestError && (
        <div className="mb-6 flex gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
          <span>{contestError}</span>
        </div>
      )}

      {isEndedContest && selectedContestNumericId && (
        <div className="mb-6 flex flex-col gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900 md:flex-row md:items-center md:justify-between">
          <div className="flex gap-3">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
            <div>
              <p className="font-semibold">Editing problems or test cases in an ended contest may affect rejudge results.</p>
              <p className="mt-1 text-amber-800">
                After changing old contest data, run rejudge for the affected problem or contest.
              </p>
            </div>
          </div>
          <Button
            type="button"
            variant="outline"
            className="gap-2 border-amber-300 bg-white text-amber-900 hover:bg-amber-100"
            onClick={() => navigateToRejudge(selectedContestNumericId)}
          >
            <RotateCcw className="h-4 w-4" />
            Open Rejudge
          </Button>
        </div>
      )}

      {!selectedContestNumericId ? (
        <Card className="border border-gray-200 shadow-sm">
          <CardContent className="p-6">
            <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-10 text-center">
              <p className="text-sm font-medium text-slate-800">No contest selected.</p>
              <p className="mt-1 text-sm text-slate-500">Select any active, paused, upcoming, or ended contest before managing problems.</p>
            </div>
          </CardContent>
        </Card>
      ) : (
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <Card className="border border-gray-200 shadow-sm lg:col-span-1">
          <CardHeader className="border-b border-slate-200 bg-slate-50">
            <div>
              <CardTitle className="text-lg text-slate-950">Problem List</CardTitle>
              <p className="mt-1 text-sm text-slate-500">{problems.length} problem(s) in this contest.</p>
            </div>
          </CardHeader>
          <CardContent className="p-4">
            <div className="relative mb-4">
              <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
              <Input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search problems..."
                className="h-10 pl-9"
              />
            </div>

            {isLoadingList ? (
              <div className="text-center py-8">
                <p className="text-slate-600 text-sm">Loading problems...</p>
              </div>
            ) : problems.length === 0 ? (
              <div className="text-center py-8">
                <p className="text-slate-600 text-sm mb-2">
                  No problems yet
                </p>
                <p className="text-slate-500 text-xs">
                  Use "Create" to add problems.
                </p>
              </div>
            ) : filteredProblems.length === 0 ? (
              <p className="py-8 text-center text-sm text-slate-500">No problems match your search.</p>
            ) : (
              <div className="space-y-2">
                {filteredProblems.map((problem, index) => (
                  <button
                    key={problem.id}
                    onClick={() => handleProblemSelect(problem)}
                    className={`w-full text-left p-3 rounded-lg border transition-colors ${
                      selectedProblem?.id === problem.id
                        ? 'bg-slate-100 border-slate-300'
                        : 'bg-white border-gray-200 hover:bg-gray-50'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex-1 min-w-0">
                        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
                          <span className="inline-flex items-center gap-2">
                            <span
                              className="h-3 w-3 rounded-full border border-slate-300"
                              style={{ backgroundColor: problem.balloonColor }}
                              aria-hidden="true"
                            />
                            Problem {String.fromCharCode(65 + index)}
                          </span>
                        </p>
                        <p className="text-sm font-medium text-slate-900 truncate">
                          {problem.title}
                        </p>
                        <div className="flex items-center gap-2 mt-1">
                          <StatusBadge kind="difficulty" value={problem.difficulty} />
                        </div>
                      </div>
                      <ChevronRight className="w-4 h-4 text-slate-400 flex-shrink-0 ml-2" />
                    </div>
                  </button>
                ))}
              </div>
            )}
          </CardContent>
        </Card>

        <Card className="border border-gray-200 shadow-sm lg:col-span-2">
          <CardHeader className="border-b border-slate-200 bg-slate-50">
            <CardTitle className="text-lg text-slate-950">Problem Details</CardTitle>
          </CardHeader>
          <CardContent className="p-6">
            {isLoadingProblem ? (
              <p className="text-slate-600 py-8 text-center">
                Loading problem details...
              </p>
            ) : !selectedProblem ? (
              <p className="text-slate-600 py-8 text-center">
                Select a problem from the list to view details and manage test cases
              </p>
            ) : (
              <div className="space-y-4">
                <div>
                  <div className="flex items-center justify-between gap-3 mb-2">
                    <h3 className="text-2xl font-semibold text-slate-950">{selectedProblem.title}</h3>
                    <div className="flex items-center gap-2">
                      <Button
                        size="sm"
                        variant="outline"
                        className="gap-2"
                        onClick={() => setEditModalOpen(true)}
                      >
                        <Pencil className="w-4 h-4" />
                        Edit Problem
                      </Button>
                      <Button
                        size="sm"
                        variant="outline"
                        className="gap-2 border-rose-200 text-rose-700 hover:bg-rose-50 hover:text-rose-800"
                        onClick={() => setProblemToDelete(selectedProblem)}
                      >
                        <Trash2 className="w-4 h-4" />
                        Delete
                      </Button>
                    </div>
                  </div>
                  <div className="flex flex-wrap items-center gap-2 mb-4">
                    <StatusBadge kind="difficulty" value={selectedProblem.difficulty} />
                    <span className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-600">
                      <span
                        className="h-3 w-3 rounded-full border border-slate-300"
                        style={{ backgroundColor: selectedProblem.balloonColor }}
                        aria-hidden="true"
                      />
                      Balloon {selectedProblem.balloonColor}
                    </span>
                    <span className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-600">
                      <Timer className="h-3.5 w-3.5" />
                      {selectedProblem.timeLimit} ms
                    </span>
                    <span className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-600">
                      <Database className="h-3.5 w-3.5" />
                      {selectedProblem.memoryLimit} MB
                    </span>
                  </div>
                </div>

                <div>
                  <h4 className="font-semibold text-slate-800 mb-2">Problem Statement</h4>
                  <div className="bg-slate-50 border border-slate-200 rounded-lg p-4">
                    <RichTextContent content={selectedProblem.description} />
                  </div>
                </div>
              </div>
            )}
          </CardContent>
        </Card>
      </div>
      )}

      {/* Test Cases Panel - Only shown when a problem is selected */}
      {selectedContestNumericId && selectedProblem && (
        <TestCasesPanel 
          problemId={selectedProblem.id} 
          problemTitle={selectedProblem.title}
        />
      )}

      {selectedContestNumericId && selectedProblem && (
        <div className="mt-6">
          <OraclePanel
            problemId={selectedProblem.id}
            problemTitle={selectedProblem.title}
            contestId={selectedContestNumericId}
          />
        </div>
      )}

      {selectedContestNumericId && selectedProblem && (
        <EditProblemModal
          open={editModalOpen}
          onOpenChange={setEditModalOpen}
          problem={selectedProblem}
          onSuccess={handleProblemUpdated}
        />
      )}

      {selectedContestNumericId && (
        <CreateProblemModal
          open={createModalOpen}
          onOpenChange={setCreateModalOpen}
          contestId={selectedContestNumericId}
          nextProblemIndex={problems.length}
          onSuccess={handleProblemCreated}
        />
      )}

      <AlertDialog
        open={Boolean(problemToDelete)}
        onOpenChange={(open) => {
          if (!open && !isDeletingProblem) setProblemToDelete(null);
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete problem?</AlertDialogTitle>
            <AlertDialogDescription>
              This permanently removes "{problemToDelete?.title}" and its test cases. Related submissions and
              clarifications for this problem will also be removed so the contest data stays consistent.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel disabled={isDeletingProblem}>Cancel</AlertDialogCancel>
            <AlertDialogAction
              className="bg-rose-600 text-white hover:bg-rose-700"
              disabled={isDeletingProblem}
              onClick={(event) => {
                event.preventDefault();
                handleDeleteProblem();
              }}
            >
              {isDeletingProblem ? 'Deleting...' : 'Delete Problem'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
