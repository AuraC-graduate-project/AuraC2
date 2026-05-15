import { useEffect, useState } from "react";
import { CalendarDays, FileCode2, Inbox, MessageSquare, RefreshCw, Trophy, Users } from "lucide-react";
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
    <div className="space-y-8">
      <section className="rounded-xl bg-surface-container">
        <div className="flex flex-col gap-4 px-6 pt-6 pb-5 lg:flex-row lg:items-center lg:justify-between">
          <div>
            <p className="text-xs font-medium uppercase tracking-[0.18em] text-primary">Admin overview</p>
            <h1 className="mt-1 font-display text-3xl font-semibold tracking-tight text-on-surface">Aura Contest Control</h1>
            <p className="mt-2 text-sm leading-6 text-on-surface-variant">
              Manage the live contest workflow without exposing planned features as production tools.
            </p>
          </div>
          <Button variant="ghost" className="gap-2" onClick={loadContest} disabled={loading}>
            <RefreshCw className="h-4 w-4" />
            Refresh
          </Button>
        </div>

        <div className="grid gap-6 px-6 pb-6 lg:grid-cols-[1.2fr_0.8fr]">
          <div className="rounded-xl bg-surface-container-low p-5">
            <div className="mb-4 flex items-center justify-between gap-3">
              <div>
                <h2 className="font-display text-lg font-semibold tracking-tight text-on-surface">Current contest</h2>
                <p className="text-xs uppercase tracking-[0.14em] text-on-surface-soft">Live from the contest API</p>
              </div>
              {contest && <StatusBadge kind="contest" value={contest.status} />}
            </div>

            {loading ? (
              <p className="py-10 text-center text-sm text-on-surface-soft">Loading contest...</p>
            ) : !contest ? (
              <div className="rounded-xl bg-surface-container-lowest p-8 text-center">
                <p className="text-sm font-medium text-on-surface">No active, paused, or upcoming contest found.</p>
                <p className="mt-1 text-sm text-on-surface-soft">Create a contest from the contest control page.</p>
                <Button className="mt-5" onClick={() => onNavigate("Contests")}>
                  Open Contest Control
                </Button>
              </div>
            ) : (
              <div className="grid gap-4 md:grid-cols-2">
                <div className="md:col-span-2">
                  <p className="text-xs font-medium uppercase tracking-[0.14em] text-on-surface-soft">Title</p>
                  <p className="mt-1 font-display text-xl font-semibold tracking-tight text-on-surface">{contest.title}</p>
                  <p className="mt-2 text-sm leading-6 text-on-surface-variant">{contest.description || "No description provided."}</p>
                </div>
                <div className="rounded-xl bg-surface-container-lowest p-4">
                  <p className="text-xs font-medium uppercase tracking-[0.14em] text-on-surface-soft">Start time</p>
                  <p className="mt-2 font-mono text-sm text-on-surface">{formatDateTime(contest.startTime)}</p>
                </div>
                <div className="rounded-xl bg-surface-container-lowest p-4">
                  <p className="text-xs font-medium uppercase tracking-[0.14em] text-on-surface-soft">Duration</p>
                  <p className="mt-2 font-mono text-sm text-on-surface">{formatDuration(contest.durationMinutes)}</p>
                </div>
              </div>
            )}
          </div>

          <div className="grid gap-3">
            {[
              ["Contest control", "Manage lifecycle from the Contests page using current API state."],
              ["Team management", "Create and maintain student logins in one place."],
              ["Review queues", "Use Submissions and Clarifications for contest operations."],
            ].map(([title, detail]) => (
              <div key={title} className="rounded-xl bg-surface-container-low p-4">
                <span className="font-semibold text-on-surface">{title}</span>
                <p className="mt-2 text-sm leading-6 text-on-surface-variant">{detail}</p>
              </div>
            ))}
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
              className="group rounded-xl bg-surface-container p-5 text-left transition hover:-translate-y-0.5 hover:bg-surface-container-high"
            >
              <div className="mb-4 flex h-10 w-10 items-center justify-center rounded-xl bg-primary-fixed text-primary transition group-hover:bg-primary group-hover:text-on-primary dark:bg-primary/15">
                <Icon className="h-5 w-5" />
              </div>
              <h3 className="font-display font-semibold tracking-tight text-on-surface">{item.title}</h3>
              <p className="mt-2 text-sm leading-5 text-on-surface-variant">{item.description}</p>
            </button>
          );
        })}
      </section>

      <section className="rounded-xl bg-surface-container p-5">
        <div className="flex items-start gap-3">
          <CalendarDays className="mt-0.5 h-5 w-5 text-primary" />
          <div>
            <h2 className="font-display font-semibold tracking-tight text-on-surface">Operational scope</h2>
            <p className="mt-1 text-sm leading-6 text-on-surface-variant">
              The main navigation now keeps working contest, team, problem, submission, and clarification workflows prominent.
            </p>
          </div>
        </div>
      </section>
    </div>
  );
}
