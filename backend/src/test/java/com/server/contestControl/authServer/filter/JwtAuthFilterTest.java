package com.server.contestControl.authServer.filter;

import com.server.contestControl.authServer.enums.TokenType;
import com.server.contestControl.authServer.service.jwt.core.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtAuthFilterTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registerPathIsAuthenticatedWhenBearerTokenIsProvided() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/register");
        request.setServletPath("/auth/register");
        request.addHeader("Authorization", "Bearer access-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        UserDetails admin = org.springframework.security.core.userdetails.User
                .withUsername("admin")
                .password("password")
                .authorities("ROLE_ADMIN")
                .build();

        when(jwtService.extractUsername("access-token", TokenType.ACCESS)).thenReturn("admin");
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(admin);
        when(jwtService.isTokenValid("access-token", admin, TokenType.ACCESS)).thenReturn(true);

        filter.doFilter(request, response, chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_ADMIN");
        verify(jwtService).extractUsername("access-token", TokenType.ACCESS);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/auth/login",
            "/auth/refresh",
            "/auth/logout",
            "/verify/token",
            "/v3/api-docs/openapi",
            "/swagger-ui/index.html",
            "/swagger-resources/configuration/ui",
            "/webjars/swagger-ui/swagger-ui.css",
            "/api/callback/judge0/1/2/3",
            "/api/contest/active",
            "/api/contest/upcoming",
            "/api/contest/paused",
            "/api/contest/ended",
            "/api/scoreboard/contests/1",
            "/api/scoreboard/contests/1/stream",
            "/api/clarifications/public/1",
            "/assets/index.js",
            "/favicon.ico",
            "/error"
    })
    void publicPathsSkipBearerTokenProcessing(String path) throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setServletPath(path);
        request.addHeader("Authorization", "Bearer invalid-public-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(jwtService, userDetailsService);
    }

    @Test
    void protectedPathWithInvalidBearerTokenIsRejected() throws Exception {
        JwtService jwtService = mock(JwtService.class);
        UserDetailsService userDetailsService = mock(UserDetailsService.class);
        JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/admin/users");
        request.setServletPath("/api/admin/users");
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(jwtService.extractUsername("bad-token", TokenType.ACCESS))
                .thenThrow(new io.jsonwebtoken.JwtException("bad"));

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        verify(jwtService).extractUsername("bad-token", TokenType.ACCESS);
        verifyNoInteractions(userDetailsService);
    }
}
