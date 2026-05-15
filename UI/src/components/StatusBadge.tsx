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

const toneClasses: Record<BadgeTone, string> = {
  blue: "border-blue-200 bg-blue-50 text-blue-700 dark:border-[#33465f] dark:bg-[#192636] dark:text-[#aebed2]",
  green: "border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-[#315040] dark:bg-[#172820] dark:text-[#a9c7b8]",
  amber: "border-amber-200 bg-amber-50 text-amber-700 dark:border-[#5a4b2d] dark:bg-[#2a2418] dark:text-[#d1c09a]",
  red: "border-rose-200 bg-rose-50 text-rose-700 dark:border-[#57363b] dark:bg-[#2b1d20] dark:text-[#d4b0b5]",
  slate: "border-slate-200 bg-slate-50 text-slate-700 dark:border-[#384352] dark:bg-[#1b2431] dark:text-[#c4ccd8]",
  purple: "border-violet-200 bg-violet-50 text-violet-700 dark:border-[#45394f] dark:bg-[#211d2a] dark:text-[#c1b4cc]",
  cyan: "border-cyan-200 bg-cyan-50 text-cyan-700 dark:border-[#304c54] dark:bg-[#17272d] dark:text-[#a7c3ca]",
  gold: "border-yellow-200 bg-yellow-50 text-yellow-800 dark:border-[#5a4b2d] dark:bg-[#2a2418] dark:text-[#d1c09a]",
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
    if (value === "PENDING" || value === "RUNNING") return "amber";
    if (value === "COMPILATION_ERROR") return "purple";
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
    if (value === "PENDING" || value === "RUNNING") return <Clock3 className="h-3.5 w-3.5" />;
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
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-1 text-xs font-semibold leading-none ${toneClasses[tone]} ${className}`}
    >
      <IconForStatus kind={kind} value={normalizedValue} />
      {display}
    </span>
  );
}
