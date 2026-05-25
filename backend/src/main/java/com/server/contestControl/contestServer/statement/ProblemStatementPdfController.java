package com.server.contestControl.contestServer.statement;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/problem-exports")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class ProblemStatementPdfController {

    private final ProblemStatementPdfService problemStatementPdfService;

    @GetMapping("/problems/{problemId}/statement.pdf")
    public ResponseEntity<byte[]> problemStatementPdf(@PathVariable Long problemId) {
        return pdfResponse(problemStatementPdfService.problemPdf(problemId));
    }

    @GetMapping("/contests/{contestId}/booklet.pdf")
    public ResponseEntity<byte[]> contestBookletPdf(@PathVariable Long contestId) {
        return pdfResponse(problemStatementPdfService.contestBookletPdf(contestId));
    }

    private ResponseEntity<byte[]> pdfResponse(ProblemStatementPdfService.PdfExport export) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(export.filename()).build().toString())
                .body(export.bytes());
    }
}
