import { CheckCircle2, Clock3, PauseCircle, XCircle, AlertTriangle, ShieldCheck } from "lucide-react";

type BadgeTone =
  | "blue"
  | "green"
  | "amber"
  | "red"
  | "slate"
  | "purple"
  | "cyan"
  | "gold";

/* Each tone gets a distinct hue so verdicts (TLE vs WA vs RE vs CE) read at a
   glance. Light theme: soft pill fill. Dark theme: low-key surface with a
   left accent bar per the Obsidian "Pulse Chips" rule. */
const toneClasses: Record<BadgeTone, string> = {
  // Cool blue — pending / in-flight
  blue:
    "bg-sky-100 text-sky-800 dark:bg-surface-container-highest dark:text-sky-300 dark:border-l-2 dark:border-sky-400",
  // Calm green — accepted / answered
  green:
    "bg-emerald-100 text-emerald-800 dark:bg-surface-container-highest dark:text-emerald-300 dark:border-l-2 dark:border-emerald-400",
  // Warm amber — timeout (TLE) / paused
  amber:
    "bg-amber-100 text-amber-900 dark:bg-surface-container-highest dark:text-amber-300 dark:border-l-2 dark:border-amber-400",
  // Brick red — wrong answer
  red:
    "bg-rose-100 text-rose-800 dark:bg-surface-container-highest dark:text-rose-300 dark:border-l-2 dark:border-rose-400",
  // Neutral slate — internal / unknown / private
  slate:
    "bg-surface-container-high text-on-surface-variant dark:bg-surface-container-highest dark:text-on-surface-variant dark:border-l-2 dark:border-outline-variant",
  // Violet — runtime error / private (crashy)
  purple:
    "bg-violet-100 text-violet-800 dark:bg-surface-container-highest dark:text-violet-300 dark:border-l-2 dark:border-violet-400",
  // Cyan — public test / brand-adjacent informational
  cyan:
    "bg-cyan-100 text-cyan-800 dark:bg-surface-container-highest dark:text-cyan-300 dark:border-l-2 dark:border-cyan-400",
  // Orange — compilation error (build broken / IDE-style warning)
  gold:
    "bg-orange-100 text-orange-800 dark:bg-surface-container-highest dark:text-orange-300 dark:border-l-2 dark:border-orange-400",
};

export type VerdictLabel =
  | "ACCEPTED"
  | "WRONG_ANSWER"
  | "TLE"
  | "COMPILATION_ERROR"
  | "RUNTIME_ERROR"
  | "INTERNAL_ERROR"
  | "PENDING"
  | "RUNNING";

function normalized(value: string | null | undefined): string {
  return String(value ?? "").trim().toUpperCase();
}

export function formatStatusText(value: string | null | undefined): string {
  const text = normalized(value);
  return text ? text.replaceAll("_", " ") : "UNKNOWN";
}

export function normalizeVerdict(value: string | null | undefined): VerdictLabel | "UNKNOWN" {
  const text = normalized(value);
  if (text === "TIME_LIMIT_EXCEEDED") return "TLE";
  if (
    text === "ACCEPTED" ||
    text === "WRONG_ANSWER" ||
    text === "TLE" ||
    text === "COMPILATION_ERROR" ||
    text === "RUNTIME_ERROR" ||
    text === "INTERNAL_ERROR" ||
    text === "PENDING" ||
    text === "RUNNING"
  ) {
    return text;
  }
  return "UNKNOWN";
}

function toneForKind(kind: StatusBadgeKind, value: string): BadgeTone {
  if (kind === "contest") {
    if (value === "RUNNING") return "green";
    if (value === "PAUSED") return "amber";
    if (value === "ENDED") return "slate";
    return "blue";
  }

  if (kind === "difficulty") {
    if (value === "EASY") return "green";
    if (value === "MEDIUM") return "amber";
    return "red";
  }

  if (kind === "verdict") {
    if (value === "ACCEPTED") return "green";
    if (value === "PENDING" || value === "RUNNING") return "blue";
    if (value === "TLE") return "amber";
    if (value === "RUNTIME_ERROR") return "purple";
    if (value === "COMPILATION_ERROR") return "gold";
    if (value === "INTERNAL_ERROR") return "slate";
    return "red";
  }

  if (kind === "clarification") {
    if (value === "ANSWERED") return "green";
    if (value === "PENDING") return "amber";
    return "slate";
  }

  if (kind === "visibility") {
    if (value === "PUBLIC") return "blue";
    if (value === "PRIVATE") return "purple";
    return "slate";
  }

  if (kind === "testcase") {
    if (value === "PUBLIC" || value === "PUBLIC SAMPLE") return "cyan";
    return "slate";
  }

  return "slate";
}

function IconForStatus({ kind, value }: { kind: StatusBadgeKind; value: string }) {
  if (kind === "verdict") {
    if (value === "ACCEPTED") return <CheckCircle2 className="h-3.5 w-3.5" />;
    if (value === "PENDING" || value === "RUNNING") return <Clock3 className="h-3.5 w-3.5 aura-pulse" />;
    return <XCircle className="h-3.5 w-3.5" />;
  }

  if (kind === "contest") {
    if (value === "RUNNING") return <CheckCircle2 className="h-3.5 w-3.5" />;
    if (value === "PAUSED") return <PauseCircle className="h-3.5 w-3.5" />;
    return <Clock3 className="h-3.5 w-3.5" />;
  }

  if (kind === "clarification" && value === "PENDING") {
    return <AlertTriangle className="h-3.5 w-3.5" />;
  }

  if (kind === "visibility" && value === "PUBLIC") {
    return <ShieldCheck className="h-3.5 w-3.5" />;
  }

  return null;
}

export type StatusBadgeKind =
  | "contest"
  | "difficulty"
  | "verdict"
  | "clarification"
  | "visibility"
  | "testcase"
  | "neutral";

export function StatusBadge({
  kind = "neutral",
  value,
  label,
  className = "",
}: {
  kind?: StatusBadgeKind;
  value: string | null | undefined;
  label?: string;
  className?: string;
}) {
  const normalizedValue = kind === "verdict" ? normalizeVerdict(value) : normalized(value);
  const tone = toneForKind(kind, normalizedValue);
  const display = label ?? (kind === "verdict" ? normalizedValue : formatStatusText(normalizedValue));

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium leading-none tracking-tight ${toneClasses[tone]} ${className}`}
    >
      <IconForStatus kind={kind} value={normalizedValue} />
      {display}
    </span>
  );
}
