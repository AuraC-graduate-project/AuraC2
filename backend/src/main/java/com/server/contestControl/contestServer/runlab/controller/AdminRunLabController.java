package com.server.contestControl.contestServer.runlab.controller;

import com.server.contestControl.contestServer.runlab.dto.AdminRunLabRequest;
import com.server.contestControl.contestServer.runlab.dto.AdminRunLabResponse;
import com.server.contestControl.contestServer.runlab.service.AdminRunLabService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/run-lab")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminRunLabController {

    private final AdminRunLabService runLabService;

    @PostMapping("/run")
    public AdminRunLabResponse run(@RequestBody AdminRunLabRequest request, Authentication authentication) {
        return runLabService.run(request, authentication.getName());
    }
}
