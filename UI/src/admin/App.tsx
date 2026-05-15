import { useState, useEffect, useCallback } from "react";
import { Sidebar } from "./components/Sidebar";
import { TopNav } from "./components/TopNav";
import { AdminOverview } from "./components/AdminOverview";
import { ContestOverview } from "./components/ContestOverview";
import { ProblemsView } from "./components/ProblemsView";
import { TeamsView } from "./components/TeamsView";
import { SubmissionsView } from "./components/SubmissionsView";
import { ClarificationsView } from "./components/ClarificationsView";
import { ScoreboardView } from "./components/ScoreboardView";
import { ScoreboardRevealDisplay } from "./components/ScoreboardRevealDisplay";
import {
  getActiveContest,
  getEndedContests,
  getPausedContest,
  getUpcomingContest,
} from "./services/api";
import { ContestResponse, ContestStreamSnapshot, ContestStreamUpdate } from "./types/api";
import { Toaster } from "./components/ui/sonner";
import { RejudgeView } from "./components/RejudgeView";
import { useContestStream } from "../hooks/useContestStream";

const ADMIN_VIEW_ROUTES: Record<string, string> = {
  Overview: "/admin/overview",
  Contests: "/admin/contests",
  Teams: "/admin/teams",
  Problems: "/admin/problems",
  Submissions: "/admin/submissions",
  Clarifications: "/admin/clarifications",
  Scoreboard: "/admin/scoreboard",
  Rejudge: "/admin/rejudge",
  RevealDisplay: "/admin/scoreboard/reveal-display",
};

function selectAdminContest(snapshot: ContestStreamSnapshot): ContestResponse | null {
  return snapshot.active ?? snapshot.paused ?? snapshot.upcoming ?? snapshot.ended[0] ?? null;
}

function adminViewFromLocation(): string {
  const path = window.location.pathname.toLowerCase();
  if (path.startsWith("/admin/scoreboard/reveal-display")) return "RevealDisplay";

  const entry = Object.entries(ADMIN_VIEW_ROUTES).find(([, route]) => {
    if (route === "/admin/overview") return false;
    return path === route || path.startsWith(`${route}/`);
  });

  if (entry && entry[0] !== "RevealDisplay") return entry[0];
  return "Overview";
}

export default function AdminApp({ onLogout }: { onLogout: () => void }) {
  const [activeView, setActiveView] = useState(() => adminViewFromLocation());
  const [currentContest, setCurrentContest] =
    useState<ContestResponse | null>(null);

  const navigateToView = useCallback((view: string, options?: { contestId?: number | null }) => {
    const nextView = ADMIN_VIEW_ROUTES[view] ? view : "Overview";
    const path = ADMIN_VIEW_ROUTES[nextView] ?? ADMIN_VIEW_ROUTES.Overview;
    const params = new URLSearchParams();
    if (options?.contestId != null) {
      params.set("contestId", String(options.contestId));
    }
    const nextUrl = `${path}${params.toString() ? `?${params}` : ""}`;
    window.history.pushState({}, "", nextUrl);
    setActiveView(nextView);
  }, []);

  useEffect(() => {
    const handlePopState = () => setActiveView(adminViewFromLocation());
    window.addEventListener("popstate", handlePopState);
    return () => window.removeEventListener("popstate", handlePopState);
  }, []);

  const handleContestSnapshot = useCallback((snapshot: ContestStreamSnapshot) => {
    setCurrentContest(selectAdminContest(snapshot));
  }, []);

  const handleContestUpdate = useCallback((update: ContestStreamUpdate) => {
    setCurrentContest((prev) => {
      if (!prev || prev.id === update.snapshot.id) {
        return update.snapshot;
      }
      return prev;
    });
  }, []);

  useContestStream({
    onSnapshot: handleContestSnapshot,
    onContestUpdate: handleContestUpdate,
  });

  useEffect(() => {
    if (activeView === "Overview" || activeView === "RevealDisplay") return;

    let mounted = true;

    (async () => {
      try {
        const active = await getActiveContest();
        if (mounted) setCurrentContest(active);
      } catch {
        try {
          const paused = await getPausedContest();
          if (mounted) setCurrentContest(paused);
        } catch {
          try {
            const upcoming = await getUpcomingContest();
            if (mounted) setCurrentContest(upcoming);
          } catch {
            try {
              const ended = await getEndedContests();
              if (mounted) setCurrentContest(ended[0] ?? null);
            } catch {
              if (mounted) setCurrentContest(null);
            }
          }
        }
      }
    })();

    return () => {
      mounted = false;
    };
  }, [activeView]);

  const renderView = () => {
    switch (activeView) {
      case "Overview":
        return <AdminOverview onNavigate={navigateToView} />;

      case "Contests":
        return (
          <ContestOverview
            onOpenScoreboard={(contestId) =>
              navigateToView("Scoreboard", { contestId })
            }
          />
        );

      case "Problems":
        return <ProblemsView contestId={currentContest?.id ?? null} />;

      case "Teams":
        return <TeamsView />;

      case "Submissions":
        return <SubmissionsView />;

      case "Clarifications":
        return <ClarificationsView contestId={currentContest?.id ?? null} />;

      case "Scoreboard":
        return <ScoreboardView contestId={currentContest?.id ?? null} />;

      case "Rejudge":
        return <RejudgeView initialContestId={currentContest?.id ?? null} />;

      default:
        return null;
    }
  };

  if (activeView === "RevealDisplay") {
    return <ScoreboardRevealDisplay />;
  }

  return (
    <div className="aura-app-shell aura-admin-shell flex h-screen bg-[#F8FAFC] text-slate-900">
      <Sidebar activeView={activeView} setActiveView={navigateToView} />

      <div className="flex flex-1 flex-col overflow-hidden">
        <TopNav activeView={activeView} onLogout={onLogout} />

        <main className="aura-main flex-1 overflow-y-auto p-6 lg:p-8">
          <div className="mx-auto max-w-7xl">
            <div key={activeView} className="aura-view-transition">
              {renderView()}
            </div>
          </div>
        </main>
      </div>

      <Toaster />
    </div>
  );
}
