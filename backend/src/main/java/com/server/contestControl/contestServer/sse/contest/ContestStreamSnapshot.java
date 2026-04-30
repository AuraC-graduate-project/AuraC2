package com.server.contestControl.contestServer.sse.contest;

import com.server.contestControl.contestServer.dto.contest.ContestResponse;
import lombok.Builder;

import java.util.List;

@Builder
public record ContestStreamSnapshot(
        ContestResponse active,
        ContestResponse upcoming,
        ContestResponse paused,
        List<ContestResponse> ended
) { }
