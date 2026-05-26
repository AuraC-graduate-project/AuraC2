import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { ChevronDown, ChevronUp, Code2, Columns3, FileText, Inbox, MessageSquare, RefreshCw, Trophy, type LucideIcon } from "lucide-react";
import type { ImperativePanelGroupHandle } from "react-resizable-panels";
import { Header } from "./components/Header";
import { ProblemSidebar, ProblemStatus } from "./components/ProblemSidebar";
import { CodeEditor } from "./components/CodeEditor";
import { SubmissionHistory, Submission } from "./components/SubmissionHistory";
import { Clarifications } from "./components/Clarifications";
import { ProblemStatementPanel } from "./components/ProblemStatementPanel";
import { Scoreboard } from "./components/Scoreboard";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "./components/ui/tabs";
import { Button } from "./components/ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "./components/ui/dialog";
import {
  ResizableHandle,
  ResizablePanel,
  ResizablePanelGroup,
} from "./components/ui/resizable";
import { ContestResponse, ProblemResponse, SubmissionResponse, TestCaseResponse } from "../admin/types/api";
import { normalizeVerdict } from "../components/StatusBadge";
import {
  createCustomTest,
  deleteCustomTest,
  getCustomTests,
  getProblemsByContest,
  getPublicTestCasesForProblem,
  getMySubmissions,
  getMyAllSubmissions,
  updateCustomTest,
  type CustomTestCaseResponse,
  type RunResponse,
} from "./services/teamApi";
import { useSubmissionStream } from "../hooks/useSubmissionStream";
import { CODE_DRAFT_FLUSH_EVENT } from "../hooks/useCodeDraft";

type WorkspaceMode = "balanced" | "problem" | "code";
type WorkspacePage = "solve" | "scoreboard";
type ProblemPanelSection = "statement" | "samples";
type MobileWorkspaceTab = "problem" | "code" | "submissions";

const workspaceModes: Array<{
  value: WorkspaceMode;
  label: string;
  icon: LucideIcon;
}> = [
  { value: "balanced", label: "Balanced View", icon: Columns3 },
  { value: "problem", label: "Focus Problem", icon: FileText },
  { value: "code", label: "Focus Code", icon: Code2 },
];

const panelSizes: Record<
  WorkspaceMode,
  { statement: number; editor: number }
> = {
  balanced: { statement: 45, editor: 55 },
  problem: { statement: 62, editor: 38 },
  code: { statement: 32, editor: 68 },
};

function formatMetric(value: number | null | undefined, unit: string): string {
  return value == null ? "-" : `${value} ${unit}`;
}

function toSubmission(api: SubmissionResponse, problems: ProblemResponse[]): Submission {
  const verdict = normalizeVerdict(api.verdict);

  return {
    id: api.id,
    problem:
      problems.find((p) => p.id === api.problemId)?.title ??
      `#${api.problemId}`,
    problemId: api.problemId,
    contestId: api.contestId,
    verdict: verdict === "UNKNOWN" ? "PENDING" : verdict,
    language: api.language,
    time: new Date(api.createdAt).toLocaleTimeString(),
    executionTime: formatMetric(api.executionTime, "ms"),
    memoryUsage: formatMetric(api.memoryUsage, "MB"),
    code: api.code,
  };
}

function getSelectedProblemKey(contestId: number): string {
  return `team_workspace_selected_problem_${contestId}`;
}

function workspacePageFromUrl(): WorkspacePage {
  try {
    return new URLSearchParams(window.location.search).get("view") === "scoreboard"
      ? "scoreboard"
      : "solve";
  } catch {
    return "solve";
  }
}

function syncWorkspacePageToUrl(page: WorkspacePage, replace = false) {
  try {
    const params = new URLSearchParams(window.location.search);
    params.set("view", page);
    const nextUrl = `/team/workspace?${params.toString()}`;
    if (replace) {
      window.history.replaceState({}, "", nextUrl);
    } else {
      window.history.pushState({}, "", nextUrl);
    }
  } catch {
    // URL persistence is a convenience; the in-memory workspace still works.
  }
}

type Props = {
  contest: ContestResponse;
  teamName: string;
  onLogout: () => void;
};

export default function TeamWorkspace({ contest, teamName, onLogout }: Props) {
  const panelGroupRef = useRef<ImperativePanelGroupHandle | null>(null);
  const [workspacePage, setWorkspacePage] = useState<WorkspacePage>(() => workspacePageFromUrl());
  const [workspaceMode, setWorkspaceMode] = useState<WorkspaceMode>("balanced");
  const [submissionsOpen, setSubmissionsOpen] = useState(false);
  const [clarificationsOpen, setClarificationsOpen] = useState(false);
  const [problems, setProblems] = useState<ProblemResponse[]>([]);
  const [selectedProblem, setSelectedProblem] = useState<ProblemResponse | null>(null);
  const [loadingProblems, setLoadingProblems] = useState(true);
  const [problemError, setProblemError] = useState<string | null>(null);
  const [problemPanelSection, setProblemPanelSection] = useState<ProblemPanelSection>("statement");
  const [mobileTab, setMobileTab] = useState<MobileWorkspaceTab>("problem");
  const [publicSamples, setPublicSamples] = useState<TestCaseResponse[]>([]);
  const [customTests, setCustomTests] = useState<CustomTestCaseResponse[]>([]);
  const [loadingTestCases, setLoadingTestCases] = useState(false);
  const [testCaseError, setTestCaseError] = useState<string | null>(null);
  const [runResponse, setRunResponse] = useState<RunResponse | null>(null);
  const [runError, setRunError] = useState<string | null>(null);
  const [runInFlight, setRunInFlight] = useState(false);
  const [savingCustomTest, setSavingCustomTest] = useState(false);
  const [updatingCustomTestId, setUpdatingCustomTestId] = useState<number | null>(null);
  const [deletingCustomTestId, setDeletingCustomTestId] = useState<number | null>(null);

  const [allSubmissions, setAllSubmissions] = useState<Submission[]>([]);
  const [submissions, setSubmissions] = useState<Submission[]>([]);
  const [loadingSubmissions, setLoadingSubmissions] = useState(false);
  const [submissionRefreshKey, setSubmissionRefreshKey] = useState(0);

  // Live submission updates via SSE — any verdict change triggers a safe refetch.
  useSubmissionStream({
    role: "TEAM",
    enabled: true,
    onEvent: () => {
      setSubmissionRefreshKey((k) => k + 1);
    },
  });

  useEffect(() => {
    syncWorkspacePageToUrl(workspacePage, true);

    const handlePopState = () => setWorkspacePage(workspacePageFromUrl());
    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, []);

  const navigateWorkspacePage = useCallback((page: WorkspacePage) => {
    window.dispatchEvent(new Event(CODE_DRAFT_FLUSH_EVENT));
    syncWorkspacePageToUrl(page);
    setWorkspacePage(page);
  }, []);

  const loadProblems = useCallback(async () => {
    setLoadingProblems(true);
    setProblemError(null);
    try {
      const list = await getProblemsByContest(contest.id);
      const savedProblemId = Number(localStorage.getItem(getSelectedProblemKey(contest.id)));
      setProblems(list);
      setSelectedProblem((prev) => {
        if (!prev) {
          return list.find((problem) => problem.id === savedProblemId) ?? list[0] ?? null;
        }
        return list.find((problem) => problem.id === prev.id) ?? list[0] ?? null;
      });
    } catch (error) {
      setProblems([]);
      setSelectedProblem(null);
      setProblemError(error instanceof Error ? error.message : "Failed to load problems.");
    } finally {
      setLoadingProblems(false);
    }
  }, [contest.id]);

  useEffect(() => {
    loadProblems();
  }, [loadProblems]);

  useEffect(() => {
    if (!selectedProblem) return;

    try {
      localStorage.setItem(getSelectedProblemKey(contest.id), String(selectedProblem.id));
    } catch {
      // Selection persistence is a convenience; drafts remain independently keyed.
    }
  }, [contest.id, selectedProblem?.id]);

  useEffect(() => {
    let mounted = true;

    setRunResponse(null);
    setRunError(null);
    setTestCaseError(null);
    setPublicSamples([]);
    setCustomTests([]);
    setProblemPanelSection("statement");

    if (!selectedProblem) {
      setLoadingTestCases(false);
      return;
    }

    setLoadingTestCases(true);
    Promise.all([
      getPublicTestCasesForProblem(selectedProblem.id),
      getCustomTests(selectedProblem.id),
    ])
      .then(([samples, tests]) => {
        if (!mounted) return;
        setPublicSamples(samples);
        setCustomTests(tests);
      })
      .catch((error) => {
        if (!mounted) return;
        setPublicSamples([]);
        setCustomTests([]);
        setTestCaseError(error instanceof Error ? error.message : "Could not load test cases.");
      })
      .finally(() => {
        if (mounted) setLoadingTestCases(false);
      });

    return () => {
      mounted = false;
    };
  }, [selectedProblem?.id]);

  useEffect(() => {
    if (problems.length === 0) {
      setAllSubmissions([]);
      return;
    }

    let mounted = true;

    (async () => {
      try {
        const res = await getMyAllSubmissions();
        if (!mounted) return;
        setAllSubmissions(res.map((api) => toSubmission(api, problems)));
      } catch {
        if (mounted) setAllSubmissions([]);
      }
    })();

    return () => {
      mounted = false;
    };
  }, [contest.id, problems, submissionRefreshKey]);

  useEffect(() => {
    if (!selectedProblem) {
      setSubmissions([]);
      setLoadingSubmissions(false);
      return;
    }

    let mounted = true;
    setLoadingSubmissions(true);

    (async () => {
      try {
        const res = await getMySubmissions(selectedProblem.id);
        if (!mounted) return;
        setSubmissions(res.map((api) => toSubmission(api, problems)));
      } catch {
        if (mounted) setSubmissions([]);
      } finally {
        if (mounted) setLoadingSubmissions(false);
      }
    })();

    return () => {
      mounted = false;
    };
  }, [selectedProblem?.id, problems, submissionRefreshKey]);

  const problemsWithStatus = useMemo(() => {
    return problems.map((problem) => {
      const subs = allSubmissions.filter((submission) => submission.problemId === problem.id);
      let status: ProblemStatus = "unsolved";

      if (subs.length > 0) {
        if (subs.some((submission) => submission.verdict === "ACCEPTED")) {
          status = "solved";
        } else if (
          subs.every(
            (submission) => submission.verdict === "PENDING" || submission.verdict === "RUNNING"
          )
        ) {
          status = "pending";
        } else {
          status = "wrong";
        }
      }

      return { ...problem, status };
    });
  }, [problems, allSubmissions]);

  const selectedIndex = selectedProblem
    ? problems.findIndex((problem) => problem.id === selectedProblem.id)
    : -1;
  const selectedLabel = selectedIndex >= 0 ? `Problem ${String.fromCharCode(65 + selectedIndex)}` : "No problem selected";
  const contestEndTime = contest.effectiveEndTime ?? contest.endTime ?? undefined;
  const sizes = panelSizes[workspaceMode];
  const submissionCount = submissions.length;
  const isScoreboardPage = workspacePage === "scoreboard";

  const handleSubmitted = useCallback(() => {
    setSubmissionsOpen(true);
    setSubmissionRefreshKey((value) => value + 1);
  }, []);

  const customTestIds = useMemo(() => customTests.map((test) => test.id), [customTests]);

  const handleAddCustomTest = useCallback(async (body: { input: string; expectedOutput: string | null }) => {
    if (!selectedProblem) return;
    setSavingCustomTest(true);
    setTestCaseError(null);
    try {
      const saved = await createCustomTest(selectedProblem.id, body);
      setCustomTests((current) => [...current, saved]);
      setRunResponse(null);
    } finally {
      setSavingCustomTest(false);
    }
  }, [selectedProblem?.id]);

  const handleUpdateCustomTest = useCallback(async (
    customTestId: number,
    body: { input: string; expectedOutput: string | null }
  ) => {
    if (!selectedProblem) return;
    setUpdatingCustomTestId(customTestId);
    setTestCaseError(null);
    try {
      const updated = await updateCustomTest(selectedProblem.id, customTestId, body);
      setCustomTests((current) => current.map((test) => test.id === customTestId ? updated : test));
      setRunResponse(null);
    } finally {
      setUpdatingCustomTestId(null);
    }
  }, [selectedProblem?.id]);

  const handleDeleteCustomTest = useCallback(async (customTestId: number) => {
    if (!selectedProblem) return;
    setDeletingCustomTestId(customTestId);
    setTestCaseError(null);
    try {
      await deleteCustomTest(selectedProblem.id, customTestId);
      setCustomTests((current) => current.filter((test) => test.id !== customTestId));
      setRunResponse(null);
    } finally {
      setDeletingCustomTestId(null);
    }
  }, [selectedProblem?.id]);

  const handleRunStarted = useCallback(() => {
    setRunInFlight(true);
    setRunResponse(null);
    setRunError(null);
    setProblemPanelSection("samples");
    setMobileTab("problem");
  }, []);

  const handleRunCompleted = useCallback((response: RunResponse) => {
    setRunResponse(response);
    setRunError(null);
    setRunInFlight(false);
    setProblemPanelSection("samples");
    setMobileTab("problem");
  }, []);

  const handleRunFailed = useCallback((message: string) => {
    setRunError(message);
    setRunInFlight(false);
    setProblemPanelSection("samples");
    setMobileTab("problem");
  }, []);

  const handleWorkspaceModeChange = useCallback((mode: WorkspaceMode) => {
    setWorkspaceMode(mode);
    const nextSizes = panelSizes[mode];
    panelGroupRef.current?.setLayout([nextSizes.statement, nextSizes.editor]);
  }, []);

  const modeControl = (
    <div className="grid w-full grid-cols-3 rounded-lg border border-slate-200 bg-slate-100 p-0.5 lg:w-auto lg:min-w-[360px]">
      {workspaceModes.map((mode) => {
        const Icon = mode.icon;
        const active = workspaceMode === mode.value;

        return (
          <button
            key={mode.value}
            type="button"
            onClick={() => handleWorkspaceModeChange(mode.value)}
            aria-pressed={active}
            className={`inline-flex h-8 items-center justify-center gap-1.5 rounded-md px-2 text-xs font-semibold transition ${
              active
                ? "bg-white text-slate-950 shadow-sm"
                : "text-slate-600 hover:bg-white/70 hover:text-slate-900"
            }`}
          >
            <Icon className="h-4 w-4" />
            <span className="hidden sm:inline">{mode.label}</span>
            <span className="sm:hidden">{mode.label.replace(" View", "").replace("Focus ", "")}</span>
          </button>
        );
      })}
    </div>
  );

  return (
    <div className="aura-team-workspace flex h-screen min-h-screen flex-col overflow-hidden bg-gray-50">
      <Header
        contestName={contest.title}
        contestStatus={contest.effectiveState ?? contest.status}
        contestEndTime={contestEndTime}
        teamName={teamName}
        onLogout={onLogout}
      />

      <section className="aura-workspace-toolbar border-b border-slate-200 bg-white px-3 py-2 lg:px-4">
        <div className="flex flex-col gap-2 xl:flex-row xl:items-center xl:justify-between">
          <div className="min-w-0">
            <p className="text-xs font-semibold uppercase text-blue-700">
              {isScoreboardPage ? "Contest standings" : "Solving workspace"}
            </p>
            <div className="mt-0.5 flex flex-wrap items-center gap-2">
              <h2 className="truncate text-base font-semibold text-slate-950">
                {isScoreboardPage
                  ? "Scoreboard"
                  : selectedProblem
                    ? selectedProblem.title
                    : "Choose a problem"}
              </h2>
              <span className="rounded-full border border-slate-200 bg-slate-50 px-2 py-0.5 text-xs font-semibold text-slate-600">
                {isScoreboardPage ? contest.title : selectedLabel}
              </span>
            </div>
          </div>

          <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
            {isScoreboardPage ? (
              <Button
                type="button"
                variant="outline"
                className="h-9 gap-2 bg-white px-3"
                onClick={() => navigateWorkspacePage("solve")}
              >
                <FileText className="h-4 w-4" />
                Workspace
              </Button>
            ) : (
              <>
                {modeControl}
                <Button
                  type="button"
                  variant="outline"
                  className="h-9 gap-2 bg-white px-3"
                  onClick={() => navigateWorkspacePage("scoreboard")}
                >
                  <Trophy className="h-4 w-4" />
                  Scoreboard
                </Button>
              </>
            )}
            <Button
              type="button"
              variant="outline"
              className="h-9 gap-2 bg-white px-3"
              onClick={() => setClarificationsOpen(true)}
            >
              <MessageSquare className="h-4 w-4" />
              Clarifications
            </Button>
          </div>
        </div>
      </section>

      {isScoreboardPage ? (
        <main className="aura-workspace-main min-h-0 flex-1 overflow-y-auto p-4 lg:p-5">
          <Scoreboard contestId={contest.id} fullPage />
        </main>
      ) : (
        <>
          <div className="hidden min-h-0 flex-1 md:flex">
            <ProblemSidebar
              problems={problemsWithStatus}
              selectedProblem={selectedProblem}
              onSelectProblem={setSelectedProblem}
              isLoading={loadingProblems}
              error={problemError}
              onRetry={loadProblems}
              className="h-full w-[280px]"
            />

            <main className="aura-workspace-main min-w-0 flex-1 p-4">
              <ResizablePanelGroup ref={panelGroupRef} direction="horizontal" className="h-full gap-0 rounded-lg bg-white">
                <ResizablePanel defaultSize={sizes.statement} minSize={30} className="min-h-0">
                  <ProblemStatementPanel
                    problem={selectedProblem}
                    className="aura-statement-flush h-full rounded-none border-0 shadow-none"
                    activeSection={problemPanelSection}
                    onActiveSectionChange={setProblemPanelSection}
                    samples={publicSamples}
                    customTests={customTests}
                    loadingTestCases={loadingTestCases}
                    runResponse={runResponse}
                    runError={runError ?? testCaseError}
                    isRunning={runInFlight}
                    savingCustomTest={savingCustomTest}
                    updatingCustomTestId={updatingCustomTestId}
                    deletingCustomTestId={deletingCustomTestId}
                    onAddCustomTest={handleAddCustomTest}
                    onUpdateCustomTest={handleUpdateCustomTest}
                    onDeleteCustomTest={handleDeleteCustomTest}
                  />
                </ResizablePanel>

                <ResizableHandle className="aura-subtle-resize-handle w-px bg-slate-200" />

                <ResizablePanel defaultSize={sizes.editor} minSize={36} className="min-h-0">
                  <div className="flex h-full min-h-0 flex-col overflow-hidden">
                    <CodeEditor
                      contestId={contest.id}
                      problem={selectedProblem}
                      customTestCaseIds={customTestIds}
                      onSubmitted={handleSubmitted}
                      onRunStarted={handleRunStarted}
                      onRunCompleted={handleRunCompleted}
                      onRunFailed={handleRunFailed}
                    />

                    <section className="aura-submissions-panel shrink-0 border-t border-slate-200 bg-white">
                      <button
                        type="button"
                        onClick={() => setSubmissionsOpen((open) => !open)}
                        aria-expanded={submissionsOpen}
                        className="flex w-full items-center justify-between gap-3 px-4 py-2.5 text-left transition hover:bg-slate-50"
                      >
                        <span className="inline-flex items-center gap-2 text-sm font-semibold text-slate-900">
                          <Inbox className="h-4 w-4 text-blue-700" />
                          Official Submissions
                        </span>
                        <span className="inline-flex items-center gap-2 text-xs font-semibold text-slate-500">
                          {submissionCount} total
                          {submissionsOpen ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
                        </span>
                      </button>

                      {submissionsOpen && (
                        <div className="max-h-[210px] overflow-auto px-3 pb-3">
                          <SubmissionHistory
                            submissions={submissions}
                            isLoading={loadingSubmissions}
                            title="Official Submissions"
                            className="border-0 shadow-none"
                            compact
                          />
                        </div>
                      )}
                    </section>
                  </div>
                </ResizablePanel>
              </ResizablePanelGroup>
            </main>
          </div>

          <div className="min-h-0 flex-1 overflow-y-auto p-4 md:hidden">
            <div className="space-y-4">
              <ProblemSidebar
                problems={problemsWithStatus}
                selectedProblem={selectedProblem}
                onSelectProblem={setSelectedProblem}
                isLoading={loadingProblems}
                error={problemError}
                onRetry={loadProblems}
                className="h-[340px] rounded-lg border border-slate-200"
              />

              <Tabs value={mobileTab} onValueChange={(value) => setMobileTab(value as MobileWorkspaceTab)} className="space-y-4">
                <TabsList className="grid w-full grid-cols-3 rounded-lg bg-slate-100 p-1">
                  <TabsTrigger value="problem">Problem</TabsTrigger>
                  <TabsTrigger value="code">Code</TabsTrigger>
                  <TabsTrigger value="submissions">Submissions</TabsTrigger>
                </TabsList>

                <TabsContent value="problem" className="m-0">
                  <ProblemStatementPanel
                    problem={selectedProblem}
                    activeSection={problemPanelSection}
                    onActiveSectionChange={setProblemPanelSection}
                    samples={publicSamples}
                    customTests={customTests}
                    loadingTestCases={loadingTestCases}
                    runResponse={runResponse}
                    runError={runError ?? testCaseError}
                    isRunning={runInFlight}
                    savingCustomTest={savingCustomTest}
                    updatingCustomTestId={updatingCustomTestId}
                    deletingCustomTestId={deletingCustomTestId}
                    onAddCustomTest={handleAddCustomTest}
                    onUpdateCustomTest={handleUpdateCustomTest}
                    onDeleteCustomTest={handleDeleteCustomTest}
                  />
                </TabsContent>

                <TabsContent value="code" className="m-0">
                  <CodeEditor
                    contestId={contest.id}
                    problem={selectedProblem}
                    customTestCaseIds={customTestIds}
                    onSubmitted={handleSubmitted}
                    onRunStarted={handleRunStarted}
                    onRunCompleted={handleRunCompleted}
                    onRunFailed={handleRunFailed}
                  />
                </TabsContent>

                <TabsContent value="submissions" className="m-0">
                  <SubmissionHistory
                    submissions={submissions}
                    isLoading={loadingSubmissions}
                    title="Official Submissions"
                  />
                </TabsContent>

              </Tabs>

              {problemError && (
                <Button variant="outline" className="w-full gap-2 bg-white" onClick={loadProblems}>
                  <RefreshCw className="h-4 w-4" />
                  Retry Loading Problems
                </Button>
              )}
            </div>
          </div>
        </>
      )}

      <Dialog open={clarificationsOpen} onOpenChange={setClarificationsOpen}>
        <DialogContent className="max-h-[88vh] overflow-hidden p-0 sm:max-w-3xl lg:max-w-4xl">
          <DialogHeader className="border-b border-slate-200 p-5 pr-12">
            <DialogTitle className="flex items-center gap-2 text-slate-950">
              <MessageSquare className="h-5 w-5 text-blue-700" />
              Clarifications
            </DialogTitle>
          </DialogHeader>
          <div className="max-h-[calc(88vh-5rem)] overflow-y-auto p-4">
            <Clarifications contestId={contest.id} problems={problems} />
          </div>
        </DialogContent>
      </Dialog>

    </div>
  );
}
