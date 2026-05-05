import { useCallback, useEffect, useMemo, useState } from "react";
import { AlertTriangle, Calendar, Clock, Pencil, Pause, Play, Plus, RefreshCw, StopCircle } from "lucide-react";
import { Button } from "./ui/button";
import {
  getActiveContest,
  getUpcomingContest,
  getPausedContest,
  getEndedContests,
  startContest,
  pauseContest,
  endContest,
} from "../services/api";
import { ContestResponse } from "../types/api";
import { CreateContestModal } from "./CreateContestModal";
import { EditContestModal } from "./EditContestModal";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "./ui/alert-dialog";
import { StatusBadge } from "../../components/StatusBadge";
import { toast } from "sonner";

type ContestTab = "active" | "upcoming" | "paused" | "ended";

const tabs: Array<{ id: ContestTab; label: string }> = [
  { id: "active", label: "Running" },
  { id: "upcoming", label: "Upcoming" },
  { id: "paused", label: "Paused" },
  { id: "ended", label: "Ended" },
];

function formatDuration(minutes: number): string {
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (!hours) return `${rest} minutes`;
  if (!rest) return `${hours} hour${hours > 1 ? "s" : ""}`;
  return `${hours} hour${hours > 1 ? "s" : ""} ${rest} minutes`;
}

function formatDateTime(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString();
}

function getRemainingSeconds(contest: ContestResponse | null): number | null {
  if (!contest || contest.status !== "RUNNING") return null;
  const start = new Date(contest.startTime).getTime();
  if (!Number.isFinite(start)) return null;
  const end = start + contest.durationMinutes * 60_000;
  return Math.max(0, Math.floor((end - Date.now()) / 1000));
}

function formatRemaining(seconds: number | null): string {
  if (seconds == null) return "Not running";
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  return `${h.toString().padStart(2, "0")}:${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`;
}

export function ContestOverview() {
  const [contestType, setContestType] = useState<ContestTab>("active");
  const [contest, setContest] = useState<ContestResponse | null>(null);
  const [endedContests, setEndedContests] = useState<ContestResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [endDialogOpen, setEndDialogOpen] = useState(false);
  const [remaining, setRemaining] = useState<number | null>(null);

  const loadContest = useCallback(async () => {
    setLoading(true);
    try {
      if (contestType === "ended") {
        const data = await getEndedContests();
        setEndedContests(data);
        setContest(null);
        return;
      }

      const data =
        contestType === "active"
          ? await getActiveContest()
          : contestType === "upcoming"
            ? await getUpcomingContest()
            : await getPausedContest();

      setContest(data);
      setEndedContests([]);
    } catch {
      setContest(null);
      setEndedContests([]);
    } finally {
      setLoading(false);
    }
  }, [contestType]);

  useEffect(() => {
    loadContest();
  }, [loadContest]);

  useEffect(() => {
    const update = () => setRemaining(getRemainingSeconds(contest));
    update();
    const timer = window.setInterval(update, 1000);
    return () => window.clearInterval(timer);
  }, [contest]);

  const selectedTitle = useMemo(() => {
    if (contestType === "active") return "Running contest";
    if (contestType === "upcoming") return "Upcoming contest";
    if (contestType === "paused") return "Paused contest";
    return "Ended contests";
  }, [contestType]);

  const handleStartOrResume = async () => {
    if (!contest) return;
    await startContest(contest.id);
    toast.success(contest.status === "PAUSED" ? "Contest resumed" : "Contest started");
    setContestType("active");
  };

  const handlePause = async () => {
    if (!contest) return;
    await pauseContest(contest.id);
    toast.success("Contest paused");
    setContestType("paused");
  };

  const handleEnd = async () => {
    if (!contest) return;
    await endContest(contest.id);
    toast.success("Contest ended");
    setEndDialogOpen(false);
    setContestType("ended");
  };

  const canStart = contest?.status === "UPCOMING";
  const canResume = contest?.status === "PAUSED";
  const canPause = contest?.status === "RUNNING";
  const canEnd = contest?.status === "RUNNING" || contest?.status === "PAUSED";
  const canEdit = contest?.status === "UPCOMING";

  return (
    <>
      <section className="space-y-6">
        <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
          <div className="border-b border-slate-200 bg-slate-50 px-6 py-5">
            <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
              <div>
                <p className="text-sm font-semibold uppercase tracking-wide text-blue-700">Contest lifecycle</p>
                <h1 className="mt-1 text-2xl font-semibold text-slate-950">Contest Control</h1>
                <p className="mt-1 text-sm text-slate-600">
                  Create one upcoming contest, then start, pause, resume, or end it using backend lifecycle endpoints.
                </p>
              </div>

              <div className="flex flex-wrap items-center gap-2">
                <Button variant="outline" className="gap-2 bg-white" onClick={loadContest} disabled={loading}>
                  <RefreshCw className="h-4 w-4" />
                  Refresh
                </Button>
                <Button className="gap-2 bg-blue-700 hover:bg-blue-800" onClick={() => setCreateModalOpen(true)}>
                  <Plus className="h-4 w-4" />
                  Create Contest
                </Button>
              </div>
            </div>
          </div>

          <div className="border-b border-slate-200 px-6 py-3">
            <div className="flex flex-wrap gap-2">
              {tabs.map((tab) => (
                <button
                  type="button"
                  key={tab.id}
                  onClick={() => setContestType(tab.id)}
                  className={`rounded-md px-3 py-2 text-sm font-semibold transition ${
                    contestType === tab.id
                      ? "bg-blue-700 text-white shadow-sm"
                      : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
                  }`}
                >
                  {tab.label}
                </button>
              ))}
            </div>
          </div>

          <div className="p-6">
            {loading ? (
              <p className="py-12 text-center text-sm text-slate-500">Loading contest...</p>
            ) : contestType === "ended" ? (
              endedContests.length === 0 ? (
                <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-10 text-center">
                  <p className="text-sm font-medium text-slate-800">No ended contests found.</p>
                  <p className="mt-1 text-sm text-slate-500">Ended contests will appear here after contest managers close them.</p>
                </div>
              ) : (
                <div className="grid gap-3">
                  {endedContests.map((item) => (
                    <article key={item.id} className="rounded-lg border border-slate-200 bg-white p-4">
                      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                        <div>
                          <h2 className="font-semibold text-slate-950">{item.title}</h2>
                          <p className="mt-1 text-sm text-slate-600">{item.description || "No description provided."}</p>
                        </div>
                        <StatusBadge kind="contest" value={item.status} />
                      </div>
                    </article>
                  ))}
                </div>
              )
            ) : !contest ? (
              <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-10 text-center">
                <p className="text-sm font-medium text-slate-800">No {selectedTitle.toLowerCase()} found.</p>
                <p className="mt-1 text-sm text-slate-500">Only one upcoming or running contest can exist at a time.</p>
                <Button className="mt-5 gap-2 bg-blue-700 hover:bg-blue-800" onClick={() => setCreateModalOpen(true)}>
                  <Plus className="h-4 w-4" />
                  Create Contest
                </Button>
              </div>
            ) : (
              <div className="grid gap-6 xl:grid-cols-[1.3fr_0.7fr]">
                <div className="space-y-5">
                  <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
                    <div>
                      <div className="mb-3 flex flex-wrap items-center gap-2">
                        <StatusBadge kind="contest" value={contest.status} />
                        <span className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold text-slate-600">
                          Contest #{contest.id}
                        </span>
                      </div>
                      <h2 className="text-2xl font-semibold text-slate-950">{contest.title}</h2>
                      <p className="mt-2 max-w-3xl text-sm leading-6 text-slate-600">
                        {contest.description || "No description provided."}
                      </p>
                    </div>

                    {canEdit && (
                      <Button variant="outline" className="gap-2 bg-white" onClick={() => setEditModalOpen(true)}>
                        <Pencil className="h-4 w-4" />
                        Edit Upcoming Contest
                      </Button>
                    )}
                  </div>

                  <div className="grid gap-4 md:grid-cols-3">
                    <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                      <Calendar className="mb-3 h-5 w-5 text-blue-700" />
                      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Start time</p>
                      <p className="mt-2 text-sm font-medium text-slate-800">{formatDateTime(contest.startTime)}</p>
                    </div>
                    <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                      <Clock className="mb-3 h-5 w-5 text-blue-700" />
                      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Duration</p>
                      <p className="mt-2 text-sm font-medium text-slate-800">{formatDuration(contest.durationMinutes)}</p>
                    </div>
                    <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                      <Clock className="mb-3 h-5 w-5 text-blue-700" />
                      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Remaining</p>
                      <p className="mt-2 font-mono text-sm font-semibold text-slate-800">{formatRemaining(remaining)}</p>
                    </div>
                  </div>
                </div>

                <aside className="rounded-lg border border-slate-200 bg-slate-50 p-5">
                  <h3 className="font-semibold text-slate-950">Lifecycle actions</h3>
                  <p className="mt-1 text-sm leading-6 text-slate-600">
                    Controls are enabled only when the current contest status allows them.
                  </p>

                  <div className="mt-5 grid gap-3">
                    <Button
                      className="justify-start gap-2 bg-emerald-600 hover:bg-emerald-700"
                      disabled={!canStart && !canResume}
                      onClick={handleStartOrResume}
                    >
                      <Play className="h-4 w-4" />
                      {canResume ? "Resume Contest" : "Start Contest"}
                    </Button>
                    <Button
                      className="justify-start gap-2 bg-amber-600 hover:bg-amber-700"
                      disabled={!canPause}
                      onClick={handlePause}
                    >
                      <Pause className="h-4 w-4" />
                      Pause Contest
                    </Button>
                    <Button
                      className="justify-start gap-2 bg-rose-600 hover:bg-rose-700"
                      disabled={!canEnd}
                      onClick={() => setEndDialogOpen(true)}
                    >
                      <StopCircle className="h-4 w-4" />
                      End Contest
                    </Button>
                  </div>

                  {!canEdit && contest.status !== "UPCOMING" && (
                    <div className="mt-5 flex gap-2 rounded-lg border border-slate-200 bg-white p-3 text-sm text-slate-600">
                      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0 text-amber-600" />
                      Contest details can only be edited while the contest is UPCOMING.
                    </div>
                  )}
                </aside>
              </div>
            )}
          </div>
        </div>
      </section>

      <CreateContestModal
        open={createModalOpen}
        onOpenChange={setCreateModalOpen}
        onSuccess={loadContest}
      />

      {contest && (
        <EditContestModal
          open={editModalOpen}
          onOpenChange={setEditModalOpen}
          contest={contest}
          onSuccess={(updated) => {
            setContest(updated);
            loadContest();
          }}
        />
      )}

      <AlertDialog open={endDialogOpen} onOpenChange={setEndDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>End Contest?</AlertDialogTitle>
            <AlertDialogDescription>
              This will close the contest for teams. This action should be used only when the contest is finished.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleEnd} className="bg-rose-600 hover:bg-rose-700">
              End Contest
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
