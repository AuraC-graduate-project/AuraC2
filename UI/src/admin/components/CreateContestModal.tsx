import { useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from './ui/dialog';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Textarea } from './ui/textarea';
import { createContest } from '../services/api';
import { ContestRequest } from '../types/api';
import { toast } from 'sonner';

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

export function CreateContestModal({ open, onOpenChange, onSuccess }: CreateContestModalProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formData, setFormData] = useState<FormData>({
    title: '',
    description: '',
    startTime: '',
    durationMinutes: 0,
    scoreboardFreezeMinutes: null,
    penaltyMinutes: 20, // ICPC default
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);

    try {
      // Validate freeze time < duration
      if (
        formData.scoreboardFreezeMinutes !== null &&
        formData.scoreboardFreezeMinutes >= formData.durationMinutes
      ) {
        throw new Error('Scoreboard freeze time must be less than contest duration');
      }

      // Convert datetime-local to ISO 8601 UTC
      // datetime-local gives us local time; we must explicitly convert to UTC
      const localDate = new Date(formData.startTime);
      if (isNaN(localDate.getTime())) {
        throw new Error('Invalid start time');
      }
      const startTimeISO = localDate.toISOString();

      const payload: ContestRequest = {
        title: formData.title,
        description: formData.description,
        startTime: startTimeISO,
        durationMinutes: formData.durationMinutes,
        scoreboardFreezeMinutes: formData.scoreboardFreezeMinutes,
        penaltyMinutes: formData.penaltyMinutes,
      };

      await createContest(payload);
      toast.success('Contest created successfully');
      onOpenChange(false);
      onSuccess();
      // Reset form
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
      <DialogContent className="sm:max-w-[600px]">
        <DialogHeader>
          <DialogTitle>Create Contest</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit}>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="title">Title</Label>
              <Input
                id="title"
                value={formData.title}
                onChange={(e) => setFormData({ ...formData, title: e.target.value })}
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="description">Description</Label>
              <Textarea
                id="description"
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                rows={4}
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="startTime">Start Time (your local timezone)</Label>
              <Input
                id="startTime"
                type="datetime-local"
                value={formData.startTime}
                onChange={(e) => setFormData({ ...formData, startTime: e.target.value })}
                required
              />
              <p className="text-xs text-slate-500">
                Time will be converted to UTC for server storage
              </p>
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="durationMinutes">Duration (minutes)</Label>
                <Input
                  id="durationMinutes"
                  type="number"
                  min="1"
                  value={formData.durationMinutes || ''}
                  onChange={(e) =>
                    setFormData({ ...formData, durationMinutes: parseInt(e.target.value) || 0 })
                  }
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
                    setFormData({ ...formData, penaltyMinutes: parseInt(e.target.value) || 0 })
                  }
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
                placeholder="e.g., 60 (leave empty for no freeze)"
                value={formData.scoreboardFreezeMinutes ?? ''}
                onChange={(e) => {
                  const val = e.target.value;
                  setFormData({
                    ...formData,
                    scoreboardFreezeMinutes: val === '' ? null : parseInt(val) || 0,
                  });
                }}
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
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? 'Creating...' : 'Create Contest'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
