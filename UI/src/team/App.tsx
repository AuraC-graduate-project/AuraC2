import { useEffect, useMemo, useState } from "react";
import TeamWorkspace from "./TeamWorkspace";
import TeamLandingPage, { LandingLifecycle } from "./components/TeamLandingPage";
import { ContestResponse } from "../admin/types/api";
import { decodeJwtSubject } from "../auth/jwt";
import {
  getActiveContest,
  getUpcomingContest,
  getPausedContest,
} from "./services/teamApi";

type ResolvedState =
  | { lifecycle: "RUNNING" | "UPCOMING" | "PAUSED"; contest: ContestResponse }
  | { lifecycle: "NONE"; contest: null };

/** JWT sub = TEAM NAME (SAFE) */
function getTeamNameFromToken(): string {
  try {
    const token = localStorage.getItem("access_token");
    if (!token) return "Team";
    return decodeJwtSubject(token) ?? "Team";
  } catch {
    return "Team";
  }
}

async function resolveContestState(): Promise<ResolvedState> {
  try {
    const c = await getActiveContest();
    if (c) return { lifecycle: "RUNNING", contest: c };
  } catch {
    // 404 / no active contest — fall through.
  }

  try {
    const c = await getUpcomingContest();
    if (c) return { lifecycle: "UPCOMING", contest: c };
  } catch {
    // fall through
  }

  try {
    const c = await getPausedContest();
    if (c) return { lifecycle: "PAUSED", contest: c };
  } catch {
    // fall through
  }

  // Note: ENDED state is intentionally not probed on cold load — past
  // contests would otherwise greet a freshly logged-in team. The ENDED
  // lifecycle is reserved for the SSE transition (contest ends while the
  // team is on the page), which lands in the follow-up task.

  return { lifecycle: "NONE", contest: null };
}

export default function TeamApp({ onLogout }: { onLogout: () => void }) {
  const teamName = useMemo(getTeamNameFromToken, []);

  const [state, setState] = useState<ResolvedState | null>(null);

  useEffect(() => {
    let mounted = true;
    (async () => {
      const resolved = await resolveContestState();
      if (mounted) setState(resolved);
    })();
    return () => {
      mounted = false;
    };
  }, []);

  if (!state) return <div className="p-6">Loading…</div>;

  if (state.lifecycle === "RUNNING") {
    return (
      <TeamWorkspace
        contest={state.contest}
        teamName={teamName}
        onLogout={onLogout}
      />
    );
  }

  const landingLifecycle: LandingLifecycle =
    state.lifecycle === "NONE" ? "NONE" : (state.lifecycle as LandingLifecycle);

  return (
    <TeamLandingPage
      lifecycle={landingLifecycle}
      contest={state.contest}
      onLogout={onLogout}
    />
  );
}
