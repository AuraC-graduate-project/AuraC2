package com.server.contestControl.contestServer.oracle.controller;

import com.server.contestControl.contestServer.dto.testcase.TestCaseResponse;
import com.server.contestControl.contestServer.oracle.dto.CounterexampleResponse;
import com.server.contestControl.contestServer.oracle.dto.GeneratedTestBatchRequest;
import com.server.contestControl.contestServer.oracle.dto.GeneratedTestBatchResponse;
import com.server.contestControl.contestServer.oracle.dto.GeneratedTestPromotionRequest;
import com.server.contestControl.contestServer.oracle.dto.GeneratedTestPromotionResponse;
import com.server.contestControl.contestServer.oracle.dto.OracleProgramRequest;
import com.server.contestControl.contestServer.oracle.dto.OracleProgramResponse;
import com.server.contestControl.contestServer.oracle.dto.OracleProgramSourceResponse;
import com.server.contestControl.contestServer.oracle.service.OracleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/oracle")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class OracleAdminController {

    private final OracleService oracleService;

    @PostMapping("/problems/{problemId}/reference-solution")
    public ResponseEntity<OracleProgramResponse> configureReferenceSolution(
            @PathVariable Long problemId,
            @Valid @RequestBody OracleProgramRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                oracleService.configureReferenceSolution(problemId, request, authentication.getName())
        );
    }

    @GetMapping("/problems/{problemId}/reference-solutions")
    public ResponseEntity<List<OracleProgramResponse>> referenceSolutions(@PathVariable Long problemId) {
        return ResponseEntity.ok(oracleService.referenceSolutions(problemId));
    }

    @GetMapping("/reference-solutions/{referenceSolutionId}/source")
    public ResponseEntity<OracleProgramSourceResponse> referenceSolutionSource(@PathVariable Long referenceSolutionId) {
        return ResponseEntity.ok(oracleService.referenceSolutionSource(referenceSolutionId));
    }

    @PostMapping("/problems/{problemId}/input-generator")
    public ResponseEntity<OracleProgramResponse> configureInputGenerator(
            @PathVariable Long problemId,
            @Valid @RequestBody OracleProgramRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                oracleService.configureInputGenerator(problemId, request, authentication.getName())
        );
    }

    @GetMapping("/problems/{problemId}/input-generators")
    public ResponseEntity<List<OracleProgramResponse>> inputGenerators(@PathVariable Long problemId) {
        return ResponseEntity.ok(oracleService.inputGenerators(problemId));
    }

    @GetMapping("/input-generators/{inputGeneratorId}/source")
    public ResponseEntity<OracleProgramSourceResponse> inputGeneratorSource(@PathVariable Long inputGeneratorId) {
        return ResponseEntity.ok(oracleService.inputGeneratorSource(inputGeneratorId));
    }

    @PostMapping("/problems/{problemId}/input-validator")
    public ResponseEntity<OracleProgramResponse> configureInputValidator(
            @PathVariable Long problemId,
            @Valid @RequestBody OracleProgramRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                oracleService.configureInputValidator(problemId, request, authentication.getName())
        );
    }

    @GetMapping("/problems/{problemId}/input-validators")
    public ResponseEntity<List<OracleProgramResponse>> inputValidators(@PathVariable Long problemId) {
        return ResponseEntity.ok(oracleService.inputValidators(problemId));
    }

    @GetMapping("/input-validators/{inputValidatorId}/source")
    public ResponseEntity<OracleProgramSourceResponse> inputValidatorSource(@PathVariable Long inputValidatorId) {
        return ResponseEntity.ok(oracleService.inputValidatorSource(inputValidatorId));
    }

    @GetMapping("/problems/{problemId}/custom-output-validator/source")
    public ResponseEntity<OracleProgramSourceResponse> customOutputValidatorSource(@PathVariable Long problemId) {
        return ResponseEntity.ok(oracleService.customOutputValidatorSource(problemId));
    }

    @PostMapping("/problems/{problemId}/generated-batches")
    public ResponseEntity<GeneratedTestBatchResponse> createGeneratedBatch(
            @PathVariable Long problemId,
            @Valid @RequestBody GeneratedTestBatchRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                oracleService.createGeneratedTestBatch(problemId, request, authentication.getName())
        );
    }

    @GetMapping("/problems/{problemId}/generated-batches")
    public ResponseEntity<List<GeneratedTestBatchResponse>> generatedBatches(@PathVariable Long problemId) {
        return ResponseEntity.ok(oracleService.batches(problemId));
    }

    @GetMapping("/generated-batches/{batchId}")
    public ResponseEntity<GeneratedTestBatchResponse> generatedBatch(@PathVariable Long batchId) {
        return ResponseEntity.ok(oracleService.getBatch(batchId));
    }

    @GetMapping("/problems/{problemId}/counterexamples")
    public ResponseEntity<List<CounterexampleResponse>> counterexamples(@PathVariable Long problemId) {
        return ResponseEntity.ok(oracleService.counterexamples(problemId));
    }

    @PostMapping("/counterexamples/{counterexampleId}/promote")
    public ResponseEntity<TestCaseResponse> promoteCounterexample(@PathVariable Long counterexampleId) {
        return ResponseEntity.ok(oracleService.promoteCounterexample(counterexampleId));
    }

    @PostMapping("/generated-test-cases/{generatedTestCaseId}/promote")
    public ResponseEntity<GeneratedTestPromotionResponse> promoteGeneratedTestCase(@PathVariable Long generatedTestCaseId) {
        return ResponseEntity.ok(oracleService.promoteGeneratedTestCase(generatedTestCaseId));
    }

    @PostMapping("/generated-test-cases/promote-selected")
    public ResponseEntity<GeneratedTestPromotionResponse> promoteSelectedGeneratedTestCases(
            @Valid @RequestBody GeneratedTestPromotionRequest request
    ) {
        return ResponseEntity.ok(
                oracleService.promoteGeneratedTestCases(request.getGeneratedTestCaseIds())
        );
    }

    @PostMapping("/generated-batches/{batchId}/promote-valid")
    public ResponseEntity<GeneratedTestPromotionResponse> promoteAllValidGeneratedTestCases(@PathVariable Long batchId) {
        return ResponseEntity.ok(oracleService.promoteAllValidGeneratedTestCases(batchId));
    }
}
