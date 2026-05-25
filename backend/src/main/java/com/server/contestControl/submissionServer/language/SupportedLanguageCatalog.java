package com.server.contestControl.submissionServer.language;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class SupportedLanguageCatalog {

    private static final List<SupportedLanguage> LANGUAGES = List.of(
            new SupportedLanguage(
                    "c",
                    "C",
                    50,
                    Set.of("gcc", "c11"),
                    true,
                    true,
                    true,
                    true,
                    true,
                    "reference.c",
                    "generator.c",
                    "validator.c",
                    "checker.c",
                    "int main(void)",
                    "Use standard C stdin/stdout only. Avoid compiler-specific extensions unless AuraC2 adds them.",
                    "Check fixed-width integer ranges carefully and avoid signed overflow."
            ),
            new SupportedLanguage(
                    "cpp",
                    "C++17",
                    54,
                    Set.of("c++", "g++", "cpp17", "c++17"),
                    true,
                    true,
                    true,
                    true,
                    true,
                    "reference.cpp",
                    "generator.cpp",
                    "validator.cpp",
                    "checker.cpp",
                    "int main()",
                    "Use C++17 and standard stdin/stdout. Include fast I/O for large inputs.",
                    "Check 64-bit arithmetic, memory bounds, and deterministic pseudo-random generation."
            ),
            new SupportedLanguage(
                    "java",
                    "Java",
                    62,
                    Set.of("java17"),
                    true,
                    true,
                    true,
                    true,
                    true,
                    "Main.java",
                    "Main.java",
                    "Main.java",
                    "Main.java",
                    "public class Main",
                    "Use a public Main class because AuraC2 runs Java through Judge0 single-file conventions.",
                    "Use long for large integer arithmetic, buffered I/O, and avoid package declarations."
            ),
            new SupportedLanguage(
                    "python",
                    "Python 3",
                    71,
                    Set.of("py", "python3"),
                    true,
                    true,
                    true,
                    true,
                    true,
                    "reference.py",
                    "generator.py",
                    "validator.py",
                    "checker.py",
                    "if __name__ == \"__main__\"",
                    "Use Python 3 stdin/stdout. Keep algorithms within time limits for interpreted execution.",
                    "Check recursion depth, integer performance, and deterministic random seeding."
            ),
            new SupportedLanguage(
                    "javascript",
                    "JavaScript",
                    63,
                    Set.of("js", "node", "nodejs"),
                    true,
                    true,
                    true,
                    true,
                    true,
                    "reference.js",
                    "generator.js",
                    "validator.js",
                    "checker.js",
                    "Node.js script",
                    "Use Node.js and read stdin with fs.readFileSync(0, 'utf8').",
                    "Watch number precision; use BigInt when constraints exceed safe integer range."
            ),
            new SupportedLanguage(
                    "go",
                    "Go",
                    60,
                    Set.of("golang"),
                    true,
                    true,
                    true,
                    true,
                    true,
                    "main.go",
                    "main.go",
                    "main.go",
                    "main.go",
                    "package main with func main()",
                    "Use package main and standard bufio stdin/stdout.",
                    "Check int size assumptions, prefer int64 for large arithmetic, and flush buffered output."
            )
    );

    private SupportedLanguageCatalog() {
    }

    public static List<SupportedLanguage> languages() {
        return LANGUAGES;
    }

    public static Optional<SupportedLanguage> findByValueOrAlias(String value) {
        return LANGUAGES.stream()
                .filter(language -> language.matchesValueOrAlias(value))
                .findFirst();
    }

    public static Optional<SupportedLanguage> findByJudge0LanguageId(Integer judge0LanguageId) {
        if (judge0LanguageId == null) {
            return Optional.empty();
        }
        return LANGUAGES.stream()
                .filter(language -> language.judge0LanguageId() == judge0LanguageId)
                .findFirst();
    }
}
