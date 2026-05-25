import { useState, useEffect, useCallback, useMemo } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Plus, ChevronRight, ChevronLeft, Pencil, Search, Timer, Database, Trash2, AlertTriangle, RefreshCw, RotateCcw, Download } from 'lucide-react';
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
import { deleteProblem, fetchContestBookletPdf, fetchProblemStatementPdf, getProblem, getProblemsByContest, getPublicTestCasesForProblem } from '../services/api';
import { ProblemResponse, TestCaseResponse } from '../types/api';
import { toast } from 'sonner';
import { StatusBadge } from '../../components/StatusBadge';
import { ProblemStatementPreview, problemReadinessWarnings } from '../../components/ProblemStatementPreview';
import {
  contestLabel,
  ContestOption,
  loadContestOptions,
} from '../utils/contestOptions';
import { AdminHelpTooltip } from './AdminHelpTooltip';

interface ProblemsViewProps {
  contestId: number | null;
}

type ProblemTab = 'statement' | 'test-cases' | 'prompt-exports' | 'engineering' | 'generated-tests' | 'counterexamples';

const PROBLEM_TABS: { value: ProblemTab; label: string }[] = [
  { value: 'statement', label: 'Statement' },
  { value: 'test-cases', label: 'Test Cases' },
  { value: 'prompt-exports', label: 'Prompt Exports' },
  { value: 'engineering', label: 'Engineering' },
  { value: 'generated-tests', label: 'Generated Tests' },
  { value: 'counterexamples', label: 'Counterexamples' },
];

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
  const [publicSamples, setPublicSamples] = useState<TestCaseResponse[]>([]);
  const [isLoadingPublicSamples, setIsLoadingPublicSamples] = useState(false);
  const [isLoadingProblem, setIsLoadingProblem] = useState(false);
  const [isLoadingList, setIsLoadingList] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [problemToDelete, setProblemToDelete] = useState<ProblemResponse | null>(null);
  const [isDeletingProblem, setIsDeletingProblem] = useState(false);
  const [exportingPdf, setExportingPdf] = useState<string | null>(null);
  const [query, setQuery] = useState('');
  const [activeProblemTab, setActiveProblemTab] = useState<ProblemTab>('statement');
  const [navigatorCollapsed, setNavigatorCollapsed] = useState(false);

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
  const readinessWarnings = selectedProblem
    ? problemReadinessWarnings(selectedProblem, publicSamples.length)
    : [];

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

  useEffect(() => {
    let mounted = true;

    if (!selectedProblem?.id) {
      setPublicSamples([]);
      return;
    }

    setIsLoadingPublicSamples(true);
    getPublicTestCasesForProblem(selectedProblem.id)
      .then((samples) => {
        if (mounted) setPublicSamples(samples);
      })
      .catch(() => {
        if (mounted) setPublicSamples([]);
      })
      .finally(() => {
        if (mounted) setIsLoadingPublicSamples(false);
      });

    return () => {
      mounted = false;
    };
  }, [selectedProblem?.id]);

  useEffect(() => {
    setActiveProblemTab('statement');
  }, [selectedProblem?.id]);

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

  const downloadBlob = (blob: Blob, filename: string) => {
    const href = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = href;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(href);
  };

  const handleProblemPdfExport = async () => {
    if (!selectedProblem) return;
    setExportingPdf(`problem-${selectedProblem.id}`);
    try {
      const { blob, filename } = await fetchProblemStatementPdf(selectedProblem.id);
      downloadBlob(blob, filename ?? `${selectedProblem.title.toLowerCase().replace(/[^a-z0-9]+/g, '-')}-statement.pdf`);
      toast.success('Problem PDF exported');
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to export problem PDF');
    } finally {
      setExportingPdf(null);
    }
  };

  const handleContestBookletExport = async () => {
    if (!selectedContestNumericId) return;
    setExportingPdf(`contest-${selectedContestNumericId}`);
    try {
      const { blob, filename } = await fetchContestBookletPdf(selectedContestNumericId);
      downloadBlob(blob, filename ?? `contest-${selectedContestNumericId}-problem-booklet.pdf`);
      toast.success('Contest booklet PDF exported');
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to export contest booklet PDF');
    } finally {
      setExportingPdf(null);
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
  const navigatorProblems = navigatorCollapsed ? problems : filteredProblems;
  const selectedProblemIndex = selectedProblem
    ? problems.findIndex((problem) => problem.id === selectedProblem.id)
    : -1;
  const selectedProblemLabel = selectedProblemIndex >= 0 ? String.fromCharCode(65 + selectedProblemIndex) : '';

  const problemLabel = (problem: ProblemResponse) => {
    const index = problems.findIndex((item) => item.id === problem.id);
    return index >= 0 ? String.fromCharCode(65 + index) : '#';
  };

  const toggleNavigator = () => {
    setNavigatorCollapsed((collapsed) => !collapsed);
  };

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
              type="button"
              variant="outline"
              className="gap-2 bg-white"
              disabled={!selectedContestNumericId || exportingPdf === `contest-${selectedContestNumericId}`}
              onClick={handleContestBookletExport}
            >
              <Download className="h-4 w-4" />
              {exportingPdf === `contest-${selectedContestNumericId}` ? 'Exporting...' : 'Booklet PDF'}
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
      <div className="space-y-4 lg:flex lg:items-start lg:gap-5 lg:space-y-0">
        <div className="lg:hidden">
          <label className="mb-2 block text-sm font-semibold text-slate-800 dark:text-slate-100" htmlFor="mobile-problem-select">
            Problem
          </label>
          <select
            id="mobile-problem-select"
            className="h-10 w-full rounded-md border border-slate-200 bg-white px-3 text-sm text-slate-900 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100"
            value={selectedProblem?.id ?? ''}
            onChange={(event) => {
              const problem = problems.find((item) => item.id === Number(event.target.value));
              if (problem) handleProblemSelect(problem);
            }}
            disabled={isLoadingList || problems.length === 0}
          >
            <option value="">{isLoadingList ? 'Loading problems...' : 'Select problem'}</option>
            {problems.map((problem) => (
              <option key={problem.id} value={problem.id}>
                {problemLabel(problem)}. {problem.title}
              </option>
            ))}
          </select>
        </div>

        <aside
          className={`hidden shrink-0 overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm transition-all duration-200 dark:border-slate-700 dark:bg-slate-900 lg:flex lg:flex-col ${
            navigatorCollapsed ? 'w-16' : 'w-[18rem]'
          }`}
          aria-label="Problem Navigator"
        >
          <div className="border-b border-slate-200 bg-slate-50 p-3 dark:border-slate-700 dark:bg-slate-900/70">
            <div className={`flex items-center ${navigatorCollapsed ? 'justify-center' : 'justify-between gap-2'}`}>
              {!navigatorCollapsed && (
                <div className="min-w-0">
                  <h2 className="text-sm font-semibold text-slate-950 dark:text-slate-50">Problem Navigator</h2>
                  <p className="mt-0.5 text-xs text-slate-500 dark:text-slate-400">{problems.length} problem(s)</p>
                </div>
              )}
              <Button
                type="button"
                variant="outline"
                size="sm"
                className="h-8 w-8 shrink-0 p-0"
                onClick={toggleNavigator}
                aria-label={navigatorCollapsed ? 'Expand problem navigator' : 'Collapse problem navigator'}
                title={navigatorCollapsed ? 'Expand problem navigator' : 'Collapse problem navigator'}
              >
                {navigatorCollapsed ? <ChevronRight className="h-4 w-4" /> : <ChevronLeft className="h-4 w-4" />}
              </Button>
            </div>

            {!navigatorCollapsed && (
              <div className="relative mt-3">
                <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-slate-400" />
                <Input
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="Search problems..."
                  className="h-9 pl-9 text-sm"
                />
              </div>
            )}
          </div>

          <div className="max-h-[calc(100vh-18rem)] min-h-[12rem] overflow-y-auto p-2">
            {isLoadingList ? (
              <div className={`py-8 text-center text-sm text-slate-600 dark:text-slate-300 ${navigatorCollapsed ? 'px-1' : 'px-3'}`}>
                {navigatorCollapsed ? '...' : 'Loading problems...'}
              </div>
            ) : problems.length === 0 ? (
              <div className={`py-8 text-center ${navigatorCollapsed ? 'px-1' : 'px-3'}`}>
                <p className="text-sm font-medium text-slate-700 dark:text-slate-200">{navigatorCollapsed ? '0' : 'No problems yet'}</p>
                {!navigatorCollapsed && <p className="mt-1 text-xs text-slate-500 dark:text-slate-400">Use Create Problem to add one.</p>}
              </div>
            ) : !navigatorCollapsed && filteredProblems.length === 0 ? (
              <p className="px-3 py-8 text-center text-sm text-slate-500 dark:text-slate-400">No problems match your search.</p>
            ) : (
              <div className="space-y-1">
                {navigatorProblems.map((problem) => {
                  const active = selectedProblem?.id === problem.id;
                  const label = problemLabel(problem);
                  return (
                    <button
                      key={problem.id}
                      type="button"
                      onClick={() => handleProblemSelect(problem)}
                      title={`${label}. ${problem.title}`}
                      aria-current={active ? 'true' : undefined}
                      className={`group relative flex w-full items-center rounded-md border text-left transition ${
                        navigatorCollapsed
                          ? 'h-11 justify-center px-1'
                          : 'min-h-11 gap-2 px-2.5 py-2'
                      } ${
                        active
                          ? 'border-blue-300 bg-blue-50 text-blue-950 shadow-sm dark:border-blue-700 dark:bg-blue-950/35 dark:text-blue-50'
                          : 'border-transparent bg-transparent text-slate-700 hover:border-slate-200 hover:bg-slate-50 dark:text-slate-200 dark:hover:border-slate-700 dark:hover:bg-slate-800/70'
                      }`}
                    >
                      <span
                        className={`shrink-0 rounded-full border border-slate-300 dark:border-slate-600 ${
                          navigatorCollapsed ? 'absolute right-2 top-2 h-2.5 w-2.5' : 'h-2.5 w-2.5'
                        }`}
                        style={{ backgroundColor: problem.balloonColor }}
                        aria-hidden="true"
                      />
                      <span
                        className={`inline-flex shrink-0 items-center justify-center rounded-md border font-mono text-xs font-bold ${
                          navigatorCollapsed ? 'h-8 w-8' : 'h-7 w-7'
                        } ${
                          active
                            ? 'border-blue-300 bg-white text-blue-800 dark:border-blue-700 dark:bg-slate-950 dark:text-blue-200'
                            : 'border-slate-200 bg-white text-slate-700 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-200'
                        }`}
                      >
                        {label}
                      </span>
                      {!navigatorCollapsed && (
                        <>
                          <span className="min-w-0 flex-1">
                            <span className="block truncate text-sm font-semibold">{problem.title}</span>
                          </span>
                          <StatusBadge kind="difficulty" value={problem.difficulty} />
                        </>
                      )}
                    </button>
                  );
                })}
              </div>
            )}
          </div>
        </aside>

        <Card className="min-w-0 flex-1 border border-gray-200 shadow-sm dark:border-slate-700">
          <CardHeader className="border-b border-slate-200 bg-slate-50 dark:border-slate-700 dark:bg-slate-900/70">
            <CardTitle className="text-lg text-slate-950 dark:text-slate-50">Problem Details</CardTitle>
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
                    <div className="min-w-0">
                      {selectedProblemLabel && (
                        <p className="text-xs font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                          Problem {selectedProblemLabel}
                        </p>
                      )}
                      <h3 className="truncate text-2xl font-semibold text-slate-950 dark:text-slate-50">{selectedProblem.title}</h3>
                    </div>
                    <div className="flex items-center gap-2">
                      <Button
                        size="sm"
                        variant="outline"
                        className="gap-2"
                        onClick={handleProblemPdfExport}
                        disabled={exportingPdf === `problem-${selectedProblem.id}`}
                      >
                        <Download className="w-4 h-4" />
                        {exportingPdf === `problem-${selectedProblem.id}` ? 'Exporting...' : 'Statement PDF'}
                      </Button>
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

                <div className="overflow-x-auto border-b border-slate-200">
                  <div className="flex min-w-max gap-1">
                    {PROBLEM_TABS.map((tab) => (
                      <button
                        key={tab.value}
                        type="button"
                        className={`border-b-2 px-3 py-2 text-sm font-semibold transition ${
                          activeProblemTab === tab.value
                            ? 'border-blue-700 text-blue-700'
                            : 'border-transparent text-slate-500 hover:border-slate-300 hover:text-slate-800'
                        }`}
                        onClick={() => setActiveProblemTab(tab.value)}
                      >
                        {tab.label}
                      </button>
                    ))}
                  </div>
                </div>

                {activeProblemTab === 'statement' && (
                  <div className="space-y-4">
                    {readinessWarnings.length > 0 && (
                      <div className="rounded-lg border border-amber-200 bg-amber-50 p-4">
                        <div className="flex gap-3">
                          <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-700" />
                          <div>
                            <p className="text-sm font-semibold text-amber-950">Readiness warnings</p>
                            <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-amber-800">
                              {readinessWarnings.map((warning) => (
                                <li key={warning}>{warning}</li>
                              ))}
                            </ul>
                          </div>
                        </div>
                      </div>
                    )}

                    <div>
                      <div className="mb-2 flex items-center justify-between gap-3">
                        <div className="flex items-center gap-2">
                          <h4 className="font-semibold text-slate-800">Contestant-Style Preview</h4>
                          <AdminHelpTooltip
                            label="Contestant preview help"
                            content="This preview uses public statement fields and public samples only. Admin notes and hidden tests stay out."
                          />
                        </div>
                        {isLoadingPublicSamples && (
                          <span className="text-xs font-medium text-slate-500">Loading public samples...</span>
                        )}
                      </div>
                      <ProblemStatementPreview problem={selectedProblem} samples={publicSamples} />
                    </div>

                    {selectedProblem.adminNotes && (
                      <details className="rounded-lg border border-amber-200 bg-amber-50">
                        <summary className="cursor-pointer px-4 py-3 text-sm font-semibold text-amber-950">
                          Admin Internal Notes
                        </summary>
                        <div className="border-t border-amber-200 p-4">
                          <p className="text-xs font-medium uppercase tracking-wide text-amber-700">
                            Hidden from team views and SAFE_MODE prompt exports
                          </p>
                          <pre className="mt-3 max-h-56 overflow-auto whitespace-pre-wrap rounded-md border border-amber-200 bg-white p-3 text-sm text-amber-950">{selectedProblem.adminNotes}</pre>
                        </div>
                      </details>
                    )}
                  </div>
                )}

                {activeProblemTab === 'test-cases' && (
                  <TestCasesPanel
                    problemId={selectedProblem.id}
                    problemTitle={selectedProblem.title}
                    embedded
                  />
                )}

                {activeProblemTab === 'prompt-exports' && selectedContestNumericId && (
                  <OraclePanel
                    problemId={selectedProblem.id}
                    problemTitle={selectedProblem.title}
                    contestId={selectedContestNumericId}
                    section="prompts"
                    embedded
                    customValidatorSourceHash={selectedProblem.validatorSourceHash}
                    customValidatorLanguageId={selectedProblem.validatorLanguageId}
                    customValidatorEnabled={selectedProblem.validatorEnabled}
                  />
                )}

                {activeProblemTab === 'engineering' && selectedContestNumericId && (
                  <OraclePanel
                    problemId={selectedProblem.id}
                    problemTitle={selectedProblem.title}
                    contestId={selectedContestNumericId}
                    section="engineering"
                    embedded
                    customValidatorSourceHash={selectedProblem.validatorSourceHash}
                    customValidatorLanguageId={selectedProblem.validatorLanguageId}
                    customValidatorEnabled={selectedProblem.validatorEnabled}
                  />
                )}

                {activeProblemTab === 'generated-tests' && selectedContestNumericId && (
                  <OraclePanel
                    problemId={selectedProblem.id}
                    problemTitle={selectedProblem.title}
                    contestId={selectedContestNumericId}
                    section="generated"
                    embedded
                  />
                )}

                {activeProblemTab === 'counterexamples' && selectedContestNumericId && (
                  <OraclePanel
                    problemId={selectedProblem.id}
                    problemTitle={selectedProblem.title}
                    contestId={selectedContestNumericId}
                    section="counterexamples"
                    embedded
                  />
                )}
              </div>
            )}
          </CardContent>
        </Card>
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
