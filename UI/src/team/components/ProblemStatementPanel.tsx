import { useEffect, useState } from "react";
import { BookOpen, Copy, Database, FileText, FlaskConical, Timer } from "lucide-react";
import { ProblemResponse, TestCaseResponse } from "../../admin/types/api";
import { StatusBadge } from "../../components/StatusBadge";
import { RichTextContent } from "../../components/RichTextContent";
import { getPublicTestCasesForProblem } from "../services/teamApi";

type ProblemStatementPanelProps = {
  problem: ProblemResponse | null;
  className?: string;
};

export function ProblemStatementPanel({
  problem,
  className = "",
}: ProblemStatementPanelProps) {
  const [samples, setSamples] = useState<TestCaseResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [activeSection, setActiveSection] = useState<"statement" | "samples">("statement");

  const copyText = async (value: string) => {
    try {
      await navigator.clipboard?.writeText(value);
    } catch {
      // Clipboard access can be blocked by the browser; selection still works.
    }
  };

  useEffect(() => {
    let mounted = true;

    if (!problem?.id) {
      setSamples([]);
      return;
    }

    setLoading(true);
    getPublicTestCasesForProblem(problem.id)
      .then((items) => {
        if (mounted) setSamples(items);
      })
      .catch(() => {
        if (mounted) setSamples([]);
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, [problem?.id]);

  if (!problem) {
    return (
      <section className={`rounded-lg border border-slate-200 bg-white p-8 text-center shadow-sm ${className}`}>
        <BookOpen className="mx-auto mb-3 h-8 w-8 text-slate-400" />
        <p className="font-medium text-slate-800">Select a problem to view the statement.</p>
        <p className="mt-1 text-sm text-slate-500">Problem statements load from the contest problem API.</p>
      </section>
    );
  }

  return (
    <section className={`aura-statement-card flex h-full min-h-0 flex-col rounded-lg border border-slate-200 bg-white shadow-sm ${className}`}>
      <div className="shrink-0 border-b border-slate-200 bg-slate-50 p-5">
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <StatusBadge kind="difficulty" value={problem.difficulty} />
          <span className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-600">
            <Timer className="h-3.5 w-3.5" />
            {problem.timeLimit} ms
          </span>
          <span className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-600">
            <Database className="h-3.5 w-3.5" />
            {problem.memoryLimit} MB
          </span>
        </div>
        <h2 className="text-2xl font-semibold leading-tight text-slate-950">{problem.title}</h2>
        <div className="mt-5 grid w-full grid-cols-2 rounded-lg border border-slate-200 bg-white p-1 shadow-sm">
          <button
            type="button"
            onClick={() => setActiveSection("statement")}
            className={`inline-flex items-center justify-center gap-2 rounded-md px-3 py-2 text-sm font-semibold ${
              activeSection === "statement"
                ? "bg-blue-700 text-white"
                : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
            }`}
          >
            <FileText className="h-4 w-4" />
            Question
          </button>
          <button
            type="button"
            onClick={() => setActiveSection("samples")}
            className={`inline-flex items-center justify-center gap-2 rounded-md px-3 py-2 text-sm font-semibold ${
              activeSection === "samples"
                ? "bg-blue-700 text-white"
                : "text-slate-600 hover:bg-slate-100 hover:text-slate-900"
            }`}
          >
            <FlaskConical className="h-4 w-4" />
            Test Cases
          </button>
        </div>
      </div>

      <div className="aura-panel-switch min-h-0 flex-1 space-y-6 overflow-y-auto p-5" key={activeSection}>
        {activeSection === "statement" ? (
          <div>
            <h3 className="mb-2 font-semibold text-slate-900">Question Statement</h3>
            <RichTextContent content={problem.description} className="max-w-[78ch]" />
          </div>
        ) : (
          <div>
            {loading ? (
              <p className="rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm text-slate-500">
                Loading samples...
              </p>
            ) : samples.length === 0 ? (
              <p className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-4 text-sm text-slate-500">
                No sample test cases are available for this problem.
              </p>
            ) : (
              <div className="space-y-4">
                {samples.map((sample, index) => (
                  <article key={sample.id} className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                    <h4 className="mb-3 text-sm font-semibold text-slate-800">Sample #{index + 1}</h4>
                    <div className="grid gap-3">
                      <div>
                        <div className="mb-1 flex items-center justify-between gap-2">
                          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Input</p>
                          <button
                            type="button"
                            onClick={() => copyText(sample.inputData)}
                            className="inline-flex items-center gap-1 text-xs font-semibold text-blue-700 hover:text-blue-800"
                          >
                            <Copy className="h-3.5 w-3.5" />
                            Copy
                          </button>
                        </div>
                        <pre className="overflow-x-auto rounded-md bg-slate-950 p-3 text-xs text-slate-100">{sample.inputData}</pre>
                      </div>
                      <div>
                        <div className="mb-1 flex items-center justify-between gap-2">
                          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Expected Output</p>
                          <button
                            type="button"
                            onClick={() => copyText(sample.expectedOutput)}
                            className="inline-flex items-center gap-1 text-xs font-semibold text-blue-700 hover:text-blue-800"
                          >
                            <Copy className="h-3.5 w-3.5" />
                            Copy
                          </button>
                        </div>
                        <pre className="overflow-x-auto rounded-md border border-slate-200 bg-white p-3 text-xs text-slate-800">
                          {sample.expectedOutput}
                        </pre>
                      </div>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </section>
  );
}
