import { ArrowDownRight, ArrowUpRight, CheckCircle2, Loader2, Lock, Medal, Minus } from "lucide-react";
import { useLayoutEffect, useRef } from "react";
import type { ScoreboardSnapshot } from "../../admin/types/api";

export type ScoreboardRankChange = "up" | "down";

function formatMinutes(value: number | null | undefined): string {
  if (value == null) return "";
  return `${value}`;
}

function cellTitle({
  solved,
  attempts,
  wrongAttempts,
  solvedTimeMinutes,
  hidden,
  revealed,
  pending,
}: {
  solved: boolean;
  attempts: number;
  wrongAttempts: number;
  solvedTimeMinutes: number | null;
  hidden: boolean;
  revealed: boolean;
  pending?: boolean;
}) {
  if (pending) return "Judging in progress";
  if (hidden && !revealed) return "Contains hidden post-freeze activity";
  if (solved) return `Accepted at ${solvedTimeMinutes} min with ${wrongAttempts} wrong attempt(s)`;
  if (attempts > 0) return `${attempts} unsuccessful attempt(s)`;
  return "No visible attempts";
}

function cellTone({
  solved,
  attempts,
  hidden,
  revealed,
  pending,
  firstToSolve,
}: {
  solved: boolean;
  attempts: number;
  hidden: boolean;
  revealed: boolean;
  pending?: boolean;
  firstToSolve: boolean;
}): string {
  if (pending) return "animate-pulse border-amber-400 bg-amber-100 text-amber-950";
  if (hidden && !revealed) return "border-slate-400 bg-slate-100 text-slate-700";

  const revealAccent = revealed ? "aura-scoreboard-cell-revealed" : "";
  if (solved) {
    return firstToSolve
      ? `border-emerald-600 bg-emerald-100 text-emerald-950 ${revealAccent}`
      : `border-emerald-500 bg-emerald-50 text-emerald-900 ${revealAccent}`;
  }
  if (attempts > 0) return `border-rose-300 bg-rose-50 text-rose-800 ${revealAccent}`;
  return `border-slate-200 bg-white text-slate-500 ${revealAccent}`;
}

function rowTone(wasChanged: boolean, rankChange: ScoreboardRankChange | undefined): string {
  if (rankChange === "up") return "aura-scoreboard-row aura-scoreboard-row-rank-up";
  if (rankChange === "down") return "aura-scoreboard-row aura-scoreboard-row-rank-down";
  if (wasChanged) return "aura-scoreboard-row aura-scoreboard-row-updated";
  return "aura-scoreboard-row hover:bg-slate-50";
}

export function ScoreboardTable({
  snapshot,
  changedTeamIds = [],
  rankChanges = {},
  presentationMode = false,
}: {
  snapshot: ScoreboardSnapshot;
  changedTeamIds?: number[];
  rankChanges?: Record<number, ScoreboardRankChange>;
  presentationMode?: boolean;
}) {
  const changed = new Set(changedTeamIds);
  const columns = snapshot.metadata.problemColumns;
  const rowRefs = useRef(new Map<number, HTMLTableRowElement>());
  const previousRowTops = useRef(new Map<number, number>());

  useLayoutEffect(() => {
    const nextTops = new Map<number, number>();

    snapshot.rows.forEach((row) => {
      const element = rowRefs.current.get(row.teamId);
      if (!element) return;

      const nextTop = element.getBoundingClientRect().top;
      const previousTop = previousRowTops.current.get(row.teamId);
      nextTops.set(row.teamId, nextTop);

      if (previousTop == null) return;

      const delta = previousTop - nextTop;
      if (Math.abs(delta) < 1) return;

      element.animate(
        [
          { transform: `translateY(${delta}px)` },
          { transform: "translateY(0)" },
        ],
        {
          duration: 900,
          easing: "cubic-bezier(0.16, 1, 0.3, 1)",
        }
      );
    });

    previousRowTops.current = nextTops;
  }, [snapshot.rows]);

  return (
    <div className={`overflow-x-auto rounded-lg border border-slate-300 bg-white ${presentationMode ? "aura-scoreboard-presentation-table" : ""}`}>
      <div className="overflow-x-auto">
        <table className={`w-max min-w-max border-collapse ${presentationMode ? "text-base" : "text-sm"}`}>
          <thead className="bg-slate-100 text-xs uppercase text-slate-600">
            <tr>
              <th className="sticky left-0 z-10 w-20 border-b border-slate-300 bg-slate-100 px-3 py-3 text-left">
                Rank
              </th>
              <th className="sticky left-20 z-10 w-56 border-b border-slate-300 bg-slate-100 px-3 py-3 text-left">
                Team
              </th>
              {columns.map((problem) => (
                <th
                  key={problem.problemId}
                  className="w-24 border-b border-slate-300 px-2 py-3 text-center"
                  title={problem.title}
                >
                  {problem.label}
                </th>
              ))}
              <th className="w-20 border-b border-slate-300 px-3 py-3 text-center">Solved</th>
              <th className="w-24 border-b border-slate-300 px-3 py-3 text-center">Penalty</th>
            </tr>
          </thead>
          <tbody>
            {snapshot.rows.map((row) => {
              const wasChanged = changed.has(row.teamId);
              const rankChange = rankChanges[row.teamId];
              return (
                <tr
                  key={row.teamId}
                  ref={(element) => {
                    if (element) {
                      rowRefs.current.set(row.teamId, element);
                    } else {
                      rowRefs.current.delete(row.teamId);
                    }
                  }}
                  className={`border-b border-slate-200 transition-[background-color,box-shadow,transform] duration-500 last:border-b-0 ${rowTone(wasChanged, rankChange)}`}
                >
                  <td className="sticky left-0 z-10 bg-inherit px-3 py-3 font-mono font-semibold text-slate-900">
                    <span className="inline-flex min-w-12 items-center gap-1">
                      {row.rank}
                      {rankChange === "up" && <ArrowUpRight className="h-4 w-4 text-emerald-700" aria-label="Rank up" />}
                      {rankChange === "down" && <ArrowDownRight className="h-4 w-4 text-rose-700" aria-label="Rank down" />}
                    </span>
                  </td>
                  <td className="sticky left-20 z-10 w-56 bg-inherit px-3 py-3">
                    <div className="font-semibold text-slate-950">{row.teamName}</div>
                  </td>
                  {row.problemCells.map((cell) => {
                    const solved = cell.solved;
                    const attempted = cell.attempts > 0 && !cell.solved;
                    const pending = cell.pending ?? false;
                    return (
                      <td key={cell.problemId} className="px-2 py-2 text-center">
                        <div
                          title={cellTitle(cell)}
                          className={`aura-scoreboard-cell mx-auto flex min-h-12 flex-col items-center justify-center rounded-md border px-2 py-1 transition-colors duration-300 ${
                            presentationMode ? "w-24 min-h-16" : "w-20"
                          } ${cellTone({
                            solved,
                            attempts: cell.attempts,
                            hidden: cell.hidden,
                            revealed: cell.revealed,
                            pending,
                            firstToSolve: cell.firstToSolve,
                          })} ${wasChanged ? "aura-scoreboard-cell-updated" : ""}`}
                        >
                          {pending ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : cell.hidden && !cell.revealed ? (
                            <>
                              <Lock className="h-4 w-4" />
                              <span className="mt-0.5 text-[10px] font-bold uppercase tracking-normal">Hidden</span>
                            </>
                          ) : solved ? (
                            <>
                              <span className="flex items-center gap-1 text-xs font-bold">
                                {cell.firstToSolve ? (
                                  <span className="inline-flex items-center rounded-full bg-amber-100 px-1 py-0.5 text-amber-800">
                                    <Medal className="h-3.5 w-3.5" />
                                  </span>
                                ) : (
                                  <CheckCircle2 className="h-3.5 w-3.5" />
                                )}
                                {formatMinutes(cell.solvedTimeMinutes)}
                              </span>
                              <span className="mt-0.5 text-[11px] font-semibold">
                                {cell.wrongAttempts > 0 ? `+${cell.wrongAttempts}` : "AC"}
                              </span>
                            </>
                          ) : attempted ? (
                            <span className="text-xs font-bold">-{cell.attempts}</span>
                          ) : (
                            <Minus className="h-4 w-4" />
                          )}
                        </div>
                      </td>
                    );
                  })}
                  <td className="px-3 py-3 text-center font-semibold text-slate-950">
                    {row.solvedCount}
                  </td>
                  <td className="px-3 py-3 text-center font-mono text-slate-900">
                    {row.totalPenalty}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
