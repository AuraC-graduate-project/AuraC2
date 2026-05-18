import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ExternalLink,
  Eye,
  Lock,
  Moon,
  Play,
  RefreshCw,
  RotateCcw,
  Sun,
  StepForward,
  Trophy,
} from "lucide-react";
import { toast } from "sonner";
import type {
  ScoreboardRevealResponse,
  ScoreboardSnapshot,
  ScoreboardUpdatePayload,
} from "../types/api";
import {
  getAdminScoreboard,
  getPublicScoreboard,
  getScoreboardRevealState,
  resetScoreboardReveal,
  revealAllScoreboardCells,
  revealNextScoreboardCell,
  startScoreboardReveal,
} from "../services/api";
import {
  contestLabel,
  type ContestOption,
  loadContestOptions as loadContestOptionsList,
} from "../utils/contestOptions";
import { useScoreboardStream } from "../../hooks/useScoreboardStream";
import { ScoreboardTable, type ScoreboardRankChange } from "../../components/scoreboard/ScoreboardTable";
import { StatusBadge } from "../../components/StatusBadge";
import { useTheme } from "../../components/ThemeProvider";
import { Button } from "./ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "./ui/card";

type Props = {
  contestId: number | null;
  presentationMode?: boolean;
};

function parseContestId(value: string | null): number | null {
  if (!value) return null;
  const next = Number(value);
  return Number.isInteger(next) && next > 0 ? next : null;
}

function contestIdFromUrl(): number | null {
  return parseContestId(new URLSearchParams(window.location.search).get("contestId"));
}

function revealDisplayPath(contestId: number): string {
  return `/admin/scoreboard/reveal-display?contestId=${contestId}`;
}

function syncScoreboardContestToUrl(contestId: number | null) {
  const url = new URL(window.location.href);
  if (contestId == null) {
    url.searchParams.delete("contestId");
  } else {
    url.searchParams.set("contestId", String(contestId));
  }
  window.history.replaceState({}, "", `${url.pathname}${url.search}${url.hash}`);
}

function patchSnapshot(snapshot: ScoreboardSnapshot | null, payload: ScoreboardUpdatePayload): ScoreboardSnapshot | null {
  if (payload.snapshot) return payload.snapshot;
  if (!snapshot) return null;

  const changedByTeam = new Map(payload.changedRows.map((row) => [row.teamId, row]));
  const mergedRows = snapshot.rows.map((row) => changedByTeam.get(row.teamId) ?? row);
  for (const row of payload.changedRows) {
    if (!snapshot.rows.some((existing) => existing.teamId === row.teamId)) {
      mergedRows.push(row);
    }
  }

  return {
    metadata: payload.metadata,
    rows: mergedRows.sort((a, b) => a.rank - b.rank || a.teamName.localeCompare(b.teamName)),
  };
}

function rankChangesFor(
  snapshot: ScoreboardSnapshot | null,
  payload: ScoreboardUpdatePayload
): Record<number, ScoreboardRankChange> {
  if (!snapshot) return {};

  const previousRanks = new Map(snapshot.rows.map((row) => [row.teamId, row.rank]));
  const changes: Record<number, ScoreboardRankChange> = {};
  for (const row of payload.changedRows) {
    const previousRank = previousRanks.get(row.teamId);
    if (previousRank == null || previousRank === row.rank) continue;
    changes[row.teamId] = row.rank < previousRank ? "up" : "down";
  }
  return changes;
}

export function ScoreboardView({ contestId, presentationMode = false }: Props) {
  const { isDark, toggleTheme } = useTheme();
  const [selectedContestId, setSelectedContestId] = useState<number | null>(() => contestIdFromUrl() ?? contestId);
  const [contestOptions, setContestOptions] = useState<ContestOption[]>([]);
  const [loadingContests, setLoadingContests] = useState(false);
  const [snapshot, setSnapshot] = useState<ScoreboardSnapshot | null>(null);
  const [reveal, setReveal] = useState<ScoreboardRevealResponse | null>(null);
  const [changedTeamIds, setChangedTeamIds] = useState<number[]>([]);
  const [rankChanges, setRankChanges] = useState<Record<number, ScoreboardRankChange>>({});
  const [loading, setLoading] = useState(false);
  const [working, setWorking] = useState<string | null>(null);

  const effectiveContestId = selectedContestId ?? contestId;

  useEffect(() => {
    const urlContestId = contestIdFromUrl();
    if (urlContestId != null) {
      setSelectedContestId(urlContestId);
      return;
    }

    setSelectedContestId((current) => current ?? contestId);
  }, [contestId]);

  useEffect(() => {
    if (presentationMode) return;

    let mounted = true;
    setLoadingContests(true);
    loadContestOptionsList()
      .then((options) => {
        if (mounted) setContestOptions(options);
      })
      .catch(() => {
        if (mounted) setContestOptions([]);
      })
      .finally(() => {
        if (mounted) setLoadingContests(false);
      });

    return () => {
      mounted = false;
    };
  }, [presentationMode]);

  useEffect(() => {
    if (presentationMode) return;
    syncScoreboardContestToUrl(effectiveContestId);
  }, [effectiveContestId, presentationMode]);

  const load = useCallback(async () => {
    if (effectiveContestId == null) {
      setSnapshot(null);
      setReveal(null);
      return;
    }

    setLoading(true);
    try {
      const [scoreboard, revealState] = await Promise.all([
        presentationMode
          ? getPublicScoreboard(effectiveContestId)
          : getAdminScoreboard(effectiveContestId),
        getScoreboardRevealState(effectiveContestId).catch(() => null),
      ]);
      setSnapshot(scoreboard);
      setReveal(revealState);
      setChangedTeamIds([]);
      setRankChanges({});
    } catch (error) {
      if (!presentationMode) {
        toast.error(error instanceof Error ? error.message : "Failed to load scoreboard");
      }
      setSnapshot(null);
      setReveal(null);
    } finally {
      setLoading(false);
    }
  }, [effectiveContestId, presentationMode]);

  useEffect(() => {
    load();
  }, [load]);

  const applyPayload = useCallback((payload: ScoreboardUpdatePayload) => {
    setSnapshot((current) => {
      setRankChanges(rankChangesFor(current, payload));
      return patchSnapshot(current, payload);
    });
    setChangedTeamIds(payload.changedTeamIds);
    window.setTimeout(() => {
      setChangedTeamIds([]);
      setRankChanges({});
    }, 1800);
  }, []);

  useScoreboardStream({
    contestId: effectiveContestId,
    role: presentationMode ? "PUBLIC" : "ADMIN",
    snapshotVersion: snapshot?.metadata.version ?? 0,
    onSnapshot: (next) => {
      setSnapshot(next);
      setChangedTeamIds([]);
      setRankChanges({});
    },
    onUpdate: applyPayload,
    onFreeze: applyPayload,
    onRevealStep: (payload) => {
      applyPayload(payload);
      getScoreboardRevealState(payload.contestId).then(setReveal).catch(() => undefined);
    },
    onVersionGap: load,
  });

  const canReveal = snapshot?.metadata.effectiveState === "ENDED";
  const revealSummary = useMemo(() => {
    if (!reveal) return "Reveal not started";
    return `${reveal.revealedCells}/${reveal.totalCells} cells revealed`;
  }, [reveal]);

  const runRevealAction = async (
    label: string,
    action: (id: number) => Promise<ScoreboardRevealResponse>
  ) => {
    if (effectiveContestId == null) return;
    setWorking(label);
    try {
      const next = await action(effectiveContestId);
      setReveal(next);
      await load();
      toast.success(`${label} complete`);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : `${label} failed`);
    } finally {
      setWorking(null);
    }
  };

  if (presentationMode) {
    if (effectiveContestId == null) {
      return (
        <main className="aura-scoreboard-presentation flex min-h-screen items-center justify-center p-8 text-center">
          <div>
            <h1 className="text-3xl font-semibold text-white">Missing contest</h1>
            <p className="mt-3 text-lg text-slate-300">
              Open this display with a valid contest ID in the URL.
            </p>
          </div>
        </main>
      );
    }

    return (
      <main className="aura-scoreboard-presentation min-h-screen overflow-auto p-6 text-white lg:p-10">
        <div className="mx-auto flex min-h-[calc(100vh-3rem)] max-w-[1800px] flex-col gap-5 lg:min-h-[calc(100vh-5rem)]">
          <header className="flex flex-col gap-3 border-b border-white/15 pb-4 lg:flex-row lg:items-end lg:justify-between">
            <div>
              <p className="text-sm font-semibold uppercase text-slate-300">AuraC2 Scoreboard</p>
              <h1 className="mt-2 text-4xl font-semibold text-white lg:text-5xl">
                {snapshot?.metadata.contestTitle ?? "Reveal Display"}
              </h1>
            </div>
            <div className="flex flex-wrap items-center gap-3 text-sm text-slate-200">
              <button
                type="button"
                onClick={toggleTheme}
                aria-label={isDark ? "Switch reveal display to light mode" : "Switch reveal display to dark mode"}
                aria-pressed={isDark}
                className="aura-scoreboard-theme-toggle inline-flex items-center gap-2 rounded-md border px-3 py-1.5 text-sm font-semibold transition"
              >
                {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
                <span>{isDark ? "Light mode" : "Dark mode"}</span>
              </button>
              {snapshot && <StatusBadge kind="contest" value={String(snapshot.metadata.effectiveState)} />}
              <span className="rounded-full border border-white/15 bg-white/10 px-3 py-1 font-semibold">
                {revealSummary}
              </span>
              {snapshot?.metadata.scoreboardFrozen && (
                <span className="inline-flex items-center gap-1.5 rounded-full border border-white/15 bg-white/10 px-3 py-1 font-semibold">
                  <Lock className="h-4 w-4" />
                  Frozen
                </span>
              )}
            </div>
          </header>

          <section className="min-h-0 flex-1">
            {snapshot ? (
              <ScoreboardTable
                snapshot={snapshot}
                changedTeamIds={changedTeamIds}
                rankChanges={rankChanges}
                presentationMode
              />
            ) : (
              <div className="flex min-h-[55vh] items-center justify-center rounded-lg border border-white/15 bg-white/10 text-xl text-slate-200">
                {loading ? "Loading scoreboard..." : "No scoreboard data is available for this contest."}
              </div>
            )}
          </section>
        </div>
      </main>
    );
  }

  if (effectiveContestId == null) {
    return (
      <Card className="border border-slate-200 shadow-sm">
        <CardHeader className="border-b border-slate-200 bg-slate-50">
          <CardTitle className="text-2xl text-slate-950">Scoreboard</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4 p-8 text-center text-slate-600">
          <p>Select or create a contest before opening the scoreboard.</p>
          <select
            className="mx-auto h-10 min-w-72 rounded-md border border-slate-300 px-3 text-sm text-slate-900"
            value=""
            onChange={(event) => setSelectedContestId(parseContestId(event.target.value))}
            disabled={loadingContests}
          >
            <option value="">{loadingContests ? "Loading contests..." : "Select contest"}</option>
            {contestOptions.map((contest) => (
              <option key={contest.id} value={contest.id}>
                {contest.bucket} - {contestLabel(contest)}
              </option>
            ))}
          </select>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-5">
      <Card className="border border-slate-200 shadow-sm">
        <CardHeader className="border-b border-slate-200 bg-slate-50">
          <div className="flex flex-col gap-4 xl:flex-row xl:items-center xl:justify-between">
            <div>
              <CardTitle className="flex items-center gap-2 text-2xl text-slate-950">
                <Trophy className="h-6 w-6 text-blue-700" />
                Scoreboard
              </CardTitle>
            </div>

            <div className="flex flex-wrap items-center gap-2">
              <select
                className="h-9 min-w-60 rounded-md border border-slate-300 bg-white px-3 text-sm font-medium text-slate-900"
                value={effectiveContestId}
                onChange={(event) => setSelectedContestId(parseContestId(event.target.value))}
                disabled={loadingContests}
                aria-label="Scoreboard contest"
              >
                <option value="">{loadingContests ? "Loading contests..." : "Select contest"}</option>
                {contestOptions.map((contest) => (
                  <option key={contest.id} value={contest.id}>
                    {contest.bucket} - {contestLabel(contest)}
                  </option>
                ))}
              </select>
              {snapshot && (
                <>
                  <StatusBadge kind="contest" value={String(snapshot.metadata.effectiveState)} />
                  <span className="inline-flex items-center gap-1.5 rounded-full bg-blue-50 px-3 py-1 text-xs font-semibold text-blue-800">
                    <Eye className="h-3.5 w-3.5" />
                    Admin live
                  </span>
                  {snapshot.metadata.scoreboardFrozen && (
                    <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-900">
                      <Lock className="h-3.5 w-3.5" />
                      Official frozen
                    </span>
                  )}
                </>
              )}
              <Button
                variant="outline"
                size="sm"
                className="gap-2 bg-white"
                onClick={() => window.open(revealDisplayPath(effectiveContestId), "_blank", "noopener,noreferrer")}
              >
                <ExternalLink className="h-4 w-4" />
                Open fullscreen reveal display
              </Button>
              <Button variant="outline" size="sm" className="gap-2 bg-white" onClick={load} disabled={loading}>
                <RefreshCw className="h-4 w-4" />
                Refresh
              </Button>
            </div>
          </div>
        </CardHeader>

        <CardContent className="space-y-4 p-5">
          {snapshot ? (
            <>
              <div className="grid gap-3 md:grid-cols-3">
                <div className="rounded-md border border-slate-200 bg-white p-3">
                  <p className="text-xs font-semibold uppercase text-slate-500">Contest</p>
                  <p className="mt-1 font-semibold text-slate-950">{snapshot.metadata.contestTitle}</p>
                </div>
                <div className="rounded-md border border-slate-200 bg-white p-3">
                  <p className="text-xs font-semibold uppercase text-slate-500">Penalty</p>
                  <p className="mt-1 font-semibold text-slate-950">{snapshot.metadata.penaltyMinutes ?? 20} min</p>
                </div>
                <div className="rounded-md border border-slate-200 bg-white p-3">
                  <p className="text-xs font-semibold uppercase text-slate-500">Reveal</p>
                  <p className="mt-1 font-semibold text-slate-950">{revealSummary}</p>
                </div>
              </div>

              <div className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
                  <div>
                    <h3 className="font-semibold text-slate-950">Reveal Mode</h3>
                    <p className="mt-1 text-sm text-slate-600">{revealSummary}</p>
                  </div>
                  <div className="flex flex-wrap gap-2">
                    <Button
                      size="sm"
                      variant="outline"
                      className="gap-2 bg-white"
                      disabled={!canReveal || working !== null}
                      onClick={() => runRevealAction("Start reveal", startScoreboardReveal)}
                    >
                      <Play className="h-4 w-4" />
                      Start
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      className="gap-2 bg-white"
                      disabled={!canReveal || working !== null || reveal?.status === "COMPLETED"}
                      onClick={() => runRevealAction("Reveal next", revealNextScoreboardCell)}
                    >
                      <StepForward className="h-4 w-4" />
                      Next
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      className="gap-2 bg-white"
                      disabled={!canReveal || working !== null}
                      onClick={() => runRevealAction("Reveal all", revealAllScoreboardCells)}
                    >
                      <Eye className="h-4 w-4" />
                      All
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      className="gap-2 bg-white"
                      disabled={!canReveal || working !== null}
                      onClick={() => runRevealAction("Reset reveal", resetScoreboardReveal)}
                    >
                      <RotateCcw className="h-4 w-4" />
                      Reset
                    </Button>
                  </div>
                </div>
              </div>

              <ScoreboardTable snapshot={snapshot} changedTeamIds={changedTeamIds} rankChanges={rankChanges} />
            </>
          ) : (
            <div className="py-10 text-center text-slate-600">
              {loading ? "Loading scoreboard..." : "No scoreboard data is available."}
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
