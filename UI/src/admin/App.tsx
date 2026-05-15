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
import { getActiveContest, getPausedContest, getUpcomingContest } from "./services/api";
import { ContestResponse, ContestStreamSnapshot, ContestStreamUpdate } from "./types/api";
import { Toaster } from "./components/ui/sonner";
import { RejudgeView } from "./components/RejudgeView";
import { useContestStream } from "../hooks/useContestStream";

function selectAdminContest(snapshot: ContestStreamSnapshot): ContestResponse | null {
  return snapshot.active ?? snapshot.paused ?? snapshot.upcoming ?? null;
}

export default function AdminApp({ onLogout }: { onLogout: () => void }) {
  const [activeView, setActiveView] = useState("Overview");
  const [currentContest, setCurrentContest] =
    useState<ContestResponse | null>(null);

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
    if (activeView === "Overview") return;

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
            if (mounted) setCurrentContest(null);
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
        return <AdminOverview onNavigate={setActiveView} />;

      case "Contests":
        return <ContestOverview />;

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

  return (
    <div className="aura-app-shell aura-admin-shell flex h-screen bg-[#F8FAFC] text-slate-900">
      <Sidebar activeView={activeView} setActiveView={setActiveView} onLogout={onLogout} />

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
