import { useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from './ui/dialog';
import { Button } from './ui/button';
import { Label } from './ui/label';
import { Textarea } from './ui/textarea';
import { Checkbox } from './ui/checkbox';
import { addTestCase } from '../services/api';
import { TestCaseRequest } from '../types/api';
import { toast } from 'sonner';

interface AddTestCaseModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  problemId: number;
  onSuccess: () => void;
}

export function AddTestCaseModal({ 
  open, 
  onOpenChange, 
  problemId,
  onSuccess 
}: AddTestCaseModalProps) {
  const [inputData, setInputData] = useState('');
  const [expectedOutput, setExpectedOutput] = useState('');
  const [isPublic, setIsPublic] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    if (!inputData.trim() || !expectedOutput.trim()) {
      toast.error('Please fill in all fields');
      return;
    }

    setIsSubmitting(true);
    
    try {
      const data: TestCaseRequest = {
        inputData,
        expectedOutput,
        isPublic,
      };
      
      await addTestCase(problemId, data);
      
      toast.success('Test case added successfully');
      
      // Reset form
      setInputData('');
      setExpectedOutput('');
      setIsPublic(false);
      onOpenChange(false);
      onSuccess();
    } catch (error: unknown) {
      console.error('Failed to add test case:', error);
      const status = (error as { response?: { status?: number } })?.response?.status;
      if (status === 409) {
        toast.error('A test case with the same input and expected output already exists.');
      } else {
        toast.error('Failed to add test case. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleClose = () => {
    if (!isSubmitting) {
      setInputData('');
      setExpectedOutput('');
      setIsPublic(false);
      onOpenChange(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle className="text-xl text-slate-950">Add Test Case</DialogTitle>
          <DialogDescription>
            Public samples are visible to teams; private tests stay hidden for judging.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit}>
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="inputData">Input Data</Label>
              <Textarea
                id="inputData"
                value={inputData}
                onChange={(e) => setInputData(e.target.value)}
                placeholder="Enter test case input"
                rows={4}
                className="font-mono text-sm"
                disabled={isSubmitting}
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="expectedOutput">Expected Output</Label>
              <Textarea
                id="expectedOutput"
                value={expectedOutput}
                onChange={(e) => setExpectedOutput(e.target.value)}
                placeholder="Enter expected output"
                rows={4}
                className="font-mono text-sm"
                disabled={isSubmitting}
              />
            </div>
            <div className="flex items-center space-x-2">
              <Checkbox 
                id="isPublic"
                checked={isPublic}
                onCheckedChange={(checked) => setIsPublic(checked === true)}
                disabled={isSubmitting}
              />
              <Label 
                htmlFor="isPublic"
                className="cursor-pointer"
              >
                Public (visible to teams)
              </Label>
            </div>
          </div>
          <DialogFooter>
            <Button 
              type="button" 
              variant="outline" 
              onClick={handleClose}
              disabled={isSubmitting}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting} className="bg-blue-700 hover:bg-blue-800">
              {isSubmitting ? 'Adding...' : 'Add Test Case'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
