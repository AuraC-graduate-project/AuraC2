import { useEffect, useState } from "react";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "./ui/dialog";
import { Button } from "./ui/button";
import { Label } from "./ui/label";
import { Textarea } from "./ui/textarea";
import { Checkbox } from "./ui/checkbox";
import { updateTestCase } from "../services/api";
import { TestCaseResponse, TestCaseUpdateRequest } from "../types/api";
import { toast } from "sonner";

interface EditTestCaseModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  testCase: TestCaseResponse;
  onSuccess: () => void;
}

export function EditTestCaseModal({
  open,
  onOpenChange,
  testCase,
  onSuccess,
}: EditTestCaseModalProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formData, setFormData] = useState<TestCaseUpdateRequest>({
    inputData: testCase.inputData,
    expectedOutput: testCase.expectedOutput,
    isPublic: testCase.isPublic,
  });

  useEffect(() => {
    if (!open) return;
    setFormData({
      inputData: testCase.inputData,
      expectedOutput: testCase.expectedOutput,
      isPublic: testCase.isPublic,
    });
  }, [open, testCase]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData.inputData.trim() || !formData.expectedOutput.trim()) {
      toast.error("Input and expected output are required");
      return;
    }

    setIsSubmitting(true);
    try {
      await updateTestCase(testCase.id, {
        inputData: formData.inputData.trim(),
        expectedOutput: formData.expectedOutput.trim(),
        isPublic: formData.isPublic,
      });
      toast.success("Test case updated successfully");
      onSuccess();
      onOpenChange(false);
    } catch (error: unknown) {
      const status = (error as { response?: { status?: number } })?.response?.status;
      if (status === 409) {
        toast.error("A test case with the same input and expected output already exists.");
      } else {
        toast.error("Failed to update test case. Please try again.");
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle className="text-xl text-slate-950">Edit Test Case</DialogTitle>
          <DialogDescription>Update input, expected output, and public/private visibility.</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit}>
          <div className="grid gap-4 py-4">
            <div className="grid gap-2">
              <Label htmlFor="edit-testcase-input">Input Data</Label>
              <Textarea
                id="edit-testcase-input"
                value={formData.inputData}
                onChange={(e) => setFormData((prev) => ({ ...prev, inputData: e.target.value }))}
                rows={4}
                className="font-mono text-sm"
                disabled={isSubmitting}
                required
              />
            </div>
            <div className="grid gap-2">
              <Label htmlFor="edit-testcase-output">Expected Output</Label>
              <Textarea
                id="edit-testcase-output"
                value={formData.expectedOutput}
                onChange={(e) => setFormData((prev) => ({ ...prev, expectedOutput: e.target.value }))}
                rows={4}
                className="font-mono text-sm"
                disabled={isSubmitting}
                required
              />
            </div>
            <div className="flex items-center space-x-2">
              <Checkbox
                id="edit-testcase-is-public"
                checked={formData.isPublic}
                onCheckedChange={(checked) =>
                  setFormData((prev) => ({ ...prev, isPublic: checked === true }))
                }
                disabled={isSubmitting}
              />
              <Label htmlFor="edit-testcase-is-public" className="cursor-pointer">
                Public (visible to teams)
              </Label>
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
