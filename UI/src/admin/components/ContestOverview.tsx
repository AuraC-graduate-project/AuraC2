import { useState, useEffect, useCallback, useMemo } from 'react';
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
import { ContestResponse } from '../types/api';
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
  const [contestType, setContestType] = useState<ContestTab>('active');
  const [contest, setContest] = useState<ContestResponse | null>(null);
  const [endedContests, setEndedContests] = useState<ContestResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [endDialogOpen, setEndDialogOpen] = useState(false);
  const [juryOverride, setJuryOverride] = useState(false);

  const loadContest = useCallback(async () => {
    setLoading(true);
    try {
      if (contestType === 'ended') {
        const data = await getEndedContests();
        setEndedContests(data);
        setContest(null);
      } else {
        const data =
          contestType === 'active'
            ? await getActiveContest()
            : contestType === 'upcoming'
              ? await getUpcomingContest()
              : await getPausedContest();

        setContest(data);
        setEndedContests([]);
      }
    } catch {
      setContest(null);
      setEndedContests([]);
    } finally {
      setLoading(false);
    }
  }, [contestType]);

  useEffect(() => {
    loadContest();
  }, [loadContest]);

  const handleStart = async () => {
    if (!contest) return;
    try {
      await startContest(contest.id);
      toast.success('Contest started');
      loadContest();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to start contest');
    }
  };

  const handleResume = async () => {
    if (!contest) return;
    try {
      await resumeContest(contest.id);
      toast.success('Contest resumed');
      loadContest();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to resume contest');
    }
  };

  const handlePause = async () => {
    if (!contest) return;
    try {
      await pauseContest(contest.id);
      toast.success('Contest paused');
      loadContest();
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
      loadContest();
    } catch (e) {
      toast.error(e instanceof Error ? e.message : 'Failed to end contest');
    }
  };

  const getStatusColor = (status: ContestResponse['status']) => {
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

  // Compute if contest should be startable based on time
  const canStartNow = useMemo(() => {
    if (!contest || contest.status !== 'UPCOMING') return false;
    const startTime = new Date(contest.startTime).getTime();
    const now = Date.now();
    // Allow starting 1 minute before scheduled time (matches backend grace period)
    return now >= startTime - 60_000;
  }, [contest]);

  const canStart = contest?.status === 'UPCOMING';
  const canResume = contest?.status === 'PAUSED';
  const canPause = contest?.status === 'RUNNING';
  const canEnd = contest?.status === 'RUNNING' || contest?.status === 'PAUSED';

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
                <Badge className={`${getStatusColor(contest.status)} border`}>
                  {contest.status}
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
                    <Badge className="bg-red-100 text-red-700 border-red-200">ENDED</Badge>
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
                      Start Time
                    </label>
                    <p className="text-slate-900">{formatTime(contest.startTime)}</p>
                  </div>
                  <div>
                    <label className="text-slate-600 flex items-center gap-2">
                      <Calendar className="w-4 h-4" />
                      End Time
                    </label>
                    <p className="text-slate-900">{formatTime(contest.endTime)}</p>
                  </div>
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
                    disabled={!canStart || !canStartNow}
                    onClick={handleStart}
                    title={canStart && !canStartNow ? 'Cannot start before scheduled time' : ''}
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
        onSuccess={loadContest}
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
