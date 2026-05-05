import { useState, useEffect } from "react";
import { Sidebar } from "./components/Sidebar";
import { TopNav } from "./components/TopNav";
import { AdminOverview } from "./components/AdminOverview";
import { ContestOverview } from "./components/ContestOverview";
import { ProblemsView } from "./components/ProblemsView";
import { TeamsView } from "./components/TeamsView";
import { TeamAccountsPage } from "./components/TeamAccountsPage";
import { SubmissionsView } from "./components/SubmissionsView";
import { ClarificationsView } from "./components/ClarificationsView";
import { getActiveContest, getUpcomingContest } from "./services/api";
import { ContestResponse } from "./types/api";
import { Toaster } from "./components/ui/sonner";
import { UnderDevelopmentPage } from "../components/UnderDevelopmentPage";

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
        return <AdminOverview onNavigate={setActiveView} />;

      case "Contests":
        return <ContestOverview />;

      case "Problems":
        return <ProblemsView contestId={currentContest?.id ?? null} />;

      case "Teams":
        return <TeamsView />;

      case "Team Accounts":
        return <TeamAccountsPage />;

      case "Submissions":
        return <SubmissionsView />;

      case "Clarifications":
        return <ClarificationsView />;

      case "Statistics (Future)":
        return (
          <UnderDevelopmentPage
            title="Statistics and Analytics"
            subtitle="Future contest analytics"
            relatedCurrentFeature="Submissions review"
            plannedItems={[
              "Contest-level solve counts and verdict distribution",
              "Problem difficulty and acceptance trends",
              "Team activity timelines without fake data",
            ]}
            onBack={() => setActiveView("Overview")}
          />
        );

      case "Security Monitor (Future)":
        return (
          <UnderDevelopmentPage
            title="Security Monitor"
            subtitle="Future operational monitoring"
            relatedCurrentFeature="Teams and submissions management"
            plannedItems={[
              "Session and suspicious activity review",
              "System logs once backend endpoints exist",
              "Clear admin alerts without using fabricated health metrics",
            ]}
            onBack={() => setActiveView("Teams")}
          />
        );

      case "Scoreboard (Future)":
        return (
          <UnderDevelopmentPage
            title="Scoreboard and Standings"
            subtitle="Future team rankings"
            relatedCurrentFeature="Submissions review"
            plannedItems={[
              "Solved problem count and penalty time",
              "Frozen scoreboard states during contest endgame",
              "Public/team-readable standings once backend ranking exists",
            ]}
            onBack={() => setActiveView("Submissions")}
          />
        );

      case "Rejudge (Future)":
        return (
          <UnderDevelopmentPage
            title="Rejudge"
            subtitle="Future judging operations"
            relatedCurrentFeature="Submissions review"
            plannedItems={[
              "Rejudge a single submission",
              "Rejudge all submissions for one problem",
              "Rejudge a whole contest with audit trail",
            ]}
            onBack={() => setActiveView("Submissions")}
          />
        );

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
