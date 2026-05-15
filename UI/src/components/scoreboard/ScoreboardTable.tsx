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
  pending,
}: {
  solved: boolean;
  attempts: number;
  wrongAttempts: number;
  solvedTimeMinutes: number | null;
  hidden: boolean;
  pending?: boolean;
}) {
  if (pending) return "Judging in progress";
  if (hidden) return "Contains hidden post-freeze activity";
  if (solved) return `Accepted at ${solvedTimeMinutes} min with ${wrongAttempts} wrong attempt(s)`;
  if (attempts > 0) return `${attempts} unsuccessful attempt(s)`;
  return "No visible attempts";
}

export function ScoreboardTable({
  snapshot,
  changedTeamIds = [],
}: {
  snapshot: ScoreboardSnapshot;
  changedTeamIds?: number[];
}) {
  const changed = new Set(changedTeamIds);
  const columns = snapshot.metadata.problemColumns;

  return (
    <div className="overflow-hidden rounded-lg border border-slate-200 bg-white">
      <div className="overflow-x-auto">
        <table className="min-w-full border-collapse text-sm">
          <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
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
                          className={`mx-auto flex min-h-12 w-20 flex-col items-center justify-center rounded-md border px-2 py-1 ${
                            pending
                              ? "animate-pulse border-blue-300 bg-blue-50 text-blue-700"
                              : solved
                                ? cell.firstToSolve
                                  ? "border-amber-300 bg-amber-50 text-amber-900"
                                  : "border-emerald-200 bg-emerald-50 text-emerald-800"
                                : attempted
                                  ? "border-rose-200 bg-rose-50 text-rose-800"
                                  : cell.hidden
                                    ? "border-slate-200 bg-slate-100 text-slate-500"
                                    : "border-slate-200 bg-white text-slate-400"
                          }`}
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
