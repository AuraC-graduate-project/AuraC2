import { AlertCircle, CheckCircle2, Circle, Clock, Loader2, RefreshCw, XCircle } from "lucide-react";
import { ProblemResponse } from "../../admin/types/api";
import { Button } from "./ui/button";

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
  isLoading = false,
  error,
  onRetry,
  className = "",
}: {
  problems: Problem[];
  selectedProblem: ProblemResponse | null;
  onSelectProblem: (p: ProblemResponse) => void;
  isLoading?: boolean;
  error?: string | null;
  onRetry?: () => void;
  className?: string;
}) {
  return (
    <aside className={`aura-problem-rail flex h-full min-h-0 shrink-0 flex-col overflow-hidden border-r border-slate-200 bg-white ${className}`}>
      <div className="shrink-0 border-b border-slate-200 p-4">
        <p className="text-xs font-semibold uppercase tracking-wide text-blue-700">Problems</p>
        <h2 className="mt-1 text-base font-semibold text-slate-950">{problems.length} available</h2>
      </div>

      <div className="min-h-0 flex-1 space-y-2 overflow-y-auto p-3">
        {isLoading ? (
          <div className="flex items-center gap-2 rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-600">
            <Loader2 className="h-4 w-4 animate-spin text-blue-700" />
            Loading problems...
          </div>
        ) : error ? (
          <div className="rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-700">
            <div className="flex gap-2">
              <AlertCircle className="mt-0.5 h-4 w-4 shrink-0" />
              <span>{error}</span>
            </div>
            {onRetry && (
              <Button size="sm" variant="outline" className="mt-3 w-full gap-2 bg-white" onClick={onRetry}>
                <RefreshCw className="h-4 w-4" />
                Retry
              </Button>
            )}
          </div>
        ) : problems.length === 0 ? (
          <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-4 text-sm leading-6 text-slate-500">
            No problems have been published for this contest yet.
          </div>
        ) : (
          problems.map((problem, index) => {
            const active = selectedProblem?.id === problem.id;
            const solved = problem.status === "solved";
            const attempted = problem.status === "wrong";
            const pending = problem.status === "pending";

            return (
              <button
                key={problem.id}
                type="button"
                onClick={() => onSelectProblem(problem)}
                aria-current={active ? "true" : undefined}
                data-status={problem.status}
                className={`aura-problem-card w-full rounded-lg border p-3 text-left transition ${
                  active && solved
                    ? "border-blue-300 bg-blue-50"
                    : active
                      ? "border-blue-300 bg-blue-50"
                      : solved
                        ? "border-slate-200 bg-white hover:border-emerald-300 hover:bg-slate-50"
                        : pending
                          ? "border-amber-200 bg-amber-50/45 hover:border-amber-300 hover:bg-amber-50/60"
                          : attempted
                            ? "border-rose-200 bg-white hover:border-rose-200 hover:bg-rose-50/40"
                            : "border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50"
                }`}
              >
                <div className="flex items-start gap-3">
                  <div
                    className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-md border bg-white font-semibold ${
                      solved
                        ? "border-emerald-300 text-emerald-700"
                        : active
                          ? "border-blue-200 text-blue-700"
                          : "border-slate-200 text-slate-700"
                    }`}
                  >
                    {String.fromCharCode(65 + index)}
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate font-semibold text-slate-900">{problem.title}</p>
                    <div
                      className={`mt-2 flex items-center gap-2 text-xs font-medium ${
                        solved ? "text-emerald-700" : "text-slate-500"
                      }`}
                    >
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
