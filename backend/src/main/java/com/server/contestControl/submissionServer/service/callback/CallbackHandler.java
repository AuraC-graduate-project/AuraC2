package com.server.contestControl.submissionServer.service.callback;

import com.server.contestControl.submissionServer.dto.Judge0Response;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/callback/judge0")
@RequiredArgsConstructor
public class CallbackHandler {

    private final Judge0CallbackService callbackService;
    private final Judge0CallbackSignatureService callbackSignatureService;


    @PutMapping("/{submissionId}/{judgeRunId}/{testCaseNumber}")
    public ResponseEntity<?> handleJudge0Callback(
            @PathVariable Long submissionId,
            @PathVariable Long judgeRunId,
            @PathVariable int testCaseNumber,
            @RequestParam(name = "signature", required = false) String signature,
            @RequestBody Judge0Response response
    ) {
        if (!callbackSignatureService.isValid(submissionId, judgeRunId, testCaseNumber, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid callback signature");
        }

        return callbackService.handleJudge0Callback(submissionId, judgeRunId, testCaseNumber, response);
    }

    @PutMapping("/{submissionId}/{testCaseNumber}")
    public ResponseEntity<?> handleLegacyJudge0Callback(
            @PathVariable Long submissionId,
            @PathVariable int testCaseNumber,
            @RequestParam(name = "signature", required = false) String signature,
            @RequestBody Judge0Response response
    ) {
        if (!callbackSignatureService.isValid(submissionId, 0L, testCaseNumber, signature)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid callback signature");
        }

        return callbackService.handleJudge0Callback(submissionId, null, testCaseNumber, response);
    }
}
