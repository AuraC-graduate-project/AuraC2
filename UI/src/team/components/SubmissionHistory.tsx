import React, { useState } from "react";
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


import {
  CheckCircle2, XCircle, Clock, Eye, AlertTriangle, Timer,
  Bug,
  ServerCrash,} from "lucide-react";

export interface Submission {
  id: number;
  problem: string;
  verdict: "Accepted" | "Wrong Answer" | "Time Limit Exceeded" | "Pending" | "Compilation Error" | "Runtime Error" | "System Error" | "Running";
  language: string;
  time: string;
  executionTime: string;

  // 🔥 THIS IS WHAT YOU WERE ASKING FOR
  code: string;
}

type Props = {
  submissions: Submission[];
};

export function SubmissionHistory({ submissions }: Props) {
  const [open, setOpen] = useState(false);
  const [selected, setSelected] = useState<Submission | null>(null);

  const openCode = (s: Submission) => {
    setSelected(s);
    setOpen(true);
  };

  const getVerdictIcon = (verdict: Submission["verdict"]) => {
    switch (verdict) {
      case "Accepted":
        return <CheckCircle2 className="w-4 h-4 text-green-600" />;

      case "Pending":
      case "Running":
        return <Clock className="w-4 h-4 text-yellow-600" />;

      case "Wrong Answer":
        return <XCircle className="w-4 h-4 text-red-600" />;

      case "Time Limit Exceeded":
        return <Timer className="w-4 h-4 text-orange-600" />;

      case "Compilation Error":
        return <AlertTriangle className="w-4 h-4 text-purple-600" />;

      case "Runtime Error":
        return <Bug className="w-4 h-4 text-pink-600" />;

      case "System Error":
        return <ServerCrash className="w-4 h-4 text-gray-600" />;

      default:
        return <XCircle className="w-4 h-4 text-gray-500" />;
    }
  };

  const getVerdictColor = (verdict: Submission["verdict"]) => {
    switch (verdict) {
      case "Accepted":
        return "text-green-600";

      case "Pending":
      case "Running":
        return "text-yellow-600";

      case "Wrong Answer":
        return "text-red-600";

      case "Time Limit Exceeded":
        return "text-orange-600";

      case "Compilation Error":
        return "text-purple-600";

      case "Runtime Error":
        return "text-pink-600";

      case "System Error":
        return "text-gray-600";

      default:
        return "text-gray-500";
    }
  };
  return (
    <>
      <div className="bg-white rounded-lg border border-gray-200 shadow-sm">
        <div className="p-4 border-b border-gray-200 bg-gray-50">
          <h3 className="text-gray-900">Submission History</h3>
        </div>

        <div className="overflow-auto">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Problem</TableHead>
                <TableHead>Verdict</TableHead>
                <TableHead>Language</TableHead>
                <TableHead>Time</TableHead>
                <TableHead>Execution</TableHead>
                <TableHead className="text-right">Code</TableHead>
              </TableRow>
            </TableHeader>

            <TableBody>
              {submissions.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="text-center text-gray-500 py-6">
                    No submissions yet
                  </TableCell>
                </TableRow>
              ) : (
                submissions.map((s) => (
                  <TableRow key={s.id}>
                    <TableCell>{s.problem}</TableCell>

                    <TableCell>
                      <div className="flex items-center gap-2">
                        {getVerdictIcon(s.verdict)}
                        <span className={getVerdictColor(s.verdict)}>
                          {s.verdict}
                        </span>
                      </div>
                    </TableCell>

                    <TableCell className="font-mono text-sm">
                      {s.language}
                    </TableCell>

                    <TableCell>{s.time}</TableCell>

                    <TableCell>{s.executionTime}</TableCell>

                    <TableCell className="text-right">
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => openCode(s)}
                      >
                        <Eye className="w-4 h-4" />
                      </Button>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      </div>

      {/* 🔥 CODE DIALOG */}
      <Dialog open={open} onOpenChange={setOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>
              Submission Code {selected ? `#${selected.id}` : ""}
            </DialogTitle>
          </DialogHeader>

          <div className="bg-slate-900 text-slate-100 rounded-lg p-4 max-h-[60vh] overflow-auto">
            <pre className="text-xs whitespace-pre-wrap">
              {selected?.code ?? ""}
            </pre>
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
