package com.server.contestControl.contestServer.controller;

import com.server.contestControl.authServer.config.SecurityConfiguration;
import com.server.contestControl.authServer.enums.TokenType;
import com.server.contestControl.authServer.filter.JwtAuthFilter;
import com.server.contestControl.authServer.service.jwt.core.JwtService;
import com.server.contestControl.authServer.service.user.UserService;
import com.server.contestControl.contestServer.dto.BulkTeamGenerationRequest;
import com.server.contestControl.contestServer.dto.GeneratedTeamCredentialResponse;
import com.server.contestControl.submissionServer.service.submission.SubmissionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@Import({SecurityConfiguration.class, JwtAuthFilter.class})
class AdminControllerBulkTeamGenerationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private SubmissionService submissionService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void nonAdminCannotGenerateTeamAccounts() throws Exception {
        mockBearerUser("team-token", "team", "ROLE_TEAM");

        mockMvc.perform(post("/api/admin/users/bulk-generate-teams")
                        .header("Authorization", "Bearer team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isForbidden());

        verify(userService, never()).generateTeamAccounts(any(BulkTeamGenerationRequest.class));
    }

    @Test
    void adminCanGenerateTeamAccounts() throws Exception {
        mockBearerUser("admin-token", "admin", "ROLE_ADMIN");
        when(userService.generateTeamAccounts(any(BulkTeamGenerationRequest.class)))
                .thenReturn(List.of(new GeneratedTeamCredentialResponse("team1", "Abc123xYz9", "TEAM")));

        mockMvc.perform(post("/api/admin/users/bulk-generate-teams")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username").value("team1"))
                .andExpect(jsonPath("$[0].password").value("Abc123xYz9"))
                .andExpect(jsonPath("$[0].role").value("TEAM"));

        verify(userService).generateTeamAccounts(any(BulkTeamGenerationRequest.class));
    }

    private void mockBearerUser(String token, String username, String authority) {
        UserDetails user = org.springframework.security.core.userdetails.User
                .withUsername(username)
                .password("password")
                .authorities(authority)
                .build();

        when(jwtService.extractUsername(token, TokenType.ACCESS)).thenReturn(username);
        when(userDetailsService.loadUserByUsername(username)).thenReturn(user);
        when(jwtService.isTokenValid(token, user, TokenType.ACCESS)).thenReturn(true);
    }

    private String requestJson() {
        return """
                {
                  "prefix": "team",
                  "startNumber": 1,
                  "endNumber": 1,
                  "passwordLength": 10
                }
                """;
    }
}
