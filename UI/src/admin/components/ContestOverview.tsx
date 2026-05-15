import { useState, useEffect, useCallback, useRef } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Button } from './ui/button';
import { Badge } from './ui/badge';
import { ToggleGroup, ToggleGroupItem } from './ui/toggle-group';
import {
  Play,
  Pause,
  StopCircle,
  Plus,
  Pencil,
  Calendar,
  Clock,
  FileText,
  Medal,
  RotateCcw,
  Snowflake
} from 'lucide-react';
import {
  getActiveContest,
  getUpcomingContest,
  getPausedContest,
  getEndedContests,
  startContest,
  resumeContest,
  pauseContest,
  endContest
} from '../services/api';
import {
  ContestLifecycleState,
  ContestResponse,
  ContestStreamSnapshot,
  ContestStreamUpdate,
  ContestUpdateReason
} from '../types/api';
import { useContestStream } from '../../hooks/useContestStream';
import { CreateContestModal } from './CreateContestModal';
import { EditContestModal } from './EditContestModal';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle
} from './ui/alert-dialog';
import { Checkbox } from './ui/checkbox';
import { toast } from 'sonner';

type ContestTab = 'active' | 'upcoming' | 'paused' | 'ended';
type ContestOverviewProps = {
  onOpenScoreboard?: (contestId: number) => void;
};

const FALLBACK_DELAY_MS = 3000;


export function ContestOverview({ onOpenScoreboard }: ContestOverviewProps) {// Every render, React runs this function again.
  const [contestType, setContestType] = useState<ContestTab>('active');// This stores which tab is currently selected. It can be 'active', 'upcoming', 'paused', or 'ended'. The default is 'active'.

  // This means UI stores all contest buckets separately. Whenever a new update comes in, we can place the contest in the right bucket based on its effective state. This also allows us to show ended contests as a list, since there can be multiple.
  const [activeContest, setActiveContest] = useState<ContestResponse | null>(null);
  const [upcomingContest, setUpcomingContest] = useState<ContestResponse | null>(null);
  const [pausedContest, setPausedContest] = useState<ContestResponse | null>(null);
  const [endedContests, setEndedContests] = useState<ContestResponse[]>([]);

  /** Hydrated here means "we've received at least one snapshot from the stream,
   *  or we've done the fallback REST hydration".
   *  Before hydration, we show a loading spinner.
   *  After hydration, we show the actual UI,
   *  which might be empty if there are no contests.
   *  This prevents a flash of "no contest found" while we're still waiting for data.
    * Hydrated in small sentence : Did we receive initial data yet?
   **/
  const [hydrated, setHydrated] = useState(false);

  // These control modal/dialog behavior.
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [endDialogOpen, setEndDialogOpen] = useState(false);
  const [juryOverride, setJuryOverride] = useState(false);

  /**
   * If backend gives me a full snapshot, replace all my local contest state with it.
   * So snapshot = full refresh.
   */
  const applySnapshot = useCallback((snap: ContestStreamSnapshot) => {// useCallback does not execute the function, it just tells React to reuse the same function object
    setActiveContest(snap.active);
    setUpcomingContest(snap.upcoming);
    setPausedContest(snap.paused);
    setEndedContests(snap.ended);
    setHydrated(true);
  }, []);

  /**
   * Take one contest and put it in the correct bucket.
   * This is used for incremental updates from the stream.
   * The backend will send us the updated contest, and we need to move it to the right place in our UI based on its new state.
   * For example, if a contest moves from UPCOMING to RUNNING, we remove it from the upcomingContest state and set it as the activeContest.
   */
  const placeContest = useCallback((snap: ContestResponse) => {

    /** First remove this contest from all buckets,
     *  in case it's moving.
     *  We identify the contest by its ID.
     *  If the contest in a bucket has the same ID as the incoming snapshot,
     *  we remove it (set to null or filter out).
     *  This ensures that we don't have duplicates when we add it to the correct bucket later.
    **/
     const removeSingle = (c: ContestResponse | null) =>
      c && c.id === snap.id ? null : c;
    setActiveContest((prev) => removeSingle(prev));
    setUpcomingContest((prev) => removeSingle(prev));
    setPausedContest((prev) => removeSingle(prev));
    setEndedContests((prev) => prev.filter((c) => c.id !== snap.id));

    /** Use effectiveState if available,
     *  otherwise fallback to status.
     *  effectiveState is what the UI should show based on pause-aware logic,
     *  while status is the raw lifecycle state.
     *  For example, a PAUSED contest might still have status RUNNING,
     *  but its effectiveState would be PAUSED.
     *  This allows the backend to communicate the true state of the contest to the UI,
     *  especially during edge cases like pausing or resuming.
    **/
     const state: ContestLifecycleState = snap.effectiveState ?? snap.status;
    // Then it puts the contest in the correct place
    switch (state) {
      case 'RUNNING':
        setActiveContest(snap);
        break;
      case 'UPCOMING':
        setUpcomingContest(snap);
        break;
      case 'PAUSED':
        setPausedContest(snap);
        break;
      case 'ENDED':
        setEndedContests((prev) => [snap, ...prev]);
        break;
    }
  }, []);

  /**
   * This decides which tab the UI should show after an event. For example, if we receive an update that a contest was just started,
   * we want to switch to the "active" tab to show it.
   * The reason field in the update tells us what happened, and we can use that to determine which tab is most relevant to show the user.
   * This is a UX decision to help guide the user to the most important information after an update.
   * CREATED → switch to upcoming
   * MANUAL_START → switch to active
   * MANUAL_PAUSE → switch to paused
   * AUTO_END → switch to ended
   **/
  const switchTabForReason = useCallback((reason: ContestUpdateReason) => {
    switch (reason) {
      case 'CREATED':
        setContestType('upcoming');
        break;
      case 'MANUAL_START':
      case 'MANUAL_RESUME':
      case 'AUTO_START':
        setContestType('active');
        break;
      case 'MANUAL_PAUSE':
        setContestType('paused');
        break;
      case 'MANUAL_END':
      case 'AUTO_END':
        setContestType('ended');
        break;
    }
  }, []);

  /**
   * This handles an incremental SSE update.
   *  When backend sends a live update:
   *  put contest in correct state bucket
   *  switch to the right tab
   */
  const handleStreamUpdate = useCallback(
    (update: ContestStreamUpdate) => {
      placeContest(update.snapshot);
      switchTabForReason(update.reason);
    },
    [placeContest, switchTabForReason]
  );

  /**
   * This is the connection point between the two files.
   * The useContestStream hook manages the SSE connection and calls our callbacks when it receives data.
   * We provide it with the applySnapshot and handleStreamUpdate functions we defined above,
   * so that it can update our UI state accordingly whenever we get new information from the backend.
   */
  const { connectionState } = useContestStream({
    onSnapshot: applySnapshot,
    onContestUpdate: handleStreamUpdate
  });


  /**
   * Fallback hydration after 3 seconds of no snapshot.
   * This covers the case where the SSE connection is established,
   * but we don't receive a snapshot event
   * (e.g., due to a backend issue or if the backend doesn't send snapshots).
   * After 3 seconds, we fetch the current state of all contests
   * through REST API calls to ensure our UI is populated with data.
   * We also set a ref to ensure this fallback only happens once,
   * and we check if we've already been hydrated by an SSE snapshot before doing the REST fetch,
   * to avoid unnecessary calls.
   */
  const fallbackDoneRef = useRef(false);
  useEffect(() => {
    if (hydrated || fallbackDoneRef.current) return;
    const timer = setTimeout(async () => {
      if (hydrated || fallbackDoneRef.current) return;
      fallbackDoneRef.current = true;
      try {
        const [active, upcoming, paused, ended] = await Promise.allSettled([
          getActiveContest(),
          getUpcomingContest(),
          getPausedContest(),
          getEndedContests()
        ]);
        // If API succeeded, use its value. If it failed, ignore and keep null/empty.
        setActiveContest(active.status === 'fulfilled' ? active.value : null);
        setUpcomingContest(upcoming.status === 'fulfilled' ? upcoming.value : null);
        setPausedContest(paused.status === 'fulfilled' ? paused.value : null);
        setEndedContests(ended.status === 'fulfilled' ? ended.value : []);
      } finally {
        setHydrated(true);// Even if some requests failed, the UI stops showing loading.
      }
    }, FALLBACK_DELAY_MS);
    return () => clearTimeout(timer);
  }, [hydrated]);

  // ─── NEW: poll REST every 10 s when SSE is dead ─────────────────────────────
// When the stream silently dies (proxy timeout, network drop), we have no way
// to receive the next SSE snapshot. Poll REST until the stream recovers.
// The moment SSE reconnects and sends a snapshot, applySnapshot runs and
// overwrites whatever polling produced — so the two never conflict.
  useEffect(() => {
    if (connectionState !== 'closed') return;

    const poll = async () => {
      try {
        const [active, upcoming, paused, ended] = await Promise.allSettled([
          getActiveContest(), getUpcomingContest(), getPausedContest(), getEndedContests()
        ]);
        setActiveContest(active.status === 'fulfilled' ? active.value : null);
        setUpcomingContest(upcoming.status === 'fulfilled' ? upcoming.value : null);
        setPausedContest(paused.status === 'fulfilled' ? paused.value : null);
        setEndedContests(ended.status === 'fulfilled' ? ended.value : []);
      } catch {
        // silently ignore — we'll retry on next interval
      }
    };

    poll(); // fetch immediately when we detect the drop
    const id = setInterval(poll, 10_000); // then every 10 s
    return () => clearInterval(id);       // stop when connectionState changes
  }, [connectionState]); // re-runs when SSE reconnects (state becomes 'open' → 'closed' again)


  /**
   * if current tab is active → use activeContest
   * if current tab is upcoming → use upcomingContest
   * if current tab is paused → use pausedContest
   * if ended tab → endedContests (but we show a list, so no single contest)
   */
  const contest: ContestResponse | null =
    contestType === 'active'
      ? activeContest
      : contestType === 'upcoming'
      ? upcomingContest
      : contestType === 'paused'
      ? pausedContest
      : null;

  /**
   * These are normal async functions,
   * These handlers do not directly change all the UI state.
   * Instead, they call the backend API to perform an action (like starting or pausing the contest).
   * The backend will then process that action,
   * update the contest state, and send us a new snapshot or update through the SSE stream.
   * When we receive that update, our onSnapshot or onContestUpdate handlers will be called,
   * which will then update our UI state accordingly.
   * This way, we ensure that our UI always reflects the true state of the backend,
   * and we avoid any inconsistencies that might arise from trying to manually update the UI state in these handlers.
   * ---------- The real source of truth is the backend + SSE ----------
   *
   * user clicks pause
   * frontend calls pauseContest(contest.id)
   * backend updates contest
   * backend emits SSE update
   * frontend receives update
   * frontend updates state through handleStreamUpdate
   */
  const handleStart = async () => {
    if (!contest) return;
    try {
      await startContest(contest.id);
      toast.success('Contest started');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to start contest');
    }
  };

  const handleResume = async () => {
    if (!contest) return;
    try {
      await resumeContest(contest.id);
      toast.success('Contest resumed');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to resume contest');
    }
  };

  const handlePause = async () => {
    if (!contest) return;
    try {
      await pauseContest(contest.id);
      toast.success('Contest paused');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to pause contest');
    }
  };

  const handleEnd = async () => {
    if (!contest) return;
    try {
      await endContest(contest.id, juryOverride);
      toast.success('Contest ended');
      setEndDialogOpen(false);
      setJuryOverride(false);
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to end contest');
    }
  };

  const getStatusColor = (status: ContestLifecycleState) => {
    switch (status) {
      case 'RUNNING':
        return 'bg-blue-100 text-blue-700 border-blue-200';
      case 'PAUSED':
        return 'bg-orange-100 text-orange-700 border-orange-200';
      case 'ENDED':
        return 'bg-slate-100 text-slate-700 border-slate-200';
      case 'UPCOMING':
        return 'bg-blue-100 text-blue-700 border-blue-200';
    }
  };

  const formatDuration = (m: number) => {
    const h = Math.floor(m / 60);
    const r = m % 60;
    if (!h) return `${r} minutes`;
    if (!r) return `${h} hour${h > 1 ? 's' : ''}`;
    return `${h} hour${h > 1 ? 's' : ''} ${r} min`;
  };

  const formatTime = (iso: string | null) => {
    if (!iso) return 'N/A';
    return new Date(iso).toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
      timeZoneName: 'short'
    });
  };

  // This controls which buttons are enabled.
  const lifecycleState: ContestLifecycleState | null = contest
    ? (contest.effectiveState ?? contest.status)
    : null;

  const canStart = lifecycleState === 'UPCOMING';
  const canResume = lifecycleState === 'PAUSED';
  const canPause = lifecycleState === 'RUNNING';
  const canEnd = lifecycleState === 'RUNNING' || lifecycleState === 'PAUSED';
  const canEdit = lifecycleState === 'UPCOMING';

  /**
   * The backend gives the base truth, and frontend creates a smooth local ticking timer between SSE updates.
   * Step 1
   *    If no contest exists:
   *    clear remaining time
   * Step 2
   *    If contest exists:
   *      initialize local countdown from server value:
   *        contest.remainingMillis
   * Step 3
   *    If contest is not running:
   *      stop there
   *      no ticking interval
   * Step 4
   *    If contest is running:
   *      create timer every 1 second
   *      decrease remainingMs by 1000
   **/
  const [remainingMs, setRemainingMs] = useState<number | null>(null);
  useEffect(() => {

    if (!contest) {
      setRemainingMs(null);
      return;
    }
    setRemainingMs(contest.remainingMillis ?? 0);
    if (lifecycleState !== 'RUNNING') return;
    const id = setInterval(() => {
      setRemainingMs((prev) => (prev == null ? prev : Math.max(0, prev - 1000)));
    }, 1000);
    return () => clearInterval(id);
  }, [contest, lifecycleState]);

  const formatCountdown = (ms: number | null) => {
    if (ms == null) return '—';
    const total = Math.max(0, Math.floor(ms / 1000));
    const h = Math.floor(total / 3600);
    const m = Math.floor((total % 3600) / 60);
    const s = total % 60;
    const pad = (n: number) => n.toString().padStart(2, '0');
    return `${pad(h)}:${pad(m)}:${pad(s)}`;
  };

  // Mirrors ContestLifecycleService.isScoreboardFrozen on the backend so the
  // badge can flip the moment the local countdown crosses the freeze threshold,
  // without waiting for an SSE push (the backend only pushes on lifecycle
  // transitions, not when the freeze window opens).
  // Falls back to the server-computed flag until the local timer initializes,
  // so the badge is correct on first paint after a snapshot.
  const freezeWindowMs =
    contest?.scoreboardFreezeMinutes != null && contest.scoreboardFreezeMinutes > 0
      ? contest.scoreboardFreezeMinutes * 60_000
      : null;
  const isFrozen =
    !!contest &&
    freezeWindowMs != null &&
    (lifecycleState === 'RUNNING' || lifecycleState === 'PAUSED') &&
    (remainingMs != null
      ? remainingMs <= freezeWindowMs
      : !!contest.scoreboardFrozen);


  const connectionLabel =
    connectionState === 'open'
      ? 'Live'
      : connectionState === 'connecting'
      ? 'Reconnecting…'
      : 'Offline';
  const connectionColor =
    connectionState === 'open'
      ? 'bg-emerald-500'
      : connectionState === 'connecting'
      ? 'bg-amber-400'
      : 'bg-slate-400';

  return (
    <>
      <Card className="border border-gray-200 shadow-sm">
        <CardHeader className="bg-[#1E293B] py-4 text-white">
          <div className="flex flex-col gap-4">
            <div className="flex flex-col gap-3 lg:flex-row lg:items-center lg:justify-between">
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-300">Contest lifecycle</p>
                <CardTitle className="mt-1 leading-none">Contest Overview</CardTitle>
              </div>

              <div className="flex items-center gap-2">
              <span
                title={`Stream ${connectionState}`}
                className="flex items-center gap-1.5 text-xs text-slate-200"
              >
                <span
                  className={`inline-block w-2 h-2 rounded-full ${connectionColor}`}
                />
                {connectionLabel}
              </span>
              {isFrozen && (
                <Badge className="bg-cyan-100 text-cyan-700 border-cyan-200 border gap-1">
                  <Snowflake className="w-3 h-3" />
                  Frozen
                </Badge>
              )}
              {contest && (
                <Badge className={`${getStatusColor(lifecycleState ?? contest.status)} border`}>
                  {lifecycleState ?? contest.status}
                </Badge>
              )}
              </div>
            </div>

            <ToggleGroup
              type="single"
              value={contestType}
              onValueChange={(v) => v && setContestType(v as ContestTab)}
              className="grid h-auto w-full grid-cols-4 rounded-lg bg-slate-700 p-1"
            >
              {(['active', 'upcoming', 'paused', 'ended'] as const).map((t) => (
                <ToggleGroupItem
                  key={t}
                  value={t}
                  className="
                    h-9 w-full justify-center rounded-md px-2 text-sm capitalize text-slate-200
                    data-[state=on]:bg-white data-[state=on]:text-slate-900 data-[state=on]:shadow-sm
                    focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-white/60
                  "
                >
                  {t}
                </ToggleGroupItem>
              ))}
            </ToggleGroup>
          </div>
        </CardHeader>

        <CardContent className="p-6">
          {!hydrated ? (
            <div className="text-center text-slate-600 py-8">Loading contest...</div>
          ) : contestType === 'ended' ? (
            endedContests.length === 0 ? (
              <div className="text-center py-12 text-slate-600">No ended contests</div>
            ) : (
              <div className="space-y-4">
                {endedContests.map((c) => (
                  <div
                    key={c.id}
                    className="border rounded-lg p-4 flex flex-col gap-3 sm:flex-row sm:justify-between sm:items-center"
                  >
                    <div>
                      <p className="font-medium text-slate-900">{c.title}</p>
                      <p className="text-sm text-slate-600">{c.description}</p>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                      {onOpenScoreboard && (
                        <Button
                          type="button"
                          variant="outline"
                          size="sm"
                          className="gap-2 bg-white"
                          onClick={() => onOpenScoreboard(c.id)}
                        >
                          <Medal className="h-4 w-4" />
                          Scoreboard / Reveal
                        </Button>
                      )}
                      <Badge className={`${getStatusColor(c.effectiveState ?? c.status)} border`}>
                        {c.effectiveState ?? c.status}
                      </Badge>
                    </div>
                  </div>
                ))}
              </div>
            )
          ) : !contest ? (
            <div className="flex flex-col items-center py-12">
              <p className="text-slate-600 mb-6">No contest found</p>
              <Button
                className="bg-[#1E293B] hover:bg-[#334155] gap-2"
                onClick={() => setCreateModalOpen(true)}
              >
                <Plus className="w-4 h-4" />
                Create Contest
              </Button>
            </div>
          ) : (
            <>
              {/* DETAILS */}
              <div className="grid md:grid-cols-2 gap-6 mb-6">
                <div className="space-y-4">
                  <div>
                    <label className="text-slate-600 flex items-center gap-2">
                      <FileText className="w-4 h-4" />
                      Contest Name
                    </label>
                    <p className="text-slate-900">{contest.title}</p>
                  </div>
                  <div>
                    <label className="text-slate-600 flex items-center gap-2">
                      <Calendar className="w-4 h-4" />
                      Scheduled Start
                    </label>
                    <p className="text-slate-900">{formatTime(contest.startTime)}</p>
                  </div>
                  {contest.actualStartTime && (
                    <div>
                      <label className="text-slate-600 flex items-center gap-2">
                        <Play className="w-4 h-4" />
                        Actual Start
                      </label>
                      <p className="text-slate-900">{formatTime(contest.actualStartTime)}</p>
                    </div>
                  )}
                  <div>
                    <label className="text-slate-600 flex items-center gap-2">
                      <Calendar className="w-4 h-4" />
                      End Time
                    </label>
                    <p className="text-slate-900">
                      {lifecycleState === 'PAUSED'
                        ? '— (paused)'
                        : formatTime(contest.effectiveEndTime ?? contest.endTime)}
                    </p>
                  </div>
                  {lifecycleState !== 'ENDED' && (
                    <div>
                      <label className="text-slate-600 flex items-center gap-2">
                        <Clock className="w-4 h-4" />
                        Time Remaining
                      </label>
                      <p className="text-slate-900 font-mono text-lg">
                        {formatCountdown(remainingMs)}
                        {lifecycleState === 'PAUSED' && (
                          <span className="ml-2 text-orange-600 text-sm">(paused)</span>
                        )}
                      </p>
                    </div>
                  )}
                </div>

                <div className="space-y-4">
                  <div>
                    <label className="text-slate-600 flex items-center gap-2">
                      <Clock className="w-4 h-4" />
                      Duration
                    </label>
                    <p className="text-slate-900">{formatDuration(contest.durationMinutes)}</p>
                  </div>
                  <div>
                    <label className="text-slate-600 flex items-center gap-2">
                      <Snowflake className="w-4 h-4" />
                      Scoreboard Freeze
                    </label>
                    <p className="text-slate-900">
                      {contest.scoreboardFreezeMinutes
                        ? `${contest.scoreboardFreezeMinutes} min before end`
                        : 'Disabled'}
                    </p>
                  </div>
                  <div>
                    <label className="text-slate-600">Penalty per Wrong Answer</label>
                    <p className="text-slate-900">{contest.penaltyMinutes} minutes</p>
                  </div>
                </div>
              </div>

              {/* CONTROLS */}
              <div className="border-t pt-6">
                <h3 className="text-slate-700 mb-4">Contest Controls</h3>
                <div className="flex flex-wrap gap-3">
                  <Button
                    variant="outline"
                    className="gap-2"
                    disabled={!canEdit}
                    onClick={() => setEditModalOpen(true)}
                  >
                    <Pencil className="w-4 h-4" />
                    Edit Contest
                  </Button>

                  <Button
                    className="bg-green-600 hover:bg-green-700 gap-2"
                    disabled={!canStart}
                    onClick={handleStart}
                  >
                    <Play className="w-4 h-4" />
                    Start Contest
                  </Button>

                  <Button
                    className="bg-blue-600 hover:bg-blue-700 gap-2"
                    disabled={!canResume}
                    onClick={handleResume}
                  >
                    <RotateCcw className="w-4 h-4" />
                    Resume Contest
                  </Button>

                  <Button
                    className="bg-orange-600 hover:bg-orange-700 gap-2"
                    disabled={!canPause}
                    onClick={handlePause}
                  >
                    <Pause className="w-4 h-4" />
                    Pause Contest
                  </Button>

                  <Button
                    className="bg-red-600 hover:bg-red-700 gap-2"
                    disabled={!canEnd}
                    onClick={() => setEndDialogOpen(true)}
                  >
                    <StopCircle className="w-4 h-4" />
                    End Contest
                  </Button>
                </div>
              </div>
            </>
          )}
        </CardContent>
      </Card>

      <CreateContestModal
          open={createModalOpen}
          onOpenChange={setCreateModalOpen}
          onSuccess={() => setContestType('upcoming')}
      />

      {contest && (
        <EditContestModal
          open={editModalOpen}
          onOpenChange={setEditModalOpen}
          contest={contest}
          onSuccess={(updated) => {
            placeContest(updated);
            setContestType('upcoming');
          }}
        />
      )}

      <AlertDialog open={endDialogOpen} onOpenChange={setEndDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>End Contest?</AlertDialogTitle>
            <AlertDialogDescription>
              This action cannot be undone. The contest will be marked as ended.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <div className="flex items-center space-x-2 py-4">
            <Checkbox
              id="jury-override"
              checked={juryOverride}
              onCheckedChange={(checked) => setJuryOverride(checked === true)}
            />
            <label
              htmlFor="jury-override"
              className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70"
            >
              Jury Override (end before scheduled time)
            </label>
          </div>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setJuryOverride(false)}>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={handleEnd} className="bg-red-600 hover:bg-red-700">
              End Contest
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
