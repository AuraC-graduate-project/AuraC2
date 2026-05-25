package com.server.contestControl.submissionServer.language;

import java.util.Set;

public record SupportedLanguage(
        String value,
        String label,
        int judge0LanguageId,
        Set<String> aliases,
        boolean supportsSubmission,
        boolean supportsReferenceSolution,
        boolean supportsGenerator,
        boolean supportsInputValidator,
        boolean supportsChecker,
        String referenceFileName,
        String generatorFileName,
        String validatorFileName,
        String checkerFileName,
        String entryPoint,
        String runtimeNotes,
        String verificationNotes
) {
    public boolean matchesValueOrAlias(String candidate) {
        if (candidate == null) {
            return false;
        }
        String normalized = candidate.trim().toLowerCase();
        return value.equalsIgnoreCase(normalized)
                || aliases.stream().anyMatch(alias -> alias.equalsIgnoreCase(normalized));
    }
}
