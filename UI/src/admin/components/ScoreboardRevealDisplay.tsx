import { ScoreboardView } from "./ScoreboardView";

function readContestId(): number | null {
  const raw = new URLSearchParams(window.location.search).get("contestId");
  if (!raw) return null;
  const contestId = Number(raw);
  return Number.isInteger(contestId) && contestId > 0 ? contestId : null;
}

export function ScoreboardRevealDisplay() {
  return <ScoreboardView contestId={readContestId()} presentationMode />;
}
