import React from "react";
import { CheckCircle2, XCircle, Clock, Circle } from "lucide-react";

export type ProblemStatus =
  | "solved"    // ACCEPTED exists
  | "wrong"     // submissions exist but none accepted
  | "pending"   // only PENDING / RUNNING
  | "unsolved"; // no submissions at all

type Problem = {
  id: number;
  title: string;
  status: ProblemStatus; // 🔒 REQUIRED
};

export function ProblemSidebar({
  problems,
  selectedProblem,
  onSelectProblem,
}: {
  problems: Problem[];
  selectedProblem: Problem | null;
  onSelectProblem: (p: Problem) => void;
}) {
  const getStatusIcon = (status: ProblemStatus) => {
    switch (status) {
      case "solved":
        return <CheckCircle2 className="w-5 h-5 text-green-600" />;

      case "wrong":
        return <XCircle className="w-5 h-5 text-red-600" />;

      case "pending":
        return <Clock className="w-5 h-5 text-yellow-600" />;

      case "unsolved":
      default:
        return <Circle className="w-5 h-5 text-gray-400" />;
    }
  };

  const getStatusStyle = (status: ProblemStatus) => {
    switch (status) {
      case "solved":
        return "bg-green-50 border-green-200";

      case "wrong":
        return "bg-red-50 border-red-200";

      case "pending":
        return "bg-yellow-50 border-yellow-200";

      case "unsolved":
      default:
        return "bg-gray-50 border-gray-200";
    }
  };

  return (
    <aside className="w-64 bg-white border-r border-gray-200 shadow-sm">
      <div className="p-4 border-b border-gray-200">
        <h2 className="text-gray-900 font-semibold">Problems</h2>
      </div>

      <div className="p-3 space-y-2">
        {problems.map((p, idx) => (
          <button
            key={p.id}
            onClick={() => onSelectProblem(p)}
            className={`
              w-full flex items-center gap-3 p-3 rounded-lg border transition-all
              ${getStatusStyle(p.status)}
              ${selectedProblem?.id === p.id
                ? "ring-2 ring-[#FACC15] shadow-md"
                : "hover:shadow-md"
              }
            `}
          >
            {getStatusIcon(p.status)}

            <div className="flex-1 text-left">
              <div className="text-gray-900 font-medium">
                {String.fromCharCode(65 + idx)}
              </div>
              <div className="text-sm text-gray-600">{p.title}</div>
            </div>
          </button>
        ))}
      </div>
    </aside>
  );
}
