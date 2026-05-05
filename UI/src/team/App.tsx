import { useCallback, useEffect, useMemo, useState } from "react";
import { Header } from "./components/Header";
import { ProblemSidebar, ProblemStatus } from "./components/ProblemSidebar";
import { CodeEditor } from "./components/CodeEditor";
import { SubmissionHistory, Submission } from "./components/SubmissionHistory";
import { Clarifications } from "./components/Clarifications";
import { ProblemStatementPanel } from "./components/ProblemStatementPanel";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "./components/ui/tabs";
import { decodeJwtSubject } from "../auth/jwt";
import { ContestResponse, ProblemResponse, SubmissionResponse, Verdict } from "../admin/types/api";
import {
  getActiveContest,
  getProblemsByContest,
  getMySubmissions,
  getMyAllSubmissions,
} from "./services/teamApi";

function getTeamNameFromToken(): string {
  try {
    const token = localStorage.getItem("access_token");
    if (!token) return "Team";
    return decodeJwtSubject(token) ?? "Team";
  } catch {
    return "Team";
  }
}

function normalizeVerdict(value: string | null | undefined): Verdict {
  switch (value) {
    case "ACCEPTED":
    case "WRONG_ANSWER":
    case "TLE":
    case "COMPILATION_ERROR":
    case "RUNTIME_ERROR":
    case "INTERNAL_ERROR":
    case "PENDING":
    case "RUNNING":
      return value;
    case "TIME_LIMIT_EXCEEDED":
      return "TLE";
    default:
      return "PENDING";
  }
}

function formatSubmission(
  api: SubmissionResponse,
  problems: ProblemResponse[]
): Submission {
  const created = api.createdAt ? new Date(api.createdAt) : null;
  const problem = problems.find((item) => item.id === api.problemId);
  return {
    id: api.id,
    problem: problem?.title ?? `#${api.problemId}`,
    problemId: api.problemId,
    contestId: api.contestId,
    verdict: normalizeVerdict(String(api.verdict)),
    language: api.language,
    time: created && !Number.isNaN(created.getTime()) ? created.toLocaleString() : String(api.createdAt ?? "-"),
    executionTime: api.executionTime == null ? "-" : `${api.executionTime} ms`,
    memoryUsage: api.memoryUsage == null ? "-" : `${api.memoryUsage} MB`,
    code: api.code,
  };
}

export default function TeamApp({ onLogout }: { onLogout: () => void }) {
  const teamName = useMemo(getTeamNameFromToken, []);

  const [contest, setContest] = useState<ContestResponse | null>(null);
  const [problems, setProblems] = useState<ProblemResponse[]>([]);
  const [selectedProblem, setSelectedProblem] = useState<ProblemResponse | null>(null);
  const [loaded, setLoaded] = useState(false);
  const [allSubmissions, setAllSubmissions] = useState<Submission[]>([]);
  const [submissions, setSubmissions] = useState<Submission[]>([]);
  const [loadingSubmissions, setLoadingSubmissions] = useState(false);
  const [codeExpanded, setCodeExpanded] = useState(false);

  useEffect(() => {
    let mounted = true;

    (async () => {
      try {
        const activeContest = await getActiveContest();
        if (!mounted) return;

        setContest(activeContest ?? null);

        if (!activeContest) {
          setProblems([]);
          setSelectedProblem(null);
          return;
        }

        const contestProblems = await getProblemsByContest(activeContest.id);
        if (!mounted) return;

        setProblems(contestProblems);
        setSelectedProblem(contestProblems[0] ?? null);
      } catch (error) {
        console.error("Contest load failed", error);
        if (mounted) {
          setContest(null);
          setProblems([]);
          setSelectedProblem(null);
        }
      } finally {
        if (mounted) setLoaded(true);
      }
    })();

    return () => {
      mounted = false;
    };
  }, []);

  const loadAllSubmissions = useCallback(async () => {
    if (!contest) {
      setAllSubmissions([]);
      return;
    }

    try {
      const response = await getMyAllSubmissions();
      setAllSubmissions(response.map((item) => formatSubmission(item, problems)));
    } catch (error) {
      console.warn("All submissions failed", error);
      setAllSubmissions([]);
    }
  }, [contest, problems]);

  const loadProblemSubmissions = useCallback(async () => {
    if (!selectedProblem) {
      setSubmissions([]);
      return;
    }

    setLoadingSubmissions(true);
    try {
      const response = await getMySubmissions(selectedProblem.id);
      setSubmissions(response.map((item) => formatSubmission(item, problems)));
    } catch (error) {
      console.error("Problem submissions failed", error);
      setSubmissions([]);
    } finally {
      setLoadingSubmissions(false);
    }
  }, [selectedProblem, problems]);

  useEffect(() => {
    if (!contest || problems.length === 0) return;
    loadAllSubmissions();
  }, [contest, problems.length, loadAllSubmissions]);

  useEffect(() => {
    loadProblemSubmissions();
  }, [loadProblemSubmissions]);

  const handleSubmitted = async () => {
    await Promise.all([loadAllSubmissions(), loadProblemSubmissions()]);
  };

  const problemsWithStatus = useMemo(() => {
    return problems.map((problem) => {
      const problemSubmissions = allSubmissions.filter((submission) => submission.problemId === problem.id);
      let status: ProblemStatus = "unsolved";

      if (problemSubmissions.length > 0) {
        if (problemSubmissions.some((submission) => submission.verdict === "ACCEPTED")) {
          status = "solved";
        } else if (problemSubmissions.every((submission) => submission.verdict === "PENDING" || submission.verdict === "RUNNING")) {
          status = "pending";
        } else {
          status = "wrong";
        }
      }

      return { ...problem, status };
    });
  }, [problems, allSubmissions]);

  const contestEndTime =
    contest?.startTime && contest?.durationMinutes
      ? new Date(new Date(contest.startTime).getTime() + contest.durationMinutes * 60_000).toISOString()
      : undefined;

  if (!loaded) {
    return (
      <div className="aura-app-shell flex min-h-screen items-center justify-center bg-[#F8FAFC] text-slate-600">
        Loading AuraC² workspace...
      </div>
    );
  }

  return (
    <div className="aura-app-shell aura-team-shell flex min-h-screen flex-col bg-[#F8FAFC] text-slate-900">
      <Header
        contestName={contest?.title}
        contestStatus={contest?.status}
        contestEndTime={contestEndTime}
        teamName={teamName}
        onLogout={onLogout}
      />

      <div className="flex min-h-0 flex-1 overflow-hidden">
        <ProblemSidebar
          problems={problemsWithStatus}
          selectedProblem={selectedProblem ? { ...selectedProblem, status: problemsWithStatus.find((p) => p.id === selectedProblem.id)?.status ?? "unsolved" } : null}
          onSelectProblem={setSelectedProblem}
        />

        <main className="aura-main flex-1 overflow-auto">
          {!contest ? (
            <div className="m-6 rounded-lg border border-dashed border-slate-300 bg-white p-10 text-center shadow-sm">
              <p className="font-medium text-slate-800">No active contest is available.</p>
              <p className="mt-1 text-sm text-slate-500">Your workspace will unlock when the administrator starts a contest.</p>
            </div>
          ) : (
            <div className="aura-view-transition p-5">
              <Tabs defaultValue="code" className="space-y-5">
                <TabsList className="aura-tabs rounded-lg border border-slate-200 bg-white p-1 shadow-sm">
                  <TabsTrigger value="code" className="rounded-md px-4 py-2 data-[state=active]:bg-blue-700 data-[state=active]:text-white">
                    Problems & Code
                  </TabsTrigger>
                  <TabsTrigger value="submissions" className="rounded-md px-4 py-2 data-[state=active]:bg-blue-700 data-[state=active]:text-white">
                    Submissions
                  </TabsTrigger>
                  <TabsTrigger value="clarifications" className="rounded-md px-4 py-2 data-[state=active]:bg-blue-700 data-[state=active]:text-white">
                    Clarifications
                  </TabsTrigger>
                </TabsList>

                <TabsContent value="code" className="space-y-5">
                  <div className={`aura-workspace-grid grid gap-5 ${codeExpanded ? "aura-workspace-grid-wide" : ""}`}>
                    <ProblemStatementPanel problem={selectedProblem} />
                    <CodeEditor
                      contestId={contest?.id}
                      problem={selectedProblem}
                      onSubmitted={handleSubmitted}
                      isExpanded={codeExpanded}
                      onToggleExpanded={() => setCodeExpanded((value) => !value)}
                    />
                  </div>

                  {loadingSubmissions ? (
                    <div className="rounded-lg border border-slate-200 bg-white p-5 text-slate-500 shadow-sm">
                      Loading submissions...
                    </div>
                  ) : (
                    <SubmissionHistory submissions={submissions} title="Selected Problem Submissions" />
                  )}
                </TabsContent>

                <TabsContent value="submissions">
                  <SubmissionHistory submissions={allSubmissions} title="All Contest Submissions" />
                </TabsContent>

                <TabsContent value="clarifications">
                  <Clarifications
                    contestId={contest?.id ?? null}
                    problems={problems.map((problem) => ({
                      id: Number(problem.id),
                      title: String(problem.title ?? `#${problem.id}`),
                    }))}
                  />
                </TabsContent>
              </Tabs>
            </div>
          )}
        </main>
      </div>
    </div>
  );
}
