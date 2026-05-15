import { useCallback, useEffect, useMemo, useState } from "react";
import TeamWorkspace from "./TeamWorkspace";
import TeamLandingPage, { LandingLifecycle } from "./components/TeamLandingPage";
import {
  ContestResponse,
  ContestStreamSnapshot,
  ContestStreamUpdate,
} from "../admin/types/api";
import { decodeJwtSubject } from "../auth/jwt";
import {
  useContestStream,
  ContestStreamConnectionState,
} from "../hooks/useContestStream";
import {
  getActiveContest,
  getUpcomingContest,
  getPausedContest,
} from "./services/teamApi";
import { Loader2, LogOut } from "lucide-react";
import { Button } from "./components/ui/button";

type ResolvedState =
  | {
      lifecycle: "RUNNING" | "UPCOMING" | "PAUSED" | "ENDED";
      contest: ContestResponse;
    }
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
  // contests would otherwise greet a freshly logged-in team. SSE drives
  // the ENDED transition live via onContestUpdate.
  return { lifecycle: "NONE", contest: null };
}

// Snapshot priority: paused before upcoming because a paused contest is
// live-but-suspended (more relevant to the team than something that may
// start hours from now). Ended contests are intentionally ignored here:
// the snapshot includes the admin archive, and teams should not be greeted
// by an old contest on login when nothing is currently scheduled or live.
function snapshotToState(snap: ContestStreamSnapshot): ResolvedState {
  if (snap.active) return { lifecycle: "RUNNING", contest: snap.active };
  if (snap.paused) return { lifecycle: "PAUSED", contest: snap.paused };
  if (snap.upcoming) return { lifecycle: "UPCOMING", contest: snap.upcoming };
  return { lifecycle: "NONE", contest: null };
}

function updateToState(update: ContestStreamUpdate): ResolvedState {
  const c = update.snapshot;
  switch (update.reason) {
    case "MANUAL_START":
    case "AUTO_START":
    case "MANUAL_RESUME":
      return { lifecycle: "RUNNING", contest: c };
    case "MANUAL_PAUSE":
      return { lifecycle: "PAUSED", contest: c };
    case "MANUAL_END":
    case "AUTO_END":
      return { lifecycle: "ENDED", contest: c };
    case "CREATED":
    case "UPDATED":
      return { lifecycle: "UPCOMING", contest: c };
  }
}

export default function TeamApp({ onLogout }: { onLogout: () => void }) {
  const teamName = useMemo(getTeamNameFromToken, []);

  const [state, setState] = useState<ResolvedState | null>(null);

  // Cold-load REST probe. Race-guard with prev: if SSE has already pushed a
  // snapshot, REST must not overwrite it — the server is the authoritative
  // clock, and the snapshot represents server state at the moment of connect.
  useEffect(() => {
    let mounted = true;
    (async () => {
      const resolved = await resolveContestState();
      if (!mounted) return;
      setState((prev) => prev ?? resolved);
    })();
    return () => {
      mounted = false;
    };
  }, []);

  const onSnapshot = useCallback((snap: ContestStreamSnapshot) => {
    setState(snapshotToState(snap));
  }, []);

  const onContestUpdate = useCallback((update: ContestStreamUpdate) => {
    setState(updateToState(update));
  }, []);

  const { connectionState } = useContestStream(
    { onSnapshot, onContestUpdate },
    "/api/team/stream"
  );

  const indicator = <ConnectionIndicator state={connectionState} />;

  if (!state) {
    return (
      <>
        <TeamLoadingPage onLogout={onLogout} />
        {indicator}
      </>
    );
  }

  if (state.lifecycle === "RUNNING") {
    return (
      <>
        <TeamWorkspace
          contest={state.contest}
          teamName={teamName}
          onLogout={onLogout}
        />
        {indicator}
      </>
    );
  }

  const landingLifecycle: LandingLifecycle =
    state.lifecycle === "NONE" ? "NONE" : state.lifecycle;

  return (
    <>
      <TeamLandingPage
        lifecycle={landingLifecycle}
        contest={state.contest}
        onLogout={onLogout}
      />
      {indicator}
    </>
  );
}

function TeamLoadingPage({ onLogout }: { onLogout: () => void }) {
  return (
    <div className="relative min-h-screen bg-background flex flex-col items-center justify-center p-6">
      <Button
        variant="ghost"
        onClick={onLogout}
        className="absolute top-4 right-4"
      >
        <LogOut className="w-4 h-4 mr-2" />
        Logout
      </Button>

      <div className="flex flex-col items-center text-center gap-4">
        <div className="h-12 w-12 rounded-full bg-surface-container flex items-center justify-center">
          <Loader2 className="w-5 h-5 text-primary animate-spin" />
        </div>
        <div className="space-y-2">
          <h1 className="font-display text-3xl md:text-4xl font-semibold tracking-tight text-on-surface">
            Contest UI
          </h1>
          <p className="text-on-surface-soft">Preparing your workspace</p>
        </div>
      </div>
    </div>
  );
}

function ConnectionIndicator({
  state,
}: {
  state: ContestStreamConnectionState;
}) {
  const color =
    state === "open"
      ? "bg-tertiary"
      : state === "connecting"
      ? "bg-secondary"
      : "bg-on-surface-soft";
  const label =
    state === "open"
      ? null
      : state === "connecting"
      ? "Reconnecting…"
      : "Offline";

  return (
    <div
      className="fixed bottom-4 right-4 z-50 flex items-center gap-1.5 rounded-full bg-surface-container/85 px-2.5 py-1 text-xs text-on-surface-variant backdrop-blur-md"
      title={`Stream ${state}`}
    >
      <span className={`inline-block w-2 h-2 rounded-full ${color} ${state === "connecting" ? "aura-pulse" : ""}`} />
      {label}
    </div>
  );
}
