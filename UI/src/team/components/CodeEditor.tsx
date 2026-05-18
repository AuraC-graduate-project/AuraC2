import { useCallback, useEffect, useMemo, useRef, useState, type KeyboardEvent, type UIEvent } from "react";
import { Send, Terminal, XCircle } from "lucide-react";
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

const TAB_INDENT = "    ";

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

function getLanguageKey(userId: string, contestId: string, problemId: string): string {
  return `draft_language_${userId}_${contestId}_${problemId}`;
}

function loadStoredLanguage(userId: string, contestId: string, problemId: string): string {
  if (problemId === "0") return "cpp";
  try {
    const stored = localStorage.getItem(getLanguageKey(userId, contestId, problemId));
    return languageOptions.some((option) => option.value === stored) ? stored! : "cpp";
  } catch {
    return "cpp";
  }
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

function selectedLineRange(value: string, selectionStart: number, selectionEnd: number) {
  const lineStart = value.lastIndexOf("\n", Math.max(0, selectionStart - 1)) + 1;
  const adjustedEnd =
    selectionEnd > selectionStart && value[selectionEnd - 1] === "\n"
      ? selectionEnd - 1
      : selectionEnd;
  const nextLineBreak = value.indexOf("\n", adjustedEnd);
  const lineEnd = nextLineBreak === -1 ? value.length : nextLineBreak;

  return { lineStart, lineEnd };
}

function indentSelection(value: string, selectionStart: number, selectionEnd: number) {
  if (selectionStart === selectionEnd) {
    return {
      nextCode: `${value.slice(0, selectionStart)}${TAB_INDENT}${value.slice(selectionEnd)}`,
      nextStart: selectionStart + TAB_INDENT.length,
      nextEnd: selectionStart + TAB_INDENT.length,
    };
  }

  const { lineStart, lineEnd } = selectedLineRange(value, selectionStart, selectionEnd);
  const selectedLines = value.slice(lineStart, lineEnd);
  const lineCount = selectedLines.split("\n").length;
  const indented = selectedLines
    .split("\n")
    .map((line) => `${TAB_INDENT}${line}`)
    .join("\n");

  return {
    nextCode: `${value.slice(0, lineStart)}${indented}${value.slice(lineEnd)}`,
    nextStart: selectionStart + TAB_INDENT.length,
    nextEnd: selectionEnd + TAB_INDENT.length * lineCount,
  };
}

function removableIndentWidth(line: string): number {
  if (line.startsWith(TAB_INDENT)) return TAB_INDENT.length;
  if (line.startsWith("\t")) return 1;
  return line.match(/^ {1,3}/)?.[0].length ?? 0;
}

function unindentSelection(value: string, selectionStart: number, selectionEnd: number) {
  const { lineStart, lineEnd } = selectedLineRange(value, selectionStart, selectionEnd);
  const selectedLines = value.slice(lineStart, lineEnd).split("\n");
  let originalOffset = 0;
  let removedBeforeStart = 0;
  let removedBeforeEnd = 0;

  const unindented = selectedLines
    .map((line) => {
      const removeCount = removableIndentWidth(line);
      const lineAbsoluteStart = lineStart + originalOffset;

      removedBeforeStart += Math.min(removeCount, Math.max(0, selectionStart - lineAbsoluteStart));
      removedBeforeEnd += Math.min(removeCount, Math.max(0, selectionEnd - lineAbsoluteStart));
      originalOffset += line.length + 1;

      return line.slice(removeCount);
    })
    .join("\n");

  return {
    nextCode: `${value.slice(0, lineStart)}${unindented}${value.slice(lineEnd)}`,
    nextStart: selectionStart - removedBeforeStart,
    nextEnd: selectionEnd - removedBeforeEnd,
  };
}

export function CodeEditor({ contestId, problem, onSubmitted }: Props) {
  const userId = useMemo(getUserId, []);
  const contestKey = contestId ? String(contestId) : "0";
  const problemKey = problem?.id ? String(problem.id) : "0";
  const [language, setLanguage] = useState(() =>
    loadStoredLanguage(userId, contestKey, problemKey)
  );
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<{ text: string } | null>(null);
  const highlightRef = useRef<HTMLPreElement | null>(null);
  const lineNumbersRef = useRef<HTMLPreElement | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);

  useEffect(() => {
    setLanguage(loadStoredLanguage(userId, contestKey, problemKey));
  }, [userId, contestKey, problemKey]);

  useEffect(() => {
    if (problemKey === "0") return;
    try {
      localStorage.setItem(getLanguageKey(userId, contestKey, problemKey), language);
    } catch {
      // Language persistence is a convenience; code drafts remain separately keyed.
    }
  }, [userId, contestKey, problemKey, language]);

  const starterCode = useMemo(() => {
    if (!problem) return "";
    return (STARTER_CODE[language] ?? STARTER_CODE.cpp)(problem.title);
  }, [language, problem?.id, problem?.title]);

  const { code, setCode } = useCodeDraft(
    userId,
    contestKey,
    problemKey,
    language,
    starterCode
  );

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
      await submitCode({
        contestId,
        problemId: problem.id,
        language,
        code,
      });
      onSubmitted?.();
    } catch (error) {
      setMessage({
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

  const handleEditorKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key !== "Tab") return;

    event.preventDefault();

    const textarea = event.currentTarget;
    const selectionStart = textarea.selectionStart;
    const selectionEnd = textarea.selectionEnd;
    const edit = event.shiftKey
      ? unindentSelection(code, selectionStart, selectionEnd)
      : indentSelection(code, selectionStart, selectionEnd);

    setCode(edit.nextCode);
    requestAnimationFrame(() => {
      textareaRef.current?.setSelectionRange(edit.nextStart, edit.nextEnd);
    });
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
            <p className="text-xs font-semibold uppercase text-blue-700">Code</p>
            <h2 className="text-lg font-semibold text-slate-950">{problem.title}</h2>
          </div>

          <div className="flex flex-wrap items-center gap-2">
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
        <div className="m-4 flex gap-2 rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-700">
          <XCircle className="h-4 w-4" />
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
            ref={textareaRef}
            value={code}
            onChange={(e) => setCode(e.target.value)}
            onKeyDown={handleEditorKeyDown}
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
