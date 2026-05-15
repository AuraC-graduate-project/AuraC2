import { useCallback, useEffect, useMemo, useRef, useState, type UIEvent } from "react";
import { CheckCircle2, Save, Send, Terminal, XCircle } from "lucide-react";
import { Button } from "./ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "./ui/select";
import { submitCode } from "../services/teamApi";
import { useCodeDraft } from "../../hooks/useCodeDraft";
import { decodeJwtSubject } from "../../auth/jwt";
import { getStoredToken } from "../../auth/tokenStore";

const STARTER_CODE: Record<string, (title: string) => string> = {
  c: (title) => `#include <stdio.h>

int main() {
    // ${title}
    // write your solution here

    return 0;
}
`,
  cpp: (title) => `#include <bits/stdc++.h>
using namespace std;

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);

    // ${title}
    // write your solution here

    return 0;
}
`,
  java: (title) => `import java.io.*;
import java.util.*;

public class Main {
    public static void main(String[] args) throws Exception {
        // ${title}
        // write your solution here
    }
}
`,
  python: (title) => `# ${title}
import sys
input = sys.stdin.readline

def main():
    # write your solution here
    pass

if __name__ == "__main__":
    main()
`,
  javascript: (title) => `// ${title}
'use strict';

const fs = require('fs');
const input = fs.readFileSync(0, 'utf8').trim().split(/\\s+/);
let idx = 0;

// write your solution here
`,
  go: (title) => `package main

import (
    "bufio"
    "fmt"
    "os"
)

func main() {
    in := bufio.NewReader(os.Stdin)
    out := bufio.NewWriter(os.Stdout)
    defer out.Flush()

    _ = in
    _ = fmt.Fscan

    // ${title}
}
`,
};

const languageOptions = [
  { value: "c", label: "C" },
  { value: "cpp", label: "C++" },
  { value: "java", label: "Java" },
  { value: "python", label: "Python" },
  { value: "javascript", label: "JavaScript" },
  { value: "go", label: "Go" },
];

type Props = {
  contestId?: number | null;
  problem: { id: number; title: string } | null;
  onSubmitted?: () => void;
};

function getUserId(): string {
  const token = getStoredToken();
  if (!token) return "unknown";
  return decodeJwtSubject(token) ?? "unknown";
}

function escapeHtml(value: string): string {
  return value
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

const commonKeywords = [
  "break", "case", "catch", "class", "const", "continue", "default", "do", "else", "enum",
  "false", "for", "function", "if", "import", "in", "new", "null", "private", "protected",
  "public", "return", "static", "switch", "this", "throw", "true", "try", "void", "while",
];

const languageKeywords: Record<string, string[]> = {
  c: ["auto", "char", "double", "extern", "float", "int", "long", "register", "short", "signed", "sizeof", "struct", "typedef", "union", "unsigned", "volatile"],
  cpp: ["auto", "bool", "char", "cin", "cout", "double", "endl", "float", "include", "int", "long", "namespace", "nullptr", "std", "string", "template", "typename", "using", "vector"],
  java: ["abstract", "boolean", "byte", "extends", "final", "implements", "int", "interface", "long", "package", "short", "String", "super", "throws"],
  python: ["and", "as", "def", "elif", "except", "from", "global", "is", "lambda", "nonlocal", "not", "or", "pass", "print", "self", "with", "yield"],
  javascript: ["async", "await", "const", "export", "let", "module", "require", "undefined", "var"],
  go: ["chan", "defer", "fallthrough", "func", "go", "import", "interface", "map", "package", "range", "select", "struct", "type", "var"],
};

function tokenTypeFor(token: string, keywords: Set<string>): string {
  if (token.startsWith("//") || token.startsWith("/*") || token.startsWith("# ")) return "comment";
  if (token.startsWith("#")) return "preprocessor";
  if (/^["'`]/.test(token)) return "string";
  if (/^\d/.test(token)) return "number";
  if (keywords.has(token)) return "keyword";
  return "operator";
}

function highlightCode(source: string, language: string): string {
  const keywords = new Set([...commonKeywords, ...(languageKeywords[language] ?? [])]);
  const keywordPattern = [...keywords]
    .sort((a, b) => b.length - a.length)
    .map(escapeRegExp)
    .join("|");
  const tokenPattern = new RegExp(
    `(\\/\\/[^\\n]*|\\/\\*[\\s\\S]*?\\*\\/|#\\s*[^\\n]*|"(?:\\\\.|[^"\\\\])*"|'(?:\\\\.|[^'\\\\])*'|\`(?:\\\\.|[^\`\\\\])*\`|\\b(?:${keywordPattern})\\b|\\b\\d+(?:\\.\\d+)?\\b|[{}()[\\].,;:+\\-*/%<>=!&|^~?]+)`,
    "g"
  );

  let result = "";
  let lastIndex = 0;
  source.replace(tokenPattern, (token, _match, offset: number) => {
    result += escapeHtml(source.slice(lastIndex, offset));
    const type = tokenTypeFor(token, keywords);
    result += `<span class="aura-token aura-token-${type}">${escapeHtml(token)}</span>`;
    lastIndex = offset + token.length;
    return token;
  });

  result += escapeHtml(source.slice(lastIndex));
  return result || " ";
}

export function CodeEditor({ contestId, problem, onSubmitted }: Props) {
  const [language, setLanguage] = useState("cpp");
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<{ type: "success" | "error"; text: string } | null>(null);
  const [savedAt, setSavedAt] = useState<string | null>(null);
  const highlightRef = useRef<HTMLPreElement | null>(null);
  const lineNumbersRef = useRef<HTMLPreElement | null>(null);

  const userId = useMemo(getUserId, []);
  const contestKey = contestId ? String(contestId) : "0";
  const problemKey = problem?.id ? String(problem.id) : "0";
  const starterCode = useMemo(() => {
    if (!problem) return "";
    return (STARTER_CODE[language] ?? STARTER_CODE.cpp)(problem.title);
  }, [language, problem?.id, problem?.title]);

  const { code, setCode, setOnSavedCallback } = useCodeDraft(
    userId,
    contestKey,
    problemKey,
    language,
    starterCode
  );

  useEffect(() => {
    setOnSavedCallback(() => {
      setSavedAt(new Date().toLocaleTimeString());
    });
    return () => setOnSavedCallback(null);
  }, [setOnSavedCallback]);

  const handleLanguageChange = useCallback((newLanguage: string) => {
    setLanguage(newLanguage);
    setMessage(null);
  }, []);

  const handleSubmit = async () => {
    if (!contestId || !problem) {
      setMessage({ type: "error", text: "A running contest and selected problem are required." });
      return;
    }

    if (!code.trim()) {
      setMessage({ type: "error", text: "Source code cannot be empty." });
      return;
    }

    setSubmitting(true);
    setMessage(null);
    try {
      const response = await submitCode({
        contestId,
        problemId: problem.id,
        language,
        code,
      });
      setMessage({
        type: "success",
        text: `Submission #${response.id} queued with verdict ${String(response.verdict ?? "PENDING")}.`,
      });
      onSubmitted?.();
    } catch (error) {
      setMessage({
        type: "error",
        text: error instanceof Error ? error.message : "Submission failed.",
      });
    } finally {
      setSubmitting(false);
    }
  };

  const lineNumbers = useMemo(() => {
    const count = Math.max(1, code.split("\n").length);
    return Array.from({ length: count }, (_, index) => index + 1).join("\n");
  }, [code]);

  const highlightedCode = useMemo(() => highlightCode(code, language), [code, language]);

  const handleEditorScroll = (event: UIEvent<HTMLTextAreaElement>) => {
    if (highlightRef.current) {
      highlightRef.current.scrollTop = event.currentTarget.scrollTop;
      highlightRef.current.scrollLeft = event.currentTarget.scrollLeft;
    }

    if (lineNumbersRef.current) {
      lineNumbersRef.current.scrollTop = event.currentTarget.scrollTop;
    }
  };

  if (!problem) {
    return (
      <section className="rounded-lg border border-slate-200 bg-white p-8 text-center shadow-sm">
        <Terminal className="mx-auto mb-3 h-8 w-8 text-slate-400" />
        <p className="font-medium text-slate-800">Select a problem before writing code.</p>
      </section>
    );
  }

  return (
    <section className="aura-code-card flex min-h-0 flex-1 flex-col overflow-hidden rounded-none border-0 bg-white shadow-none">
      <div className="shrink-0 border-b border-slate-200 bg-slate-50 p-4">
        <div className="flex flex-col gap-3 xl:flex-row xl:items-center xl:justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wide text-blue-700">Code</p>
            <h2 className="text-lg font-semibold text-slate-950">{problem.title}</h2>
          </div>

          <div className="flex flex-wrap items-center gap-2">
            <div className="inline-flex items-center gap-2 rounded-md border border-slate-200 bg-white px-3 py-2 text-xs text-slate-600">
              <Save className="h-4 w-4 text-blue-700" />
              {savedAt ? `Draft saved ${savedAt}` : "Draft autosaves"}
            </div>

            <Select value={language} onValueChange={handleLanguageChange}>
              <SelectTrigger className="w-40 bg-white">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {languageOptions.map((item) => (
                  <SelectItem key={item.value} value={item.value}>
                    {item.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>

            <Button
              onClick={handleSubmit}
              disabled={submitting || !contestId}
              className="gap-2 bg-blue-700 hover:bg-blue-800"
            >
              <Send className="h-4 w-4" />
              {submitting ? "Submitting..." : "Submit"}
            </Button>
          </div>
        </div>
      </div>

      {message && (
        <div
          className={`m-4 flex gap-2 rounded-lg border p-3 text-sm ${
            message.type === "success"
              ? "border-emerald-200 bg-emerald-50 text-emerald-700"
              : "border-rose-200 bg-rose-50 text-rose-700"
          }`}
        >
          {message.type === "success" ? <CheckCircle2 className="h-4 w-4" /> : <XCircle className="h-4 w-4" />}
          <span>{message.text}</span>
        </div>
      )}

      <div className="aura-code-shell grid min-h-0 flex-1 grid-cols-[3.25rem_1fr] overflow-hidden rounded-b-lg bg-slate-950">
        <pre
          ref={lineNumbersRef}
          className="select-none overflow-hidden border-r border-slate-800 bg-slate-900 px-3 py-4 text-right font-mono text-xs leading-6 text-slate-500"
        >
          {lineNumbers}
        </pre>
        <div className="aura-code-editor-layer relative min-h-0 overflow-hidden">
          <pre
            ref={highlightRef}
            aria-hidden="true"
            className="aura-code-highlight pointer-events-none absolute inset-0 overflow-auto px-4 py-4 font-mono text-sm leading-6"
            dangerouslySetInnerHTML={{ __html: `${highlightedCode}\n` }}
          />
          <textarea
            value={code}
            onChange={(e) => setCode(e.target.value)}
            onScroll={handleEditorScroll}
            className="aura-code-input relative z-10 h-full min-h-0 w-full resize-none border-0 bg-transparent px-4 py-4 font-mono text-sm leading-6 shadow-none outline-none"
            placeholder="Write your solution here..."
            spellCheck={false}
            wrap="off"
          />
        </div>
      </div>
    </section>
  );
}
