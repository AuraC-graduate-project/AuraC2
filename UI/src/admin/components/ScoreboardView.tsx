import { useCallback, useEffect, useMemo, useState } from "react";
import { Eye, Lock, Play, RefreshCw, RotateCcw, StepForward, Trophy } from "lucide-react";
import { toast } from "sonner";
import type {
  ScoreboardRevealResponse,
  ScoreboardSnapshot,
  ScoreboardUpdatePayload,
} from "../types/api";
import {
  getAdminScoreboard,
  getScoreboardRevealState,
  resetScoreboardReveal,
  revealAllScoreboardCells,
  revealNextScoreboardCell,
  startScoreboardReveal,
} from "../services/api";
import { useScoreboardStream } from "../../hooks/useScoreboardStream";
import { ScoreboardTable } from "../../components/scoreboard/ScoreboardTable";
import { StatusBadge } from "../../components/StatusBadge";
import { Button } from "./ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "./ui/card";

type Props = {
  contestId: number | null;
};

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

export function ScoreboardView({ contestId }: Props) {
  const [snapshot, setSnapshot] = useState<ScoreboardSnapshot | null>(null);
  const [reveal, setReveal] = useState<ScoreboardRevealResponse | null>(null);
  const [changedTeamIds, setChangedTeamIds] = useState<number[]>([]);
  const [loading, setLoading] = useState(false);
  const [working, setWorking] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (contestId == null) {
      setSnapshot(null);
      setReveal(null);
      return;
    }

    setLoading(true);
    try {
      const [scoreboard, revealState] = await Promise.all([
        getAdminScoreboard(contestId),
        getScoreboardRevealState(contestId).catch(() => null),
      ]);
      setSnapshot(scoreboard);
      setReveal(revealState);
      setChangedTeamIds([]);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to load scoreboard");
      setSnapshot(null);
      setReveal(null);
    } finally {
      setLoading(false);
    }
  }, [contestId]);

  useEffect(() => {
    load();
  }, [load]);

  const applyPayload = useCallback((payload: ScoreboardUpdatePayload) => {
    setSnapshot((current) => patchSnapshot(current, payload));
    setChangedTeamIds(payload.changedTeamIds);
    window.setTimeout(() => setChangedTeamIds([]), 1800);
  }, []);

  useScoreboardStream({
    contestId,
    role: "ADMIN",
    snapshotVersion: snapshot?.metadata.version ?? 0,
    onSnapshot: (next) => {
      setSnapshot(next);
      setChangedTeamIds([]);
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
    if (contestId == null) return;
    setWorking(label);
    try {
      const next = await action(contestId);
      setReveal(next);
      await load();
      toast.success(`${label} complete`);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : `${label} failed`);
    } finally {
      setWorking(null);
    }
  };

  if (contestId == null) {
    return (
      <Card className="border border-slate-200 shadow-sm">
        <CardHeader className="border-b border-slate-200 bg-slate-50">
          <CardTitle className="text-2xl text-slate-950">Scoreboard</CardTitle>
        </CardHeader>
        <CardContent className="p-8 text-center text-slate-600">
          Select or create a contest before opening the scoreboard.
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
              <div className="grid gap-3 md:grid-cols-4">
                <div className="rounded-md border border-slate-200 bg-white p-3">
                  <p className="text-xs font-semibold uppercase text-slate-500">Contest</p>
                  <p className="mt-1 font-semibold text-slate-950">{snapshot.metadata.contestTitle}</p>
                </div>
                <div className="rounded-md border border-slate-200 bg-white p-3">
                  <p className="text-xs font-semibold uppercase text-slate-500">Version</p>
                  <p className="mt-1 font-mono text-slate-950">{snapshot.metadata.version}</p>
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

              <ScoreboardTable snapshot={snapshot} changedTeamIds={changedTeamIds} />
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
