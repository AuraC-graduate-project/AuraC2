import { useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from './ui/dialog';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Textarea } from './ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from './ui/select';
import { createProblem } from '../services/api';
import { ProblemRequest, ProblemResponse } from '../types/api';
import { toast } from 'sonner';

interface CreateProblemModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  contestId: number;
  onSuccess: (problem: ProblemResponse) => void;
}

export function CreateProblemModal({ open, onOpenChange, contestId, onSuccess }: CreateProblemModalProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formData, setFormData] = useState<ProblemRequest>({
    contestId,
    title: '',
    description: '',
    timeLimit: 0,
    memoryLimit: 0,
    difficulty: 'EASY',
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData.title.trim() || !formData.description.trim()) {
      toast.error('Title and problem statement are required');
      return;
    }
    if (formData.timeLimit <= 0 || formData.memoryLimit <= 0) {
      toast.error('Time and memory limits must be greater than 0');
      return;
    }

    setIsSubmitting(true);

    try {
      const createdProblem = await createProblem({
        ...formData,
        contestId,
        title: formData.title.trim(),
        description: formData.description.trim(),
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
      });
    } catch (error) {
      toast.error(error instanceof Error ? error.message : 'Failed to create problem');
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[600px]">
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
              <Textarea
                id="description"
                value={formData.description}
                onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                rows={6}
                className="resize-y"
                required
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
