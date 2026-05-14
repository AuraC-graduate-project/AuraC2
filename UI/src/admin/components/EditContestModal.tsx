import { useEffect, useState } from "react";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "./ui/dialog";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { updateContestDetails } from "../services/api";
import { ContestResponse, ContestUpdateRequest } from "../types/api";
import { toast } from "sonner";
import { AlertTriangle } from "lucide-react";

interface EditContestModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  contest: ContestResponse;
  onSuccess: (contest: ContestResponse) => void;
}

function toDatetimeLocal(isoString: string): string {
  const date = new Date(isoString);
  if (Number.isNaN(date.getTime())) return "";

  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

function toDatetimeLocalInputValue(date: Date): string {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
}

function minimumStartTime(): string {
  return toDatetimeLocalInputValue(new Date(Date.now() + 60_000));
}

export function EditContestModal({
  open,
  onOpenChange,
  contest,
  onSuccess,
}: EditContestModalProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [formData, setFormData] = useState<ContestUpdateRequest>({
    title: contest.title,
    startTime: toDatetimeLocal(contest.startTime),
    durationMinutes: contest.durationMinutes,
    scoreboardFreezeMinutes: contest.scoreboardFreezeMinutes,
    penaltyMinutes: contest.penaltyMinutes,
  });

  useEffect(() => {
    if (!open) return;
    setFormData({
      title: contest.title,
      startTime: toDatetimeLocal(contest.startTime),
      durationMinutes: contest.durationMinutes,
      scoreboardFreezeMinutes: contest.scoreboardFreezeMinutes,
      penaltyMinutes: contest.penaltyMinutes,
    });
  }, [open, contest]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    if (!formData.title.trim()) {
      toast.error("Contest title is required");
      return;
    }

    if (!formData.startTime) {
      toast.error("Start time is required");
      return;
    }

    if (!formData.durationMinutes || formData.durationMinutes <= 0) {
      toast.error("Duration must be greater than 0");
      return;
    }

    if (
      formData.scoreboardFreezeMinutes !== null &&
      formData.scoreboardFreezeMinutes !== undefined &&
      formData.scoreboardFreezeMinutes < 0
    ) {
      toast.error("Scoreboard freeze time cannot be negative");
      return;
    }

    if (
      formData.scoreboardFreezeMinutes !== null &&
      formData.scoreboardFreezeMinutes !== undefined &&
      formData.scoreboardFreezeMinutes >= formData.durationMinutes
    ) {
      toast.error("Scoreboard freeze time must be less than contest duration");
      return;
    }

    if (formData.penaltyMinutes < 0) {
      toast.error("Penalty minutes cannot be negative");
      return;
    }

    setIsSubmitting(true);
    try {
      const localDate = new Date(formData.startTime);
      if (Number.isNaN(localDate.getTime())) {
        throw new Error("Invalid start time");
      }
      if (localDate.getTime() <= Date.now()) {
        toast.error("Start time must be in the future");
        return;
      }

      const updated = await updateContestDetails(contest.id, {
        title: formData.title.trim(),
        startTime: localDate.toISOString(),
        durationMinutes: formData.durationMinutes,
        scoreboardFreezeMinutes: formData.scoreboardFreezeMinutes ?? null,
        penaltyMinutes: formData.penaltyMinutes,
      });
      toast.success("Contest updated successfully");
      onSuccess(updated);
      onOpenChange(false);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to update contest");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[560px]">
        <DialogHeader>
          <DialogTitle className="text-xl text-slate-950">Edit Contest Details</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit}>
          <div className="space-y-5 py-4">
            {contest.status !== "UPCOMING" && (
              <div className="flex gap-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-800">
                <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
                <span>Only UPCOMING contests can be edited.</span>
              </div>
            )}

            <div className="space-y-2">
              <Label htmlFor="edit-contest-title">Contest Name</Label>
              <Input
                id="edit-contest-title"
                value={formData.title}
                onChange={(e) => setFormData((prev) => ({ ...prev, title: e.target.value }))}
                disabled={isSubmitting || contest.status !== "UPCOMING"}
                className="h-10"
                required
              />
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="edit-contest-start-time">Start Date / Time</Label>
                <Input
                  id="edit-contest-start-time"
                  type="datetime-local"
                  min={minimumStartTime()}
                  value={formData.startTime}
                  onChange={(e) => setFormData((prev) => ({ ...prev, startTime: e.target.value }))}
                  disabled={isSubmitting || contest.status !== "UPCOMING"}
                  className="h-10"
                  required
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="edit-contest-duration">Duration (minutes)</Label>
                <Input
                  id="edit-contest-duration"
                  type="number"
                  min="1"
                  value={formData.durationMinutes || ""}
                  onChange={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      durationMinutes: parseInt(e.target.value, 10) || 0,
                    }))
                  }
                  disabled={isSubmitting || contest.status !== "UPCOMING"}
                  className="h-10"
                  required
                />
              </div>
            </div>

            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="edit-contest-scoreboard-freeze">
                  Scoreboard Freeze (minutes before end)
                </Label>
                <Input
                  id="edit-contest-scoreboard-freeze"
                  type="number"
                  min="0"
                  max={formData.durationMinutes > 0 ? formData.durationMinutes - 1 : undefined}
                  placeholder="Leave empty for no freeze"
                  value={formData.scoreboardFreezeMinutes ?? ""}
                  onChange={(e) => {
                    const val = e.target.value;
                    setFormData((prev) => ({
                      ...prev,
                      scoreboardFreezeMinutes: val === "" ? null : parseInt(val, 10) || 0,
                    }));
                  }}
                  disabled={isSubmitting || contest.status !== "UPCOMING"}
                  className="h-10"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="edit-contest-penalty">Penalty (minutes per wrong answer)</Label>
                <Input
                  id="edit-contest-penalty"
                  type="number"
                  min="0"
                  value={formData.penaltyMinutes}
                  onChange={(e) =>
                    setFormData((prev) => ({
                      ...prev,
                      penaltyMinutes: parseInt(e.target.value, 10) || 0,
                    }))
                  }
                  disabled={isSubmitting || contest.status !== "UPCOMING"}
                  className="h-10"
                  required
                />
              </div>
            </div>
          </div>

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting || contest.status !== "UPCOMING"} className="bg-blue-700 hover:bg-blue-800">
              {isSubmitting ? "Saving..." : "Save Changes"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
