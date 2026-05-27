package com.server.contestControl.submissionServer.service.judge;

import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.contestServer.enums.ComparePolicy;
import org.springframework.stereotype.Service;

@Service
public class Judge0ExpectedOutputPolicy {

    public String expectedOutputForJudge0(Problem problem, String expectedOutput) {
        return usesJudge0ExpectedOutput(problem) ? expectedOutput : null;
    }

    public boolean usesJudge0ExpectedOutput(Problem problem) {
        if (problem != null && problem.hasActiveCustomValidator()) {
            return false;
        }

        return problem == null
                || problem.getComparePolicy() == null
                || problem.getComparePolicy() == ComparePolicy.EXACT;
    }
}
