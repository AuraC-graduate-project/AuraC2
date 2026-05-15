package com.server.contestControl.authServer.controller;

import com.server.contestControl.authServer.config.SecurityConfiguration;
import com.server.contestControl.authServer.dto.register.RegisterRequest;
import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.authServer.enums.TokenType;
import com.server.contestControl.authServer.filter.JwtAuthFilter;
import com.server.contestControl.authServer.service.auth.AuthFacade;
import com.server.contestControl.authServer.service.jwt.core.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfiguration.class, JwtAuthFilter.class})
class AuthControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthFacade authFacade;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserDetailsService userDetailsService;

    @Test
    void methodSecurityIsEnabled() {
        assertThat(SecurityConfiguration.class.getAnnotation(EnableMethodSecurity.class))
                .isNotNull();
    }

    @Test
    void registrationRequiresAdminRoleAtMethodLevel() throws NoSuchMethodException {
        Method register = AuthController.class.getMethod("register", RegisterRequest.class);

        PreAuthorize preAuthorize = register.getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasRole('ADMIN')");
    }

    @Test
    void usersExposeRolePrefixedAuthorities() {
        User admin = User.builder().role(Role.ADMIN).build();
        User team = User.builder().role(Role.TEAM).build();

        assertThat(admin.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
        assertThat(team.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_TEAM");
    }

    @Test
    void unauthenticatedRegisterIsRejected() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson()))
                .andExpect(status().isUnauthorized());

        verify(authFacade, never()).registerTeam("team01", "secret");
    }

    @Test
    void teamAuthenticatedRegisterIsRejected() throws Exception {
        mockBearerUser("team-token", "team", "ROLE_TEAM");

        mockMvc.perform(post("/auth/register")
                        .header("Authorization", "Bearer team-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson()))
                .andExpect(status().isForbidden());

        verify(authFacade, never()).registerTeam("team01", "secret");
    }

    @Test
    void adminAuthenticatedRegisterSucceeds() throws Exception {
        mockBearerUser("admin-token", "admin", "ROLE_ADMIN");

        mockMvc.perform(post("/auth/register")
                        .header("Authorization", "Bearer admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerRequestJson()))
                .andExpect(status().isOk());

        verify(authFacade).registerTeam("team01", "secret");
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

    private String registerRequestJson() {
        return """
                {
                  "username": "team01",
                  "password": "secret"
                }
                """;
    }
}
