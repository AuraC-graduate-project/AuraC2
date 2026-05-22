package com.server.contestControl.submissionServer.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.contestServer.entity.Contest;
import com.server.contestControl.contestServer.entity.Problem;
import com.server.contestControl.submissionServer.entity.Submission;
import com.server.contestControl.submissionServer.entity.SubmissionJudgeResult;
import com.server.contestControl.submissionServer.enums.Verdict;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SubmissionResponseTest {

    @Test
    void exposesSafeJudgeAuditFieldsWithoutHiddenExpectedOutput() throws Exception {
        Contest contest = Contest.builder().id(20L).build();
        Problem problem = Problem.builder()
                .id(10L)
                .contest(contest)
                .title("Sum")
                .build();
        User team = User.builder()
                .id(30L)
                .username("team30")
                .role(Role.TEAM)
                .build();
        Submission submission = Submission.builder()
                .id(40L)
                .contest(contest)
                .problem(problem)
                .user(team)
                .language("java")
                .code("class Main {}")
                .verdict(Verdict.COMPILATION_ERROR)
                .executionTime(12)
                .memoryUsage(1024)
                .judgeRunId(3L)
                .build();
        SubmissionJudgeResult result = SubmissionJudgeResult.builder()
                .submission(submission)
                .judgeRunId(3L)
                .testCaseNumber(1)
                .judge0StatusId(6)
                .judge0StatusDescription("Compilation Error")
                .verdict(Verdict.COMPILATION_ERROR)
                .executionTime(12)
                .memoryUsage(1024)
                .diagnostic("Main.java:1: error")
                .build();

        SubmissionResponse response = SubmissionResponse.fromEntity(submission, List.of(result));
        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(response.judgeRunId()).isEqualTo(3L);
        assertThat(response.judgeResults()).hasSize(1);
        assertThat(response.judgeResults().getFirst().judge0StatusId()).isEqualTo(6);
        assertThat(response.judgeResults().getFirst().judge0StatusDescription())
                .isEqualTo("Compilation Error");
        assertThat(response.judgeResults().getFirst().diagnostic())
                .isEqualTo("Main.java:1: error");
        assertThat(json).contains("judgeResults", "Compilation Error", "Main.java:1: error");
        assertThat(json).doesNotContain("expectedOutput", "expected_output", "hidden answer");
    }
}
