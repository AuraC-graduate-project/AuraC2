package com.server.contestControl.authServer.service.login;
import com.server.contestControl.authServer.dto.login.LoginRequest;
import com.server.contestControl.authServer.dto.login.LoginResponse;
import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.exception.api.InvalidCredentialsException;
import com.server.contestControl.authServer.service.user.UserService;
import com.server.contestControl.authServer.util.TokenIssuerUtil;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final TokenIssuerUtil tokenIssuerUtil;

    @Transactional
    public LoginResponse login(LoginRequest request,
                               String deviceIp,
                               HttpServletResponse response) {

        User user = userService.getUserByUsernameOrThrow(request.getUsername());
        validateCredentials(user, request);
        String accessToken = tokenIssuerUtil.issueTokens(user, deviceIp, response);
        return new LoginResponse(accessToken, "Login successful");
    }

    private void validateCredentials(User user, LoginRequest req) {
        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException();
        }
    }
}
