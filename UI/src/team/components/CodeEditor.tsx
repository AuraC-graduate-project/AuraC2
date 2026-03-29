import { useEffect, useState } from "react";
import { Button } from "./ui/button";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "./ui/select";
import { Textarea } from "./ui/textarea";
import { Send } from "lucide-react";
import { submitCode } from "../services/teamApi";

/* ================= STARTER CODE (BACKEND-ALIGNED) ================= */

/* ================= STARTER CODE (PURE FAST-I/O SETUP) ================= */

const STARTER_CODE: Record<string, (title: string) => string> = {
  /* ---------- C (ID 50) ---------- */
  c: (title) => `#include <stdio.h>

int main() {
    // ${title}

    // write your solution here

    return 0;
}
`,

  /* ---------- C++17 (ID 54) ---------- */
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

  /* ---------- Java 11 (ID 62) ---------- */
  java: (title) => `import java.io.*;
import java.util.*;

public class Main {
    static final FastScanner fs = new FastScanner(System.in);

    public static void main(String[] args) throws Exception {
        // ${title}

        // write your solution here
    }

    static class FastScanner {
        private final byte[] buffer = new byte[1 << 16];
        private int ptr = 0, len = 0;
        private final InputStream in;

        FastScanner(InputStream in) {
            this.in = in;
        }

        private int read() throws IOException {
            if (ptr >= len) {
                len = in.read(buffer);
                ptr = 0;
                if (len <= 0) return -1;
            }
            return buffer[ptr++];
        }

        int nextInt() throws IOException {
            int c, sgn = 1, res = 0;
            do c = read(); while (c <= ' ');
            if (c == '-') { sgn = -1; c = read(); }
            while (c > ' ') {
                res = res * 10 + (c - '0');
                c = read();
            }
            return res * sgn;
        }
    }
}
`,

  /* ---------- Python 3 (ID 71) ---------- */
  python: (title) => `# ${title}
import sys
input = sys.stdin.readline

def main():
    # write your solution here
    pass

if __name__ == "__main__":
    main()
`,

  /* ---------- JavaScript / Node.js (ID 63) ---------- */
  javascript: (title) => `// ${title}
'use strict';

const fs = require('fs');
const input = fs.readFileSync(0, 'utf8').trim().split(/\\s+/);
let idx = 0;

// write your solution here
`,

  /* ---------- Go (ID 60) ---------- */
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

    // ${title}

    // write your solution here
}
`,
};


/* ================= COMPONENT ================= */

type Props = {
  contestId: number;
  problem: { id: number; title: string } | null;
};

export function CodeEditor({ contestId, problem }: Props) {
  const [language, setLanguage] = useState("cpp");
  const [code, setCode] = useState("");

  /* ===== Reset starter code on problem or language change ===== */
  useEffect(() => {
    if (!problem) return;
    setCode(STARTER_CODE[language](problem.title));
  }, [problem, language]);

  if (!problem) {
    return (
      <div className="flex flex-1 items-center justify-center text-gray-500 bg-white rounded-lg border border-gray-200 shadow-sm">
        Select a problem
      </div>
    );
  }

  const handleSubmit = async () => {
    await submitCode({
      contestId,
      problemId: problem.id,
      language, // MUST match backend convertLanguage()
      code,
    });
    alert("Submitted");
  };

  return (
    <div className="flex flex-col h-full bg-white rounded-lg border border-gray-200 shadow-sm">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-gray-200 bg-gray-50">
        <h3 className="text-gray-900">{problem.title}</h3>

        <div className="flex items-center gap-3">
          <Select value={language} onValueChange={setLanguage}>
            <SelectTrigger className="w-44">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="c">C</SelectItem>
              <SelectItem value="cpp">C++17</SelectItem>
              <SelectItem value="java">Java 11</SelectItem>
              <SelectItem value="python">Python 3</SelectItem>
              <SelectItem value="javascript">JavaScript</SelectItem>
              <SelectItem value="go">Go</SelectItem>
            </SelectContent>
          </Select>

          <Button
            onClick={handleSubmit}
            className="bg-[#FACC15] hover:bg-[#F4C000] text-gray-900"
          >
            <Send className="w-4 h-4 mr-2" />
            Submit
          </Button>
        </div>
      </div>

      {/* Editor */}
      <div className="flex-1 p-0">
        <Textarea
          value={code}
          onChange={(e) => setCode(e.target.value)}
          className="w-full h-full min-h-[400px] font-mono resize-none border-0 focus-visible:ring-0 rounded-none"
          placeholder="Write your code here..."
        />
      </div>
    </div>
  );
}
