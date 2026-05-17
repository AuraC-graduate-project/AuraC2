import { useEffect, useState } from "react";
import { FileCode2, Inbox, MessageSquare, RefreshCw, Trophy, Users } from "lucide-react";
import { Button } from "./ui/button";
import { getActiveContest, getPausedContest, getUpcomingContest } from "../services/api";
import { ContestResponse } from "../types/api";
import { StatusBadge } from "../../components/StatusBadge";

type AdminOverviewProps = {
  onNavigate: (view: string) => void;
};

function formatDateTime(value: string | undefined): string {
  if (!value) return "Not scheduled";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function formatDuration(minutes: number | undefined): string {
  if (!minutes) return "Not set";
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (!hours) return `${rest} minutes`;
  if (!rest) return `${hours} hour${hours > 1 ? "s" : ""}`;
  return `${hours} hour${hours > 1 ? "s" : ""} ${rest} minutes`;
}

const quickLinks = [
  {
    title: "Contests",
    view: "Contests",
    description: "Create, edit, start, pause, resume, and end contests.",
    icon: Trophy,
  },
  {
    title: "Teams",
    view: "Teams",
    description: "Register teams, update credentials, and remove non-admin accounts.",
    icon: Users,
  },
  {
    title: "Problems",
    view: "Problems",
    description: "Create problems and manage public/private test cases.",
    icon: FileCode2,
  },
  {
    title: "Submissions",
    view: "Submissions",
    description: "Review aggregate verdicts and submitted source code.",
    icon: Inbox,
  },
  {
    title: "Clarifications",
    view: "Clarifications",
    description: "Answer team questions privately or publicly.",
    icon: MessageSquare,
  },
];

export function AdminOverview({ onNavigate }: AdminOverviewProps) {
  const [contest, setContest] = useState<ContestResponse | null>(null);
  const [loading, setLoading] = useState(true);

  const loadContest = async () => {
    setLoading(true);
    try {
      const active = await getActiveContest();
      setContest(active);
    } catch {
      try {
        const upcoming = await getUpcomingContest();
        setContest(upcoming);
      } catch {
        try {
          const paused = await getPausedContest();
          setContest(paused);
        } catch {
          setContest(null);
        }
      }
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadContest();
  }, []);

  return (
    <div className="space-y-6">
      <section className="rounded-lg border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-4 border-b border-slate-200 bg-slate-50 px-6 py-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="text-sm font-semibold uppercase tracking-wide text-blue-700">Admin overview</p>
            <h1 className="mt-1 text-2xl font-semibold text-slate-950">Aura Contest Control</h1>
            <p className="mt-1 text-sm text-slate-600">
              Manage the live contest workflow without exposing planned features as production tools.
            </p>
          </div>
          <Button variant="outline" className="gap-2 bg-white" onClick={loadContest} disabled={loading}>
            <RefreshCw className="h-4 w-4" />
            Refresh
          </Button>
        </div>

        <div className="p-6">
          <div className="rounded-lg border border-slate-200 p-5">
            <div className="mb-4 flex items-center justify-between gap-3">
              <div>
                <h2 className="text-lg font-semibold text-slate-950">Current contest summary</h2>
              </div>
              {contest && <StatusBadge kind="contest" value={contest.status} />}
            </div>

            {loading ? (
              <p className="py-10 text-center text-sm text-slate-500">Loading contest...</p>
            ) : !contest ? (
              <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-8 text-center">
                <p className="text-sm font-medium text-slate-800">No active, paused, or upcoming contest found.</p>
                <p className="mt-1 text-sm text-slate-500">Create a contest from the contest control page.</p>
                <Button className="mt-5 bg-blue-700 hover:bg-blue-800" onClick={() => onNavigate("Contests")}>
                  Open Contest Control
                </Button>
              </div>
            ) : (
              <div className="grid gap-4 md:grid-cols-2">
                <div className="md:col-span-2">
                  <p className="text-sm font-semibold text-slate-500">Title</p>
                  <p className="mt-1 text-lg font-semibold text-slate-950">{contest.title}</p>
                  <p className="mt-2 text-sm leading-6 text-slate-600">{contest.description || "No description provided."}</p>
                </div>
                <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Start time</p>
                  <p className="mt-2 text-sm font-medium text-slate-800">{formatDateTime(contest.startTime)}</p>
                </div>
                <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                  <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Duration</p>
                  <p className="mt-2 text-sm font-medium text-slate-800">{formatDuration(contest.durationMinutes)}</p>
                </div>
              </div>
            )}
          </div>
        </div>
      </section>

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-5">
        {quickLinks.map((item) => {
          const Icon = item.icon;
          return (
            <button
              type="button"
              key={item.title}
              onClick={() => onNavigate(item.view)}
              className="rounded-lg border border-slate-200 bg-white p-5 text-left shadow-sm transition hover:-translate-y-0.5 hover:border-blue-200 hover:shadow-md"
            >
              <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-blue-700">
                <Icon className="h-5 w-5" />
              </div>
              <h3 className="font-semibold text-slate-950">{item.title}</h3>
              <p className="mt-2 text-sm leading-5 text-slate-600">{item.description}</p>
            </button>
          );
        })}
      </section>
    </div>
  );
}
