import { ArrowDownRight, ArrowUpRight, CheckCircle2, Loader2, Lock, Medal, Minus } from "lucide-react";
import { useLayoutEffect, useRef, type CSSProperties } from "react";
import type { ScoreboardProblemCell, ScoreboardSnapshot } from "../../admin/types/api";

export type ScoreboardRankChange = "up" | "down";

type ScoreCellState = "not-attempted" | "attempted" | "solved" | "first-solve" | "pending" | "frozen";

const DEFAULT_BALLOON_COLOR = "#2563EB";
const HEX_COLOR_PATTERN = /^#[0-9A-Fa-f]{6}$/;

const SCOREBOARD_LEGEND: Array<{ label: string; state: ScoreCellState }> = [
  { label: "First to solve", state: "first-solve" },
  { label: "Solved", state: "solved" },
  { label: "Attempted", state: "attempted" },
  { label: "Pending", state: "pending" },
  { label: "Frozen/Hidden", state: "frozen" },
];

function formatMinutes(value: number | null | undefined): string {
  if (value == null) return "";
  return `${value}`;
}

function normalizeHexColor(value: string | null | undefined): string {
  const trimmed = value?.trim() ?? "";
  if (!trimmed) return DEFAULT_BALLOON_COLOR;
  const withHash = trimmed.startsWith("#") ? trimmed : `#${trimmed}`;
  return HEX_COLOR_PATTERN.test(withHash) ? withHash.toUpperCase() : DEFAULT_BALLOON_COLOR;
}

function readableTextColor(hexColor: string): string {
  const color = normalizeHexColor(hexColor).slice(1);
  const red = Number.parseInt(color.slice(0, 2), 16);
  const green = Number.parseInt(color.slice(2, 4), 16);
  const blue = Number.parseInt(color.slice(4, 6), 16);
  const luminance = (0.2126 * red + 0.7152 * green + 0.0722 * blue) / 255;
  return luminance > 0.58 ? "#0F172A" : "#F8FAFC";
}

function balloonStyle(color: string): CSSProperties {
  const balloonColor = normalizeHexColor(color);
  return {
    "--balloon-color": balloonColor,
    "--balloon-text": readableTextColor(balloonColor),
  } as CSSProperties;
}

function pendingCountFor(cell: ScoreboardProblemCell): number {
  return Math.max(cell.pendingCount ?? 0, cell.pending ? 1 : 0);
}

function wrongAttemptCountFor(cell: ScoreboardProblemCell): number {
  return cell.wrongAttempts > 0 ? cell.wrongAttempts : cell.attempts;
}

function stateForCell(cell: ScoreboardProblemCell): ScoreCellState {
  if (cell.hidden && !cell.revealed) return "frozen";
  if (!cell.solved && pendingCountFor(cell) > 0) return "pending";
  if (cell.solved && cell.firstToSolve) return "first-solve";
  if (cell.solved) return "solved";
  if (wrongAttemptCountFor(cell) > 0) return "attempted";
  return "not-attempted";
}

function cellTitle(cell: ScoreboardProblemCell): string {
  const pendingCount = pendingCountFor(cell);
  const wrongAttempts = wrongAttemptCountFor(cell);

  if (cell.hidden && !cell.revealed) return "Frozen cell with hidden post-freeze activity";
  if (!cell.solved && pendingCount > 0) {
    return `${pendingCount} submission${pendingCount === 1 ? "" : "s"} still judging`;
  }
  if (cell.solved) {
    return `Accepted on attempt ${cell.attempts} at ${cell.solvedTimeMinutes} min with ${cell.wrongAttempts} wrong attempt(s)`;
  }
  if (wrongAttempts > 0) return `${wrongAttempts} unsuccessful attempt(s)`;
  return "No visible attempts";
}

function cellTone(state: ScoreCellState): string {
  return `aura-scoreboard-cell--${state}`;
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
          duration: 1200,
          easing: "cubic-bezier(0.16, 1, 0.3, 1)",
        }
      );
    });

    previousRowTops.current = nextTops;
  }, [snapshot.rows]);

  return (
    <div className={`overflow-hidden rounded-lg border border-slate-300 bg-white ${presentationMode ? "aura-scoreboard-presentation-table" : ""}`}>
      <div className="flex flex-wrap items-center gap-2 border-b border-slate-200 bg-slate-50 px-3 py-2 text-xs font-semibold text-slate-600">
        <span className="mr-1 text-slate-500">Legend</span>
        {SCOREBOARD_LEGEND.map((item) => (
          <span key={item.state} className="inline-flex items-center gap-1.5 rounded-full border border-slate-200 bg-white px-2 py-1">
            <span className={`aura-scoreboard-legend-swatch ${cellTone(item.state)}`} aria-hidden="true" />
            {item.label}
          </span>
        ))}
      </div>

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
                  className="w-24 border-b border-slate-300 px-2 py-2 text-center"
                  title={`${problem.label}: ${problem.title}`}
                >
                  <span className="inline-flex flex-col items-center gap-1">
                    <span className="aura-scoreboard-balloon" style={balloonStyle(problem.balloonColor)}>
                      {problem.label}
                    </span>
                  </span>
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
                  className={`border-b border-slate-200 transition-[background-color,box-shadow,transform] duration-700 last:border-b-0 ${rowTone(wasChanged, rankChange)}`}
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
                    const state = stateForCell(cell);
                    const pendingCount = pendingCountFor(cell);
                    const wrongAttempts = wrongAttemptCountFor(cell);
                    const revealAccent = cell.revealed ? "aura-scoreboard-cell-revealed" : "";
                    return (
                      <td key={cell.problemId} className="px-2 py-2 text-center">
                        <div
                          title={cellTitle(cell)}
                          className={`aura-scoreboard-cell mx-auto flex min-h-12 flex-col items-center justify-center rounded-md border px-2 py-1 text-center transition-colors duration-300 ${
                            presentationMode ? "w-24 min-h-16" : "w-20"
                          } ${cellTone(state)} ${revealAccent} ${wasChanged ? "aura-scoreboard-cell-updated" : ""}`}
                        >
                          {state === "pending" ? (
                            <>
                              <Loader2 className="h-4 w-4 animate-spin" />
                              <span className="mt-0.5 text-[10px] font-bold uppercase tracking-normal">
                                {pendingCount > 1 ? `${pendingCount} pending` : "Pending"}
                              </span>
                            </>
                          ) : state === "frozen" ? (
                            <>
                              <Lock className="h-4 w-4" />
                              <span className="mt-0.5 text-[10px] font-bold uppercase tracking-normal">Hidden</span>
                            </>
                          ) : state === "solved" || state === "first-solve" ? (
                            <>
                              <span className="flex items-center gap-1 text-xs font-bold">
                                {state === "first-solve" ? (
                                  <span className="inline-flex items-center rounded-full bg-white/20 px-1 py-0.5">
                                    <Medal className="h-3.5 w-3.5" />
                                  </span>
                                ) : (
                                  <CheckCircle2 className="h-3.5 w-3.5" />
                                )}
                                {cell.attempts} / {formatMinutes(cell.solvedTimeMinutes)}
                              </span>
                              <span className="mt-0.5 text-[11px] font-semibold">
                                {state === "first-solve" ? "FTS" : "AC"}
                              </span>
                            </>
                          ) : state === "attempted" ? (
                            <span className="text-sm font-bold">-{wrongAttempts}</span>
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
