import { useState, useEffect, useCallback } from 'react';
import { Card, CardContent, CardHeader, CardTitle } from './ui/card';
import { Button } from './ui/button';
import { Badge } from './ui/badge';
import { ToggleGroup, ToggleGroupItem } from './ui/toggle-group';
import {
  Play,
  Pause,
  StopCircle,
  Plus,
  Calendar,
  Clock,
  FileText,
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
import { ContestLifecycleState, ContestResponse } from '../types/api';
import { CreateContestModal } from './CreateContestModal';
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

export function ContestOverview() {
  const [contestType, setContestType] = useState<ContestTab>('active'); // This stores which tab is currently selected.
  const [contest, setContest] = useState<ContestResponse | null>(null); // This stores the current contest for: active, upcoming, paused. For ended, we use a separate list.
  const [endedContests, setEndedContests] = useState<ContestResponse[]>([]); // whether fetch is in progress
  const [loading, setLoading] = useState(true);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [endDialogOpen, setEndDialogOpen] = useState(false);
  const [juryOverride, setJuryOverride] = useState(false);

  const loadContest = useCallback(async () => {
    console.log('==============================');
    console.log('[loadContest] START');
    console.log('[loadContest] Current Tab:', contestType);

    setLoading(true);

    try {
      if (contestType === 'ended') {
        console.log('[loadContest] Fetching ENDED contests...');

        const data = await getEndedContests();

        console.log('[loadContest] ENDED contests response:', data);

        data.forEach((c, index) => {
          console.log(`-- Contest[${index}] --`);
          console.log('ID:', c.id);
          console.log('Persisted Status:', c.status);
          console.log('Effective State:', c.effectiveState);
          console.log('Start Time:', c.startTime);
          console.log('End Time:', c.endTime);
        });

        setEndedContests(data);
        setContest(null);
      } else {
        console.log('[loadContest] Fetching SINGLE contest...');

        let data: ContestResponse;

        if (contestType === 'active') {
          console.log('[loadContest] Calling getActiveContest()');
          data = await getActiveContest();
        } else if (contestType === 'upcoming') {
          console.log('[loadContest] Calling getUpcomingContest()');
          data = await getUpcomingContest();
        } else {
          console.log('[loadContest] Calling getPausedContest()');
          data = await getPausedContest();
        }

        console.log('[loadContest] Response received:');
        console.log('ID:', data?.id);
        console.log('Persisted Status:', data?.status);
        console.log('Effective State:', data?.effectiveState);
        console.log('Start Time:', data?.startTime);
        console.log('Actual Start Time:', data?.actualStartTime);
        console.log('Effective End Time:', data?.effectiveEndTime);
        console.log('End Time:', data?.endTime);

        setContest(data);
        setEndedContests([]);
      }
    } catch (error) {
      console.error('[loadContest] ERROR:', error);
      setContest(null);
      setEndedContests([]);
    } finally {
      setLoading(false);
      console.log('[loadContest] END');
      console.log('==============================');
    }
  }, [contestType]);

  useEffect(() => {
    loadContest();
  }, [loadContest]);

  useEffect(() => {
    if (contest) {
      console.log('====== FRONTEND STATE ======');
      console.log('contest.status:', contest.status);
      console.log('contest.effectiveState:', contest.effectiveState);

      const lifecycleState =
          contest.effectiveState ?? contest.status;

      console.log('lifecycleState (USED):', lifecycleState);
      console.log('============================');
    }
  }, [contest]);
  useEffect(() => {
    if (contest) {
      console.log('contest.startTime:', contest.startTime);
      console.log('contest.actualStartTime:', contest.actualStartTime);
      console.log('contest.effectiveEndTime:', contest.effectiveEndTime);
    }
  }, [contest]);
  const handleStart = async () => {
    if (!contest) {
      console.log('[StartContest] No contest loaded');
      return;
    }

    try {
      await startContest(contest.id);
      toast.success('Contest started');
      setContest(null);
      setContestType('active');

    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to start contest');
    }
  };

  const handleResume = async () => {
    if (!contest) return;
    try {
      await resumeContest(contest.id);
      toast.success('Contest resumed');
      setContest(null);
      setContestType('active');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to resume contest');
    }
  };

  const handlePause = async () => {
    if (!contest) return;
    try {
      await pauseContest(contest.id);
      toast.success('Contest paused');
      setContest(null);
      setContestType('paused');
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
      setContest(null);
      setContestType('ended');
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to end contest');
    }
  };

  const getStatusColor = (status: ContestLifecycleState) => {
    switch (status) {
      case 'RUNNING':
        return 'bg-green-100 text-green-700 border-green-200';
      case 'PAUSED':
        return 'bg-orange-100 text-orange-700 border-orange-200';
      case 'ENDED':
        return 'bg-red-100 text-red-700 border-red-200';
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

  const lifecycleState: ContestLifecycleState | null = contest
    ? (contest.effectiveState ?? contest.status)
    : null;

  // Start is a manual event; no wall-clock guard — admin can start at any time.
  const canStart = lifecycleState === 'UPCOMING';
  const canResume = lifecycleState === 'PAUSED';
  const canPause = lifecycleState === 'RUNNING';
  const canEnd = lifecycleState === 'RUNNING' || lifecycleState === 'PAUSED';

  // Live countdown driven by remainingMillis; ticks locally only while RUNNING.
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

  return (
    <>
      <Card className="border border-gray-200 shadow-sm">
        <CardHeader className="bg-[#1E293B] text-white py-4">
          <div className="flex items-center justify-between min-h-[48px]">
            <div className="flex items-center gap-4">
              <CardTitle className="leading-none">Contest Overview</CardTitle>

              <ToggleGroup
                type="single"
                value={contestType}
                onValueChange={(v) => v && setContestType(v as ContestTab)}
                className="bg-slate-700 rounded-lg h-9 px-1 flex items-center"
              >
                {(['active', 'upcoming', 'paused', 'ended'] as const).map((t) => (
                  <ToggleGroupItem
                    key={t}
                    value={t}
                    className="
                      capitalize h-7 px-3 text-sm text-slate-200
                      data-[state=on]:bg-white data-[state=on]:text-slate-900 data-[state=on]:shadow
                    "
                  >
                    {t}
                  </ToggleGroupItem>
                ))}
              </ToggleGroup>
            </div>

            <div className="flex items-center gap-2">
              {contest?.scoreboardFrozen && (
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
        </CardHeader>

        <CardContent className="p-6">
          {loading ? (
            <div className="text-center text-slate-600 py-8">Loading contest...</div>
          ) : contestType === 'ended' ? (
            endedContests.length === 0 ? (
              <div className="text-center py-12 text-slate-600">No ended contests</div>
            ) : (
              <div className="space-y-4">
                {endedContests.map((c) => (
                  <div
                    key={c.id}
                    className="border rounded-lg p-4 flex justify-between items-center"
                  >
                    <div>
                      <p className="font-medium text-slate-900">{c.title}</p>
                      <p className="text-sm text-slate-600">{c.description}</p>
                    </div>
                    <Badge className={`${getStatusColor(c.effectiveState ?? c.status)} border`}>
                      {c.effectiveState ?? c.status}
                    </Badge>
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
