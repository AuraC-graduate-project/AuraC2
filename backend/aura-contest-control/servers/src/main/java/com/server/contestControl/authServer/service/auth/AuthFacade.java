package com.server.contestControl.authServer.service.auth;

import com.server.contestControl.authServer.dto.login.LoginRequest;
import com.server.contestControl.authServer.dto.login.LoginResponse;
import com.server.contestControl.authServer.dto.logout.LogoutResponse;
import com.server.contestControl.authServer.dto.refresh.RefreshResponse;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.authServer.service.login.LoginService;
import com.server.contestControl.authServer.service.logout.LogoutService;
import com.server.contestControl.authServer.service.refreshToken.RefreshTokenService;
import com.server.contestControl.authServer.service.register.RegistrationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthFacade {

    private final RegistrationService registrationService;
    private final LoginService loginService;
    private final RefreshTokenService tokenRefreshService;
    private final LogoutService logoutService;

    public void register( String username, String password, Role role) {
        registrationService.register( username, password, role);
    }

    public LoginResponse login(LoginRequest req, String deviceIp, HttpServletResponse response) {
        return loginService.login(req, deviceIp, response);
    }

    public RefreshResponse refreshToken(HttpServletRequest req, HttpServletResponse res) {
        return tokenRefreshService.refresh(req, res);
    }

    public LogoutResponse logout(HttpServletRequest request,
                                 HttpServletResponse response) {

        logoutService.logout(request, response);

        return new LogoutResponse("Logout successful");
    }
}