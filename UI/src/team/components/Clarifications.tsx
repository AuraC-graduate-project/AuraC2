import { useEffect, useMemo, useState } from "react";
import { Button } from "./ui/button";
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from "./ui/dialog";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./ui/select";
import { Textarea } from "./ui/textarea";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "./ui/table";
import { MessageCircle, MessageSquarePlus, RefreshCw } from "lucide-react";
import { getMyClarifications, submitClarification } from "../services/teamApi";
import { ClarificationResponse, StandardReply } from "../../admin/types/api";
import { StatusBadge } from "../../components/StatusBadge";
import { getStoredToken } from "../../auth/tokenStore";
import { decodeJwtSubject } from "../../auth/jwt";

interface ClarificationsProps {
  contestId: number | null;
  problems: Array<{ id: number; title: string }>;
}

type ProblemSelectValue = "GENERAL" | `${number}`;

const standardReplyText: Record<Exclude<StandardReply, "CUSTOM">, string> = {
  NO_COMMENT: "No comment.",
  READ_PROBLEM_STATEMENT_CAREFULLY: "Read the problem statement carefully.",
  YES: "Yes.",
  NO: "No.",
  ANSWERED: "Answered.",
};

function formatCreatedAt(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return date.toLocaleString();
}

function getReplyText(item: ClarificationResponse): string {
  if (item.reply && item.reply.trim()) return item.reply;
  if (item.standardReply && item.standardReply !== "CUSTOM") {
    return standardReplyText[item.standardReply];
  }
  return "";
}

function getTeamName(): string {
  const token = getStoredToken();
  if (!token) return "";
  return decodeJwtSubject(token) ?? "";
}

function ClarificationTable({
  items,
  emptyText,
}: {
  items: ClarificationResponse[];
  emptyText: string;
}) {
  return (
    <div className="overflow-auto rounded-lg border border-slate-200">
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>Problem</TableHead>
            <TableHead>Question</TableHead>
            <TableHead>Status</TableHead>
            <TableHead>Visibility</TableHead>
            <TableHead>Reply</TableHead>
            <TableHead>Created</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {items.length === 0 ? (
            <TableRow>
              <TableCell colSpan={6} className="py-8 text-center text-slate-500">
                {emptyText}
              </TableCell>
            </TableRow>
          ) : (
            items.map((item) => (
              <TableRow key={item.id}>
                <TableCell>
                  <span className="rounded-md bg-slate-100 px-2 py-1 text-sm text-slate-700">
                    {item.problemTitle ?? "General"}
                  </span>
                </TableCell>
                <TableCell className="max-w-xs whitespace-normal break-words">
                  <div className="flex items-start gap-2">
                    <MessageCircle className="mt-0.5 h-4 w-4 shrink-0 text-slate-400" />
                    <span className="text-slate-700">{item.question}</span>
                  </div>
                </TableCell>
                <TableCell>
                  <StatusBadge kind="clarification" value={item.status} />
                </TableCell>
                <TableCell>
                  {item.replyType ? (
                    <StatusBadge kind="visibility" value={item.replyType} />
                  ) : (
                    <span className="text-slate-400">-</span>
                  )}
                </TableCell>
                <TableCell className="max-w-md whitespace-normal break-words text-slate-700">
                  {getReplyText(item) || (
                    <span className="text-slate-400 italic">Waiting for response...</span>
                  )}
                </TableCell>
                <TableCell className="text-slate-500">{formatCreatedAt(item.createdAt)}</TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </div>
  );
}

export function Clarifications({ contestId, problems }: ClarificationsProps) {
  const [open, setOpen] = useState(false);
  const [selectedProblem, setSelectedProblem] = useState<ProblemSelectValue>("GENERAL");
  const [question, setQuestion] = useState("");
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [clarifications, setClarifications] = useState<ClarificationResponse[]>([]);

  const teamName = useMemo(getTeamName, []);
  const problemOptions = useMemo(
    () => problems.map((problem) => ({ value: String(problem.id) as `${number}`, label: problem.title })),
    [problems]
  );

  const myClarifications = useMemo(() => {
    if (!teamName) return clarifications;
    return clarifications.filter((item) => item.username === teamName);
  }, [clarifications, teamName]);

  const publicAnswered = useMemo(
    () => clarifications.filter((item) => item.status === "ANSWERED" && item.replyType === "PUBLIC"),
    [clarifications]
  );

  const loadClarifications = async () => {
    if (!contestId) {
      setClarifications([]);
      return;
    }

    setIsLoading(true);
    setError(null);
    try {
      const data = await getMyClarifications(contestId);
      setClarifications(data);
    } catch (e) {
      setClarifications([]);
      setError(e instanceof Error ? e.message : "Failed to load clarifications");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadClarifications();
  }, [contestId]);

  const handleSubmit = async () => {
    if (!contestId) return;

    const normalizedQuestion = question.trim();
    if (!normalizedQuestion) {
      setError("Question cannot be empty.");
      return;
    }

    setIsSubmitting(true);
    setError(null);
    try {
      await submitClarification({
        contestId,
        problemId: selectedProblem === "GENERAL" ? null : Number(selectedProblem),
        question: normalizedQuestion,
      });

      setOpen(false);
      setQuestion("");
      setSelectedProblem("GENERAL");
      await loadClarifications();
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to submit clarification");
    } finally {
      setIsSubmitting(false);
    }
  };

  if (!contestId) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-8 text-center shadow-sm">
        <p className="font-medium text-slate-800">Clarifications are available only while a contest is active.</p>
      </section>
    );
  }

  return (
    <section className="space-y-6">
      <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
        <div className="flex flex-col gap-4 border-b border-slate-200 bg-slate-50 p-5 md:flex-row md:items-center md:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-700">Clarifications</p>
            <h2 className="text-xl font-semibold text-slate-950">Ask and Review Questions</h2>
            <p className="mt-1 text-sm text-slate-600">Private replies are team-specific; public replies are visible to all teams.</p>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <Button variant="outline" className="gap-2 bg-white" onClick={loadClarifications} disabled={isLoading}>
              <RefreshCw className="h-4 w-4" />
              Refresh
            </Button>
            <Dialog open={open} onOpenChange={setOpen}>
              <DialogTrigger asChild>
                <Button className="gap-2 bg-blue-700 hover:bg-blue-800">
                  <MessageSquarePlus className="h-4 w-4" />
                  Ask Clarification
                </Button>
              </DialogTrigger>
              <DialogContent>
                <DialogHeader>
                  <DialogTitle>Ask Clarification</DialogTitle>
                </DialogHeader>
                <div className="space-y-4 mt-4">
                  <div className="space-y-2">
                    <label className="text-sm font-semibold text-slate-700">Related Problem</label>
                    <Select
                      value={selectedProblem}
                      onValueChange={(value) => setSelectedProblem(value as ProblemSelectValue)}
                    >
                      <SelectTrigger>
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="GENERAL">General</SelectItem>
                        {problemOptions.map((problem) => (
                          <SelectItem key={problem.value} value={problem.value}>
                            {problem.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>

                  <div className="space-y-2">
                    <label className="text-sm font-semibold text-slate-700">Question</label>
                    <Textarea
                      value={question}
                      onChange={(e) => setQuestion(e.target.value)}
                      placeholder="Type your question clearly..."
                      className="min-h-[120px]"
                      disabled={isSubmitting}
                    />
                  </div>

                  {error && <p className="text-sm text-rose-600">{error}</p>}

                  <div className="flex justify-end gap-2">
                    <Button variant="outline" onClick={() => setOpen(false)} disabled={isSubmitting}>
                      Cancel
                    </Button>
                    <Button onClick={handleSubmit} className="bg-blue-700 hover:bg-blue-800" disabled={isSubmitting}>
                      {isSubmitting ? "Submitting..." : "Submit Question"}
                    </Button>
                  </div>
                </div>
              </DialogContent>
            </Dialog>
          </div>
        </div>

        <div className="p-5">
          {error && <p className="mb-3 text-sm text-rose-600">{error}</p>}
          {isLoading ? (
            <p className="py-8 text-center text-slate-500">Loading clarifications...</p>
          ) : (
            <ClarificationTable
              items={myClarifications}
              emptyText="You have not asked any clarifications yet."
            />
          )}
        </div>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="mb-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-blue-700">Public Replies</p>
          <h2 className="text-lg font-semibold text-slate-950">Visible to All Teams</h2>
        </div>
        <ClarificationTable
          items={publicAnswered}
          emptyText="No public answered clarifications yet."
        />
      </div>
    </section>
  );
}
