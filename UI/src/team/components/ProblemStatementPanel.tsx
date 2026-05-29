import { useEffect, useMemo, useState } from "react";
import {
  AlertTriangle,
  BookOpen,
  CheckCircle2,
  ChevronDown,
  Copy,
  Database,
  FileText,
  FlaskConical,
  LockKeyhole,
  Pencil,
  Plus,
  Save,
  Timer,
  Trash2,
  X,
  XCircle,
} from "lucide-react";
import { ProblemResponse, TestCaseResponse } from "../../admin/types/api";
import { StatusBadge } from "../../components/StatusBadge";
import { RichTextContent } from "../../components/RichTextContent";
import { effectiveProblemStatement } from "../../components/ProblemStatementPreview";
import { richTextToPlainText } from "../../components/richText";
import type { CustomTestCaseResponse, RunCaseResult, RunResponse } from "../services/teamApi";
import { Button } from "./ui/button";

type ProblemPanelSection = "statement" | "samples";

type ProblemStatementPanelProps = {
  problem: ProblemResponse | null;
  className?: string;
  activeSection?: ProblemPanelSection;
  onActiveSectionChange?: (section: ProblemPanelSection) => void;
  samples?: TestCaseResponse[];
  customTests?: CustomTestCaseResponse[];
  loadingTestCases?: boolean;
  runResponse?: RunResponse | null;
  runError?: string | null;
  isRunning?: boolean;
  savingCustomTest?: boolean;
  updatingCustomTestId?: number | null;
  deletingCustomTestId?: number | null;
  onAddCustomTest?: (body: { input: string; expectedOutput: string | null }) => Promise<void>;
  onUpdateCustomTest?: (id: number, body: { input: string; expectedOutput: string | null }) => Promise<void>;
  onDeleteCustomTest?: (id: number) => Promise<void>;
};

type VerdictMeta = {
  badge: string;
  label: string;
  icon: typeof CheckCircle2;
  className: string;
};

const MAX_BLOCK_LENGTH = 2400;

function truncateBlock(value: string | null | undefined): string {
  if (!value) return "";
  return value.length > MAX_BLOCK_LENGTH
    ? `${value.slice(0, MAX_BLOCK_LENGTH)}\n...`
    : value;
}

function verdictMeta(status?: string | null): VerdictMeta {
  switch (status) {
    case "PASSED":
      return {
        badge: "AC",
        label: "Passed",
        icon: CheckCircle2,
        className: "border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-800 dark:bg-emerald-950/30 dark:text-emerald-200",
      };
    case "WRONG_ANSWER":
      return {
        badge: "WA",
        label: "Wrong Answer",
        icon: XCircle,
        className: "border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-800 dark:bg-rose-950/30 dark:text-rose-200",
      };
    case "TIME_LIMIT_EXCEEDED":
      return {
        badge: "TLE",
        label: "Time Limit Exceeded",
        icon: AlertTriangle,
        className: "border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-200",
      };
    case "RUNTIME_ERROR":
      return {
        badge: "RE",
        label: "Runtime Error",
        icon: AlertTriangle,
        className: "border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-800 dark:bg-rose-950/30 dark:text-rose-200",
      };
    case "COMPILATION_ERROR":
      return {
        badge: "CE",
        label: "Compilation Error",
        icon: AlertTriangle,
        className: "border-violet-200 bg-violet-50 text-violet-700 dark:border-violet-800 dark:bg-violet-950/30 dark:text-violet-200",
      };
    case "SKIPPED":
      return {
        badge: "SKIPPED",
        label: "Not Run",
        icon: AlertTriangle,
        className: "border-slate-200 bg-slate-50 text-slate-600 dark:border-slate-700 dark:bg-slate-900/60 dark:text-slate-300",
      };
    case "RUN_COMPLETED":
      return {
        badge: "RUN COMPLETED",
        label: "No expected output",
        icon: CheckCircle2,
        className: "border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-800 dark:bg-blue-950/30 dark:text-blue-200",
      };
    case "VALIDATION_ERROR":
      return {
        badge: "INVALID",
        label: "Validation Error",
        icon: AlertTriangle,
        className: "border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-200",
      };
    case "INTERNAL_ERROR":
      return {
        badge: "ERROR",
        label: "Internal Error",
        icon: AlertTriangle,
        className: "border-rose-200 bg-rose-50 text-rose-700 dark:border-rose-800 dark:bg-rose-950/30 dark:text-rose-200",
      };
    case "RUNNING":
      return {
        badge: "RUNNING",
        label: "Running",
        icon: FlaskConical,
        className: "border-blue-200 bg-blue-50 text-blue-700 dark:border-blue-800 dark:bg-blue-950/30 dark:text-blue-200",
      };
    default:
      return {
        badge: "NOT RUN",
        label: "Not Run",
        icon: AlertTriangle,
        className: "border-slate-200 bg-white text-slate-500 dark:border-slate-700 dark:bg-slate-900/40 dark:text-slate-400",
      };
  }
}

function ResultBadge({ status }: { status?: string | null }) {
  const meta = verdictMeta(status);
  const Icon = meta.icon;
  return (
    <span className={`inline-flex items-center gap-1 rounded-full border px-2 py-0.5 text-[11px] font-bold uppercase ${meta.className}`}>
      <Icon className="h-3 w-3" />
      {meta.badge}
    </span>
  );
}

function resultKey(caseType: string, caseId: number | null | undefined) {
  return `${caseType}:${caseId ?? "inline"}`;
}

function escapedOutput(value: string | null | undefined): string {
  return JSON.stringify(value ?? "");
}

function normalizedVisible(value: string | null | undefined): string {
  return (value ?? "").replace(/\r\n/g, "\n").replace(/\r/g, "\n").trimEnd();
}

function CodeBlock({
  label,
  value,
  muted = false,
}: {
  label: string;
  value: string | null | undefined;
  muted?: boolean;
}) {
  return (
    <div className="min-w-0">
      <p className="mb-1 text-[11px] font-semibold uppercase tracking-wide text-slate-500">{label}</p>
      <pre
        className={`max-h-40 overflow-auto rounded-md border p-2.5 text-xs leading-5 ${
          muted
            ? "border-slate-200 bg-white text-slate-800 dark:border-slate-700 dark:bg-slate-900/50 dark:text-slate-100"
            : "border-slate-800 bg-slate-950 text-slate-100"
        }`}
      >
        {truncateBlock(value) || " "}
      </pre>
    </div>
  );
}

export function ProblemStatementPanel({
  problem,
  className = "",
  activeSection: controlledSection,
  onActiveSectionChange,
  samples = [],
  customTests = [],
  loadingTestCases = false,
  runResponse = null,
  runError = null,
  isRunning = false,
  savingCustomTest = false,
  updatingCustomTestId = null,
  deletingCustomTestId = null,
  onAddCustomTest,
  onUpdateCustomTest,
  onDeleteCustomTest,
}: ProblemStatementPanelProps) {
  const [localSection, setLocalSection] = useState<ProblemPanelSection>("statement");
  const [expandedKeys, setExpandedKeys] = useState<Set<string>>(new Set());
  const [addOpen, setAddOpen] = useState(false);
  const [customInput, setCustomInput] = useState("");
  const [customExpectedOutput, setCustomExpectedOutput] = useState("");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editInput, setEditInput] = useState("");
  const [editExpectedOutput, setEditExpectedOutput] = useState("");
  const [formError, setFormError] = useState<string | null>(null);
  const activeSection = controlledSection ?? localSection;

  const setActiveSection = (section: ProblemPanelSection) => {
    if (onActiveSectionChange) {
      onActiveSectionChange(section);
    } else {
      setLocalSection(section);
    }
  };

  const copyText = async (value: string) => {
    try {
      await navigator.clipboard?.writeText(value);
    } catch {
      // Clipboard access can be blocked by the browser; selection still works.
    }
  };

  useEffect(() => {
    setExpandedKeys(new Set());
    setAddOpen(false);
    setCustomInput("");
    setCustomExpectedOutput("");
    setEditingId(null);
    setFormError(null);
  }, [problem?.id]);

  const resultByKey = useMemo(() => {
    const map = new Map<string, RunCaseResult>();
    for (const result of runResponse?.results ?? []) {
      map.set(resultKey(result.caseType, result.caseId), result);
    }
    return map;
  }, [runResponse]);

  if (!problem) {
    return (
      <section className={`rounded-lg border border-slate-200 bg-white p-8 text-center shadow-sm ${className}`}>
        <BookOpen className="mx-auto mb-3 h-8 w-8 text-slate-400" />
        <p className="font-medium text-slate-800">Select a problem to view the statement.</p>
        <p className="mt-1 text-sm text-slate-500">Problem statements load from the contest problem API.</p>
      </section>
    );
  }

  const statementSections = [
    { title: "Statement", content: effectiveProblemStatement(problem), emptyText: "No statement provided." },
    { title: "Input", content: problem.inputFormat, emptyText: "No input format provided." },
    { title: "Output", content: problem.outputFormat, emptyText: "No output format provided." },
    { title: "Constraints", content: problem.constraintsText, emptyText: "No constraints provided." },
    { title: "Note", content: problem.publicNotes, emptyText: "No public notes provided." },
  ].filter((section) => hasRichText(section.content));

  const compileError = runResponse?.compileStatus === "COMPILATION_ERROR"
    ? runResponse.compileOutput ?? "Compilation failed."
    : null;

  const handleAddCustomTest = async () => {
    if (!customInput.trim() || !onAddCustomTest) return;
    setFormError(null);
    try {
      await onAddCustomTest({
        input: customInput,
        expectedOutput: customExpectedOutput.trim() ? customExpectedOutput : null,
      });
      setCustomInput("");
      setCustomExpectedOutput("");
      setAddOpen(false);
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Could not add custom test.");
    }
  };

  const startEditing = (test: CustomTestCaseResponse) => {
    setEditingId(test.id);
    setEditInput(test.input);
    setEditExpectedOutput(test.expectedOutput ?? "");
    setFormError(null);
  };

  const handleUpdateCustomTest = async (testId: number) => {
    if (!editInput.trim() || !onUpdateCustomTest) return;
    setFormError(null);
    try {
      await onUpdateCustomTest(testId, {
        input: editInput,
        expectedOutput: editExpectedOutput.trim() ? editExpectedOutput : null,
      });
      setEditingId(null);
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Could not update custom test.");
    }
  };

  const handleDeleteCustomTest = async (testId: number) => {
    if (!onDeleteCustomTest) return;
    setFormError(null);
    try {
      await onDeleteCustomTest(testId);
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Could not delete custom test.");
    }
  };

  const toggleExpanded = (key: string) => {
    setExpandedKeys((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  };

  return (
    <section className={`aura-statement-card flex h-full min-h-0 flex-col rounded-lg border border-slate-200 bg-white shadow-sm ${className}`}>
      <div className="shrink-0 border-b border-slate-200 bg-slate-50 px-4 py-3">
        <div className="mb-2 flex flex-wrap items-center gap-2">
          <StatusBadge kind="difficulty" value={problem.difficulty} />
          <span className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-2 py-0.5 text-xs font-semibold text-slate-600">
            <Timer className="h-3.5 w-3.5" />
            {problem.timeLimit} ms
          </span>
          <span className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-2 py-0.5 text-xs font-semibold text-slate-600">
            <Database className="h-3.5 w-3.5" />
            {problem.memoryLimit} MB
          </span>
        </div>
        <h2 className="truncate text-lg font-semibold leading-tight text-slate-950">{problem.title}</h2>
        <div className="mt-3 grid w-full grid-cols-2 rounded-lg border border-slate-200 bg-white p-1 shadow-sm">
          <button
            type="button"
            onClick={() => setActiveSection("statement")}
            className={`inline-flex h-8 items-center justify-center gap-2 rounded-md px-3 text-sm font-semibold ${
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
            className={`inline-flex h-8 items-center justify-center gap-2 rounded-md px-3 text-sm font-semibold ${
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

      <div className="aura-panel-switch min-h-0 flex-1 overflow-y-auto p-4" key={activeSection}>
        {activeSection === "statement" ? (
          <div className="space-y-5">
            {statementSections.length === 0 ? (
              <p className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-3 text-sm text-slate-500">
                No statement content is available for this problem.
              </p>
            ) : (
              statementSections.map((section) => (
                <StatementSection
                  key={section.title}
                  title={section.title}
                  content={section.content}
                  emptyText={section.emptyText}
                />
              ))
            )}
          </div>
        ) : (
          <div className="space-y-4">
            {runError && (
              <div className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700 dark:border-rose-800 dark:bg-rose-950/30 dark:text-rose-200">
                <div className="flex items-start gap-2">
                  <XCircle className="mt-0.5 h-4 w-4" />
                  <span>{runError}</span>
                </div>
              </div>
            )}

            {compileError && (
              <div className="rounded-lg border border-violet-200 bg-violet-50 p-3 text-sm text-violet-800 dark:border-violet-800 dark:bg-violet-950/30 dark:text-violet-200">
                <div className="mb-2 flex items-center gap-2 font-semibold">
                  <AlertTriangle className="h-4 w-4" />
                  Compilation Error
                </div>
                <pre className="max-h-36 overflow-auto rounded-md border border-violet-200 bg-white p-2.5 text-xs leading-5 text-slate-800 dark:border-violet-800 dark:bg-slate-900/70 dark:text-slate-100">
                  {truncateBlock(compileError)}
                </pre>
              </div>
            )}

            {formError && (
              <p className="rounded-lg border border-amber-200 bg-amber-50 p-2.5 text-sm text-amber-800 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-200">
                {formError}
              </p>
            )}

            <section className="space-y-2">
              <div className="flex items-center justify-between gap-3">
                <div>
                  <h3 className="text-sm font-semibold text-slate-900">Public Samples</h3>
                  <p className="text-xs text-slate-500">{loadingTestCases ? "Loading..." : `${samples.length} locked case${samples.length === 1 ? "" : "s"}`}</p>
                </div>
                <span className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-2 py-0.5 text-xs font-semibold text-slate-500">
                  <LockKeyhole className="h-3.5 w-3.5" />
                  Locked
                </span>
              </div>

              {loadingTestCases ? (
                <p className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-500">
                  Loading samples...
                </p>
              ) : samples.length === 0 ? (
                <p className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-3 text-sm text-slate-500">
                  No sample test cases are available for this problem.
                </p>
              ) : (
                <div className="space-y-2">
                  {samples.map((sample, index) => {
                    const key = resultKey("PUBLIC_SAMPLE", sample.id);
                    return (
                      <TestCaseCard
                        key={sample.id}
                        title={`Public sample ${index + 1}`}
                        label="Public sample"
                        locked
                        input={sample.inputData}
                        expectedOutput={sample.expectedOutput}
                        result={resultByKey.get(key)}
                        running={isRunning}
                        expanded={expandedKeys.has(key)}
                        onToggleExpanded={() => toggleExpanded(key)}
                        onCopy={copyText}
                      />
                    );
                  })}
                </div>
              )}
            </section>

            <section className="space-y-2">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div>
                  <h3 className="text-sm font-semibold text-slate-900">Custom Tests</h3>
                  <p className="text-xs text-slate-500">{customTests.length} private case{customTests.length === 1 ? "" : "s"}</p>
                </div>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setAddOpen((open) => !open);
                    setFormError(null);
                  }}
                  className="h-8 gap-2 bg-white px-3"
                >
                  <Plus className="h-4 w-4" />
                  Add Custom Test
                </Button>
              </div>

              {addOpen && (
                <CustomTestForm
                  input={customInput}
                  expectedOutput={customExpectedOutput}
                  onInputChange={setCustomInput}
                  onExpectedOutputChange={setCustomExpectedOutput}
                  onCancel={() => setAddOpen(false)}
                  onSave={handleAddCustomTest}
                  saving={savingCustomTest}
                  saveLabel="Add"
                />
              )}

              {customTests.length === 0 ? (
                <p className="rounded-lg border border-dashed border-slate-300 bg-slate-50 p-3 text-sm text-slate-500">
                  No custom tests for this problem.
                </p>
              ) : (
                <div className="space-y-2">
                  {customTests.map((test, index) => {
                    const key = resultKey("CUSTOM", test.id);
                    const isEditing = editingId === test.id;

                    return isEditing ? (
                      <CustomTestForm
                        key={test.id}
                        input={editInput}
                        expectedOutput={editExpectedOutput}
                        onInputChange={setEditInput}
                        onExpectedOutputChange={setEditExpectedOutput}
                        onCancel={() => setEditingId(null)}
                        onSave={() => handleUpdateCustomTest(test.id)}
                        saving={updatingCustomTestId === test.id}
                        saveLabel="Save"
                      />
                    ) : (
                      <TestCaseCard
                        key={test.id}
                        title={`Custom test ${index + 1}`}
                        label="Custom test"
                        input={test.input}
                        expectedOutput={test.expectedOutput}
                        result={resultByKey.get(key)}
                        running={isRunning}
                        expanded={expandedKeys.has(key)}
                        onToggleExpanded={() => toggleExpanded(key)}
                        onCopy={copyText}
                        onEdit={() => startEditing(test)}
                        onDelete={() => handleDeleteCustomTest(test.id)}
                        deleting={deletingCustomTestId === test.id}
                      />
                    );
                  })}
                </div>
              )}
            </section>
          </div>
        )}
      </div>
    </section>
  );
}

function TestCaseCard({
  title,
  label,
  input,
  expectedOutput,
  result,
  running,
  locked = false,
  expanded,
  deleting = false,
  onToggleExpanded,
  onCopy,
  onEdit,
  onDelete,
}: {
  title: string;
  label: string;
  input: string;
  expectedOutput?: string | null;
  result?: RunCaseResult;
  running: boolean;
  locked?: boolean;
  expanded: boolean;
  deleting?: boolean;
  onToggleExpanded: () => void;
  onCopy: (value: string) => void;
  onEdit?: () => void;
  onDelete?: () => void;
}) {
  const status = running ? "RUNNING" : result?.status;
  const meta = verdictMeta(status);
  const hasResultDetails = Boolean(result);
  const displayedInput = result?.input ?? input;
  const displayedExpectedOutput = result?.expectedOutput ?? expectedOutput;
  const whitespaceOnlyMismatch =
    result?.status === "WRONG_ANSWER"
    && displayedExpectedOutput != null
    && result.actualOutput != null
    && displayedExpectedOutput !== result.actualOutput
    && normalizedVisible(displayedExpectedOutput) === normalizedVisible(result.actualOutput);

  return (
    <article className={`rounded-lg border p-3 ${meta.className}`}>
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div className="min-w-0">
          <div className="flex flex-wrap items-center gap-2">
            <h4 className="text-sm font-semibold text-slate-900">{title}</h4>
            <span className="rounded-full border border-slate-200 bg-white px-2 py-0.5 text-[11px] font-semibold uppercase text-slate-500">
              {label}
            </span>
            {locked && (
              <span className="inline-flex items-center gap-1 rounded-full border border-slate-200 bg-white px-2 py-0.5 text-[11px] font-semibold uppercase text-slate-500">
                <LockKeyhole className="h-3 w-3" />
                Read-only
              </span>
            )}
          </div>
          <p className="mt-0.5 text-xs text-slate-500">{status ? meta.label : "Not run"}</p>
        </div>

        <div className="flex items-center gap-1.5">
          <ResultBadge status={status} />
          {onEdit && (
            <button
              type="button"
              onClick={onEdit}
              className="rounded-md p-1.5 text-slate-500 hover:bg-white hover:text-blue-700"
              aria-label={`Edit ${title}`}
            >
              <Pencil className="h-4 w-4" />
            </button>
          )}
          {onDelete && (
            <button
              type="button"
              onClick={onDelete}
              disabled={deleting}
              className="rounded-md p-1.5 text-slate-500 hover:bg-white hover:text-rose-700 disabled:opacity-50"
              aria-label={`Delete ${title}`}
            >
              <Trash2 className="h-4 w-4" />
            </button>
          )}
        </div>
      </div>

      <div className="mt-3 grid gap-3 xl:grid-cols-2">
        <CaseValue label="Input" value={displayedInput} onCopy={onCopy} dark />
        {displayedExpectedOutput != null ? (
          <CaseValue label="Expected Output" value={displayedExpectedOutput} onCopy={onCopy} />
        ) : (
          <p className="rounded-md border border-dashed border-slate-300 bg-white p-2.5 text-xs text-slate-500 dark:border-slate-700 dark:bg-slate-900/40 dark:text-slate-300">
            No expected output
          </p>
        )}
      </div>

      {whitespaceOnlyMismatch && (
        <p className="mt-3 rounded-md border border-amber-200 bg-amber-50 p-2.5 text-xs text-amber-800 dark:border-amber-800 dark:bg-amber-950/30 dark:text-amber-200">
          Outputs differ by whitespace, line endings, or hidden characters.
        </p>
      )}

      {hasResultDetails && (
        <div className="mt-3">
          <button
            type="button"
            onClick={onToggleExpanded}
            className="inline-flex items-center gap-1 text-xs font-semibold text-blue-700 hover:text-blue-800"
          >
            <ChevronDown className={`h-4 w-4 transition ${expanded ? "rotate-180" : ""}`} />
            Result details
          </button>

          {expanded && (
            <div className="mt-3 grid gap-3 xl:grid-cols-2">
              <CodeBlock label="Actual Output" value={result.actualOutput} muted />
              {result.stderr && <CodeBlock label="Stderr" value={result.stderr} muted />}
              {result.status === "WRONG_ANSWER" && displayedExpectedOutput != null && (
                <CodeBlock label="Expected Escaped" value={escapedOutput(displayedExpectedOutput)} muted />
              )}
              {result.status === "WRONG_ANSWER" && (
                <CodeBlock label="Actual Escaped" value={escapedOutput(result.actualOutput)} muted />
              )}
              <div className="rounded-md border border-slate-200 bg-white p-2.5 text-xs text-slate-600 dark:border-slate-700 dark:bg-slate-900/50 dark:text-slate-200">
                <p className="font-semibold uppercase tracking-wide text-slate-500">Runtime</p>
                <p className="mt-1">{result.runtimeMillis ?? 0} ms</p>
              </div>
              <div className="rounded-md border border-slate-200 bg-white p-2.5 text-xs text-slate-600 dark:border-slate-700 dark:bg-slate-900/50 dark:text-slate-200">
                <p className="font-semibold uppercase tracking-wide text-slate-500">Memory</p>
                <p className="mt-1">{result.memoryKb != null ? `${result.memoryKb} KB` : "-"}</p>
              </div>
              {result.diagnostic && (
                <p className="xl:col-span-2 rounded-md border border-slate-200 bg-white p-2.5 text-xs text-slate-600 dark:border-slate-700 dark:bg-slate-900/50 dark:text-slate-200">
                  {result.diagnostic}
                </p>
              )}
            </div>
          )}
        </div>
      )}
    </article>
  );
}

function CaseValue({
  label,
  value,
  onCopy,
  dark = false,
}: {
  label: string;
  value: string;
  onCopy: (value: string) => void;
  dark?: boolean;
}) {
  return (
    <div className="min-w-0">
      <div className="mb-1 flex items-center justify-between gap-2">
        <p className="text-[11px] font-semibold uppercase tracking-wide text-slate-500">{label}</p>
        <button
          type="button"
          onClick={() => onCopy(value)}
          className="inline-flex items-center gap-1 text-xs font-semibold text-blue-700 hover:text-blue-800"
        >
          <Copy className="h-3.5 w-3.5" />
          Copy
        </button>
      </div>
      <pre
        className={`max-h-32 overflow-auto rounded-md border p-2.5 text-xs leading-5 ${
          dark
            ? "border-slate-800 bg-slate-950 text-slate-100"
            : "border-slate-200 bg-white text-slate-800 dark:border-slate-700 dark:bg-slate-900/50 dark:text-slate-100"
        }`}
      >
        {truncateBlock(value) || " "}
      </pre>
    </div>
  );
}

function CustomTestForm({
  input,
  expectedOutput,
  onInputChange,
  onExpectedOutputChange,
  onCancel,
  onSave,
  saving,
  saveLabel,
}: {
  input: string;
  expectedOutput: string;
  onInputChange: (value: string) => void;
  onExpectedOutputChange: (value: string) => void;
  onCancel: () => void;
  onSave: () => void;
  saving: boolean;
  saveLabel: string;
}) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-3 dark:border-slate-700 dark:bg-slate-900/40">
      <div className="grid gap-3">
        <textarea
          value={input}
          onChange={(event) => onInputChange(event.target.value)}
          className="min-h-20 w-full resize-y rounded-md border border-slate-200 bg-white p-3 font-mono text-xs text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
          placeholder="Custom input"
          spellCheck={false}
        />
        <textarea
          value={expectedOutput}
          onChange={(event) => onExpectedOutputChange(event.target.value)}
          className="min-h-16 w-full resize-y rounded-md border border-slate-200 bg-white p-3 font-mono text-xs text-slate-900 outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-100 dark:border-slate-700 dark:bg-slate-950 dark:text-slate-100"
          placeholder="Expected output (optional)"
          spellCheck={false}
        />
      </div>
      <div className="mt-3 flex justify-end gap-2">
        <Button type="button" variant="outline" onClick={onCancel} className="h-8 gap-2 bg-white px-3">
          <X className="h-4 w-4" />
          Cancel
        </Button>
        <Button
          type="button"
          onClick={onSave}
          disabled={saving || !input.trim()}
          className="h-8 gap-2 bg-blue-700 px-3 hover:bg-blue-800"
        >
          <Save className="h-4 w-4" />
          {saving ? "Saving..." : saveLabel}
        </Button>
      </div>
    </div>
  );
}

function hasRichText(value: string | null | undefined) {
  return Boolean(richTextToPlainText(value));
}

function StatementSection({
  title,
  content,
  emptyText,
}: {
  title: string;
  content?: string | null;
  emptyText: string;
}) {
  return (
    <section>
      <h3 className="mb-2 font-semibold text-slate-900">{title}</h3>
      <RichTextContent content={content} emptyText={emptyText} className="max-w-[78ch]" />
    </section>
  );
}
