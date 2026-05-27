import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { Code2, Play, RefreshCw, TerminalSquare } from 'lucide-react';
import { toast } from 'sonner';
import {
  getProblemsByContest,
  getSupportedLanguages,
  runAdminLab,
} from '../services/api';
import {
  AdminRunLabResponse,
  ProblemResponse,
  SupportedLanguageResponse,
} from '../types/api';
import {
  contestLabel,
  ContestOption,
  loadContestOptions as loadContestOptionsList,
} from '../utils/contestOptions';
import { Button } from './ui/button';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from './ui/select';
import { Textarea } from './ui/textarea';
import { AdminHelpTooltip } from './AdminHelpTooltip';

type RunLabViewProps = {
  initialContestId?: number | null;
};

function idFromUrl(key: string): number | null {
  try {
    const raw = new URLSearchParams(window.location.search).get(key);
    if (!raw) return null;
    const parsed = Number(raw);
    return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
  } catch {
    return null;
  }
}

function problemLetter(index: number): string {
  if (index >= 0 && index < 26) return String.fromCharCode(65 + index);
  return String(index + 1);
}

function verdictClass(verdict?: string | null): string {
  switch (verdict) {
    case 'ACCEPTED':
      return 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-[#315040] dark:bg-[#172820] dark:text-[#a9c7b8]';
    case 'WRONG_ANSWER':
    case 'RUNTIME_ERROR':
      return 'border-rose-200 bg-rose-50 text-rose-700 dark:border-[#57363b] dark:bg-[#2b1d20] dark:text-[#d4b0b5]';
    case 'TLE':
      return 'border-amber-200 bg-amber-50 text-amber-800 dark:border-[#5a4b2d] dark:bg-[#2a2418] dark:text-[#d1c09a]';
    case 'COMPILATION_ERROR':
      return 'border-violet-200 bg-violet-50 text-violet-700 dark:border-[#4f3b63] dark:bg-[#241d2e] dark:text-[#c5b1d7]';
    default:
      return 'border-slate-200 bg-slate-50 text-slate-700 dark:border-[#384352] dark:bg-[#1b2431] dark:text-[#c4ccd8]';
  }
}

function outputText(value?: string | null): string {
  return value && value.length > 0 ? value : '(empty)';
}

export function RunLabView({ initialContestId = null }: RunLabViewProps) {
  const preferredContestId = idFromUrl('contestId') ?? initialContestId;
  const preferredProblemId = idFromUrl('problemId');
  const [contestOptions, setContestOptions] = useState<ContestOption[]>([]);
  const [problems, setProblems] = useState<ProblemResponse[]>([]);
  const [languages, setLanguages] = useState<SupportedLanguageResponse[]>([]);
  const [selectedContestId, setSelectedContestId] = useState('');
  const [selectedProblemId, setSelectedProblemId] = useState('');
  const [selectedLanguageId, setSelectedLanguageId] = useState('');
  const [sourceCode, setSourceCode] = useState('');
  const [customInput, setCustomInput] = useState('');
  const [loadingContests, setLoadingContests] = useState(false);
  const [loadingProblems, setLoadingProblems] = useState(false);
  const [running, setRunning] = useState(false);
  const [result, setResult] = useState<AdminRunLabResponse | null>(null);

  const selectedContest = useMemo(
    () => contestOptions.find((contest) => String(contest.id) === selectedContestId) ?? null,
    [contestOptions, selectedContestId],
  );
  const selectedProblem = useMemo(
    () => problems.find((problem) => String(problem.id) === selectedProblemId) ?? null,
    [problems, selectedProblemId],
  );

  const runnableLanguages = useMemo(
    () => languages.filter((language) => language.supportsSubmission),
    [languages],
  );

  const loadContests = useCallback(async () => {
    setLoadingContests(true);
    try {
      const options = await loadContestOptionsList();
      setContestOptions(options);
      const preferred = preferredContestId && options.some((contest) => contest.id === preferredContestId)
        ? preferredContestId
        : options[0]?.id;
      setSelectedContestId(preferred ? String(preferred) : '');
    } catch {
      toast.error('Failed to load contests.');
      setContestOptions([]);
    } finally {
      setLoadingContests(false);
    }
  }, [preferredContestId]);

  const loadLanguages = useCallback(async () => {
    try {
      const next = await getSupportedLanguages();
      setLanguages(next);
      const firstSubmissionLanguage = next.find((language) => language.supportsSubmission);
      setSelectedLanguageId((current) => current || (firstSubmissionLanguage ? String(firstSubmissionLanguage.judge0LanguageId) : ''));
    } catch {
      toast.error('Failed to load supported languages.');
      setLanguages([]);
    }
  }, []);

  const loadProblems = useCallback(async () => {
    if (!selectedContestId) {
      setProblems([]);
      setSelectedProblemId('');
      return;
    }
    setLoadingProblems(true);
    try {
      const next = await getProblemsByContest(Number(selectedContestId));
      setProblems(next);
      setSelectedProblemId((current) => {
        if (current && next.some((problem) => String(problem.id) === current)) return current;
        if (preferredProblemId && next.some((problem) => problem.id === preferredProblemId)) {
          return String(preferredProblemId);
        }
        return next[0] ? String(next[0].id) : '';
      });
    } catch {
      toast.error('Failed to load problems.');
      setProblems([]);
      setSelectedProblemId('');
    } finally {
      setLoadingProblems(false);
    }
  }, [preferredProblemId, selectedContestId]);

  useEffect(() => {
    loadContests();
    loadLanguages();
  }, [loadContests, loadLanguages]);

  useEffect(() => {
    loadProblems();
  }, [loadProblems]);

  const handleRun = async () => {
    if (!selectedContest || !selectedProblem || !selectedLanguageId) {
      toast.error('Select a contest, problem, and language first.');
      return;
    }
    if (!sourceCode.trim()) {
      toast.error('Source code cannot be empty.');
      return;
    }

    setRunning(true);
    setResult(null);
    try {
      const response = await runAdminLab({
        contestId: selectedContest.id,
        problemId: selectedProblem.id,
        languageId: Number(selectedLanguageId),
        sourceCode,
        customInput,
      });
      setResult(response);
      toast.success('Run Lab execution completed.');
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Run Lab execution failed.');
    } finally {
      setRunning(false);
    }
  };

  const runDisabled = running || !selectedContest || !selectedProblem || !selectedLanguageId || !sourceCode.trim();

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Admin-only sandbox</p>
          <h1 className="text-3xl font-semibold text-slate-950">Run Lab</h1>
          <p className="mt-1 max-w-3xl text-sm text-slate-600">
            Run source code against custom input through Judge0 without creating an official submission,
            changing penalties, or touching the scoreboard.
          </p>
        </div>
        <Button variant="outline" className="gap-2 bg-white" onClick={() => { loadContests(); loadProblems(); }}>
          <RefreshCw className="h-4 w-4" />
          Refresh
        </Button>
      </div>

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_380px]">
        <Card>
          <CardHeader className="border-b border-slate-100">
            <CardTitle className="flex items-center gap-2 text-xl text-slate-950">
              <Code2 className="h-5 w-5 text-blue-700" />
              Execution Setup
              <AdminHelpTooltip content="Run Lab is admin-only and currently runs custom input only. It does not run hidden tests or create official submissions." />
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-5 p-5">
            <div className="grid gap-4 lg:grid-cols-3">
              <Field label="Contest">
                <Select
                  value={selectedContestId}
                  onValueChange={(value) => {
                    setSelectedContestId(value);
                    setSelectedProblemId('');
                    setResult(null);
                  }}
                  disabled={loadingContests || contestOptions.length === 0}
                >
                  <SelectTrigger className="h-10 bg-white">
                    <SelectValue placeholder={loadingContests ? 'Loading contests...' : 'Select contest'} />
                  </SelectTrigger>
                  <SelectContent>
                    {contestOptions.map((contest) => (
                      <SelectItem key={contest.id} value={String(contest.id)}>
                        {contestLabel(contest)} - {contest.bucket}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>

              <Field label="Problem">
                <Select
                  value={selectedProblemId}
                  onValueChange={(value) => {
                    setSelectedProblemId(value);
                    setResult(null);
                  }}
                  disabled={loadingProblems || problems.length === 0}
                >
                  <SelectTrigger className="h-10 bg-white">
                    <SelectValue placeholder={loadingProblems ? 'Loading problems...' : 'Select problem'} />
                  </SelectTrigger>
                  <SelectContent>
                    {problems.map((problem, index) => (
                      <SelectItem key={problem.id} value={String(problem.id)}>
                        {problemLetter(index)}. {problem.title}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>

              <Field label="Language">
                <Select
                  value={selectedLanguageId}
                  onValueChange={(value) => {
                    setSelectedLanguageId(value);
                    setResult(null);
                  }}
                  disabled={runnableLanguages.length === 0}
                >
                  <SelectTrigger className="h-10 bg-white">
                    <SelectValue placeholder="Select language" />
                  </SelectTrigger>
                  <SelectContent>
                    {runnableLanguages.map((language) => (
                      <SelectItem key={language.value} value={String(language.judge0LanguageId)}>
                        {language.label} - Judge0 #{language.judge0LanguageId}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
            </div>

            <div className="grid gap-4 lg:grid-cols-[minmax(0,1.2fr)_minmax(280px,0.8fr)]">
              <Field label="Source Code">
                <Textarea
                  value={sourceCode}
                  onChange={(event) => {
                    setSourceCode(event.target.value);
                    setResult(null);
                  }}
                  placeholder="Paste or write the solution source code here."
                  className="min-h-[430px] resize-y border-slate-300 bg-slate-950 font-mono text-sm text-slate-100 placeholder:text-slate-500"
                  spellCheck={false}
                />
              </Field>

              <Field label="Custom Input">
                <Textarea
                  value={customInput}
                  onChange={(event) => {
                    setCustomInput(event.target.value);
                    setResult(null);
                  }}
                  placeholder="Input passed to stdin. Leave empty for no stdin."
                  className="min-h-[430px] resize-y bg-white font-mono text-sm"
                  spellCheck={false}
                />
              </Field>
            </div>

            <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-100 pt-4">
              <p className="text-sm text-slate-600">
                Custom input mode only. Hidden tests and hidden expected outputs are not loaded.
              </p>
              <Button className="gap-2 bg-blue-700 hover:bg-blue-800" disabled={runDisabled} onClick={handleRun}>
                <Play className="h-4 w-4" />
                {running ? 'Running...' : 'Run'}
              </Button>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="border-b border-slate-100">
            <CardTitle className="flex items-center gap-2 text-xl text-slate-950">
              <TerminalSquare className="h-5 w-5 text-blue-700" />
              Result
            </CardTitle>
          </CardHeader>
          <CardContent className="space-y-4 p-5">
            {!result ? (
              <div className="rounded-lg border border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-sm text-slate-600">
                Run code to see status, stdout, stderr, compile output, time, and memory.
              </div>
            ) : (
              <>
                <div className={`inline-flex rounded-full border px-3 py-1 text-xs font-semibold ${verdictClass(result.verdict)}`}>
                  {result.verdict?.replaceAll('_', ' ') ?? 'UNKNOWN'}
                </div>
                <div className="grid grid-cols-2 gap-3 text-sm">
                  <Metric label="Status" value={result.statusDescription ?? `Judge0 #${result.statusId ?? '-'}`} />
                  <Metric label="Scoring" value={result.scoring ? 'Yes' : 'No'} />
                  <Metric label="Runtime" value={result.runtimeMillis == null ? '-' : `${result.runtimeMillis} ms`} />
                  <Metric label="Memory" value={result.memoryKb == null ? '-' : `${result.memoryKb} KB`} />
                </div>
                {result.compileOutput ? <OutputBlock label="Compile Output" value={result.compileOutput} tone="warning" /> : null}
                <OutputBlock label="Stdout" value={outputText(result.stdout)} />
                <OutputBlock label="Stderr" value={outputText(result.stderr)} />
              </>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="space-y-1.5">
      <label className="text-xs font-semibold uppercase text-slate-500">{label}</label>
      {children}
    </div>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
      <p className="text-xs font-semibold uppercase text-slate-500">{label}</p>
      <p className="mt-1 text-sm font-medium text-slate-900">{value}</p>
    </div>
  );
}

function OutputBlock({
  label,
  value,
  tone = 'neutral',
}: {
  label: string;
  value: string;
  tone?: 'neutral' | 'warning';
}) {
  const toneClass = tone === 'warning'
    ? 'border-amber-200 bg-amber-50 text-amber-950'
    : 'border-slate-200 bg-slate-950 text-slate-100';

  return (
    <div>
      <p className="mb-1 text-xs font-semibold uppercase text-slate-500">{label}</p>
      <pre className={`max-h-64 overflow-auto whitespace-pre-wrap rounded-lg border p-3 font-mono text-xs ${toneClass}`}>
        {value}
      </pre>
    </div>
  );
}
