import { CheckCircle2, Loader2, Lock, Medal, Minus } from "lucide-react";
import type { ScoreboardSnapshot } from "../../admin/types/api";

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
  if (pending) return "animate-pulse border-amber-300 bg-amber-50 text-amber-800";
  if (hidden && !revealed) return "border-slate-300 bg-slate-100 text-slate-600";
  if (solved) {
    return firstToSolve
      ? "border-amber-300 bg-amber-50 text-amber-900"
      : "border-emerald-200 bg-emerald-50 text-emerald-800";
  }
  if (attempts > 0) return "border-rose-200 bg-rose-50 text-rose-800";
  return "border-slate-200 bg-white text-slate-400";
}

export function ScoreboardTable({
  snapshot,
  changedTeamIds = [],
  presentationMode = false,
}: {
  snapshot: ScoreboardSnapshot;
  changedTeamIds?: number[];
  presentationMode?: boolean;
}) {
  const changed = new Set(changedTeamIds);
  const columns = snapshot.metadata.problemColumns;

  return (
    <div className={`overflow-hidden rounded-lg border border-slate-200 bg-white ${presentationMode ? "aura-scoreboard-presentation-table" : ""}`}>
      <div className="overflow-x-auto">
        <table className={`min-w-full border-collapse ${presentationMode ? "text-base" : "text-sm"}`}>
          <thead className="bg-slate-50 text-xs uppercase text-slate-500">
            <tr>
              <th className="sticky left-0 z-10 w-16 border-b border-slate-200 bg-slate-50 px-3 py-3 text-left">
                Rank
              </th>
              <th className="sticky left-16 z-10 min-w-56 border-b border-slate-200 bg-slate-50 px-3 py-3 text-left">
                Team
              </th>
              {columns.map((problem) => (
                <th
                  key={problem.problemId}
                  className="w-24 border-b border-slate-200 px-2 py-3 text-center"
                  title={problem.title}
                >
                  {problem.label}
                </th>
              ))}
              <th className="w-20 border-b border-slate-200 px-3 py-3 text-center">Solved</th>
              <th className="w-24 border-b border-slate-200 px-3 py-3 text-center">Penalty</th>
            </tr>
          </thead>
          <tbody>
            {snapshot.rows.map((row) => {
              const wasChanged = changed.has(row.teamId);
              return (
                <tr
                  key={row.teamId}
                  className={`border-b border-slate-100 transition-colors last:border-b-0 ${
                    wasChanged ? "bg-blue-50/70" : "bg-white hover:bg-slate-50"
                  }`}
                >
                  <td className="sticky left-0 z-10 bg-inherit px-3 py-3 font-mono font-semibold text-slate-800">
                    {row.rank}
                  </td>
                  <td className="sticky left-16 z-10 bg-inherit px-3 py-3">
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
                            <Lock className="h-4 w-4" />
                          ) : solved ? (
                            <>
                              <span className="flex items-center gap-1 text-xs font-bold">
                                {cell.firstToSolve ? <Medal className="h-3.5 w-3.5" /> : <CheckCircle2 className="h-3.5 w-3.5" />}
                                {formatMinutes(cell.solvedTimeMinutes)}
                              </span>
                              <span className="mt-0.5 text-[11px] font-medium">
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
                  <td className="px-3 py-3 text-center font-mono text-slate-800">
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
