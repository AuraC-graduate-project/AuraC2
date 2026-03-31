package com.server.contestControl.authServer.controller;

import com.server.contestControl.authServer.dto.login.LoginRequest;
import com.server.contestControl.authServer.dto.login.LoginResponse;
import com.server.contestControl.authServer.dto.logout.LogoutResponse;
import com.server.contestControl.authServer.dto.refresh.RefreshResponse;
import com.server.contestControl.authServer.dto.register.RegisterRequest;
import com.server.contestControl.authServer.dto.register.RegisterResponse;
import com.server.contestControl.authServer.service.auth.AuthFacade;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthFacade authFacade;

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest request) {
        authFacade.register(request.getUsername(),request.getPassword(), request.getRole());
        return ResponseEntity.ok(new RegisterResponse("Registration successful!"));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @RequestBody LoginRequest request,
            HttpServletRequest http,
            HttpServletResponse response
    ) {
        String deviceIp = http.getRemoteAddr();
        return ResponseEntity.ok(authFacade.login(request, deviceIp, response));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(HttpServletRequest request, HttpServletResponse response) {
        RefreshResponse tokens = authFacade.refreshToken(request, response);
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        return ResponseEntity.ok(
                authFacade.logout(request, response)
        );
    }
}
