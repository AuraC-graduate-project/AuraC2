import { useEffect, useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from './ui/dialog';
import { Button } from './ui/button';
import { Input } from './ui/input';
import { Label } from './ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from './ui/select';
import { Checkbox } from './ui/checkbox';
import { Textarea } from './ui/textarea';
import { RichTextEditor } from './RichTextEditor';
import { richTextToPlainText, sanitizeRichText } from '../../components/richText';
import { createProblem } from '../services/api';
import { ProblemRequest, ProblemResponse } from '../types/api';
import { BALLOON_COLOR_PRESETS, defaultBalloonColor, DEFAULT_BALLOON_COLOR, isBalloonColor, normalizeBalloonColor } from '../utils/balloonColors';
import { DEFAULT_VALIDATOR_LANGUAGE_ID, SUPPORTED_JUDGE0_LANGUAGES } from '../../constants/judge0Languages';
import { toast } from 'sonner';
import { AdminHelpTooltip } from './AdminHelpTooltip';

const COMPARE_POLICIES = [
  { value: 'EXACT', label: 'Exact' },
  { value: 'NORMALIZED_TEXT', label: 'Normalized text' },
  { value: 'TOKEN_NORMALIZED', label: 'Token normalized' },
  { value: 'FLOAT_TOLERANCE', label: 'Float tolerance' },
] as const;

const VALIDATION_MODES = [
  { value: 'BUILTIN_COMPARE_POLICY', label: 'Built-in compare policy' },
  { value: 'CUSTOM_VALIDATOR', label: 'Custom validator' },
] as const;

const STATEMENT_EDITOR_CLASS = 'min-h-[240px] max-h-[300px]';
const COMPACT_EDITOR_CLASS = 'min-h-[120px] max-h-[160px]';

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
    statement: '',
    inputFormat: '',
    outputFormat: '',
    constraintsText: '',
    publicNotes: '',
    adminNotes: '',
    timeLimit: 0,
    memoryLimit: 0,
    difficulty: 'EASY',
    comparePolicy: 'EXACT',
    floatAbsoluteEpsilon: null,
    floatRelativeEpsilon: null,
    validationMode: 'BUILTIN_COMPARE_POLICY',
    validatorEnabled: true,
    validatorLanguageId: DEFAULT_VALIDATOR_LANGUAGE_ID,
    validatorSource: '',
    balloonColor: initialBalloonColor,
  });

  useEffect(() => {
    if (!open) return;
    setFormData({
      contestId,
      title: '',
      description: '',
      statement: '',
      inputFormat: '',
      outputFormat: '',
      constraintsText: '',
      publicNotes: '',
      adminNotes: '',
      timeLimit: 0,
      memoryLimit: 0,
      difficulty: 'EASY',
      comparePolicy: 'EXACT',
      floatAbsoluteEpsilon: null,
      floatRelativeEpsilon: null,
      validationMode: 'BUILTIN_COMPARE_POLICY',
      validatorEnabled: true,
      validatorLanguageId: DEFAULT_VALIDATOR_LANGUAGE_ID,
      validatorSource: '',
      balloonColor: defaultBalloonColor(nextProblemIndex),
    });
  }, [contestId, nextProblemIndex, open]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const sanitizedStatement = sanitizeRichText(formData.statement ?? formData.description);
    const sanitizedInputFormat = sanitizeRichText(formData.inputFormat);
    const sanitizedOutputFormat = sanitizeRichText(formData.outputFormat);
    const sanitizedConstraints = sanitizeRichText(formData.constraintsText);
    const sanitizedPublicNotes = sanitizeRichText(formData.publicNotes);

    if (!formData.title.trim() || !richTextToPlainText(sanitizedStatement)) {
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
    const usesCustomValidator = formData.validationMode === 'CUSTOM_VALIDATOR';

    if (
      !usesCustomValidator &&
      formData.comparePolicy === 'FLOAT_TOLERANCE' &&
      (Number(formData.floatAbsoluteEpsilon ?? 0) <= 0 && Number(formData.floatRelativeEpsilon ?? 0) <= 0)
    ) {
      toast.error('Float tolerance requires a positive epsilon');
      return;
    }
    if (usesCustomValidator && formData.validatorEnabled !== false) {
      if (!formData.validatorLanguageId || formData.validatorLanguageId <= 0) {
        toast.error('Custom validator requires a validator language');
        return;
      }
      if (!formData.validatorSource?.trim()) {
        toast.error('Custom validator source is required when the validator is enabled');
        return;
      }
    }

    setIsSubmitting(true);

    try {
      const balloonColor = normalizeBalloonColor(formData.balloonColor);
      const validatorSource = formData.validatorSource?.trim();
      const createdProblem = await createProblem({
        ...formData,
        contestId,
        title: formData.title.trim(),
        description: sanitizedStatement,
        statement: sanitizedStatement,
        inputFormat: sanitizedInputFormat || null,
        outputFormat: sanitizedOutputFormat || null,
        constraintsText: sanitizedConstraints || null,
        publicNotes: sanitizedPublicNotes || null,
        adminNotes: formData.adminNotes?.trim() || null,
        floatAbsoluteEpsilon:
          !usesCustomValidator && formData.comparePolicy === 'FLOAT_TOLERANCE' ? formData.floatAbsoluteEpsilon ?? null : null,
        floatRelativeEpsilon:
          !usesCustomValidator && formData.comparePolicy === 'FLOAT_TOLERANCE' ? formData.floatRelativeEpsilon ?? null : null,
        validationMode: formData.validationMode,
        validatorEnabled: usesCustomValidator ? formData.validatorEnabled !== false : undefined,
        validatorLanguageId: usesCustomValidator ? formData.validatorLanguageId ?? null : undefined,
        validatorSource: usesCustomValidator && validatorSource ? validatorSource : undefined,
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
        statement: '',
        inputFormat: '',
        outputFormat: '',
        constraintsText: '',
        publicNotes: '',
        adminNotes: '',
        timeLimit: 0,
        memoryLimit: 0,
        difficulty: 'EASY',
        comparePolicy: 'EXACT',
        floatAbsoluteEpsilon: null,
        floatRelativeEpsilon: null,
        validationMode: 'BUILTIN_COMPARE_POLICY',
        validatorEnabled: true,
        validatorLanguageId: DEFAULT_VALIDATOR_LANGUAGE_ID,
        validatorSource: '',
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
  const usesCustomValidator = formData.validationMode === 'CUSTOM_VALIDATOR';

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="flex max-h-[90vh] flex-col overflow-hidden p-0 sm:max-w-[1180px]">
        <DialogHeader className="sticky top-0 z-10 border-b border-slate-200 bg-white px-6 py-4">
          <DialogTitle className="text-xl text-slate-950">Create Problem</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="flex min-h-0 flex-1 flex-col">
          <div className="min-h-0 flex-1 space-y-5 overflow-y-auto px-6 py-5">
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
              <Label htmlFor="statement">Statement</Label>
              <RichTextEditor
                id="statement"
                value={formData.statement ?? ''}
                onChange={(statement) => setFormData((prev) => ({ ...prev, statement }))}
                disabled={isSubmitting}
                placeholder="Write the main problem statement here..."
                inputClassName={STATEMENT_EDITOR_CLASS}
                ariaLabel="Main problem statement editor"
              />
            </div>

            <div className="grid gap-4 xl:grid-cols-2">
              <div className="space-y-2">
                <Label htmlFor="inputFormat">Input Format</Label>
                <RichTextEditor
                  id="inputFormat"
                  value={formData.inputFormat ?? ''}
                  onChange={(inputFormat) => setFormData((prev) => ({ ...prev, inputFormat }))}
                  disabled={isSubmitting}
                  placeholder="Describe the exact input format here..."
                  inputClassName={COMPACT_EDITOR_CLASS}
                  toolbarVariant="compact"
                  ariaLabel="Input format editor"
                  footerText="Compact formatting"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="outputFormat">Output Format</Label>
                <RichTextEditor
                  id="outputFormat"
                  value={formData.outputFormat ?? ''}
                  onChange={(outputFormat) => setFormData((prev) => ({ ...prev, outputFormat }))}
                  disabled={isSubmitting}
                  placeholder="Describe the exact output format here..."
                  inputClassName={COMPACT_EDITOR_CLASS}
                  toolbarVariant="compact"
                  ariaLabel="Output format editor"
                  footerText="Compact formatting"
                />
              </div>

              <div className="space-y-2">
                <Label htmlFor="constraintsText">Constraints</Label>
                <RichTextEditor
                  id="constraintsText"
                  value={formData.constraintsText ?? ''}
                  onChange={(constraintsText) => setFormData((prev) => ({ ...prev, constraintsText }))}
                  disabled={isSubmitting}
                  placeholder="Write all input limits and constraints here..."
                  inputClassName={COMPACT_EDITOR_CLASS}
                  toolbarVariant="compact"
                  ariaLabel="Constraints editor"
                  footerText="Compact formatting"
                />
              </div>

              <div className="space-y-2">
                <div className="flex items-center gap-2">
                  <Label htmlFor="publicNotes">Public Notes</Label>
                  <AdminHelpTooltip
                    label="Public notes help"
                    content="Contestant-facing clarifications or safe hints. These appear in team statements and public/safe prompt exports."
                  />
                </div>
                <RichTextEditor
                  id="publicNotes"
                  value={formData.publicNotes ?? ''}
                  onChange={(publicNotes) => setFormData((prev) => ({ ...prev, publicNotes }))}
                  disabled={isSubmitting}
                  placeholder="Write contestant-facing notes, edge-case clarifications, or safe hints here..."
                  inputClassName={COMPACT_EDITOR_CLASS}
                  toolbarVariant="compact"
                  ariaLabel="Public notes editor"
                  footerText="Contestant-facing"
                />
                <p className="text-sm text-slate-500">
                  Contestant-facing notes for examples, edge cases, special definitions, or safe hints.
                </p>
              </div>
            </div>

            <details className="rounded-md border border-amber-200 bg-amber-50">
              <summary className="cursor-pointer px-4 py-3 text-sm font-semibold text-amber-950">
                Admin Internal Notes
                <AdminHelpTooltip
                  label="Admin internal notes help"
                  content="Setter-only guidance such as intended solution notes or hidden-test strategy. Never shown to teams."
                  className="ml-2 text-amber-700 hover:bg-amber-100 hover:text-amber-900"
                />
              </summary>
              <div className="space-y-2 border-t border-amber-200 p-4">
                <Textarea
                  id="adminNotes"
                  value={formData.adminNotes ?? ''}
                  onChange={(e) => setFormData({ ...formData, adminNotes: e.target.value })}
                  className="max-h-40 min-h-32 resize-y overflow-y-auto bg-white"
                  placeholder="Setter notes, intended solution notes, trap cases, hidden-test strategy..."
                  disabled={isSubmitting}
                />
                <p className="text-sm text-amber-800">
                  Admin-only. These notes do not appear in team views or SAFE_MODE prompt exports.
                </p>
              </div>
            </details>
            
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
              <Label htmlFor="validationMode">Validation Mode</Label>
              <Select
                value={formData.validationMode}
                onValueChange={(value: ProblemRequest['validationMode']) =>
                  setFormData({
                    ...formData,
                    validationMode: value,
                    comparePolicy: value === 'CUSTOM_VALIDATOR' ? 'EXACT' : formData.comparePolicy,
                    floatAbsoluteEpsilon: value === 'CUSTOM_VALIDATOR' ? null : formData.floatAbsoluteEpsilon,
                    floatRelativeEpsilon: value === 'CUSTOM_VALIDATOR' ? null : formData.floatRelativeEpsilon,
                    validatorLanguageId:
                      value === 'CUSTOM_VALIDATOR'
                        ? formData.validatorLanguageId ?? DEFAULT_VALIDATOR_LANGUAGE_ID
                        : formData.validatorLanguageId,
                  })
                }
              >
                <SelectTrigger id="validationMode">
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
              <Label htmlFor="comparePolicy">Compare Policy</Label>
              <Select
                value={formData.comparePolicy}
                onValueChange={(value: ProblemRequest['comparePolicy']) =>
                  setFormData({
                    ...formData,
                    comparePolicy: value,
                    floatAbsoluteEpsilon: value === 'FLOAT_TOLERANCE' ? formData.floatAbsoluteEpsilon ?? 0.000001 : null,
                    floatRelativeEpsilon: value === 'FLOAT_TOLERANCE' ? formData.floatRelativeEpsilon ?? null : null,
                  })
                }
              >
                <SelectTrigger id="comparePolicy">
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

            {!usesCustomValidator && formData.comparePolicy === 'FLOAT_TOLERANCE' && (
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label htmlFor="floatAbsoluteEpsilon">Absolute Epsilon</Label>
                  <Input
                    id="floatAbsoluteEpsilon"
                    type="number"
                    min="0"
                    step="any"
                    value={formData.floatAbsoluteEpsilon ?? ''}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        floatAbsoluteEpsilon: e.target.value === '' ? null : Number(e.target.value),
                      })
                    }
                    className="h-10"
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="floatRelativeEpsilon">Relative Epsilon</Label>
                  <Input
                    id="floatRelativeEpsilon"
                    type="number"
                    min="0"
                    step="any"
                    value={formData.floatRelativeEpsilon ?? ''}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        floatRelativeEpsilon: e.target.value === '' ? null : Number(e.target.value),
                      })
                    }
                    className="h-10"
                  />
                </div>
              </div>
            )}

            {usesCustomValidator && (
              <div className="space-y-4 rounded-md border border-slate-200 p-4">
                <div className="flex items-center gap-2">
                  <Checkbox
                    id="validatorEnabled"
                    checked={formData.validatorEnabled !== false}
                    onCheckedChange={(checked) =>
                      setFormData({ ...formData, validatorEnabled: checked === true })
                    }
                    disabled={isSubmitting}
                  />
                  <Label htmlFor="validatorEnabled">Enable custom validator</Label>
                </div>
                <div className="space-y-2">
                  <Label htmlFor="validatorLanguageId">Validator Language</Label>
                  <Select
                    value={String(formData.validatorLanguageId ?? DEFAULT_VALIDATOR_LANGUAGE_ID)}
                    onValueChange={(value) =>
                      setFormData({
                        ...formData,
                        validatorLanguageId: Number(value),
                      })
                    }
                    disabled={isSubmitting}
                  >
                    <SelectTrigger id="validatorLanguageId">
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
                <div className="space-y-2">
                  <Label htmlFor="validatorSource">Validator Source</Label>
                  <Textarea
                    id="validatorSource"
                    value={formData.validatorSource ?? ''}
                    onChange={(e) => setFormData({ ...formData, validatorSource: e.target.value })}
                    className="h-56 max-h-80 resize-y overflow-y-auto font-mono text-sm"
                    placeholder="Paste custom output validator source code"
                    disabled={isSubmitting}
                  />
                </div>
              </div>
            )}

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
          
          <DialogFooter className="sticky bottom-0 z-10 border-t border-slate-200 bg-white px-6 py-4">
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
