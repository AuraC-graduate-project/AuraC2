package com.server.contestControl.submissionServer.language;

public record SupportedLanguageResponse(
        String value,
        String label,
        int judge0LanguageId,
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
    public static SupportedLanguageResponse from(SupportedLanguage language) {
        return new SupportedLanguageResponse(
                language.value(),
                language.label(),
                language.judge0LanguageId(),
                language.supportsSubmission(),
                language.supportsReferenceSolution(),
                language.supportsGenerator(),
                language.supportsInputValidator(),
                language.supportsChecker(),
                language.referenceFileName(),
                language.generatorFileName(),
                language.validatorFileName(),
                language.checkerFileName(),
                language.entryPoint(),
                language.runtimeNotes(),
                language.verificationNotes()
        );
    }
}
