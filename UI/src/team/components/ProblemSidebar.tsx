import { CheckCircle2, Circle, Clock, XCircle } from "lucide-react";
import { ProblemResponse } from "../../admin/types/api";

export type ProblemStatus =
  | "solved"
  | "wrong"
  | "pending"
  | "unsolved";

type Problem = ProblemResponse & { status: ProblemStatus };

function statusIcon(status: ProblemStatus) {
  switch (status) {
    case "solved":
      return <CheckCircle2 className="h-4 w-4 text-emerald-600" />;
    case "wrong":
      return <XCircle className="h-4 w-4 text-rose-600" />;
    case "pending":
      return <Clock className="h-4 w-4 text-amber-600" />;
    default:
      return <Circle className="h-4 w-4 text-slate-400" />;
  }
}

export function ProblemSidebar({
  problems,
  selectedProblem,
  onSelectProblem,
}: {
  problems: Problem[];
  selectedProblem: Problem | null;
  onSelectProblem: (p: ProblemResponse) => void;
}) {
  return (
    <aside className="aura-problem-rail w-72 shrink-0 overflow-y-auto border-r border-slate-200 bg-white">
      <div className="border-b border-slate-200 p-5">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-700">Problems</p>
        <h2 className="mt-1 text-lg font-semibold text-slate-950">{problems.length} available</h2>
      </div>

      <div className="space-y-2 p-3">
        {problems.length === 0 ? (
          <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-4 text-sm text-slate-500">
            No problems have been published for this contest.
          </div>
        ) : (
          problems.map((problem, index) => {
            const active = selectedProblem?.id === problem.id;
            return (
              <button
                key={problem.id}
                type="button"
                onClick={() => onSelectProblem(problem)}
                className={`aura-problem-card w-full rounded-lg border p-3 text-left transition ${
                  active
                    ? "border-blue-300 bg-blue-50 shadow-sm"
                    : "border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50"
                }`}
              >
                <div className="flex items-start gap-3">
                  <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-md border border-slate-200 bg-white font-semibold text-slate-700">
                    {String.fromCharCode(65 + index)}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-semibold text-slate-900">{problem.title}</p>
                    <div className="mt-2 flex items-center gap-2 text-xs font-medium text-slate-500">
                      {statusIcon(problem.status)}
                      {problem.status === "solved"
                        ? "Solved"
                        : problem.status === "wrong"
                          ? "Attempted"
                          : problem.status === "pending"
                            ? "Judging"
                            : "Unsolved"}
                    </div>
                  </div>
                </div>
              </button>
            );
          })
        )}
      </div>
    </aside>
  );
}
