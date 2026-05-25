import { useCallback, useEffect, useMemo, useState } from "react";
import { Card, CardContent, CardHeader, CardTitle } from "./ui/card";
import { Button } from "./ui/button";
import { Label } from "./ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./ui/select";
import { Textarea } from "./ui/textarea";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "./ui/table";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from "./ui/dialog";
import { getAllClarifications, getContestClarifications, replyClarification } from "../services/api";
import { ClarificationResponse, ClarificationType, ReplyRequest, StandardReply } from "../types/api";
import { MessageSquareReply, RefreshCw } from "lucide-react";
import { toast } from "sonner";
import { StatusBadge } from "../../components/StatusBadge";
import { useClarificationStream } from "../../hooks/useClarificationStream";
import { AdminHelpTooltip } from "./AdminHelpTooltip";

type ClarificationFilter = "ALL" | "PENDING" | "ANSWERED" | "PUBLIC" | "PRIVATE";

const standardReplyOptions: { value: StandardReply; label: string }[] = [
  { value: "NO_COMMENT", label: "No comment." },
  { value: "READ_PROBLEM_STATEMENT_CAREFULLY", label: "Read the problem statement carefully." },
  { value: "YES", label: "Yes." },
  { value: "NO", label: "No." },
  { value: "ANSWERED", label: "Answered." },
  { value: "CUSTOM", label: "Custom reply" },
];

const standardReplyText: Record<Exclude<StandardReply, "CUSTOM">, string> = {
  NO_COMMENT: "No comment.",
  READ_PROBLEM_STATEMENT_CAREFULLY: "Read the problem statement carefully.",
  YES: "Yes.",
  NO: "No.",
  ANSWERED: "Answered.",
};

function formatDateTime(value: string | null): string {
  if (!value) return "-";
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

export function ClarificationsView({ contestId }: { contestId: number | null }) {
  const [loading, setLoading] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [clarifications, setClarifications] = useState<ClarificationResponse[]>([]);
  const [statusFilter, setStatusFilter] = useState<ClarificationFilter>("ALL");
  const [selected, setSelected] = useState<ClarificationResponse | null>(null);
  const [replyDialogOpen, setReplyDialogOpen] = useState(false);
  const [replyType, setReplyType] = useState<ClarificationType>("PUBLIC");
  const [standardReply, setStandardReply] = useState<StandardReply>("NO_COMMENT");
  const [customReply, setCustomReply] = useState("");

  const loadClarifications = useCallback(async () => {
    setLoading(true);
    try {
      const data = contestId == null
        ? await getAllClarifications()
        : await getContestClarifications(contestId);
      setClarifications(data);
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to load clarifications");
      setClarifications([]);
    } finally {
      setLoading(false);
    }
  }, [contestId]);

  useEffect(() => {
    void loadClarifications();
  }, [loadClarifications]);

  useClarificationStream({
    contestId,
    role: "ADMIN",
    enabled: contestId != null,
    onEvent: (event) => {
      if (event.contestId !== contestId) return;
      void loadClarifications();
    },
  });

  const filteredClarifications = useMemo(() => {
    if (statusFilter === "ALL") return clarifications;
    if (statusFilter === "PUBLIC" || statusFilter === "PRIVATE") {
      return clarifications.filter((item) => item.replyType === statusFilter);
    }
    return clarifications.filter((item) => item.status === statusFilter);
  }, [clarifications, statusFilter]);

  const openReplyDialog = (item: ClarificationResponse) => {
    setSelected(item);
    setReplyType(item.replyType ?? "PUBLIC");
    if (item.standardReply) {
      setStandardReply(item.standardReply);
      setCustomReply(item.reply ?? "");
    } else if (item.reply) {
      setStandardReply("CUSTOM");
      setCustomReply(item.reply);
    } else {
      setStandardReply("NO_COMMENT");
      setCustomReply("");
    }
    setReplyDialogOpen(true);
  };

  const handleReplySubmit = async () => {
    if (!selected) return;

    if (standardReply === "CUSTOM" && !customReply.trim()) {
      toast.error("Custom reply text is required when using CUSTOM reply.");
      return;
    }

    setIsSubmitting(true);
    try {
      const payload: ReplyRequest = {
        standardReply,
        reply: standardReply === "CUSTOM" ? customReply.trim() : null,
        replyType,
      };
      await replyClarification(selected.id, payload);
      toast.success("Clarification reply saved");
      setReplyDialogOpen(false);
      setSelected(null);
      await loadClarifications();
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Failed to reply to clarification");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <>
      <Card className="border border-gray-200 shadow-sm">
        <CardHeader className="border-b border-slate-200 bg-slate-50">
          <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
            <div>
              <div className="flex items-center gap-2">
                <CardTitle className="text-2xl text-slate-950">Clarifications</CardTitle>
                <AdminHelpTooltip
                  label="Clarifications help"
                  content="Private replies go only to the asking team. Public replies are visible to all teams."
                />
              </div>
              <p className="mt-1 text-sm text-slate-600">Reply privately to one team or publicly to all teams.</p>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <div className="flex flex-wrap rounded-md border border-slate-200 bg-white p-1">
                {(["ALL", "PENDING", "ANSWERED", "PUBLIC", "PRIVATE"] as ClarificationFilter[]).map((filter) => (
                  <button
                    key={filter}
                    type="button"
                    onClick={() => setStatusFilter(filter)}
                    className={`rounded px-3 py-1.5 text-sm font-semibold transition ${
                      statusFilter === filter
                        ? "bg-blue-700 text-white"
                        : "text-slate-600 hover:bg-slate-100"
                    }`}
                  >
                    {filter === "ALL" ? "All" : filter[0] + filter.slice(1).toLowerCase()}
                  </button>
                ))}
              </div>
              <Button variant="outline" size="sm" onClick={loadClarifications} disabled={loading} className="bg-white">
                <RefreshCw className="w-4 h-4 mr-1" />
                Refresh
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent className="p-6">
          {loading ? (
            <p className="text-slate-600 py-8 text-center">Loading clarifications...</p>
          ) : filteredClarifications.length === 0 ? (
            <p className="text-slate-600 py-8 text-center">No clarifications found.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>ID</TableHead>
                  <TableHead>Contest</TableHead>
                  <TableHead>Problem</TableHead>
                  <TableHead>Team</TableHead>
                  <TableHead>Question</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Visibility</TableHead>
                  <TableHead>Reply</TableHead>
                  <TableHead>Created</TableHead>
                  <TableHead className="text-right">Action</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {filteredClarifications.map((item) => (
                  <TableRow key={item.id}>
                    <TableCell>{item.id}</TableCell>
                    <TableCell>#{item.contestId}</TableCell>
                    <TableCell>{item.problemTitle ?? "General"}</TableCell>
                    <TableCell>{item.username}</TableCell>
                    <TableCell className="max-w-[320px] whitespace-normal break-words">{item.question}</TableCell>
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
                    <TableCell className="max-w-[280px] whitespace-normal break-words">
                      {getReplyText(item) || <span className="text-slate-400 italic">No reply yet</span>}
                    </TableCell>
                    <TableCell>{formatDateTime(item.createdAt)}</TableCell>
                    <TableCell className="text-right">
                      <Button size="sm" variant="outline" onClick={() => openReplyDialog(item)}>
                        <MessageSquareReply className="w-4 h-4 mr-1" />
                        {item.status === "PENDING" ? "Reply" : "Update"}
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </CardContent>
      </Card>

      <Dialog open={replyDialogOpen} onOpenChange={setReplyDialogOpen}>
        <DialogContent className="max-h-[calc(100vh-2rem)] overflow-y-auto sm:max-w-[560px]">
          <DialogHeader>
            <DialogTitle>Reply to Clarification</DialogTitle>
          </DialogHeader>

          {!selected ? null : (
            <div className="space-y-4">
              <div className="rounded-md border border-gray-200 bg-gray-50 p-3">
                <p className="text-sm text-slate-700">
                  <span className="font-medium">Question:</span> {selected.question}
                </p>
              </div>

              <div className="space-y-2">
                <Label>Reply Visibility</Label>
                <Select
                  value={replyType}
                  onValueChange={(value) => setReplyType(value as ClarificationType)}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="PUBLIC">Public Reply - Visible to All Teams</SelectItem>
                    <SelectItem value="PRIVATE">Private Reply - Requesting Team Only</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label>Standard Reply</Label>
                <Select
                  value={standardReply}
                  onValueChange={(value) => setStandardReply(value as StandardReply)}
                >
                  <SelectTrigger>
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {standardReplyOptions.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              {standardReply === "CUSTOM" && (
                <div className="space-y-2">
                  <Label htmlFor="custom-reply">Custom Reply</Label>
                  <Textarea
                    id="custom-reply"
                    value={customReply}
                    onChange={(e) => setCustomReply(e.target.value)}
                    rows={4}
                    placeholder="Enter custom reply..."
                  />
                </div>
              )}
            </div>
          )}

          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setReplyDialogOpen(false)} disabled={isSubmitting}>
              Cancel
            </Button>
            <Button type="button" className="bg-blue-700 hover:bg-blue-800" onClick={handleReplySubmit} disabled={isSubmitting || !selected}>
              {isSubmitting ? "Saving..." : "Submit Reply"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}
