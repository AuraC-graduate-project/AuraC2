import { useState, useEffect } from "react";
import { Sidebar } from "./components/Sidebar";
import { TopNav } from "./components/TopNav";
import { ContestOverview } from "./components/ContestOverview";
import { StatsPanel } from "./components/StatsPanel";
import { ProblemsView } from "./components/ProblemsView";
import { TeamsView } from "./components/TeamsView";
import { SubmissionsView } from "./components/SubmissionsView";
import { PlaceholderView } from "./components/PlaceholderView";
import { getActiveContest, getUpcomingContest } from "./services/api";
import { ContestResponse } from "./types/api";
import { Toaster } from "./components/ui/sonner";

export default function AdminApp({ onLogout }: { onLogout: () => void }) {
  const [activeView, setActiveView] = useState("Overview");
  const [currentContest, setCurrentContest] =
    useState<ContestResponse | null>(null);

  useEffect(() => {
    if (activeView === "Overview") return;

    (async () => {
      try {
        const active = await getActiveContest();
        setCurrentContest(active);
      } catch {
        try {
          const upcoming = await getUpcomingContest();
          setCurrentContest(upcoming);
        } catch {
          setCurrentContest(null);
        }
      }
    })();
  }, [activeView]);

  const renderView = () => {
    switch (activeView) {
      case "Overview":
        return (
          <>
            <h1 className="mb-6 text-slate-800">
              Contest Management
            </h1>

            <ContestOverview />

            <div className="mt-8">
              <h2 className="mb-4 text-slate-700">
                Quick Statistics
              </h2>
              <StatsPanel />
            </div>
          </>
        );

      case "Problems":
        return (
          <>
            <h1 className="mb-6 text-slate-800">
              Problems Management
            </h1>
            <ProblemsView contestId={currentContest?.id ?? null} />
          </>
        );

      case "Teams":
        return (
          <>
            <h1 className="mb-6 text-slate-800">
              Teams
            </h1>
            <TeamsView />
          </>
        );

      case "Submissions":
        return (
          <>
            <h1 className="mb-6 text-slate-800">
              Submissions
            </h1>
            <SubmissionsView />
          </>
        );

      case "Clarifications":
        return (
          <>
            <h1 className="mb-6 text-slate-800">
              Clarifications
            </h1>
            <PlaceholderView
              title="Clarifications"
              message="Coming soon. This page will be wired once clarification endpoints are available."
            />
          </>
        );

      case "Security Monitor":
        return (
          <>
            <h1 className="mb-6 text-slate-800">
              Security Monitor
            </h1>
            <PlaceholderView
              title="Security Monitor"
              message="Coming soon. This page will be wired once security monitoring endpoints are available."
            />
          </>
        );

      default:
        return null;
    }
  };

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Sidebar */}
      <Sidebar activeView={activeView} setActiveView={setActiveView} />

      {/* Main column */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Top bar */}
        <TopNav onLogout={onLogout} />

        {/* Main content */}
        <main className="flex-1 overflow-y-auto p-8">
          <div className="max-w-7xl mx-auto">
            {renderView()}
          </div>
        </main>
      </div>

      <Toaster />
    </div>
  );
}
