import React, { useEffect, useMemo, useState } from "react";
import { Header } from "./components/Header";
import { ProblemSidebar, ProblemStatus } from "./components/ProblemSidebar";
import { CodeEditor } from "./components/CodeEditor";
import { SubmissionHistory, Submission } from "./components/SubmissionHistory";
import { Clarifications } from "./components/Clarifications";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "./components/ui/tabs";
import {
  getActiveContest,
  getProblemsByContest,
  getMySubmissions,
  getMyAllSubmissions,
} from "./services/teamApi";

/* ============================================================
   SAFE HELPERS
============================================================ */

/** JWT sub = TEAM NAME (SAFE) */
function getTeamNameFromToken(): string {
  try {
    const token = localStorage.getItem("access_token");
    if (!token) return "Team";

    const [, payload] = token.split(".");
    if (!payload) return "Team";

    const data = JSON.parse(atob(payload));
    return typeof data.sub === "string" ? data.sub : "Team";
  } catch {
    return "Team";
  }
}

/** Backend verdict → UI verdict */
function mapVerdict(v: string): Submission["verdict"] {
  switch (v) {
    case "ACCEPTED":
      return "Accepted";
    case "WRONG_ANSWER":
      return "Wrong Answer";
    case "TLE":
      return "Time Limit Exceeded";
    case "COMPILATION_ERROR":
      return "Compilation Error";
    case "RUNTIME_ERROR":
      return "Runtime Error";
    case "INTERNAL_ERROR":
      return "System Error";
    case "RUNNING":
      return "Running";
    default:
      return "Pending";
  }
}

/* ============================================================
   TEAM APP
============================================================ */

export default function TeamApp({ onLogout }: { onLogout: () => void }) {
  const teamName = useMemo(getTeamNameFromToken, []);

  const [contest, setContest] = useState<any | null>(null);
  const [problems, setProblems] = useState<any[]>([]);
  const [selectedProblem, setSelectedProblem] = useState<any | null>(null);
  const [loaded, setLoaded] = useState(false);

  // Sidebar vs Editor submissions (DO NOT MIX)
  const [allSubmissions, setAllSubmissions] = useState<Submission[]>([]);
  const [submissions, setSubmissions] = useState<Submission[]>([]);
  const [loadingSubmissions, setLoadingSubmissions] = useState(false);

  /* ================= LOAD CONTEST + PROBLEMS ================= */

  useEffect(() => {
    let mounted = true;

    (async () => {
      try {
        const c = await getActiveContest();
        if (!mounted) return;

        setContest(c ?? null);

        if (!c) {
          setProblems([]);
          setSelectedProblem(null);
          setLoaded(true);
          return;
        }

        const p = await getProblemsByContest(c.id);
        if (!mounted) return;

        setProblems(p);
        setSelectedProblem(p[0] ?? null);
      } catch (e) {
        console.error("Contest load failed", e);
      } finally {
        if (mounted) setLoaded(true);
      }
    })();

    return () => {
      mounted = false;
    };
  }, []);

  /* ================= LOAD ALL SUBMISSIONS (SIDEBAR) ================= */

  useEffect(() => {
    if (!contest || problems.length === 0) return;

    let mounted = true;

    (async () => {
      try {
        const res = await getMyAllSubmissions();
        if (!mounted) return;

        setAllSubmissions(
          res.map(api => ({
            id: api.id,
            problem:
              problems.find(p => p.id === api.problemId)?.title ??
              `#${api.problemId}`,
            problemId: api.problemId,
            contestId: api.contestId,
            verdict: mapVerdict(api.verdict),
            language: api.language,
            time: new Date(api.createdAt).toLocaleTimeString(),
            executionTime: `${api.executionTime} ms`,
            code: api.code,
          }))
        );
      } catch (e) {
        console.warn("Sidebar submissions failed — ignored", e);
        if (mounted) setAllSubmissions([]);
      }
    })();

    return () => {
      mounted = false;
    };
  }, [contest?.id, problems.length]);

  /* ================= LOAD SUBMISSIONS (EDITOR) ================= */

  useEffect(() => {
    if (!selectedProblem) {
      setSubmissions([]);
      return;
    }

    let mounted = true;
    setLoadingSubmissions(true);

    (async () => {
      try {
        const res = await getMySubmissions(selectedProblem.id);
        if (!mounted) return;

        setSubmissions(
          res.map(api => ({
            id: api.id,
            problem:
              problems.find(p => p.id === api.problemId)?.title ??
              `#${api.problemId}`,
            problemId: api.problemId,
            contestId: api.contestId,
            verdict: mapVerdict(api.verdict),
            language: api.language,
            time: new Date(api.createdAt).toLocaleTimeString(),
            executionTime: `${api.executionTime} ms`,
            code: api.code,
          }))
        );
      } catch (e) {
        console.error("Editor submissions failed", e);
        if (mounted) setSubmissions([]);
      } finally {
        if (mounted) setLoadingSubmissions(false);
      }
    })();

    return () => {
      mounted = false;
    };
  }, [selectedProblem?.id, problems]);

  /* ================= COMPUTE SIDEBAR STATUS ================= */

  const problemsWithStatus = useMemo(() => {
    return problems.map(p => {
      const subs = allSubmissions.filter(s => s.problemId === p.id);

      let status: ProblemStatus = "unsolved";

      if (subs.length > 0) {
        if (subs.some(s => s.verdict === "Accepted")) {
          status = "solved";
        } else if (
          subs.every(
            s => s.verdict === "Pending" || s.verdict === "Running"
          )
        ) {
          status = "pending";
        } else {
          status = "wrong";
        }
      }

      return { ...p, status };
    });
  }, [problems, allSubmissions]);

  /* ================= TIMER ================= */

  const contestEndTime =
    contest?.startTime && contest?.durationMinutes
      ? new Date(
        new Date(contest.startTime).getTime() +
        contest.durationMinutes * 60_000
      ).toISOString()
      : undefined;

  if (!loaded) return <div className="p-6">Loading…</div>;

  /* ================= RENDER ================= */

  return (
    <div className="min-h-screen bg-gray-50 flex flex-col">
      <Header
        contestName={contest?.title}
        contestEndTime={contestEndTime}
        teamName={teamName}
        onLogout={onLogout}
      />

      <div className="flex flex-1 overflow-hidden">
        <ProblemSidebar
          problems={problemsWithStatus}
          selectedProblem={selectedProblem}
          onSelectProblem={setSelectedProblem}
        />

        <main className="flex-1 overflow-auto">
          <div className="p-6 space-y-6">
            <Tabs defaultValue="editor">
              <TabsList className="bg-gray-100 p-1 rounded-lg w-fit">
                <TabsTrigger
                  value="editor"
                  className="
      px-4 py-1.5 rounded-md text-sm font-medium
      text-gray-600
      data-[state=active]:bg-[#FACC15]
      data-[state=active]:text-gray-900
      data-[state=active]:shadow-sm
    "
                >
                  Code Editor
                </TabsTrigger>

                <TabsTrigger
                  value="clarifications"
                  className="
      px-4 py-1.5 rounded-md text-sm font-medium
      text-gray-600
      data-[state=active]:bg-[#FACC15]
      data-[state=active]:text-gray-900
      data-[state=active]:shadow-sm
    "
                >
                  Clarifications
                </TabsTrigger>
              </TabsList>


              <TabsContent value="editor" className="space-y-6">
                <CodeEditor
                  contestId={contest?.id}
                  problem={selectedProblem}
                />

                {loadingSubmissions ? (
                  <div className="text-gray-500">Loading submissions…</div>
                ) : (
                  <SubmissionHistory submissions={submissions} />
                )}
              </TabsContent>

              <TabsContent value="clarifications">
                <Clarifications />
              </TabsContent>
            </Tabs>
          </div>
        </main>
      </div>
    </div>
  );
}
