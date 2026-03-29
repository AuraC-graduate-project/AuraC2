import { useState, useEffect } from 'react';
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
  FileText
} from 'lucide-react';
import {
  getActiveContest,
  getUpcomingContest,
  getPausedContest,
  getEndedContests,
  startContest,
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
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger
} from './ui/tooltip';
import { toast } from 'sonner';

type ContestTab = 'active' | 'upcoming' | 'paused' | 'ended';

export function ContestOverview() {
  const [contestType, setContestType] = useState<ContestTab>('active');
  const [contest, setContest] = useState<ContestResponse | null>(null);
  const [endedContests, setEndedContests] = useState<ContestResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [endDialogOpen, setEndDialogOpen] = useState(false);

  const loadContest = async () => {
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
    } catch (e) {
      toast.error('Failed to load contest');
      setContest(null);
      setEndedContests([]);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadContest();
  }, [contestType]);

  const handleStart = async () => {
    if (!contest) return;
    await startContest(contest.id);
    toast.success('Contest started');
    loadContest();
  };

  const handlePause = async () => {
    if (!contest) return;
    await pauseContest(contest.id);
    toast.success('Contest paused');
    loadContest();
  };

  const handleEnd = async () => {
    if (!contest) return;
    await endContest(contest.id);
    toast.success('Contest ended');
    setEndDialogOpen(false);
    loadContest();
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

  const formatStartTime = (iso: string) =>
    new Date(iso).toLocaleString('en-US', {
      month: 'short',
      day: 'numeric',
      year: 'numeric',
      hour: 'numeric',
      minute: '2-digit',
      timeZoneName: 'short'
    });

  const canStart = contest?.status === 'UPCOMING';
  const canPause = contest?.status === 'RUNNING';
  const canEnd = contest?.status === 'RUNNING' || contest?.status === 'PAUSED';

  return (
    <>
      <Card className="border border-gray-200 shadow-sm">
        <CardHeader className="bg-[#1E293B] text-white py-4">
          <div className="flex items-center justify-between min-h-[48px]">
            <div className="flex items-center gap-4">
              <CardTitle className="leading-none">
                Contest Overview
              </CardTitle>

              <ToggleGroup
                type="single"
                value={contestType}
                onValueChange={(v) => v && setContestType(v as ContestTab)}
                className="bg-slate-700 rounded-lg h-9 px-1 flex items-center"
              >
                {['active', 'upcoming', 'paused', 'ended'].map((t) => (
                  <ToggleGroupItem
                    key={t}
                    value={t}
                    className="
              capitalize
              h-7
              px-3
              text-sm
              text-slate-200
              data-[state=on]:bg-white
              data-[state=on]:text-slate-900
              data-[state=on]:shadow
            "
                  >
                    {t}
                  </ToggleGroupItem>
                ))}
              </ToggleGroup>
            </div>

            {contest && (
              <Badge className={`${getStatusColor(contest.status)} border`}>
                {contest.status}
              </Badge>
            )}
          </div>
        </CardHeader>


        <CardContent className="p-6">
          {loading ? (
            <div className="text-center text-slate-600 py-8">
              Loading contest...
            </div>
          ) : contestType === 'ended' ? (
            endedContests.length === 0 ? (
              <div className="text-center py-12 text-slate-600">
                No ended contests
              </div>
            ) : (
              <div className="space-y-4">
                {endedContests.map((c) => (
                  <div
                    key={c.id}
                    className="border rounded-lg p-4 flex justify-between items-center"
                  >
                    <div>
                      <p className="font-medium text-slate-900">{c.title}</p>
                      <p className="text-sm text-slate-600">
                        {c.description}
                      </p>
                    </div>
                    <Badge className="bg-red-100 text-red-700 border-red-200">
                      ENDED
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
                      Start Time
                    </label>
                    <p className="text-slate-900">
                      {formatStartTime(contest.startTime)}
                    </p>
                  </div>
                </div>

                <div className="space-y-4">
                  <div>
                    <label className="text-slate-600 flex items-center gap-2">
                      <Clock className="w-4 h-4" />
                      Duration
                    </label>
                    <p className="text-slate-900">
                      {formatDuration(contest.durationMinutes)}
                    </p>
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
              This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleEnd}
              className="bg-red-600 hover:bg-red-700"
            >
              End Contest
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
