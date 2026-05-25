import { Database, Timer } from "lucide-react";
import { ProblemResponse, TestCaseResponse } from "../admin/types/api";
import { RichTextContent } from "./RichTextContent";
import { richTextToPlainText } from "./richText";

type ProblemStatementPreviewProps = {
  problem: ProblemResponse;
  samples?: TestCaseResponse[];
  className?: string;
};

export function effectiveProblemStatement(problem: Pick<ProblemResponse, "statement" | "description">) {
  return hasRichText(problem.statement) ? problem.statement : problem.description;
}

export function problemReadinessWarnings(problem: ProblemResponse, publicSampleCount: number) {
  const warnings: string[] = [];
  if (!problem.title.trim()) warnings.push("Missing title.");
  if (!hasRichText(effectiveProblemStatement(problem))) warnings.push("Missing statement.");
  if (!hasRichText(problem.inputFormat)) warnings.push("Missing input format.");
  if (!hasRichText(problem.outputFormat)) warnings.push("Missing output format.");
  if (!hasRichText(problem.constraintsText)) warnings.push("Missing constraints.");
  if (!problem.timeLimit || problem.timeLimit <= 0) warnings.push("Missing positive time limit.");
  if (!problem.memoryLimit || problem.memoryLimit <= 0) warnings.push("Missing positive memory limit.");
  if (publicSampleCount === 0) warnings.push("No public samples are configured.");
  if (!hasRichText(problem.publicNotes)) warnings.push("Public notes are empty; add them when safe clarifications are useful.");
  return warnings;
}

export function ProblemStatementPreview({
  problem,
  samples = [],
  className = "",
}: ProblemStatementPreviewProps) {
  const sections = [
    { title: "Statement", content: effectiveProblemStatement(problem), empty: "No statement provided." },
    { title: "Input", content: problem.inputFormat, empty: "No input format provided." },
    { title: "Output", content: problem.outputFormat, empty: "No output format provided." },
    { title: "Constraints", content: problem.constraintsText, empty: "No constraints provided." },
    { title: "Notes", content: problem.publicNotes, empty: "No public notes provided." },
  ];

  return (
    <article className={`rounded-lg border border-slate-200 bg-white ${className}`}>
      <header className="border-b border-slate-200 bg-slate-50 p-5">
        <div className="mb-3 flex flex-wrap items-center gap-2">
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
      </header>

      <div className="space-y-7 p-5">
        {sections.map((section) => (
          <section key={section.title}>
            <h3 className="mb-2 text-base font-semibold text-slate-950">{section.title}</h3>
            <RichTextContent content={section.content} emptyText={section.empty} className="max-w-[78ch]" />
          </section>
        ))}

        <section>
          <h3 className="mb-3 text-base font-semibold text-slate-950">Samples</h3>
          {samples.length === 0 ? (
            <p className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-4 text-sm text-slate-500">
              No public sample test cases are available for this problem.
            </p>
          ) : (
            <div className="space-y-4">
              {samples.map((sample, index) => (
                <article key={sample.id} className="rounded-lg border border-slate-200 bg-slate-50 p-4">
                  <h4 className="mb-3 text-sm font-semibold text-slate-800">Sample #{index + 1}</h4>
                  <div className="grid gap-3">
                    <SampleBlock title="Input" value={sample.inputData} dark />
                    <SampleBlock title="Output" value={sample.expectedOutput} />
                  </div>
                </article>
              ))}
            </div>
          )}
        </section>
      </div>
    </article>
  );
}

function SampleBlock({ title, value, dark = false }: { title: string; value: string; dark?: boolean }) {
  return (
    <div>
      <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">{title}</p>
      <pre
        className={
          dark
            ? "overflow-x-auto rounded-md bg-slate-950 p-3 text-xs text-slate-100"
            : "overflow-x-auto rounded-md border border-slate-200 bg-white p-3 text-xs text-slate-800"
        }
      >
        {value}
      </pre>
    </div>
  );
}

function hasRichText(value: string | null | undefined) {
  return Boolean(richTextToPlainText(value));
}
