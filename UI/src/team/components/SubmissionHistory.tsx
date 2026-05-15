import { useEffect, useState } from "react";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "./ui/table";
import { Button } from "./ui/button";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "./ui/dialog";
import { Eye } from "lucide-react";
import { getStoredToken } from "../../auth/tokenStore";
import { decodeJwtSubject } from "../../auth/jwt";
import { clearDraftFromStorage } from "../../hooks/useCodeDraft";
import { StatusBadge, VerdictLabel } from "../../components/StatusBadge";

export interface Submission {
  id: number;
  problem: string;
  problemId: number;
  contestId: number;
  verdict: VerdictLabel;
  language: string;
  time: string;
  executionTime: string;
  memoryUsage: string;
  code: string;
}

type Props = {
  submissions: Submission[];
  title?: string;
  isLoading?: boolean;
  compact?: boolean;
  className?: string;
};

function getUserId(): string {
  const token = getStoredToken();
  if (!token) return "unknown";
  return decodeJwtSubject(token) ?? "unknown";
}

export function SubmissionHistory({
  submissions,
  title = "Submission History",
  isLoading = false,
  compact = false,
  className = "",
}: Props) {
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<Submission | null>(null);
  const userId = getUserId();

  useEffect(() => {
    if (!userId || userId === "unknown") return;

    const cleared = new Set<string>();
    submissions.forEach((submission) => {
      if (submission.verdict === "ACCEPTED") {
        const key = `${submission.problemId}-${submission.language}`;
        if (!cleared.has(key)) {
          clearDraftFromStorage(userId, String(submission.contestId), String(submission.problemId), submission.language);
          cleared.add(key);
        }
      }
    });
  }, [submissions, userId]);

  return (
    <>
      <section
        className={`flex min-h-0 flex-col bg-white ${
          compact ? "rounded-none border-0 shadow-none" : "rounded-lg border border-slate-200 shadow-sm"
        } ${className}`}
      >
        <div className={`shrink-0 border-b border-slate-200 bg-slate-50 ${compact ? "p-3" : "p-4"}`}>
          <div className="flex items-center justify-between gap-3">
            <div>
              <p className="text-xs font-semibold uppercase tracking-wide text-blue-700">Submissions</p>
              <h2 className={`${compact ? "text-sm" : "text-lg"} font-semibold text-slate-950`}>{title}</h2>
            </div>
            <span className="rounded-full border border-slate-200 bg-white px-2.5 py-1 text-xs font-semibold text-slate-600">
              {submissions.length} total
            </span>
          </div>
        </div>

        <div className="min-h-0 flex-1 overflow-auto">
          {compact && !isLoading && submissions.length === 0 ? (
            <div className="px-3 py-3 text-sm text-slate-500">
              No submissions yet.
            </div>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead className="w-[90px]">ID</TableHead>
                  <TableHead>Problem</TableHead>
                  <TableHead>Language</TableHead>
                  <TableHead>Verdict</TableHead>
                  {!compact && <TableHead>Execution</TableHead>}
                  {!compact && <TableHead>Memory</TableHead>}
                  {!compact && <TableHead>Submitted</TableHead>}
                  <TableHead className="text-right">Code</TableHead>
                </TableRow>
              </TableHeader>

              <TableBody>
                {isLoading ? (
                  <TableRow>
                    <TableCell colSpan={compact ? 5 : 8} className={`${compact ? "py-4" : "py-8"} text-center text-slate-500`}>
                      Loading submissions...
                    </TableCell>
                  </TableRow>
                ) : submissions.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={compact ? 5 : 8} className={`${compact ? "py-4" : "py-8"} text-center text-slate-500`}>
                      No submissions yet.
                    </TableCell>
                  </TableRow>
                ) : (
                  submissions.map((submission) => (
                    <TableRow key={submission.id}>
                      <TableCell className="font-mono text-sm">{submission.id}</TableCell>
                      <TableCell className="font-medium text-slate-900">{submission.problem}</TableCell>
                      <TableCell className="font-mono text-sm">{submission.language}</TableCell>
                      <TableCell>
                        <StatusBadge kind="verdict" value={submission.verdict} />
                      </TableCell>
                      {!compact && <TableCell className="text-sm">{submission.executionTime}</TableCell>}
                      {!compact && <TableCell className="text-sm">{submission.memoryUsage}</TableCell>}
                      {!compact && <TableCell className="text-sm text-slate-600">{submission.time}</TableCell>}
                      <TableCell className="text-right">
                        <Button
                          size="sm"
                          variant="outline"
                          className="gap-2"
                          onClick={() => {
                            setSelected(submission);
                            setOpen(true);
                          }}
                        >
                          <Eye className="h-4 w-4" />
                          View
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          )}
        </div>
      </section>

      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>Submission Code {selected ? `#${selected.id}` : ""}</DialogTitle>
          </DialogHeader>

          {selected && (
            <div className="mb-3 grid gap-3 rounded-lg border border-slate-200 bg-slate-50 p-4 text-sm md:grid-cols-3">
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Problem</p>
                <p className="mt-1 font-medium text-slate-900">{selected.problem}</p>
              </div>
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Language</p>
                <p className="mt-1 font-mono text-slate-900">{selected.language}</p>
              </div>
              <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">Verdict</p>
                <div className="mt-1">
                  <StatusBadge kind="verdict" value={selected.verdict} />
                </div>
              </div>
            </div>
          )}

          <div className="max-h-[60vh] overflow-auto rounded-lg bg-surface-container-lowest p-4 text-on-surface dark:bg-black">
            <pre className="whitespace-pre-wrap font-mono text-xs">{selected?.code ?? ""}</pre>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
