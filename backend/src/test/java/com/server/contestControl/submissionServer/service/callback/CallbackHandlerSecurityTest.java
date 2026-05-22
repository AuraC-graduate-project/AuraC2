package com.server.contestControl.submissionServer.service.callback;

import com.server.contestControl.authServer.config.SecurityConfiguration;
import com.server.contestControl.authServer.filter.JwtAuthFilter;
import com.server.contestControl.authServer.service.jwt.core.JwtService;
import com.server.contestControl.submissionServer.dto.Judge0Response;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CallbackHandler.class)
@Import({SecurityConfiguration.class, JwtAuthFilter.class})
class CallbackHandlerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Judge0CallbackService callbackService;

    @MockBean
    private Judge0CallbackSignatureService callbackSignatureService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void callbackEndpointRemainsPublicButRejectsMissingSignature() throws Exception {
        mockMvc.perform(put("/api/callback/judge0/1/7/2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(judge0ResponseJson()))
                .andExpect(status().isUnauthorized());

        verify(callbackService, never()).handleJudge0Callback(any(), any(), anyInt(), any());
    }

    @Test
    void callbackEndpointRejectsInvalidSignatureBeforeStateMutation() throws Exception {
        when(callbackSignatureService.isValid(1L, 7L, 2, "bad")).thenReturn(false);

        mockMvc.perform(put("/api/callback/judge0/1/7/2")
                        .queryParam("signature", "bad")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(judge0ResponseJson()))
                .andExpect(status().isUnauthorized());

        verify(callbackService, never()).handleJudge0Callback(any(), any(), anyInt(), any());
    }

    @Test
    void callbackEndpointAcceptsValidSignatureWithoutJwt() throws Exception {
        when(callbackSignatureService.isValid(1L, 7L, 2, "good")).thenReturn(true);
        ResponseEntity<?> okResponse = ResponseEntity.ok("ok");
        doReturn(okResponse)
                .when(callbackService)
                .handleJudge0Callback(eq(1L), eq(7L), eq(2), any(Judge0Response.class));

        mockMvc.perform(put("/api/callback/judge0/1/7/2")
                        .queryParam("signature", "good")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(judge0ResponseJson()))
                .andExpect(status().isOk());

        verify(callbackService).handleJudge0Callback(eq(1L), eq(7L), eq(2), any(Judge0Response.class));
    }

    private String judge0ResponseJson() {
        return """
                {
                  "status": { "id": 3 },
                  "time": "0.010",
                  "memory": 1024
                }
                """;
    }
}
