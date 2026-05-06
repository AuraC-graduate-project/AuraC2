import { useEffect, useState } from "react";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "./ui/dialog";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./ui/select";
import { RichTextEditor } from "./RichTextEditor";
import { richTextToPlainText, sanitizeRichText } from "../../components/richText";
import { updateProblem } from "../services/api";
import { ProblemResponse, ProblemUpdateRequest } from "../types/api";
import { toast } from "sonner";

interface EditProblemModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  problem: ProblemResponse;
  onSuccess: (problem: ProblemResponse) => void;
}

export function EditProblemModal({
  open,
  onOpenChange,
  problem,
  onSuccess,
}: EditProblemModalProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formData, setFormData] = useState<ProblemUpdateRequest>({
    title: problem.title,
    description: problem.description,
    timeLimit: problem.timeLimit,
    memoryLimit: problem.memoryLimit,
    difficulty: problem.difficulty,
  });

  useEffect(() => {
    if (!open) return;
    setFormData({
      title: problem.title,
      description: problem.description,
      timeLimit: problem.timeLimit,
      memoryLimit: problem.memoryLimit,
      difficulty: problem.difficulty,
    });
  }, [open, problem]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const sanitizedDescription = sanitizeRichText(formData.description);

    if (!formData.title.trim() || !richTextToPlainText(sanitizedDescription)) {
      toast.error("Title and description are required");
      return;
    }

    if (formData.timeLimit <= 0 || formData.memoryLimit <= 0) {
      toast.error("Time and memory limits must be greater than 0");
      return;
    }

    setIsSubmitting(true);
    try {
      const updated = await updateProblem(problem.id, {
        ...formData,
        title: formData.title.trim(),
        description: sanitizedDescription,
      });
      toast.success("Problem updated successfully");
      onSuccess(updated);
      onOpenChange(false);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to update problem");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto sm:max-w-[760px]">
        <DialogHeader>
          <DialogTitle className="text-xl text-slate-950">Edit Problem</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit}>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label htmlFor="edit-problem-title">Title</Label>
              <Input
                id="edit-problem-title"
                value={formData.title}
                onChange={(e) => setFormData((prev) => ({ ...prev, title: e.target.value }))}
                className="h-10"
                disabled={isSubmitting}
                required
              />
            </div>

            <div className="space-y-2">
              <Label htmlFor="edit-problem-description">Description</Label>
              <RichTextEditor
                id="edit-problem-description"
                value={formData.description}
                onChange={(description) => setFormData((prev) => ({ ...prev, description }))}
                disabled={isSubmitting}
              />
            </div>

            <div className="grid grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label htmlFor="edit-problem-time-limit">Time Limit (ms)</Label>
                <Input
                  id="edit-problem-time-limit"
                  type="number"
                  min="1"
                  value={formData.timeLimit || ""}
                  onChange={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      timeLimit: parseInt(e.target.value, 10) || 0,
                    }))
                  }
                  className="h-10"
                  disabled={isSubmitting}
                  required
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="edit-problem-memory-limit">Memory Limit (MB)</Label>
                <Input
                  id="edit-problem-memory-limit"
                  type="number"
                  min="1"
                  value={formData.memoryLimit || ""}
                  onChange={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      memoryLimit: parseInt(e.target.value, 10) || 0,
                    }))
                  }
                  className="h-10"
                  disabled={isSubmitting}
                  required
                />
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="edit-problem-difficulty">Difficulty</Label>
              <Select
                value={formData.difficulty}
                onValueChange={(value: "EASY" | "MEDIUM" | "HARD") =>
                  setFormData((prev) => ({ ...prev, difficulty: value }))
                }
              >
                <SelectTrigger id="edit-problem-difficulty">
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
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting} className="bg-blue-700 hover:bg-blue-800">
              {isSubmitting ? "Saving..." : "Save Changes"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
