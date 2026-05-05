package com.server.contestControl.submissionServer.service.callback;

import com.server.contestControl.submissionServer.dto.Judge0Response;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/callback/judge0")
@RequiredArgsConstructor
public class CallbackHandler {

    private final Judge0CallbackService callbackService;


    @PutMapping("/{submissionId}/{judgeRunId}/{testCaseNumber}")
    public ResponseEntity<?> handleJudge0Callback(
            @PathVariable Long submissionId,
            @PathVariable Long judgeRunId,
            @PathVariable int testCaseNumber,
            @RequestBody Judge0Response response
    ) {
        return callbackService.handleJudge0Callback(submissionId, judgeRunId, testCaseNumber, response);
    }
}
