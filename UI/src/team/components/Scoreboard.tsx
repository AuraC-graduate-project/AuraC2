import { useCallback, useEffect, useState } from "react";
import { Lock, RefreshCw, Trophy } from "lucide-react";
import type { ScoreboardSnapshot, ScoreboardUpdatePayload } from "../../admin/types/api";
import { useScoreboardStream } from "../../hooks/useScoreboardStream";
import { ScoreboardTable } from "../../components/scoreboard/ScoreboardTable";
import { getPublicScoreboard } from "../services/teamApi";
import { Button } from "./ui/button";

function patchSnapshot(snapshot: ScoreboardSnapshot | null, payload: ScoreboardUpdatePayload): ScoreboardSnapshot | null {
  if (payload.snapshot) return payload.snapshot;
  if (!snapshot) return null;

  const changedByTeam = new Map(payload.changedRows.map((row) => [row.teamId, row]));
  const mergedRows = snapshot.rows.map((row) => changedByTeam.get(row.teamId) ?? row);
  return {
    metadata: payload.metadata,
    rows: mergedRows.sort((a, b) => a.rank - b.rank || a.teamName.localeCompare(b.teamName)),
  };
}

export function Scoreboard({ contestId }: { contestId: number }) {
  const [snapshot, setSnapshot] = useState<ScoreboardSnapshot | null>(null);
  const [changedTeamIds, setChangedTeamIds] = useState<number[]>([]);
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getPublicScoreboard(contestId);
      setSnapshot(data);
      setChangedTeamIds([]);
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
    role: "PUBLIC",
    snapshotVersion: snapshot?.metadata.version ?? 0,
    onSnapshot: (next) => {
      setSnapshot(next);
      setChangedTeamIds([]);
    },
    onUpdate: applyPayload,
    onFreeze: applyPayload,
    onRevealStep: applyPayload,
    onVersionGap: load,
  });

  return (
    <section className="space-y-4">
      <div className="flex flex-col gap-3 border-b border-slate-200 pb-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="flex items-center gap-2 text-xl font-semibold text-slate-950">
            <Trophy className="h-5 w-5 text-blue-700" />
            Scoreboard
          </h2>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          {snapshot?.metadata.scoreboardFrozen && (
            <span className="inline-flex items-center gap-1.5 rounded-full bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-900">
              <Lock className="h-3.5 w-3.5" />
              Frozen
            </span>
          )}
          <Button variant="outline" size="sm" className="gap-2 bg-white" onClick={load} disabled={loading}>
            <RefreshCw className="h-4 w-4" />
            Refresh
          </Button>
        </div>
      </div>

      {snapshot ? (
        <>
          {snapshot.metadata.scoreboardFrozen && (
            <div className="rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
              The official scoreboard is frozen. Hidden cells will update during the reveal after the contest ends.
            </div>
          )}
          <ScoreboardTable snapshot={snapshot} changedTeamIds={changedTeamIds} />
        </>
      ) : (
        <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-8 text-center text-slate-600">
          {loading ? "Loading scoreboard..." : "No scoreboard data is available yet."}
        </div>
      )}
    </section>
  );
}
