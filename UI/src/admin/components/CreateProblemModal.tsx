import { useEffect, useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from './ui/dialog';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from './ui/select';
import { RichTextEditor } from './RichTextEditor';
import { richTextToPlainText, sanitizeRichText } from '../../components/richText';
import { createProblem } from '../services/api';
import { ProblemRequest, ProblemResponse } from '../types/api';
import { BALLOON_COLOR_PRESETS, defaultBalloonColor, DEFAULT_BALLOON_COLOR, isBalloonColor, normalizeBalloonColor } from '../utils/balloonColors';
import { toast } from 'sonner';

interface CreateProblemModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  contestId: number;
  nextProblemIndex?: number;
  onSuccess: (problem: ProblemResponse) => void;
}

export function CreateProblemModal({ open, onOpenChange, contestId, nextProblemIndex = 0, onSuccess }: CreateProblemModalProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const initialBalloonColor = defaultBalloonColor(nextProblemIndex);
  const [formData, setFormData] = useState<ProblemRequest>({
    contestId,
    title: '',
    description: '',
    timeLimit: 0,
    memoryLimit: 0,
    difficulty: 'EASY',
    balloonColor: initialBalloonColor,
  });

  useEffect(() => {
    if (!open) return;
    setFormData({
      contestId,
      title: '',
      description: '',
      timeLimit: 0,
      memoryLimit: 0,
      difficulty: 'EASY',
      balloonColor: defaultBalloonColor(nextProblemIndex),
    });
  }, [contestId, nextProblemIndex, open]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const sanitizedDescription = sanitizeRichText(formData.description);

    if (!formData.title.trim() || !richTextToPlainText(sanitizedDescription)) {
      toast.error('Title and problem statement are required');
      return;
    }
    if (formData.timeLimit <= 0 || formData.memoryLimit <= 0) {
      toast.error('Time and memory limits must be greater than 0');
      return;
    }
    if (!isBalloonColor(formData.balloonColor)) {
      toast.error('Balloon color must be a hex color like #2563EB');
      return;
    }

    setIsSubmitting(true);

    try {
      const balloonColor = normalizeBalloonColor(formData.balloonColor);
      const createdProblem = await createProblem({
        ...formData,
        contestId,
        title: formData.title.trim(),
        description: sanitizedDescription,
        balloonColor,
      });
      toast.success('Problem created successfully');
      onOpenChange(false);
      onSuccess(createdProblem);
      // Reset form
      setFormData({
        contestId,
        title: '',
        description: '',
        timeLimit: 0,
        memoryLimit: 0,
        difficulty: 'EASY',
        balloonColor: defaultBalloonColor(nextProblemIndex),
      });
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to create problem');
    } finally {
      setIsSubmitting(false);
    }
  };

  const normalizedBalloonColor = normalizeBalloonColor(formData.balloonColor);
  const colorInputValue = isBalloonColor(formData.balloonColor) ? normalizedBalloonColor : DEFAULT_BALLOON_COLOR;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto sm:max-w-[760px]">
        <DialogHeader>
          <DialogTitle className="text-xl text-slate-950">Create Problem</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit}>
          <div className="space-y-4 py-4">
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
              <RichTextEditor
                id="description"
                value={formData.description}
                onChange={(description) => setFormData((prev) => ({ ...prev, description }))}
                disabled={isSubmitting}
              />
            </div>
            
            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="timeLimit">Time Limit (ms)</Label>
                <Input
                  id="timeLimit"
                  type="number"
                  min="0"
                  value={formData.timeLimit || ''}
                  onChange={(e) => setFormData({ ...formData, timeLimit: parseInt(e.target.value) || 0 })}
                  className="h-10"
                  required
                />
              </div>
              
              <div className="space-y-2">
                <Label htmlFor="memoryLimit">Memory Limit (MB)</Label>
                <Input
                  id="memoryLimit"
                  type="number"
                  min="0"
                  value={formData.memoryLimit || ''}
                  onChange={(e) => setFormData({ ...formData, memoryLimit: parseInt(e.target.value) || 0 })}
                  className="h-10"
                  required
                />
              </div>
            </div>
            
            <div className="space-y-2">
              <Label htmlFor="difficulty">Difficulty</Label>
              <Select
                value={formData.difficulty}
                onValueChange={(value: 'EASY' | 'MEDIUM' | 'HARD') => setFormData({ ...formData, difficulty: value })}
              >
                <SelectTrigger>
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="EASY">Easy</SelectItem>
                  <SelectItem value="MEDIUM">Medium</SelectItem>
                  <SelectItem value="HARD">Hard</SelectItem>
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="balloonColor">Balloon Color</Label>
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
                <Input
                  id="balloonColor"
                  type="color"
                  value={colorInputValue}
                  onChange={(e) => setFormData({ ...formData, balloonColor: normalizeBalloonColor(e.target.value) })}
                  className="h-10 w-16 cursor-pointer p-1"
                  disabled={isSubmitting}
                  aria-label="Choose balloon color"
                />
                <Input
                  value={formData.balloonColor}
                  onChange={(e) => setFormData({ ...formData, balloonColor: normalizeBalloonColor(e.target.value) })}
                  className="h-10 font-mono uppercase"
                  placeholder="#2563EB"
                  disabled={isSubmitting}
                />
              </div>
              <div className="flex flex-wrap gap-2" aria-label="Balloon color presets">
                {BALLOON_COLOR_PRESETS.map((color) => {
                  const selected = normalizedBalloonColor === color;
                  return (
                    <button
                      key={color}
                      type="button"
                      className={`h-8 w-8 rounded-md border transition ${
                        selected ? 'border-slate-950 ring-2 ring-slate-300' : 'border-slate-200 hover:border-slate-400'
                      }`}
                      style={{ backgroundColor: color }}
                      onClick={() => setFormData({ ...formData, balloonColor: color })}
                      disabled={isSubmitting}
                      aria-label={`Use ${color}`}
                    />
                  );
                })}
              </div>
            </div>
          </div>
          
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting} className="bg-blue-700 hover:bg-blue-800">
              {isSubmitting ? 'Creating...' : 'Create Problem'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
