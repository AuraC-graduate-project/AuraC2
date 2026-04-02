package com.server.contestControl.submissionServer.service.callback;


import com.server.contestControl.contestServer.repository.TestCaseRepository;
import com.server.contestControl.submissionServer.dto.Judge0Response;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.enums.Verdict;
import com.server.contestControl.submissionServer.repository.SubmissionRepository;
import com.server.contestControl.submissionServer.service.submission.SubmissionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/callback/judge0")
@RequiredArgsConstructor
@Slf4j
public class CallbackHandler {
    private final SubmissionRepository submissionRepository;
    private final TestCaseRepository testCaseRepository;

    @PutMapping("/{submissionId}/{testCaseNumber}")
    public ResponseEntity<?> handleJudge0Callback(
            @PathVariable Long submissionId,
            @PathVariable int testCaseNumber,
            @RequestBody Judge0Response response
    ) {
        Submission submission = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new RuntimeException("Submission not found"));

        Verdict verdict = Verdict.fromJudge0Status(response.getStatus().getId());

        if (verdict != Verdict.ACCEPTED) {
            submission.setVerdict(verdict);
            submission.setExecutionTime(response.getTimeAsInt());
            submission.setMemoryUsage(response.getMemoryAsInt());
            submissionRepository.save(submission);

            return ResponseEntity.ok(
                    "Failed on Test Case " + testCaseNumber
                            + " → Verdict = " + verdict
            );
        }

        if (testCaseNumber == testCaseRepository.countByProblemId(submission.getProblem().getId())) {
            submission.setVerdict(Verdict.ACCEPTED);
            submission.setExecutionTime(response.getTimeAsInt());
            submission.setMemoryUsage(response.getMemoryAsInt());
            submissionRepository.save(submission);
            return ResponseEntity.ok("All test cases passed!");
        }

        return ResponseEntity.ok("Test Case " + testCaseNumber + " passed");
    }
}