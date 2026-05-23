import { Dispatch, SetStateAction, useCallback, useEffect, useMemo, useState } from "react";
import {
  CheckCircle2,
  FlaskConical,
  Play,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
  TriangleAlert,
} from "lucide-react";
import { Button } from "./ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "./ui/card";
import { Checkbox } from "./ui/checkbox";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./ui/select";
import { Textarea } from "./ui/textarea";
import {
  configureInputGenerator,
  configureInputValidator,
  configureReferenceSolution,
  createGeneratedTestBatch,
  getCounterexamples,
  getGeneratedTestBatches,
  getInputGenerators,
  getInputValidators,
  getReferenceSolutions,
  promoteAllValidGeneratedTestCases,
  promoteCounterexample,
  promoteGeneratedTestCase,
  promoteSelectedGeneratedTestCases,
  rejudgeProblem,
} from "../services/api";
import {
  CounterexampleResponse,
  GeneratedTestBatchResponse,
  GeneratedTestCaseResponse,
  OracleProgramResponse,
} from "../types/api";
import {
  DEFAULT_VALIDATOR_LANGUAGE_ID,
  getJudge0LanguageOptionById,
  SUPPORTED_JUDGE0_LANGUAGES,
} from "../../constants/judge0Languages";
import { toast } from "sonner";

type ProgramKind = "reference" | "generator" | "validator";
type SubmittingState =
  | ProgramKind
  | "prepare-batch"
  | "counterexample-batch"
  | "promote-one"
  | "promote-selected"
  | "promote-all"
  | "promote-counterexample"
  | "rejudge"
  | null;

type ProgramFormState = {
  languageId: number;
  active: boolean;
  source: string;
  defaultTestCount: number;
};

type PreparationFormState = {
  testCount: string;
  seed: string;
};

type CounterexampleFormState = PreparationFormState & {
  submissionId: string;
};

interface OraclePanelProps {
  problemId: number;
  problemTitle: string;
  contestId: number;
}

const defaultProgramForm = (defaultTestCount = 10): ProgramFormState => ({
  languageId: DEFAULT_VALIDATOR_LANGUAGE_ID,
  active: true,
  source: "",
  defaultTestCount,
});

const defaultPreparationForm: PreparationFormState = {
  testCount: "10",
  seed: "",
};

const defaultCounterexampleForm: CounterexampleFormState = {
  testCount: "10",
  seed: "",
  submissionId: "",
};

function shortHash(hash?: string | null) {
  return hash ? `${hash.slice(0, 10)}...` : "none";
}

function languageLabel(languageId?: number | null) {
  return getJudge0LanguageOptionById(languageId)?.label ?? `Judge0 #${languageId ?? "unknown"}`;
}

function formatDate(value?: string | null) {
  if (!value) return "not recorded";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

function statusClass(status: string) {
  switch (status) {
    case "COMPLETED":
    case "GENERATED":
    case "ACCEPTED":
      return "border-emerald-200 bg-emerald-50 text-emerald-700";
    case "PARTIAL":
    case "INVALID_INPUT":
    case "PENDING":
    case "RUNNING":
      return "border-amber-200 bg-amber-50 text-amber-700";
    case "FAILED":
    case "GENERATOR_FAILED":
    case "REFERENCE_FAILED":
    case "WRONG_ANSWER":
    case "INTERNAL_ERROR":
      return "border-rose-200 bg-rose-50 text-rose-700";
    default:
      return "border-slate-200 bg-slate-50 text-slate-700";
  }
}

function navigateToRejudge(contestId: number) {
  try {
    window.history.pushState({}, "", `/admin/rejudge?contestId=${contestId}`);
    window.dispatchEvent(new PopStateEvent("popstate"));
  } catch {
    window.location.href = `/admin/rejudge?contestId=${contestId}`;
  }
}

export function OraclePanel({ problemId, problemTitle, contestId }: OraclePanelProps) {
  const [referenceSolutions, setReferenceSolutions] = useState<OracleProgramResponse[]>([]);
  const [inputGenerators, setInputGenerators] = useState<OracleProgramResponse[]>([]);
  const [inputValidators, setInputValidators] = useState<OracleProgramResponse[]>([]);
  const [batches, setBatches] = useState<GeneratedTestBatchResponse[]>([]);
  const [counterexamples, setCounterexamples] = useState<CounterexampleResponse[]>([]);
  const [referenceForm, setReferenceForm] = useState<ProgramFormState>(() => defaultProgramForm());
  const [generatorForm, setGeneratorForm] = useState<ProgramFormState>(() => defaultProgramForm(10));
  const [validatorForm, setValidatorForm] = useState<ProgramFormState>(() => defaultProgramForm());
  const [preparationForm, setPreparationForm] = useState<PreparationFormState>(defaultPreparationForm);
  const [counterexampleForm, setCounterexampleForm] = useState<CounterexampleFormState>(defaultCounterexampleForm);
  const [selectedGeneratedCaseIds, setSelectedGeneratedCaseIds] = useState<Set<number>>(new Set());
  const [isLoading, setIsLoading] = useState(false);
  const [submitting, setSubmitting] = useState<SubmittingState>(null);

  const activeReference = useMemo(
    () => referenceSolutions.find((program) => program.active) ?? referenceSolutions[0] ?? null,
    [referenceSolutions]
  );
  const activeGenerator = useMemo(
    () => inputGenerators.find((program) => program.active) ?? inputGenerators[0] ?? null,
    [inputGenerators]
  );
  const activeValidator = useMemo(
    () => inputValidators.find((program) => program.active) ?? inputValidators[0] ?? null,
    [inputValidators]
  );

  const validCandidateCount = useMemo(
    () =>
      batches.reduce(
        (count, batch) =>
          count +
          batch.testCases.filter((testCase) => testCase.status === "GENERATED" && !testCase.promoted).length,
        0
      ),
    [batches]
  );

  const reload = useCallback(async () => {
    setIsLoading(true);
    try {
      const [references, generators, validators, batchList, counterexampleList] = await Promise.all([
        getReferenceSolutions(problemId),
        getInputGenerators(problemId),
        getInputValidators(problemId),
        getGeneratedTestBatches(problemId),
        getCounterexamples(problemId),
      ]);
      setReferenceSolutions(references);
      setInputGenerators(generators);
      setInputValidators(validators);
      setBatches(batchList);
      setCounterexamples(counterexampleList);
      setSelectedGeneratedCaseIds((previous) => {
        const visibleIds = new Set(
          batchList.flatMap((batch) =>
            batch.testCases
              .filter((testCase) => testCase.status === "GENERATED" && !testCase.promoted)
              .map((testCase) => testCase.id)
          )
        );
        return new Set([...previous].filter((id) => visibleIds.has(id)));
      });
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to load oracle data");
    } finally {
      setIsLoading(false);
    }
  }, [problemId]);

  useEffect(() => {
    setReferenceForm(defaultProgramForm());
    setGeneratorForm(defaultProgramForm(10));
    setValidatorForm(defaultProgramForm());
    setPreparationForm(defaultPreparationForm);
    setCounterexampleForm(defaultCounterexampleForm);
    setSelectedGeneratedCaseIds(new Set());
    void reload();
  }, [problemId, reload]);

  async function submitProgram(kind: ProgramKind) {
    const form =
      kind === "reference" ? referenceForm : kind === "generator" ? generatorForm : validatorForm;
    if (!form.source.trim()) {
      toast.error("Source is required for this configuration update");
      return;
    }

    setSubmitting(kind);
    try {
      const payload = {
        languageId: form.languageId,
        source: form.source.trim(),
        active: form.active,
        defaultTestCount: kind === "generator" ? form.defaultTestCount : undefined,
      };

      if (kind === "reference") {
        await configureReferenceSolution(problemId, payload);
        setReferenceForm(defaultProgramForm());
      } else if (kind === "generator") {
        await configureInputGenerator(problemId, payload);
        setGeneratorForm(defaultProgramForm(form.defaultTestCount));
      } else {
        await configureInputValidator(problemId, payload);
        setValidatorForm(defaultProgramForm());
      }

      toast.success("Oracle configuration saved");
      await reload();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to save oracle configuration");
    } finally {
      setSubmitting(null);
    }
  }

  function parseBatchForm(form: PreparationFormState, requireSubmissionId = false) {
    const testCount = Number(form.testCount);
    const seed = form.seed.trim() ? Number(form.seed) : null;
    const submissionId =
      "submissionId" in form && form.submissionId.trim() ? Number(form.submissionId) : null;

    if (!Number.isInteger(testCount) || testCount <= 0 || testCount > 100) {
      toast.error("Generated batch size must be between 1 and 100");
      return null;
    }
    if (seed != null && !Number.isFinite(seed)) {
      toast.error("Seed must be numeric");
      return null;
    }
    if (requireSubmissionId && (!submissionId || !Number.isInteger(submissionId) || submissionId <= 0)) {
      toast.error("Counterexample search requires a positive Submission ID");
      return null;
    }

    return { testCount, seed, submissionId };
  }

  async function runPreparationBatch() {
    const payload = parseBatchForm(preparationForm);
    if (!payload) return;

    setSubmitting("prepare-batch");
    try {
      await createGeneratedTestBatch(problemId, {
        testCount: payload.testCount,
        seed: payload.seed,
        submissionId: null,
      });
      toast.success("Candidate generated tests created");
      await reload();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to generate candidate tests");
    } finally {
      setSubmitting(null);
    }
  }

  async function runCounterexampleSearch() {
    const payload = parseBatchForm(counterexampleForm, true);
    if (!payload) return;

    setSubmitting("counterexample-batch");
    try {
      await createGeneratedTestBatch(problemId, payload);
      toast.success("Counterexample search completed");
      await reload();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to run counterexample search");
    } finally {
      setSubmitting(null);
    }
  }

  async function promoteOneGeneratedCase(generatedTestCaseId: number) {
    setSubmitting("promote-one");
    try {
      await promoteGeneratedTestCase(generatedTestCaseId);
      toast.success("Generated case promoted to hidden official test");
      await reload();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to promote generated case");
    } finally {
      setSubmitting(null);
    }
  }

  async function promoteSelectedGeneratedCases() {
    const ids = [...selectedGeneratedCaseIds];
    if (ids.length === 0) {
      toast.error("Select at least one valid generated case");
      return;
    }

    setSubmitting("promote-selected");
    try {
      await promoteSelectedGeneratedTestCases({ generatedTestCaseIds: ids });
      toast.success(`${ids.length} generated case(s) promoted`);
      await reload();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to promote selected generated cases");
    } finally {
      setSubmitting(null);
    }
  }

  async function promoteAllValidFromBatch(batchId: number) {
    setSubmitting("promote-all");
    try {
      await promoteAllValidGeneratedTestCases(batchId);
      toast.success("All valid generated cases in this batch were promoted");
      await reload();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to promote generated cases");
    } finally {
      setSubmitting(null);
    }
  }

  async function handlePromoteCounterexample(counterexampleId: number) {
    setSubmitting("promote-counterexample");
    try {
      await promoteCounterexample(counterexampleId);
      toast.success("Counterexample promoted to hidden official test");
      await reload();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to promote counterexample");
    } finally {
      setSubmitting(null);
    }
  }

  async function handleRejudge() {
    setSubmitting("rejudge");
    try {
      await rejudgeProblem(problemId);
      toast.success("Rejudge queued for this problem");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to queue rejudge");
    } finally {
      setSubmitting(null);
    }
  }

  const canGenerate = Boolean(activeReference && activeGenerator);

  return (
    <Card className="border border-gray-200 shadow-sm">
      <CardHeader className="border-b border-slate-200 bg-slate-50">
        <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
          <div>
            <CardTitle className="flex items-center gap-2 text-lg text-slate-950">
              <FlaskConical className="h-5 w-5 text-blue-700" />
              Hybrid Oracle and Generated Tests
            </CardTitle>
            <p className="mt-1 text-sm text-slate-500">
              Prepare hidden tests before the contest, then optionally search for counterexamples against a known submission.
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="outline" className="gap-2 bg-white" onClick={reload} disabled={isLoading}>
              <RefreshCw className="h-4 w-4" />
              Refresh
            </Button>
            <Button type="button" variant="outline" className="gap-2 bg-white" onClick={() => navigateToRejudge(contestId)}>
              <RotateCcw className="h-4 w-4" />
              Open Rejudge
            </Button>
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-8 p-6">
        <SectionHeading
          index="1"
          title="Configuration"
          description="Configure the sandboxed programs used to generate candidate tests and reference outputs."
        />
        <div className="grid gap-3 md:grid-cols-3">
          <StatusTile title="Reference solution" program={activeReference} />
          <StatusTile title="Input generator" program={activeGenerator} />
          <StatusTile title="Input validator" program={activeValidator} optional />
        </div>
        <div className="grid gap-4 xl:grid-cols-3">
          <ProgramSection
            title="Reference Solution"
            description="Runs through Judge0 to produce deterministic reference output."
            form={referenceForm}
            setForm={setReferenceForm}
            latest={activeReference}
            submitting={submitting === "reference"}
            onSubmit={() => submitProgram("reference")}
          />
          <ProgramSection
            title="Input Generator"
            description="Runs through Judge0 using seed and test number on stdin."
            form={generatorForm}
            setForm={setGeneratorForm}
            latest={activeGenerator}
            includeDefaultTestCount
            submitting={submitting === "generator"}
            onSubmit={() => submitProgram("generator")}
          />
          <ProgramSection
            title="Input Validator"
            description="Optional Judge0 program; stdout decision should be VALID/ACCEPT or INVALID/REJECT."
            form={validatorForm}
            setForm={setValidatorForm}
            latest={activeValidator}
            submitting={submitting === "validator"}
            onSubmit={() => submitProgram("validator")}
          />
        </div>

        <SectionHeading
          index="2"
          title="Test Preparation"
          description="Primary workflow: generate candidate hidden tests before the contest, then promote the useful ones."
        />
        <section className="rounded-lg border border-slate-200 bg-white p-4">
          <div className="grid gap-4 lg:grid-cols-[1fr_380px] lg:items-end">
            <div className="space-y-2">
              <h3 className="text-base font-semibold text-slate-950">Generate Candidate Tests</h3>
              <p className="text-sm text-slate-600">Seed lets you reproduce the same generated tests.</p>
              <p className="text-sm text-slate-600">Generated tests are candidates until promoted.</p>
              <p className="text-sm text-slate-600">
                Promoted cases become official hidden tests used by normal submissions.
              </p>
            </div>
            <div className="grid gap-3 sm:grid-cols-[1fr_1fr_auto]">
              <div className="space-y-2">
                <Label htmlFor="oracle-prep-test-count">Tests</Label>
                <Input
                  id="oracle-prep-test-count"
                  type="number"
                  min="1"
                  max="100"
                  value={preparationForm.testCount}
                  onChange={(event) =>
                    setPreparationForm((prev) => ({ ...prev, testCount: event.target.value }))
                  }
                  disabled={submitting === "prepare-batch"}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="oracle-prep-seed">Seed</Label>
                <Input
                  id="oracle-prep-seed"
                  type="number"
                  placeholder="Auto"
                  value={preparationForm.seed}
                  onChange={(event) => setPreparationForm((prev) => ({ ...prev, seed: event.target.value }))}
                  disabled={submitting === "prepare-batch"}
                />
              </div>
              <Button
                type="button"
                className="gap-2 self-end bg-blue-700 hover:bg-blue-800"
                onClick={runPreparationBatch}
                disabled={submitting === "prepare-batch" || !canGenerate}
              >
                <Play className="h-4 w-4" />
                {submitting === "prepare-batch" ? "Generating..." : "Generate Candidates"}
              </Button>
            </div>
          </div>
          {!canGenerate && (
            <p className="mt-3 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
              Configure an active reference solution and input generator before generating candidates.
            </p>
          )}
        </section>

        <SectionHeading
          index="3"
          title="Candidate Generated Tests"
          description="Review generated input/reference output and promote valid candidates into official hidden tests."
        />
        <CandidateGeneratedTests
          batches={batches}
          selectedIds={selectedGeneratedCaseIds}
          setSelectedIds={setSelectedGeneratedCaseIds}
          validCandidateCount={validCandidateCount}
          submitting={submitting}
          onPromoteOne={promoteOneGeneratedCase}
          onPromoteSelected={promoteSelectedGeneratedCases}
          onPromoteAll={promoteAllValidFromBatch}
        />

        <SectionHeading
          index="4"
          title="Counterexample Search"
          description="Secondary workflow: check one existing submission against freshly generated candidates."
        />
        <section className="rounded-lg border border-slate-200 bg-white p-4">
          <div className="grid gap-4 lg:grid-cols-[1fr_520px] lg:items-end">
            <div className="space-y-2">
              <h3 className="text-base font-semibold text-slate-950">Analyze One Submission</h3>
              <p className="text-sm text-slate-600">
                Counterexample search checks one existing submission against generated tests.
              </p>
              <p className="text-sm text-slate-600">
                Counterexamples are concrete failing inputs, not probabilistic predictions.
              </p>
            </div>
            <div className="grid gap-3 sm:grid-cols-[1fr_1fr_1fr_auto]">
              <div className="space-y-2">
                <Label htmlFor="oracle-search-test-count">Tests</Label>
                <Input
                  id="oracle-search-test-count"
                  type="number"
                  min="1"
                  max="100"
                  value={counterexampleForm.testCount}
                  onChange={(event) =>
                    setCounterexampleForm((prev) => ({ ...prev, testCount: event.target.value }))
                  }
                  disabled={submitting === "counterexample-batch"}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="oracle-search-seed">Seed</Label>
                <Input
                  id="oracle-search-seed"
                  type="number"
                  placeholder="Auto"
                  value={counterexampleForm.seed}
                  onChange={(event) =>
                    setCounterexampleForm((prev) => ({ ...prev, seed: event.target.value }))
                  }
                  disabled={submitting === "counterexample-batch"}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="oracle-search-submission">Submission ID</Label>
                <Input
                  id="oracle-search-submission"
                  type="number"
                  placeholder="Required"
                  value={counterexampleForm.submissionId}
                  onChange={(event) =>
                    setCounterexampleForm((prev) => ({ ...prev, submissionId: event.target.value }))
                  }
                  disabled={submitting === "counterexample-batch"}
                />
              </div>
              <Button
                type="button"
                variant="outline"
                className="gap-2 self-end bg-white"
                onClick={runCounterexampleSearch}
                disabled={submitting === "counterexample-batch" || !canGenerate}
              >
                <Play className="h-4 w-4" />
                {submitting === "counterexample-batch" ? "Searching..." : "Search"}
              </Button>
            </div>
          </div>
        </section>

        <SectionHeading
          index="5"
          title="Counterexamples"
          description="Review mismatches found during counterexample search and promote concrete failing inputs when useful."
        />
        <Counterexamples
          counterexamples={counterexamples}
          submitting={submitting}
          onPromoteCounterexample={handlePromoteCounterexample}
          onRejudge={handleRejudge}
        />
      </CardContent>
    </Card>
  );
}

function SectionHeading({
  index,
  title,
  description,
}: {
  index: string;
  title: string;
  description: string;
}) {
  return (
    <div className="flex gap-3">
      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-blue-700 text-sm font-semibold text-white">
        {index}
      </span>
      <div>
        <h2 className="text-lg font-semibold text-slate-950">{title}</h2>
        <p className="mt-1 text-sm text-slate-500">{description}</p>
      </div>
    </div>
  );
}

function StatusTile({
  title,
  program,
  optional = false,
}: {
  title: string;
  program: OracleProgramResponse | null;
  optional?: boolean;
}) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="flex items-center justify-between gap-2">
        <p className="text-sm font-semibold text-slate-950">{title}</p>
        {program ? (
          <CheckCircle2 className="h-4 w-4 text-emerald-600" />
        ) : (
          <TriangleAlert className={optional ? "h-4 w-4 text-slate-400" : "h-4 w-4 text-amber-600"} />
        )}
      </div>
      <p className="mt-2 text-sm text-slate-600">
        {program ? `${languageLabel(program.languageId)} - ${shortHash(program.sourceHash)}` : optional ? "Optional" : "Missing"}
      </p>
      <p className="mt-1 text-xs text-slate-500">Updated {formatDate(program?.updatedAt)}</p>
    </div>
  );
}

function ProgramSection({
  title,
  description,
  form,
  setForm,
  latest,
  includeDefaultTestCount = false,
  submitting,
  onSubmit,
}: {
  title: string;
  description: string;
  form: ProgramFormState;
  setForm: Dispatch<SetStateAction<ProgramFormState>>;
  latest: OracleProgramResponse | null;
  includeDefaultTestCount?: boolean;
  submitting: boolean;
  onSubmit: () => void;
}) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="mb-4">
        <h3 className="text-base font-semibold text-slate-950">{title}</h3>
        <p className="mt-1 text-sm text-slate-500">{description}</p>
        {latest && (
          <p className="mt-2 rounded-md bg-slate-50 px-3 py-2 text-xs text-slate-600">
            Existing source is hidden. Paste new source only if you want to replace it. Current hash:{" "}
            <span className="font-mono">{shortHash(latest.sourceHash)}</span>
          </p>
        )}
      </div>

      <div className="space-y-3">
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-2">
            <Label>{title} Language</Label>
            <Select
              value={String(form.languageId)}
              onValueChange={(value) => setForm((prev) => ({ ...prev, languageId: Number(value) }))}
              disabled={submitting}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {SUPPORTED_JUDGE0_LANGUAGES.map((language) => (
                  <SelectItem key={language.value} value={String(language.judge0LanguageId)}>
                    {language.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          {includeDefaultTestCount && (
            <div className="space-y-2">
              <Label>Default Tests</Label>
              <Input
                type="number"
                min="1"
                max="100"
                value={form.defaultTestCount}
                onChange={(event) =>
                  setForm((prev) => ({ ...prev, defaultTestCount: Number(event.target.value) || 10 }))
                }
                disabled={submitting}
              />
            </div>
          )}
        </div>

        <div className="flex items-center gap-2">
          <Checkbox
            checked={form.active}
            onCheckedChange={(checked) => setForm((prev) => ({ ...prev, active: checked === true }))}
            disabled={submitting}
          />
          <Label>Make this configuration active</Label>
        </div>

        <div className="space-y-2">
          <Label>Source</Label>
          <Textarea
            value={form.source}
            onChange={(event) => setForm((prev) => ({ ...prev, source: event.target.value }))}
            className="min-h-40 font-mono text-sm"
            placeholder={`Paste ${title.toLowerCase()} source code`}
            disabled={submitting}
          />
          <p className="text-sm text-slate-500">
            This source is stored for admin execution and sent to Judge0 only through the sandboxed path.
          </p>
        </div>

        <Button
          type="button"
          className="w-full bg-blue-700 hover:bg-blue-800"
          onClick={onSubmit}
          disabled={submitting}
        >
          {submitting ? "Saving..." : `Save ${title}`}
        </Button>
      </div>
    </section>
  );
}

function CandidateGeneratedTests({
  batches,
  selectedIds,
  setSelectedIds,
  validCandidateCount,
  submitting,
  onPromoteOne,
  onPromoteSelected,
  onPromoteAll,
}: {
  batches: GeneratedTestBatchResponse[];
  selectedIds: Set<number>;
  setSelectedIds: Dispatch<SetStateAction<Set<number>>>;
  validCandidateCount: number;
  submitting: SubmittingState;
  onPromoteOne: (generatedTestCaseId: number) => void;
  onPromoteSelected: () => void;
  onPromoteAll: (batchId: number) => void;
}) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white">
      <div className="flex flex-col gap-3 border-b border-slate-200 bg-slate-50 p-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h3 className="text-base font-semibold text-slate-950">Candidate Generated Tests</h3>
          <p className="mt-1 text-sm text-slate-500">
            Select valid candidates and promote them into hidden official tests.
          </p>
        </div>
        <Button
          type="button"
          className="gap-2 bg-blue-700 hover:bg-blue-800"
          disabled={selectedIds.size === 0 || submitting === "promote-selected"}
          onClick={onPromoteSelected}
        >
          <ShieldCheck className="h-4 w-4" />
          Promote Selected Cases
        </Button>
      </div>
      {batches.length === 0 ? (
        <EmptyState text="No candidate batches yet." />
      ) : (
        <div className="divide-y divide-slate-100">
          {batches.map((batch) => {
            const unpromotedValidCount = batch.testCases.filter(
              (testCase) => testCase.status === "GENERATED" && !testCase.promoted
            ).length;
            return (
              <div key={batch.id} className="space-y-3 p-4">
                <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-sm font-semibold text-slate-950">Batch #{batch.id}</span>
                    <StatusPill status={batch.status} />
                    <span className="text-sm text-slate-500">seed {batch.seed}</span>
                    <span className="text-sm text-slate-500">
                      valid {batch.generatedCount}/{batch.requestedCount}
                    </span>
                    <span className="text-sm text-slate-500">invalid {batch.invalidCount}</span>
                  </div>
                  <Button
                    type="button"
                    variant="outline"
                    className="gap-2 bg-white"
                    disabled={unpromotedValidCount === 0 || submitting === "promote-all"}
                    onClick={() => onPromoteAll(batch.id)}
                  >
                    <ShieldCheck className="h-4 w-4" />
                    Promote All Valid Cases
                  </Button>
                </div>
                {batch.diagnostic && (
                  <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                    {batch.diagnostic}
                  </p>
                )}
                <GeneratedCaseTable
                  testCases={batch.testCases}
                  selectedIds={selectedIds}
                  setSelectedIds={setSelectedIds}
                  submitting={submitting}
                  onPromoteOne={onPromoteOne}
                />
              </div>
            );
          })}
        </div>
      )}
      {batches.length > 0 && validCandidateCount === 0 && (
        <p className="border-t border-slate-100 px-4 py-3 text-sm text-slate-500">
          All valid candidates are already promoted or no valid generated cases are available.
        </p>
      )}
    </section>
  );
}

function GeneratedCaseTable({
  testCases,
  selectedIds,
  setSelectedIds,
  submitting,
  onPromoteOne,
}: {
  testCases: GeneratedTestCaseResponse[];
  selectedIds: Set<number>;
  setSelectedIds: Dispatch<SetStateAction<Set<number>>>;
  submitting: SubmittingState;
  onPromoteOne: (generatedTestCaseId: number) => void;
}) {
  function toggleSelected(testCase: GeneratedTestCaseResponse, checked: boolean) {
    setSelectedIds((previous) => {
      const next = new Set(previous);
      if (checked) {
        next.add(testCase.id);
      } else {
        next.delete(testCase.id);
      }
      return next;
    });
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[900px] text-left text-sm">
        <thead className="text-xs uppercase text-slate-500">
          <tr>
            <th className="px-2 py-2">Select</th>
            <th className="px-2 py-2">#</th>
            <th className="px-2 py-2">Status</th>
            <th className="px-2 py-2">Generated input</th>
            <th className="px-2 py-2">Reference output</th>
            <th className="px-2 py-2">Promotion</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">
          {testCases.map((testCase) => {
            const canPromote = testCase.status === "GENERATED" && !testCase.promoted;
            return (
              <tr key={testCase.id} className="align-top">
                <td className="px-2 py-3">
                  <Checkbox
                    checked={selectedIds.has(testCase.id)}
                    disabled={!canPromote}
                    onCheckedChange={(checked) => toggleSelected(testCase, checked === true)}
                  />
                </td>
                <td className="px-2 py-3 font-medium text-slate-900">{testCase.testNumber}</td>
                <td className="px-2 py-3">
                  <StatusPill status={testCase.status} />
                  {testCase.diagnostic && (
                    <p className="mt-2 max-w-xs text-xs text-amber-700">{testCase.diagnostic}</p>
                  )}
                </td>
                <td className="px-2 py-3">
                  <CodeSnippet value={testCase.inputData ?? ""} compact />
                </td>
                <td className="px-2 py-3">
                  <CodeSnippet value={testCase.referenceOutput ?? ""} compact />
                </td>
                <td className="px-2 py-3">
                  {testCase.promoted ? (
                    <span className="text-sm text-emerald-700">Hidden test #{testCase.promotedTestCaseId}</span>
                  ) : (
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      className="gap-2 bg-white"
                      disabled={!canPromote || submitting === "promote-one"}
                      onClick={() => onPromoteOne(testCase.id)}
                    >
                      <ShieldCheck className="h-4 w-4" />
                      Promote
                    </Button>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function Counterexamples({
  counterexamples,
  submitting,
  onPromoteCounterexample,
  onRejudge,
}: {
  counterexamples: CounterexampleResponse[];
  submitting: SubmittingState;
  onPromoteCounterexample: (counterexampleId: number) => void;
  onRejudge: () => void;
}) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white">
      <div className="flex flex-col gap-3 border-b border-slate-200 bg-slate-50 p-4 md:flex-row md:items-center md:justify-between">
        <div>
          <h3 className="text-base font-semibold text-slate-950">Counterexamples</h3>
          <p className="mt-1 text-sm text-slate-500">
            Promotion creates a hidden official test case. Rejudge applies promoted tests to existing submissions.
          </p>
        </div>
        <Button
          type="button"
          variant="outline"
          className="gap-2 bg-white"
          onClick={onRejudge}
          disabled={submitting === "rejudge"}
        >
          <RotateCcw className="h-4 w-4" />
          {submitting === "rejudge" ? "Queueing..." : "Rejudge Problem"}
        </Button>
      </div>
      <div className="divide-y divide-slate-100">
        {counterexamples.length === 0 ? (
          <EmptyState text="No counterexamples stored for this problem." />
        ) : (
          counterexamples.map((counterexample) => (
            <div key={counterexample.id} className="space-y-3 p-4">
              <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
                <div className="flex flex-wrap items-center gap-2 text-sm">
                  <span className="font-semibold text-slate-950">Counterexample #{counterexample.id}</span>
                  <StatusPill status={counterexample.verdict} />
                  <span className="text-slate-500">submission #{counterexample.submissionId}</span>
                  <span className="text-slate-500">judge run {counterexample.judgeRunId}</span>
                </div>
                <Button
                  type="button"
                  size="sm"
                  className="gap-2 bg-blue-700 hover:bg-blue-800"
                  disabled={counterexample.promoted || submitting === "promote-counterexample"}
                  onClick={() => onPromoteCounterexample(counterexample.id)}
                >
                  <ShieldCheck className="h-4 w-4" />
                  {counterexample.promoted ? `Promoted #${counterexample.promotedTestCaseId}` : "Promote Counterexample"}
                </Button>
              </div>
              <div className="grid gap-3 lg:grid-cols-3">
                <CodeSnippet label="Generated input" value={counterexample.generatedInput} />
                <CodeSnippet label="Reference output" value={counterexample.referenceOutput} />
                <CodeSnippet label="Team output" value={counterexample.teamOutput ?? ""} />
              </div>
              {counterexample.diagnostic && (
                <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800">
                  {counterexample.diagnostic}
                </p>
              )}
            </div>
          ))
        )}
      </div>
    </section>
  );
}

function StatusPill({ status }: { status: string }) {
  return (
    <span className={`inline-flex rounded-md border px-2 py-0.5 text-xs font-semibold ${statusClass(status)}`}>
      {status}
    </span>
  );
}

function CodeSnippet({
  label,
  value,
  compact = false,
}: {
  label?: string;
  value: string;
  compact?: boolean;
}) {
  return (
    <div>
      {label && <p className="mb-1 text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</p>}
      <pre
        className={`overflow-auto rounded-md border border-slate-200 bg-slate-950 p-3 font-mono text-xs text-slate-50 ${
          compact ? "max-h-28 min-w-[180px]" : "max-h-40"
        }`}
      >
        {value || "(empty)"}
      </pre>
    </div>
  );
}

function EmptyState({ text }: { text: string }) {
  return (
    <div className="p-6 text-center">
      <p className="text-sm text-slate-500">{text}</p>
    </div>
  );
}
