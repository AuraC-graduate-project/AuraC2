import { useCallback, useEffect, useMemo, useState } from "react";
import TeamWorkspace from "./TeamWorkspace";
import TeamLandingPage, { LandingLifecycle } from "./components/TeamLandingPage";
import {
  ContestResponse,
  ContestStreamSnapshot,
  ContestStreamUpdate,
  TeamContestAccessResponse,
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
  getMyContestAccess,
} from "./services/teamApi";
import { Loader2, LogOut, ShieldAlert } from "lucide-react";
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
    case "UPDATED": {
      const lifecycle = c.effectiveState ?? c.status;
      if (
        lifecycle === "RUNNING" ||
        lifecycle === "UPCOMING" ||
        lifecycle === "PAUSED" ||
        lifecycle === "ENDED"
      ) {
        return { lifecycle, contest: c };
      }
      return { lifecycle: "NONE", contest: null };
    }
    case "CREATED":
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
        <TeamContestGate
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

function TeamContestGate({
  contest,
  teamName,
  onLogout,
}: {
  contest: ContestResponse;
  teamName: string;
  onLogout: () => void;
}) {
  const [access, setAccess] = useState<TeamContestAccessResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    setError(null);
    getMyContestAccess(contest.id)
      .then((next) => {
        if (mounted) setAccess(next);
      })
      .catch((err) => {
        if (mounted) setError(err instanceof Error ? err.message : "Could not load contest access.");
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });

    return () => {
      mounted = false;
    };
  }, [contest.id]);

  if (loading) {
    return <TeamLoadingPage onLogout={onLogout} />;
  }

  if (error || !access) {
    return (
      <TeamBlockedPage
        title="Contest access unavailable"
        message={error ?? "AuraC2 could not verify your contest access."}
        onLogout={onLogout}
      />
    );
  }

  if (!access.workspaceVisible || access.status === "DISQUALIFIED") {
    return (
      <TeamBlockedPage
        title="You are disqualified from this contest."
        message="Please contact the contest administrator if you believe this is a mistake."
        onLogout={onLogout}
      />
    );
  }

  return (
    <TeamWorkspace
      contest={contest}
      teamName={teamName}
      teamAccess={access}
      onLogout={onLogout}
    />
  );
}

function TeamBlockedPage({
  title,
  message,
  onLogout,
}: {
  title: string;
  message: string;
  onLogout: () => void;
}) {
  return (
    <div className="relative flex min-h-screen flex-col items-center justify-center bg-gray-50 p-6 text-center">
      <Button
        variant="ghost"
        onClick={onLogout}
        className="absolute right-4 top-4 text-gray-600 hover:text-gray-900"
      >
        <LogOut className="mr-2 h-4 w-4" />
        Logout
      </Button>

      <div className="w-full max-w-md rounded-lg border border-slate-200 bg-white p-8 shadow-sm">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full border border-rose-200 bg-rose-50 text-rose-700">
          <ShieldAlert className="h-6 w-6" />
        </div>
        <h1 className="text-2xl font-semibold text-slate-950">{title}</h1>
        <p className="mt-3 text-sm leading-6 text-slate-600">{message}</p>
      </div>
    </div>
  );
}

function TeamLoadingPage({ onLogout }: { onLogout: () => void }) {
  return (
    <div className="relative min-h-screen bg-gray-50 flex flex-col items-center justify-center p-6">
      <Button
        variant="ghost"
        onClick={onLogout}
        className="absolute top-4 right-4 text-gray-600 hover:text-gray-900"
      >
        <LogOut className="w-4 h-4 mr-2" />
        Logout
      </Button>

      <div className="flex flex-col items-center text-center gap-4">
        <div className="h-12 w-12 rounded-full border border-gray-200 bg-white shadow-sm flex items-center justify-center">
          <Loader2 className="w-5 h-5 text-[#1E293B] animate-spin" />
        </div>
        <div className="space-y-2">
          <h1 className="text-3xl md:text-4xl font-bold text-[#1E293B]">
            Contest UI
          </h1>
          <p className="text-gray-500">Preparing your workspace</p>
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
      ? "bg-emerald-500"
      : state === "connecting"
      ? "bg-amber-400"
      : "bg-slate-400";
  const label =
    state === "open"
      ? null
      : state === "connecting"
      ? "Reconnecting…"
      : "Offline";

  return (
    <div
      className="fixed bottom-4 right-4 z-50 flex items-center gap-1.5 px-2 py-1 rounded bg-white/80 backdrop-blur-sm border border-gray-200 shadow-sm text-xs text-gray-600"
      title={`Stream ${state}`}
    >
      <span className={`inline-block w-2 h-2 rounded-full ${color}`} />
      {label}
    </div>
  );
}
