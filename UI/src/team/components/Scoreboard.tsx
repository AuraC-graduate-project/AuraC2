import { useCallback, useEffect, useMemo, useState } from "react";
import { Lock, RefreshCw, Trophy } from "lucide-react";
import type { ScoreboardSnapshot, ScoreboardUpdatePayload } from "../../admin/types/api";
import { useScoreboardStream } from "../../hooks/useScoreboardStream";
import { useSubmissionStream } from "../../hooks/useSubmissionStream";
import { ScoreboardTable } from "../../components/scoreboard/ScoreboardTable";
import { getPublicScoreboard } from "../services/teamApi";
import { Button } from "./ui/button";

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

type ScoreboardProps = {
  contestId: number;
  fullPage?: boolean;
};

function formatGeneratedAt(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
}

export function Scoreboard({ contestId, fullPage = false }: ScoreboardProps) {
  const [snapshot, setSnapshot] = useState<ScoreboardSnapshot | null>(null);
  const [changedTeamIds, setChangedTeamIds] = useState<number[]>([]);
  const [loading, setLoading] = useState(false);
  const [pendingByTeam, setPendingByTeam] = useState<Map<string, Set<number>>>(new Map());

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

  useSubmissionStream({
    role: "TEAM",
    onEvent: (event) => {
      if (event.contestId !== contestId) return;
      const name = event.username;
      if (event.eventType === "CREATED" || event.eventType === "RUNNING") {
        setPendingByTeam((prev) => {
          const next = new Map(prev);
          const ids = new Set(next.get(name) ?? []);
          ids.add(event.problemId);
          next.set(name, ids);
          return next;
        });
      } else if (event.eventType === "FINALIZED") {
        setPendingByTeam((prev) => {
          const next = new Map(prev);
          const ids = new Set(next.get(name) ?? []);
          ids.delete(event.problemId);
          if (ids.size === 0) next.delete(name);
          else next.set(name, ids);
          return next;
        });
      }
    },
  });

  const displaySnapshot = useMemo(() => {
    if (!snapshot || pendingByTeam.size === 0) return snapshot;
    return {
      ...snapshot,
      rows: snapshot.rows.map((row) => {
        const pending = pendingByTeam.get(row.teamName);
        if (!pending || pending.size === 0) return row;
        return {
          ...row,
          problemCells: row.problemCells.map((cell) =>
            pending.has(cell.problemId) && !cell.solved
              ? { ...cell, pending: true }
              : cell
          ),
        };
      }),
    };
  }, [snapshot, pendingByTeam]);

  const totalTeams = snapshot?.rows.length ?? 0;
  const solvedTotal = snapshot?.rows.reduce((sum, row) => sum + row.solvedCount, 0) ?? 0;

  return (
    <section className={fullPage ? "aura-panel-switch mx-auto max-w-7xl space-y-4" : "space-y-4"}>
      <div className={`flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between ${
        fullPage
          ? "rounded-lg border border-slate-200 bg-white p-5 shadow-sm"
          : "border-b border-slate-200 pb-4"
      }`}>
        <div>
          <h2 className="flex items-center gap-2 text-xl font-semibold text-slate-950">
            <Trophy className="h-5 w-5 text-blue-700" />
            Scoreboard
          </h2>
          {snapshot && (
            <div className="mt-2 flex flex-wrap items-center gap-2 text-sm text-slate-600">
              <span className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold">
                Version {snapshot.metadata.version}
              </span>
              <span className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold">
                Updated {formatGeneratedAt(snapshot.metadata.generatedAt)}
              </span>
              {snapshot.metadata.revealStatus !== "NOT_STARTED" && (
                <span className="rounded-full border border-slate-200 bg-slate-50 px-2.5 py-1 text-xs font-semibold">
                  Reveal {snapshot.metadata.revealedCells}/{snapshot.metadata.totalHiddenCells}
                </span>
              )}
            </div>
          )}
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

      {displaySnapshot ? (
        <>
          {fullPage && (
            <div className="grid gap-3 md:grid-cols-3">
              <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Teams</p>
                <p className="mt-2 text-2xl font-semibold text-slate-950">{totalTeams}</p>
              </div>
              <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Solved</p>
                <p className="mt-2 text-2xl font-semibold text-slate-950">{solvedTotal}</p>
              </div>
              <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Penalty</p>
                <p className="mt-2 text-2xl font-semibold text-slate-950">{displaySnapshot.metadata.penaltyMinutes ?? 20}</p>
              </div>
            </div>
          )}

          {displaySnapshot.metadata.scoreboardFrozen && (
            <div className="rounded-md border border-amber-200 bg-amber-50 px-4 py-3 text-sm font-medium text-amber-900">
              Official scoreboard frozen
            </div>
          )}
          <ScoreboardTable snapshot={displaySnapshot} changedTeamIds={changedTeamIds} />
        </>
      ) : (
        <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-8 text-center text-slate-600">
          {loading ? "Loading scoreboard..." : "No scoreboard data is available yet."}
        </div>
      )}
    </section>
  );
}
