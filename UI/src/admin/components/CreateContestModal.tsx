import { useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from './ui/dialog';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Textarea } from './ui/textarea';
import { createContest } from '../services/api';
import { ContestRequest } from '../types/api';
import { toast } from 'sonner';
import { AlertTriangle } from 'lucide-react';

interface CreateContestModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess: () => void;
}

interface FormData {
  title: string;
  description: string;
  startTime: string;
  durationMinutes: number;
  scoreboardFreezeMinutes: number | null;
  penaltyMinutes: number;
}

function toDatetimeLocalInputValue(date: Date): string {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

function minimumStartTime(): string {
  return toDatetimeLocalInputValue(new Date(Date.now() + 60_000));
}

export function CreateContestModal({ open, onOpenChange, onSuccess }: CreateContestModalProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formData, setFormData] = useState<FormData>({
    title: '',
    description: '',
    startTime: '',
    durationMinutes: 0,
    scoreboardFreezeMinutes: null,
    penaltyMinutes: 20,
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData.title.trim()) {
      toast.error('Contest title is required');
      return;
    }
    if (!formData.startTime) {
      toast.error('Start time is required');
      return;
    }
    if (!formData.durationMinutes || formData.durationMinutes <= 0) {
      toast.error('Duration must be greater than 0');
      return;
    }
    if (
      formData.scoreboardFreezeMinutes !== null &&
      formData.scoreboardFreezeMinutes >= formData.durationMinutes
    ) {
      toast.error('Scoreboard freeze time must be less than contest duration');
      return;
    }

    setIsSubmitting(true);
    try {
      const localDate = new Date(formData.startTime);
      if (isNaN(localDate.getTime())) {
        throw new Error('Invalid start time');
      }
      if (localDate.getTime() <= Date.now()) {
        toast.error('Start time must be in the future');
        return;
      }

      const payload: ContestRequest = {
        title: formData.title.trim(),
        description: formData.description.trim(),
        startTime: localDate.toISOString(),
        durationMinutes: formData.durationMinutes,
        scoreboardFreezeMinutes: formData.scoreboardFreezeMinutes,
        penaltyMinutes: formData.penaltyMinutes,
      };

      await createContest(payload);
      toast.success('Contest created successfully');
      onOpenChange(false);
      onSuccess();
      setFormData({
        title: '',
        description: '',
        startTime: '',
        durationMinutes: 0,
        scoreboardFreezeMinutes: null,
        penaltyMinutes: 20,
      });
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to create contest');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto sm:max-w-[600px]">
        <DialogHeader>
          <DialogTitle className="text-xl text-slate-950">Create Contest</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit}>
          <div className="space-y-5 py-4">
            <div className="flex gap-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              <span>Only one upcoming or running contest can exist at a time.</span>
            </div>

            <div className="space-y-2">
              <Label htmlFor="title">Title</Label>
              <Input
                id="title"
                value={formData.title}
                onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                className="h-10"
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">Description</Label>
              <Textarea
                id="description"
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                rows={5}
                className="resize-y"
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="startTime">Start Time (your local timezone)</Label>
              <Input
                id="startTime"
                type="datetime-local"
                min={minimumStartTime()}
                value={formData.startTime}
                onChange={(e) => setFormData({ ...formData, startTime: e.target.value })}
                className="h-10"
                required
              />
              <p className="text-xs text-slate-500">
                Time will be converted to UTC for server storage.
              </p>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="durationMinutes">Duration (minutes)</Label>
                <Input
                  id="durationMinutes"
                  type="number"
                  min="1"
                  value={formData.durationMinutes || ''}
                  onChange={(e) =>
                    setFormData({ ...formData, durationMinutes: parseInt(e.target.value, 10) || 0 })
                  }
                  className="h-10"
                  required
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="penaltyMinutes">Penalty (minutes per wrong answer)</Label>
                <Input
                  id="penaltyMinutes"
                  type="number"
                  min="0"
                  value={formData.penaltyMinutes}
                  onChange={(e) =>
                    setFormData({ ...formData, penaltyMinutes: parseInt(e.target.value, 10) || 0 })
                  }
                  className="h-10"
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="scoreboardFreezeMinutes">
                Scoreboard Freeze (minutes before end)
              </Label>
              <Input
                id="scoreboardFreezeMinutes"
                type="number"
                min="0"
                max={formData.durationMinutes > 0 ? formData.durationMinutes - 1 : undefined}
                placeholder="e.g., 60 (leave empty for no freeze)"
                value={formData.scoreboardFreezeMinutes ?? ''}
                onChange={(e) => {
                  const val = e.target.value;
                  setFormData({
                    ...formData,
                    scoreboardFreezeMinutes: val === '' ? null : parseInt(val, 10) || 0,
                  });
                }}
                className="h-10"
              />
              <p className="text-xs text-slate-500">
                ICPC standard: 60 minutes. Leave empty to disable freeze.
              </p>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting} className="bg-blue-700 hover:bg-blue-800">
              {isSubmitting ? 'Creating...' : 'Create Contest'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
