import { Dispatch, SetStateAction, useCallback, useEffect, useMemo, useState } from "react";
import {
  CheckCircle2,
  Copy,
  Download,
  Eye,
  EyeOff,
  FileText,
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
  getCustomOutputValidatorSource,
  getCounterexamples,
  getGeneratedTestBatches,
  getInputGeneratorSource,
  getInputGenerators,
  getInputValidatorSource,
  getInputValidators,
  getSupportedLanguages,
  getReferenceSolutionSource,
  getReferenceSolutions,
  previewPromptExport,
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
  GeneratedTestPromotionResponse,
  OracleProgramResponse,
  OracleProgramSourceResponse,
  PromptExportRequest,
  PromptExportResponse,
  PromptMode,
  PromptType,
  PromptVisibilityMode,
  SupportedLanguageResponse,
} from "../types/api";
import {
  DEFAULT_VALIDATOR_LANGUAGE_ID,
  getJudge0LanguageOptionById,
  SUPPORTED_JUDGE0_LANGUAGES,
} from "../../constants/judge0Languages";
import { toast } from "sonner";
import { AdminHelpTooltip } from "./AdminHelpTooltip";

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

type PromptFormState = PromptExportRequest;

interface OraclePanelProps {
  problemId: number;
  problemTitle: string;
  contestId: number;
  section?: OraclePanelSection;
  embedded?: boolean;
  customValidatorSourceHash?: string | null;
  customValidatorLanguageId?: number | null;
  customValidatorEnabled?: boolean;
}

type OraclePanelSection = "all" | "prompts" | "engineering" | "generated" | "counterexamples";

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

const defaultPromptForm: PromptFormState = {
  promptType: "INPUT_GENERATOR",
  promptMode: "RECOMMENDED_ADMIN",
  visibilityMode: "ADMIN_FULL_MODE",
  targetLanguage: "",
  includePublicSamples: true,
  includeComparePolicy: true,
  includePublicNotes: true,
  includeAdminInternalNotes: false,
  includeReferenceSolution: false,
  includeProgramMetadata: false,
  includeProgramSources: false,
  includeAdditionalInstructions: true,
  confirmSensitiveMaterial: false,
  additionalInstructions: "",
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
    case "DUPLICATE":
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

export function OraclePanel({
  problemId,
  problemTitle,
  contestId,
  section = "all",
  embedded = false,
  customValidatorSourceHash = null,
  customValidatorLanguageId = null,
  customValidatorEnabled = false,
}: OraclePanelProps) {
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
  const [promptForm, setPromptForm] = useState<PromptFormState>(defaultPromptForm);
  const [supportedLanguages, setSupportedLanguages] = useState<SupportedLanguageResponse[]>([]);
  const [promptPreview, setPromptPreview] = useState<PromptExportResponse | null>(null);
  const [selectedGeneratedCaseIds, setSelectedGeneratedCaseIds] = useState<Set<number>>(new Set());
  const [isLoading, setIsLoading] = useState(false);
  const [isLoadingLanguages, setIsLoadingLanguages] = useState(false);
  const [submitting, setSubmitting] = useState<SubmittingState>(null);
  const [isPromptSubmitting, setIsPromptSubmitting] = useState(false);
  const [revealedSources, setRevealedSources] = useState<Record<string, OracleProgramSourceResponse>>({});
  const [sourceLoadingKey, setSourceLoadingKey] = useState<string | null>(null);

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
    setPromptForm(defaultPromptForm);
    setPromptPreview(null);
    setSelectedGeneratedCaseIds(new Set());
    setRevealedSources({});
    setSourceLoadingKey(null);
    void reload();
  }, [problemId, reload]);

  useEffect(() => {
    let mounted = true;
    setIsLoadingLanguages(true);
    getSupportedLanguages()
      .then((languages) => {
        if (mounted) setSupportedLanguages(languages);
      })
      .catch((error) => {
        toast.error(error instanceof Error ? error.message : "Failed to load supported languages");
      })
      .finally(() => {
        if (mounted) setIsLoadingLanguages(false);
      });
    return () => {
      mounted = false;
    };
  }, []);

  const promptLanguageOptions = useMemo(
    () => supportedLanguages.filter((language) => supportsPromptType(language, promptForm.promptType)),
    [supportedLanguages, promptForm.promptType]
  );

  useEffect(() => {
    if (!promptForm.targetLanguage) return;
    if (!promptLanguageOptions.some((language) => language.value === promptForm.targetLanguage)) {
      setPromptForm((prev) => ({ ...prev, targetLanguage: "" }));
    }
  }, [promptForm.targetLanguage, promptLanguageOptions]);

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
      const response = await promoteGeneratedTestCase(generatedTestCaseId);
      showPromotionToast(response);
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
      const response = await promoteSelectedGeneratedTestCases({ generatedTestCaseIds: ids });
      showPromotionToast(response);
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
      const response = await promoteAllValidGeneratedTestCases(batchId);
      showPromotionToast(response);
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

  async function handlePreviewPrompt() {
    if (!promptForm.targetLanguage) {
      toast.error("Select a target language before previewing a prompt");
      return;
    }
    if (
      promptForm.promptMode === "CUSTOM_ADVANCED" &&
      promptForm.visibilityMode === "ADMIN_FULL_MODE" &&
      !promptForm.confirmSensitiveMaterial
    ) {
      toast.error("Custom Advanced Prompt with admin-sensitive context requires confirmation");
      return;
    }

    setIsPromptSubmitting(true);
    try {
      const response = await previewPromptExport(problemId, promptForm);
      setPromptPreview(response);
      toast.success("Prompt preview generated");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to generate prompt preview");
    } finally {
      setIsPromptSubmitting(false);
    }
  }

  async function handleCopyPrompt() {
    if (!promptPreview?.promptText) {
      toast.error("Generate a prompt before copying it");
      return;
    }
    await copyText(promptPreview.promptText, "Prompt copied", "Clipboard access was blocked");
  }

  async function copyText(value: string, successMessage: string, failureMessage = "Copy failed") {
    try {
      await navigator.clipboard.writeText(value);
      toast.success(successMessage);
    } catch {
      toast.error(failureMessage);
    }
  }

  function handleExportPrompt(extension: "txt" | "md") {
    if (!promptPreview?.promptText) return;
    const safeTitle = problemTitle.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "") || "problem";
    const fileName = `${safeTitle}-${promptPreview.promptType.toLowerCase()}.${extension}`;
    const blob = new Blob([promptPreview.promptText], {
      type: extension === "md" ? "text/markdown;charset=utf-8" : "text/plain;charset=utf-8",
    });
    const href = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = href;
    link.download = fileName;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(href);
  }

  function sourceKey(kind: ProgramKind | "checker", id: number) {
    return `${kind}-${id}`;
  }

  async function handleRevealProgramSource(kind: ProgramKind, id: number) {
    const key = sourceKey(kind, id);
    setSourceLoadingKey(key);
    try {
      const source =
        kind === "reference"
          ? await getReferenceSolutionSource(id)
          : kind === "generator"
            ? await getInputGeneratorSource(id)
            : await getInputValidatorSource(id);
      setRevealedSources((prev) => ({ ...prev, [key]: source }));
      toast.success("Stored source revealed");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to reveal stored source");
    } finally {
      setSourceLoadingKey(null);
    }
  }

  async function handleRevealCustomValidatorSource() {
    const key = sourceKey("checker", problemId);
    setSourceLoadingKey(key);
    try {
      const source = await getCustomOutputValidatorSource(problemId);
      setRevealedSources((prev) => ({ ...prev, [key]: source }));
      toast.success("Custom output validator source revealed");
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to reveal custom validator source");
    } finally {
      setSourceLoadingKey(null);
    }
  }

  function handleHideSource(kind: ProgramKind | "checker", id: number) {
    const key = sourceKey(kind, id);
    setRevealedSources((prev) => {
      const next = { ...prev };
      delete next[key];
      return next;
    });
  }

  function handleDownloadSource(source: OracleProgramSourceResponse) {
    const blob = new Blob([source.source], { type: "text/plain;charset=utf-8" });
    const href = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = href;
    link.download = source.fileName || `${source.kind.toLowerCase()}.txt`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(href);
  }

  function showPromotionToast(response: GeneratedTestPromotionResponse) {
    if (response.promotedCount > 0) {
      toast.success(response.message || `${response.promotedCount} generated case(s) promoted`);
    } else if (response.skippedDuplicateCount > 0 || response.skippedInvalidCount > 0) {
      toast.warning(response.message || "No generated cases were promoted");
    } else {
      toast.info(response.message || "No generated cases were promoted");
    }
  }

  const canGenerate = Boolean(activeReference && activeGenerator);
  const showPrompts = section === "all" || section === "prompts";
  const showEngineering = section === "all" || section === "engineering";
  const showGenerated = section === "all" || section === "generated";
  const showCounterexamples = section === "all" || section === "counterexamples";

  const content = (
    <div className="space-y-8">
      {embedded && (
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
      )}

      {showPrompts && (
        <>
          <SectionHeading
            index="1"
            title="Prompt Exports"
            description="Generate deterministic copyable prompt text from structured problem data without calling AI services."
          />
          <PromptExportsSection
            form={promptForm}
            setForm={setPromptForm}
            languages={promptLanguageOptions}
            isLoadingLanguages={isLoadingLanguages}
            preview={promptPreview}
            submitting={isPromptSubmitting}
            onPreview={handlePreviewPrompt}
            onCopy={handleCopyPrompt}
            onExport={handleExportPrompt}
          />
        </>
      )}

      {showEngineering && (
        <>
          <SectionHeading
            index={section === "all" ? "2" : "1"}
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
              kind="reference"
              title="Reference Solution"
              description="Runs through Judge0 to produce deterministic reference output."
              form={referenceForm}
              setForm={setReferenceForm}
              latest={activeReference}
              revealedSource={activeReference ? revealedSources[sourceKey("reference", activeReference.id)] ?? null : null}
              sourceLoading={activeReference ? sourceLoadingKey === sourceKey("reference", activeReference.id) : false}
              submitting={submitting === "reference"}
              onSubmit={() => submitProgram("reference")}
              onRevealSource={(id) => handleRevealProgramSource("reference", id)}
              onHideSource={(id) => handleHideSource("reference", id)}
              onCopySource={(source) => copyText(source.source, "Reference solution source copied")}
              onDownloadSource={handleDownloadSource}
            />
            <ProgramSection
              kind="generator"
              title="Input Generator"
              description="Runs through Judge0 using seed and test number on stdin."
              form={generatorForm}
              setForm={setGeneratorForm}
              latest={activeGenerator}
              revealedSource={activeGenerator ? revealedSources[sourceKey("generator", activeGenerator.id)] ?? null : null}
              sourceLoading={activeGenerator ? sourceLoadingKey === sourceKey("generator", activeGenerator.id) : false}
              includeDefaultTestCount
              submitting={submitting === "generator"}
              onSubmit={() => submitProgram("generator")}
              onRevealSource={(id) => handleRevealProgramSource("generator", id)}
              onHideSource={(id) => handleHideSource("generator", id)}
              onCopySource={(source) => copyText(source.source, "Input generator source copied")}
              onDownloadSource={handleDownloadSource}
            />
            <ProgramSection
              kind="validator"
              title="Input Validator"
              description="Optional Judge0 program; stdout decision should be VALID/ACCEPT or INVALID/REJECT."
              form={validatorForm}
              setForm={setValidatorForm}
              latest={activeValidator}
              revealedSource={activeValidator ? revealedSources[sourceKey("validator", activeValidator.id)] ?? null : null}
              sourceLoading={activeValidator ? sourceLoadingKey === sourceKey("validator", activeValidator.id) : false}
              submitting={submitting === "validator"}
              onSubmit={() => submitProgram("validator")}
              onRevealSource={(id) => handleRevealProgramSource("validator", id)}
              onHideSource={(id) => handleHideSource("validator", id)}
              onCopySource={(source) => copyText(source.source, "Input validator source copied")}
              onDownloadSource={handleDownloadSource}
            />
          </div>
          {customValidatorSourceHash && (
            <CustomValidatorSourceSection
              problemId={problemId}
              sourceHash={customValidatorSourceHash}
              languageId={customValidatorLanguageId}
              enabled={customValidatorEnabled}
              revealedSource={revealedSources[sourceKey("checker", problemId)] ?? null}
              loading={sourceLoadingKey === sourceKey("checker", problemId)}
              onReveal={handleRevealCustomValidatorSource}
              onHide={() => handleHideSource("checker", problemId)}
              onCopy={(source) => copyText(source.source, "Custom output validator source copied")}
              onDownload={handleDownloadSource}
            />
          )}

          <SectionHeading
            index={section === "all" ? "3" : "2"}
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
        </>
      )}

      {showGenerated && (
        <>
          <SectionHeading
            index={section === "all" ? "4" : "1"}
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
        </>
      )}

      {showCounterexamples && (
        <>
          <SectionHeading
            index={section === "all" ? "5" : "1"}
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
            index={section === "all" ? "6" : "2"}
            title="Counterexamples"
            description="Review mismatches found during counterexample search and promote concrete failing inputs when useful."
          />
          <Counterexamples
            counterexamples={counterexamples}
            submitting={submitting}
            onPromoteCounterexample={handlePromoteCounterexample}
            onRejudge={handleRejudge}
          />
        </>
      )}
    </div>
  );

  if (embedded) {
    return content;
  }

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
      <CardContent className="p-6">{content}</CardContent>
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

const PROMPT_TYPES: { value: PromptType; label: string }[] = [
  { value: "REFERENCE_SOLUTION", label: "Reference Solution" },
  { value: "INPUT_GENERATOR", label: "Input Generator" },
  { value: "INPUT_VALIDATOR", label: "Input Validator" },
  { value: "CHECKER_OUTPUT_VALIDATOR", label: "Checker / Output Validator" },
  { value: "FULL_PROBLEM_ENGINEERING_BUNDLE", label: "Full Problem Engineering Bundle" },
];

const VISIBILITY_MODES: { value: PromptVisibilityMode; label: string }[] = [
  { value: "SAFE_MODE", label: "Public/Safe boundary" },
  { value: "ADMIN_FULL_MODE", label: "Admin-sensitive boundary" },
];

const PROMPT_MODES: { value: PromptMode; label: string; description: string }[] = [
  {
    value: "RECOMMENDED_ADMIN",
    label: "Recommended Admin Prompt",
    description: "AuraC2 selects useful admin context for this prompt type while excluding hidden tests and hidden expected outputs by default.",
  },
  {
    value: "PUBLIC_SAFE",
    label: "Public/Safe Prompt",
    description: "Contestant-safe context only. No admin notes, sources, hidden tests, or sensitive artifacts.",
  },
  {
    value: "CUSTOM_ADVANCED",
    label: "Custom Advanced Prompt",
    description: "Manual include/exclude controls for expert admins with sensitive-material warnings.",
  },
];

function promptModeLabel(promptMode: PromptMode) {
  return PROMPT_MODES.find((mode) => mode.value === promptMode)?.label ?? promptMode;
}

function visibilityBoundaryLabel(visibilityMode: PromptVisibilityMode) {
  return visibilityMode === "SAFE_MODE" ? "Public/Safe boundary" : "Admin-sensitive boundary";
}

function PromptExportsSection({
  form,
  setForm,
  languages,
  isLoadingLanguages,
  preview,
  submitting,
  onPreview,
  onCopy,
  onExport,
}: {
  form: PromptFormState;
  setForm: Dispatch<SetStateAction<PromptFormState>>;
  languages: SupportedLanguageResponse[];
  isLoadingLanguages: boolean;
  preview: PromptExportResponse | null;
  submitting: boolean;
  onPreview: () => void;
  onCopy: () => void;
  onExport: (extension: "txt" | "md") => void;
}) {
  const selectedLanguage = languages.find((language) => language.value === form.targetLanguage) ?? null;
  const selectedPromptMode = PROMPT_MODES.find((mode) => mode.value === form.promptMode) ?? PROMPT_MODES[0];
  const customAdvancedMode = form.promptMode === "CUSTOM_ADVANCED";
  const adminFullMode = customAdvancedMode && form.visibilityMode === "ADMIN_FULL_MODE";

  const setPromptMode = (promptMode: PromptMode) => {
    setForm((prev) => {
      if (promptMode === "PUBLIC_SAFE") {
        return {
          ...prev,
          promptMode,
          visibilityMode: "SAFE_MODE",
          includePublicSamples: true,
          includeComparePolicy: true,
          includePublicNotes: true,
          includeAdminInternalNotes: false,
          includeReferenceSolution: false,
          includeProgramMetadata: false,
          includeProgramSources: false,
          includeAdditionalInstructions: true,
          confirmSensitiveMaterial: false,
        };
      }
      if (promptMode === "RECOMMENDED_ADMIN") {
        return {
          ...prev,
          promptMode,
          visibilityMode: "ADMIN_FULL_MODE",
          includePublicSamples: true,
          includeComparePolicy: true,
          includePublicNotes: true,
          includeAdditionalInstructions: true,
          confirmSensitiveMaterial: false,
        };
      }
      return {
        ...prev,
        promptMode,
        visibilityMode: prev.visibilityMode ?? "SAFE_MODE",
      };
    });
  };

  const setVisibilityMode = (visibilityMode: PromptVisibilityMode) => {
    setForm((prev) => ({
      ...prev,
      visibilityMode,
      includeAdminInternalNotes: visibilityMode === "ADMIN_FULL_MODE" ? prev.includeAdminInternalNotes : false,
      includeReferenceSolution: visibilityMode === "ADMIN_FULL_MODE" ? prev.includeReferenceSolution : false,
      includeProgramSources: visibilityMode === "ADMIN_FULL_MODE" ? prev.includeProgramSources : false,
      confirmSensitiveMaterial: visibilityMode === "ADMIN_FULL_MODE" ? prev.confirmSensitiveMaterial : false,
    }));
  };

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="mb-4 rounded-md border border-blue-200 bg-blue-50 p-4 text-sm text-blue-950">
        <p className="font-semibold">AuraC2 only generates prompt text. It does not call AI services.</p>
        <p className="mt-1">
          Any AI-generated code must be reviewed, compiled, tested, and verified inside AuraC2 before use.
        </p>
      </div>

      <div className="grid gap-4 xl:grid-cols-[360px_1fr]">
        <div className="space-y-4">
          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <Label htmlFor="prompt-type">Prompt Type</Label>
              <AdminHelpTooltip
                label="Prompt type help"
                content="Choose the artifact the external admin prompt should ask for. AuraC2 only prepares text; it does not call AI services."
              />
            </div>
            <Select
              value={form.promptType}
              onValueChange={(value: PromptType) =>
                setForm((prev) => ({ ...prev, promptType: value, targetLanguage: "" }))
              }
            >
              <SelectTrigger id="prompt-type">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {PROMPT_TYPES.map((type) => (
                  <SelectItem key={type.value} value={type.value}>
                    {type.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <Label htmlFor="prompt-language">Target Language</Label>
              <AdminHelpTooltip
                label="Target language help"
                content="Only Judge0-supported AuraC2 languages are listed. The selected language changes file names, entry points, and verification notes."
              />
            </div>
            <Select
              value={form.targetLanguage}
              onValueChange={(value) => setForm((prev) => ({ ...prev, targetLanguage: value }))}
              disabled={isLoadingLanguages || languages.length === 0}
            >
              <SelectTrigger id="prompt-language">
                <SelectValue placeholder={isLoadingLanguages ? "Loading languages..." : "Select language"} />
              </SelectTrigger>
              <SelectContent>
                {languages.map((language) => (
                  <SelectItem key={language.value} value={language.value}>
                    {language.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            {selectedLanguage && (
              <div className="rounded-md border border-slate-200 bg-slate-50 p-3 text-xs text-slate-600">
                <p className="font-semibold text-slate-800">
                  {selectedLanguage.label} / Judge0 #{selectedLanguage.judge0LanguageId}
                </p>
                <p className="mt-1">{selectedLanguage.entryPoint}</p>
                <p className="mt-1">{selectedLanguage.runtimeNotes}</p>
              </div>
            )}
          </div>

          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <Label htmlFor="prompt-mode">Prompt Mode</Label>
              <AdminHelpTooltip
                label="Prompt mode help"
                content="Recommended mode adds useful admin context while skipping hidden tests by default. Public/Safe keeps contestant-safe data only. Advanced exposes manual sensitive controls."
              />
            </div>
            <Select value={form.promptMode} onValueChange={(value: PromptMode) => setPromptMode(value)}>
              <SelectTrigger id="prompt-mode">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {PROMPT_MODES.map((mode) => (
                  <SelectItem key={mode.value} value={mode.value}>
                    {mode.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <p className="rounded-md border border-slate-200 bg-slate-50 p-3 text-xs leading-5 text-slate-600">
              {selectedPromptMode.description}
            </p>
          </div>
        </div>

        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="prompt-additional">Additional Admin Instructions</Label>
            <Textarea
              id="prompt-additional"
              value={form.additionalInstructions ?? ""}
              onChange={(event) =>
                setForm((prev) => ({ ...prev, additionalInstructions: event.target.value }))
              }
              disabled={customAdvancedMode && !form.includeAdditionalInstructions}
              className="min-h-28"
              placeholder="Optional constraints for the external admin prompt, such as preferred algorithm family or edge-case focus."
            />
          </div>

          {customAdvancedMode && (
            <details className="rounded-md border border-slate-200 bg-slate-50">
              <summary className="cursor-pointer px-4 py-3 text-sm font-semibold text-slate-900">
                Advanced Options
              </summary>
              <div className="space-y-4 border-t border-slate-200 bg-white p-4">
                <div className="space-y-2">
                  <Label htmlFor="prompt-visibility">Visibility Boundary</Label>
                  <Select value={form.visibilityMode} onValueChange={(value: PromptVisibilityMode) => setVisibilityMode(value)}>
                    <SelectTrigger id="prompt-visibility">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {VISIBILITY_MODES.map((mode) => (
                        <SelectItem key={mode.value} value={mode.value}>
                          {mode.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>

                {adminFullMode && (
                  <div className="rounded-md border border-amber-200 bg-amber-50 p-3">
                    <p className="text-sm font-semibold text-amber-950">Sensitive-material warning</p>
                    <p className="mt-1 text-sm text-amber-800">
                      Admin full boundary can export contest-sensitive material selected below.
                    </p>
                    <label className="mt-3 flex items-start gap-2 text-sm text-amber-950">
                      <Checkbox
                        checked={form.confirmSensitiveMaterial}
                        onCheckedChange={(checked) =>
                          setForm((prev) => ({ ...prev, confirmSensitiveMaterial: checked === true }))
                        }
                      />
                      <span>I confirm this export may contain sensitive admin material.</span>
                    </label>
                  </div>
                )}

                <div className="grid gap-3 md:grid-cols-2">
                  <PromptCheckbox
                    label="Include public samples"
                    checked={form.includePublicSamples}
                    onChange={(value) => setForm((prev) => ({ ...prev, includePublicSamples: value }))}
                  />
                  <PromptCheckbox
                    label="Include compare policy"
                    checked={form.includeComparePolicy}
                    onChange={(value) => setForm((prev) => ({ ...prev, includeComparePolicy: value }))}
                  />
                  <PromptCheckbox
                    label="Include Public Notes"
                    checked={form.includePublicNotes}
                    onChange={(value) => setForm((prev) => ({ ...prev, includePublicNotes: value }))}
                  />
                  <PromptCheckbox
                    label="Include safe artifact metadata"
                    checked={form.includeProgramMetadata}
                    onChange={(value) => setForm((prev) => ({ ...prev, includeProgramMetadata: value }))}
                  />
                  <PromptCheckbox
                    label="Sensitive: include reference solution source"
                    checked={form.includeReferenceSolution}
                    disabled={form.visibilityMode !== "ADMIN_FULL_MODE"}
                    onChange={(value) => setForm((prev) => ({ ...prev, includeReferenceSolution: value }))}
                  />
                  <PromptCheckbox
                    label="Sensitive: include generator/validator/checker snippets"
                    checked={form.includeProgramSources}
                    disabled={form.visibilityMode !== "ADMIN_FULL_MODE"}
                    onChange={(value) => setForm((prev) => ({ ...prev, includeProgramSources: value }))}
                  />
                  <PromptCheckbox
                    label="Sensitive: include Admin Internal Notes"
                    checked={form.includeAdminInternalNotes}
                    disabled={form.visibilityMode !== "ADMIN_FULL_MODE"}
                    onChange={(value) => setForm((prev) => ({ ...prev, includeAdminInternalNotes: value }))}
                  />
                  <PromptCheckbox
                    label="Include additional instructions"
                    checked={form.includeAdditionalInstructions}
                    onChange={(value) => setForm((prev) => ({ ...prev, includeAdditionalInstructions: value }))}
                  />
                </div>
              </div>
            </details>
          )}

          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              className="gap-2 bg-blue-700 hover:bg-blue-800"
              onClick={onPreview}
              disabled={submitting || !form.targetLanguage || (adminFullMode && !form.confirmSensitiveMaterial)}
            >
              <FileText className="h-4 w-4" />
              {submitting ? "Generating..." : customAdvancedMode ? "Preview Prompt" : "Preview Best Prompt"}
            </Button>
            <Button type="button" variant="outline" className="gap-2 bg-white" disabled={!preview} onClick={onCopy}>
              <Copy className="h-4 w-4" />
              Copy Prompt
            </Button>
            <Button type="button" variant="outline" className="gap-2 bg-white" disabled={!preview} onClick={() => onExport("txt")}>
              <Download className="h-4 w-4" />
              Export .txt
            </Button>
            <Button type="button" variant="outline" className="gap-2 bg-white" disabled={!preview} onClick={() => onExport("md")}>
              <Download className="h-4 w-4" />
              Export .md
            </Button>
          </div>
        </div>
      </div>

      {!preview && (
        <p className="mt-4 rounded-md border border-dashed border-slate-300 bg-slate-50 p-4 text-center text-sm text-slate-500">
          Generate a prompt to enable copy and export actions.
        </p>
      )}

      {preview && (
        <div className="mt-4 space-y-3">
          {(preview.warnings.length > 0 || preview.readinessWarnings.length > 0) && (
            <div className="grid gap-3 md:grid-cols-2">
              {preview.warnings.length > 0 && (
                <WarningList title="Safety Decisions" warnings={preview.warnings} tone="blue" />
              )}
              {preview.readinessWarnings.length > 0 && (
                <WarningList title="Readiness Warnings" warnings={preview.readinessWarnings} tone="amber" />
              )}
            </div>
          )}
          <div className="flex flex-col gap-2 rounded-lg border border-slate-200 bg-slate-50 p-3 md:flex-row md:items-center md:justify-between">
            <div>
              <p className="text-sm font-semibold text-slate-950">Generated Prompt Preview</p>
              <p className="text-xs text-slate-500">
                {preview.targetLanguage.label} · {promptModeLabel(preview.promptMode)} · {visibilityBoundaryLabel(preview.visibilityMode)}
              </p>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button type="button" size="sm" variant="outline" className="gap-2 bg-white" onClick={onCopy}>
                <Copy className="h-3.5 w-3.5" />
                Copy
              </Button>
              <Button type="button" size="sm" variant="outline" className="gap-2 bg-white" onClick={() => onExport("txt")}>
                <Download className="h-3.5 w-3.5" />
                .txt
              </Button>
              <Button type="button" size="sm" variant="outline" className="gap-2 bg-white" onClick={() => onExport("md")}>
                <Download className="h-3.5 w-3.5" />
                .md
              </Button>
            </div>
          </div>
          <pre className="max-h-[520px] overflow-auto rounded-lg border border-slate-200 bg-slate-950 p-4 text-xs leading-6 text-slate-50">{preview.promptText}</pre>
        </div>
      )}
    </section>
  );
}

function PromptCheckbox({
  label,
  checked,
  disabled = false,
  onChange,
}: {
  label: string;
  checked: boolean;
  disabled?: boolean;
  onChange: (checked: boolean) => void;
}) {
  return (
    <label className={`flex items-center gap-2 rounded-md border border-slate-200 p-3 text-sm ${disabled ? "bg-slate-50 text-slate-400" : "bg-white text-slate-800"}`}>
      <Checkbox checked={checked} disabled={disabled} onCheckedChange={(value) => onChange(value === true)} />
      <span>{label}</span>
    </label>
  );
}

function WarningList({
  title,
  warnings,
  tone,
}: {
  title: string;
  warnings: string[];
  tone: "blue" | "amber";
}) {
  const className =
    tone === "blue"
      ? "border-blue-200 bg-blue-50 text-blue-950"
      : "border-amber-200 bg-amber-50 text-amber-900";

  return (
    <div className={`rounded-md border p-3 ${className}`}>
      <p className="text-sm font-semibold">{title}</p>
      <ul className="mt-2 list-disc space-y-1 pl-5 text-sm">
        {warnings.map((warning) => (
          <li key={warning}>{warning}</li>
        ))}
      </ul>
    </div>
  );
}

function supportsPromptType(language: SupportedLanguageResponse, promptType: PromptType) {
  switch (promptType) {
    case "REFERENCE_SOLUTION":
      return language.supportsReferenceSolution;
    case "INPUT_GENERATOR":
      return language.supportsGenerator;
    case "INPUT_VALIDATOR":
      return language.supportsInputValidator;
    case "CHECKER_OUTPUT_VALIDATOR":
      return language.supportsChecker;
    case "FULL_PROBLEM_ENGINEERING_BUNDLE":
      return language.supportsReferenceSolution && language.supportsGenerator && language.supportsInputValidator;
    default:
      return false;
  }
}

function ProgramSection({
  kind,
  title,
  description,
  form,
  setForm,
  latest,
  revealedSource,
  sourceLoading,
  includeDefaultTestCount = false,
  submitting,
  onSubmit,
  onRevealSource,
  onHideSource,
  onCopySource,
  onDownloadSource,
}: {
  kind: ProgramKind;
  title: string;
  description: string;
  form: ProgramFormState;
  setForm: Dispatch<SetStateAction<ProgramFormState>>;
  latest: OracleProgramResponse | null;
  revealedSource: OracleProgramSourceResponse | null;
  sourceLoading: boolean;
  includeDefaultTestCount?: boolean;
  submitting: boolean;
  onSubmit: () => void;
  onRevealSource: (id: number) => void;
  onHideSource: (id: number) => void;
  onCopySource: (source: OracleProgramSourceResponse) => void;
  onDownloadSource: (source: OracleProgramSourceResponse) => void;
}) {
  const helperText =
    title === "Reference Solution"
      ? "Stored for admin execution and expected-output generation. Hidden from teams."
      : title === "Input Generator"
        ? "Reads seed and testNumber from stdin. Prints exactly one generated input."
        : "Prints VALID or INVALID. AuraC2 reads the decision from stdout.";

  return (
    <section className="rounded-lg border border-slate-200 bg-white p-4">
      <div className="mb-4">
        <div className="flex items-center gap-2">
          <h3 className="text-base font-semibold text-slate-950">{title}</h3>
          <AdminHelpTooltip
            label={`${title} help`}
            content={
              kind === "reference"
                ? "Official admin-side solution used to produce expected outputs. It is never shown to teams."
                : kind === "generator"
                  ? "Generates candidate inputs from seed and testNumber. Candidates become official only after promotion."
                  : "Checks generated or uploaded input structure and prints VALID or INVALID for AuraC2 to read."
            }
          />
        </div>
        <p className="mt-1 text-sm text-slate-500">{description}</p>
        {latest && (
          <div className="mt-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-xs text-amber-900">
            <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
              <p>
                Admin-only source is hidden. Paste new source only to replace it. Current hash:{" "}
                <span className="font-mono">{shortHash(latest.sourceHash)}</span>
              </p>
              <div className="flex flex-wrap gap-2">
                {revealedSource ? (
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    className="h-7 gap-1 bg-white px-2 text-xs"
                    onClick={() => onHideSource(latest.id)}
                  >
                    <EyeOff className="h-3.5 w-3.5" />
                    Hide
                  </Button>
                ) : (
                  <Button
                    type="button"
                    size="sm"
                    variant="outline"
                    className="h-7 gap-1 bg-white px-2 text-xs"
                    onClick={() => onRevealSource(latest.id)}
                    disabled={sourceLoading}
                  >
                    <Eye className="h-3.5 w-3.5" />
                    {sourceLoading ? "Revealing..." : "Reveal Source"}
                  </Button>
                )}
              </div>
            </div>
          </div>
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
            className="h-64 max-h-80 resize-y overflow-y-auto font-mono text-sm leading-5"
            placeholder={`Paste ${title.toLowerCase()} source code`}
            disabled={submitting}
          />
          <p className="text-sm text-slate-500">
            {helperText}
          </p>
        </div>

        {revealedSource && (
          <SourceRevealPanel
            source={revealedSource}
            onCopy={() => onCopySource(revealedSource)}
            onDownload={() => onDownloadSource(revealedSource)}
          />
        )}

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

function SourceRevealPanel({
  source,
  onCopy,
  onDownload,
}: {
  source: OracleProgramSourceResponse;
  onCopy: () => void;
  onDownload: () => void;
}) {
  return (
    <div className="rounded-md border border-amber-200 bg-amber-50">
      <div className="flex flex-col gap-2 border-b border-amber-200 px-3 py-2 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-amber-800">Admin-only source</p>
          <p className="text-xs text-amber-900">
            {languageLabel(source.languageId)} · {source.fileName} · {shortHash(source.sourceHash)}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <Button type="button" size="sm" variant="outline" className="h-8 gap-2 bg-white" onClick={onCopy}>
            <Copy className="h-3.5 w-3.5" />
            Copy
          </Button>
          <Button type="button" size="sm" variant="outline" className="h-8 gap-2 bg-white" onClick={onDownload}>
            <Download className="h-3.5 w-3.5" />
            Download
          </Button>
        </div>
      </div>
      <pre className="max-h-72 overflow-auto whitespace-pre-wrap p-3 font-mono text-xs leading-5 text-slate-950">
        {source.source}
      </pre>
    </div>
  );
}

function CustomValidatorSourceSection({
  sourceHash,
  languageId,
  enabled,
  revealedSource,
  loading,
  onReveal,
  onHide,
  onCopy,
  onDownload,
}: {
  problemId: number;
  sourceHash: string;
  languageId?: number | null;
  enabled?: boolean;
  revealedSource: OracleProgramSourceResponse | null;
  loading: boolean;
  onReveal: () => void;
  onHide: () => void;
  onCopy: (source: OracleProgramSourceResponse) => void;
  onDownload: (source: OracleProgramSourceResponse) => void;
}) {
  return (
    <section className="rounded-lg border border-amber-200 bg-amber-50 p-4">
      <div className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
        <div>
          <div className="flex items-center gap-2">
            <h3 className="text-base font-semibold text-amber-950">Custom Output Validator Source</h3>
            <AdminHelpTooltip
              label="Custom output validator help"
              content="Use a custom checker only when built-in compare policies cannot express the accepted outputs."
            />
          </div>
          <p className="mt-1 text-sm text-amber-900">
            Admin-only checker material. Hidden from teams and public/safe prompt exports.
          </p>
          <p className="mt-2 text-xs text-amber-800">
            {enabled ? "Enabled" : "Stored but not enabled"} · {languageLabel(languageId)} ·{" "}
            <span className="font-mono">{shortHash(sourceHash)}</span>
          </p>
        </div>
        {revealedSource ? (
          <Button type="button" variant="outline" className="gap-2 bg-white" onClick={onHide}>
            <EyeOff className="h-4 w-4" />
            Hide Source
          </Button>
        ) : (
          <Button type="button" variant="outline" className="gap-2 bg-white" onClick={onReveal} disabled={loading}>
            <Eye className="h-4 w-4" />
            {loading ? "Revealing..." : "Reveal Source"}
          </Button>
        )}
      </div>
      {revealedSource && (
        <div className="mt-4">
          <SourceRevealPanel
            source={revealedSource}
            onCopy={() => onCopy(revealedSource)}
            onDownload={() => onDownload(revealedSource)}
          />
        </div>
      )}
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
  const [filter, setFilter] = useState<"all" | "generated" | "invalid" | "promoted" | "unpromoted">("all");
  const [rowLimit, setRowLimit] = useState(20);

  const matchesFilter = (testCase: GeneratedTestCaseResponse) => {
    switch (filter) {
      case "generated":
        return testCase.status === "GENERATED";
      case "invalid":
        return testCase.status !== "GENERATED";
      case "promoted":
        return testCase.promoted;
      case "unpromoted":
        return !testCase.promoted;
      default:
        return true;
    }
  };

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
      <div className="flex flex-col gap-3 border-b border-slate-100 p-4 xl:flex-row xl:items-center xl:justify-between">
        <div className="flex flex-wrap gap-2">
          {[
            { value: "all" as const, label: "All" },
            { value: "generated" as const, label: "Valid/generated" },
            { value: "invalid" as const, label: "Invalid" },
            { value: "promoted" as const, label: "Promoted" },
            { value: "unpromoted" as const, label: "Unpromoted" },
          ].map((option) => (
            <button
              key={option.value}
              type="button"
              className={`rounded-md border px-3 py-1.5 text-sm font-semibold transition ${
                filter === option.value
                  ? "border-blue-700 bg-blue-50 text-blue-700"
                  : "border-slate-200 bg-white text-slate-600 hover:border-slate-300"
              }`}
              onClick={() => setFilter(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>
        <label className="flex items-center gap-2 text-sm text-slate-600">
          Rows per batch
          <select
            className="h-9 rounded-md border border-slate-200 bg-white px-2 text-sm text-slate-900"
            value={rowLimit}
            onChange={(event) => setRowLimit(Number(event.target.value))}
          >
            <option value={20}>20</option>
            <option value={50}>50</option>
            <option value={100}>100</option>
          </select>
        </label>
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
                {(() => {
                  const filteredCases = batch.testCases.filter(matchesFilter);
                  const visibleCases = filteredCases.slice(0, rowLimit);
                  return filteredCases.length === 0 ? (
                    <p className="rounded-md border border-dashed border-slate-300 bg-slate-50 p-4 text-center text-sm text-slate-500">
                      No generated cases in this batch match the current filter.
                    </p>
                  ) : (
                    <>
                      <GeneratedCaseTable
                        testCases={visibleCases}
                        selectedIds={selectedIds}
                        setSelectedIds={setSelectedIds}
                        submitting={submitting}
                        onPromoteOne={onPromoteOne}
                      />
                      {filteredCases.length > visibleCases.length && (
                        <p className="text-sm text-slate-500">
                          Showing {visibleCases.length} of {filteredCases.length} matching cases in this batch. Increase rows per batch to see more.
                        </p>
                      )}
                    </>
                  );
                })()}
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
    <div className="max-h-[520px] overflow-auto rounded-md border border-slate-200">
      <table className="w-full min-w-[900px] text-left text-sm">
        <thead className="sticky top-0 z-10 bg-slate-50 text-xs uppercase text-slate-500">
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
