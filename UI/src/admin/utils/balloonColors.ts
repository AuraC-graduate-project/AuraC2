export const BALLOON_COLOR_PRESETS = [
  "#2563EB",
  "#E11D48",
  "#F59E0B",
  "#16A34A",
  "#7C3AED",
  "#DB2777",
  "#0891B2",
  "#475569",
];

export const DEFAULT_BALLOON_COLOR = BALLOON_COLOR_PRESETS[0];

const HEX_COLOR_PATTERN = /^#[0-9A-Fa-f]{6}$/;

export function normalizeBalloonColor(value: string | null | undefined): string {
  const trimmed = value?.trim() ?? "";
  if (!trimmed) return DEFAULT_BALLOON_COLOR;
  const withHash = trimmed.startsWith("#") ? trimmed : `#${trimmed}`;
  return withHash.toUpperCase();
}

export function isBalloonColor(value: string | null | undefined): boolean {
  return HEX_COLOR_PATTERN.test(normalizeBalloonColor(value));
}

export function defaultBalloonColor(problemIndex: number): string {
  const index = Number.isFinite(problemIndex) ? problemIndex : 0;
  return BALLOON_COLOR_PRESETS[((index % BALLOON_COLOR_PRESETS.length) + BALLOON_COLOR_PRESETS.length) % BALLOON_COLOR_PRESETS.length];
}
