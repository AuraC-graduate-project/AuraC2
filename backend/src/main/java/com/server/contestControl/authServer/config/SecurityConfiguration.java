package com.server.contestControl.authServer.config;

import com.server.contestControl.authServer.filter.JwtAuthFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Profile("!no-security")
@RequiredArgsConstructor
public class SecurityConfiguration {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/assets/**",
                                "/favicon.ico",

                                "/error",

                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/swagger-resources/**",
                                "/webjars/**",

                                "/auth/login",
                                "/auth/refresh",
                                "/auth/logout",
                                "/verify/**",
                                "/api/callback/judge0/**"
                        ).permitAll()

                        .requestMatchers(HttpMethod.POST, "/auth/register").hasRole("ADMIN")

                        .requestMatchers(
                                "/api/contest/active",
                                "/api/contest/upcoming",
                                "/api/contest/paused",
                                "/api/contest/ended",
                                "/api/contest/stream",
                                "/api/team/stream"
                        ).permitAll()

                        .requestMatchers("/api/contest/**").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/submissions/**").hasAnyRole("TEAM", "ADMIN")

                        .requestMatchers("/api/clarifications/public/**").permitAll()
                        .requestMatchers("/api/clarifications/my/stream/**").hasRole("TEAM")
                        .requestMatchers("/api/clarifications/admin/stream/**").hasRole("ADMIN")
                        .requestMatchers("/api/clarifications/my/**").hasRole("TEAM")
                        .requestMatchers("/api/clarifications/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/clarifications").hasRole("TEAM")

                        .anyRequest().authenticated()
                )

                .sessionManagement(sess ->
                        sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                ).exceptionHandling(ex -> ex
                .authenticationEntryPoint(
                        (req, res, e) -> res.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                )
        )

                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);


        return http.build();
    }
}
