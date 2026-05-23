import { useEffect, useState } from "react";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "./ui/dialog";
import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./ui/select";
import { Checkbox } from "./ui/checkbox";
import { Textarea } from "./ui/textarea";
import { RichTextEditor } from "./RichTextEditor";
import { richTextToPlainText, sanitizeRichText } from "../../components/richText";
import { updateProblem } from "../services/api";
import { ProblemResponse, ProblemUpdateRequest } from "../types/api";
import { BALLOON_COLOR_PRESETS, DEFAULT_BALLOON_COLOR, isBalloonColor, normalizeBalloonColor } from "../utils/balloonColors";
import { DEFAULT_VALIDATOR_LANGUAGE_ID, SUPPORTED_JUDGE0_LANGUAGES } from "../../constants/judge0Languages";
import { toast } from "sonner";

const COMPARE_POLICIES = [
  { value: "EXACT", label: "Exact" },
  { value: "NORMALIZED_TEXT", label: "Normalized text" },
  { value: "TOKEN_NORMALIZED", label: "Token normalized" },
  { value: "FLOAT_TOLERANCE", label: "Float tolerance" },
] as const;

const VALIDATION_MODES = [
  { value: "BUILTIN_COMPARE_POLICY", label: "Built-in compare policy" },
  { value: "CUSTOM_VALIDATOR", label: "Custom validator" },
] as const;

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
    comparePolicy: problem.comparePolicy ?? "EXACT",
    floatAbsoluteEpsilon: problem.floatAbsoluteEpsilon ?? null,
    floatRelativeEpsilon: problem.floatRelativeEpsilon ?? null,
    validationMode: problem.validationMode ?? "BUILTIN_COMPARE_POLICY",
    validatorEnabled: problem.validatorEnabled ?? true,
    validatorLanguageId: problem.validatorLanguageId ?? DEFAULT_VALIDATOR_LANGUAGE_ID,
    validatorSource: "",
    balloonColor: problem.balloonColor ?? DEFAULT_BALLOON_COLOR,
  });

  useEffect(() => {
    if (!open) return;
    setFormData({
      title: problem.title,
      description: problem.description,
      timeLimit: problem.timeLimit,
      memoryLimit: problem.memoryLimit,
      difficulty: problem.difficulty,
      comparePolicy: problem.comparePolicy ?? "EXACT",
      floatAbsoluteEpsilon: problem.floatAbsoluteEpsilon ?? null,
      floatRelativeEpsilon: problem.floatRelativeEpsilon ?? null,
      validationMode: problem.validationMode ?? "BUILTIN_COMPARE_POLICY",
      validatorEnabled: problem.validatorEnabled ?? true,
      validatorLanguageId: problem.validatorLanguageId ?? DEFAULT_VALIDATOR_LANGUAGE_ID,
      validatorSource: "",
      balloonColor: problem.balloonColor ?? DEFAULT_BALLOON_COLOR,
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

    if (!isBalloonColor(formData.balloonColor)) {
      toast.error("Balloon color must be a hex color like #2563EB");
      return;
    }

    const usesCustomValidator = formData.validationMode === "CUSTOM_VALIDATOR";

    if (
      !usesCustomValidator &&
      formData.comparePolicy === "FLOAT_TOLERANCE" &&
      (Number(formData.floatAbsoluteEpsilon ?? 0) <= 0 && Number(formData.floatRelativeEpsilon ?? 0) <= 0)
    ) {
      toast.error("Float tolerance requires a positive epsilon");
      return;
    }
    if (usesCustomValidator && formData.validatorEnabled !== false) {
      if (!formData.validatorLanguageId || formData.validatorLanguageId <= 0) {
        toast.error("Custom validator requires a validator language");
        return;
      }
      if (!problem.validatorSourceHash && !formData.validatorSource?.trim()) {
        toast.error("Custom validator source is required when enabling a validator without existing source");
        return;
      }
    }

    setIsSubmitting(true);
    try {
      const balloonColor = normalizeBalloonColor(formData.balloonColor);
      const validatorSource = formData.validatorSource?.trim();
      const updated = await updateProblem(problem.id, {
        ...formData,
        title: formData.title.trim(),
        description: sanitizedDescription,
        floatAbsoluteEpsilon:
          !usesCustomValidator && formData.comparePolicy === "FLOAT_TOLERANCE" ? formData.floatAbsoluteEpsilon ?? null : null,
        floatRelativeEpsilon:
          !usesCustomValidator && formData.comparePolicy === "FLOAT_TOLERANCE" ? formData.floatRelativeEpsilon ?? null : null,
        validationMode: formData.validationMode,
        validatorEnabled: usesCustomValidator ? formData.validatorEnabled !== false : undefined,
        validatorLanguageId: usesCustomValidator ? formData.validatorLanguageId ?? null : undefined,
        validatorSource: usesCustomValidator && validatorSource ? validatorSource : undefined,
        balloonColor,
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

  const normalizedBalloonColor = normalizeBalloonColor(formData.balloonColor);
  const colorInputValue = isBalloonColor(formData.balloonColor) ? normalizedBalloonColor : DEFAULT_BALLOON_COLOR;
  const usesCustomValidator = formData.validationMode === "CUSTOM_VALIDATOR";

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

            <div className="space-y-2">
              <Label htmlFor="edit-problem-validation-mode">Validation Mode</Label>
              <Select
                value={formData.validationMode}
                onValueChange={(value: ProblemUpdateRequest["validationMode"]) =>
                  setFormData((prev) => ({
                    ...prev,
                    validationMode: value,
                    comparePolicy: value === "CUSTOM_VALIDATOR" ? "EXACT" : prev.comparePolicy,
                    floatAbsoluteEpsilon: value === "CUSTOM_VALIDATOR" ? null : prev.floatAbsoluteEpsilon,
                    floatRelativeEpsilon: value === "CUSTOM_VALIDATOR" ? null : prev.floatRelativeEpsilon,
                    validatorLanguageId:
                      value === "CUSTOM_VALIDATOR"
                        ? prev.validatorLanguageId ?? DEFAULT_VALIDATOR_LANGUAGE_ID
                        : prev.validatorLanguageId,
                  }))
                }
              >
                <SelectTrigger id="edit-problem-validation-mode">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {VALIDATION_MODES.map((mode) => (
                    <SelectItem key={mode.value} value={mode.value}>
                      {mode.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            {!usesCustomValidator && (
              <div className="space-y-2">
              <Label htmlFor="edit-problem-compare-policy">Compare Policy</Label>
              <Select
                value={formData.comparePolicy}
                onValueChange={(value: ProblemUpdateRequest["comparePolicy"]) =>
                  setFormData((prev) => ({
                    ...prev,
                    comparePolicy: value,
                    floatAbsoluteEpsilon:
                      value === "FLOAT_TOLERANCE" ? prev.floatAbsoluteEpsilon ?? 0.000001 : null,
                    floatRelativeEpsilon:
                      value === "FLOAT_TOLERANCE" ? prev.floatRelativeEpsilon ?? null : null,
                  }))
                }
              >
                <SelectTrigger id="edit-problem-compare-policy">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {COMPARE_POLICIES.map((policy) => (
                    <SelectItem key={policy.value} value={policy.value}>
                      {policy.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              </div>
            )}

            {!usesCustomValidator && formData.comparePolicy === "FLOAT_TOLERANCE" && (
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="edit-problem-float-absolute-epsilon">Absolute Epsilon</Label>
                  <Input
                    id="edit-problem-float-absolute-epsilon"
                    type="number"
                    min="0"
                    step="any"
                    value={formData.floatAbsoluteEpsilon ?? ""}
                    onChange={(e) =>
                      setFormData((prev) => ({
                        ...prev,
                        floatAbsoluteEpsilon: e.target.value === "" ? null : Number(e.target.value),
                      }))
                    }
                    className="h-10"
                    disabled={isSubmitting}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="edit-problem-float-relative-epsilon">Relative Epsilon</Label>
                  <Input
                    id="edit-problem-float-relative-epsilon"
                    type="number"
                    min="0"
                    step="any"
                    value={formData.floatRelativeEpsilon ?? ""}
                    onChange={(e) =>
                      setFormData((prev) => ({
                        ...prev,
                        floatRelativeEpsilon: e.target.value === "" ? null : Number(e.target.value),
                      }))
                    }
                    className="h-10"
                    disabled={isSubmitting}
                  />
                </div>
              </div>
            )}

            {usesCustomValidator && (
              <div className="space-y-4 rounded-md border border-slate-200 p-4">
                <div className="flex items-center gap-2">
                  <Checkbox
                    id="edit-problem-validator-enabled"
                    checked={formData.validatorEnabled !== false}
                    onCheckedChange={(checked) =>
                      setFormData((prev) => ({ ...prev, validatorEnabled: checked === true }))
                    }
                    disabled={isSubmitting}
                  />
                  <Label htmlFor="edit-problem-validator-enabled">Enable custom validator</Label>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="edit-problem-validator-language-id">Validator Language</Label>
                  <Select
                    value={String(formData.validatorLanguageId ?? DEFAULT_VALIDATOR_LANGUAGE_ID)}
                    onValueChange={(value) =>
                      setFormData((prev) => ({
                        ...prev,
                        validatorLanguageId: Number(value),
                      }))
                    }
                    disabled={isSubmitting}
                  >
                    <SelectTrigger id="edit-problem-validator-language-id">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {SUPPORTED_JUDGE0_LANGUAGES.map((language) => (
                        <SelectItem key={language.value} value={String(language.judge0LanguageId)}>
                          {language.label}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <p className="text-sm text-slate-500">
                    Validator code will be executed through Judge0 using the selected language.
                  </p>
                </div>
                {problem.validatorSourceHash && (
                  <p className="rounded-md bg-slate-50 px-3 py-2 text-sm text-slate-600">
                    Existing validator source is hidden. Paste new source only if you want to replace it.
                  </p>
                )}
                <div className="space-y-2">
                  <Label htmlFor="edit-problem-validator-source">Validator Source</Label>
                  <Textarea
                    id="edit-problem-validator-source"
                    value={formData.validatorSource ?? ""}
                    onChange={(e) =>
                      setFormData((prev) => ({ ...prev, validatorSource: e.target.value }))
                    }
                    className="min-h-48 font-mono text-sm"
                    placeholder="Paste checker source code"
                    disabled={isSubmitting}
                  />
                </div>
              </div>
            )}

            <div className="space-y-2">
              <Label htmlFor="edit-problem-balloon-color">Balloon Color</Label>
              <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
                <Input
                  id="edit-problem-balloon-color"
                  type="color"
                  value={colorInputValue}
                  onChange={(e) =>
                    setFormData((prev) => ({ ...prev, balloonColor: normalizeBalloonColor(e.target.value) }))
                  }
                  className="h-10 w-16 cursor-pointer p-1"
                  disabled={isSubmitting}
                  aria-label="Choose balloon color"
                />
                <Input
                  value={formData.balloonColor}
                  onChange={(e) =>
                    setFormData((prev) => ({ ...prev, balloonColor: normalizeBalloonColor(e.target.value) }))
                  }
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
                        selected ? "border-slate-950 ring-2 ring-slate-300" : "border-slate-200 hover:border-slate-400"
                      }`}
                      style={{ backgroundColor: color }}
                      onClick={() => setFormData((prev) => ({ ...prev, balloonColor: color }))}
                      disabled={isSubmitting}
                      aria-label={`Use ${color}`}
                    />
                  );
                })}
              </div>
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
