package com.server.contestControl.authServer.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

public final class CookieUtil {

    private static final String REFRESH_COOKIE_PATH = "/auth";

    private CookieUtil() {}

    public static void addRefreshToCookie(
            HttpServletResponse response,
            String refreshToken,
            boolean isProd
    ) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(isProd)
                .sameSite(isProd ? "Strict" : "Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(Duration.ofDays(7))
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }

    public static void clearRefreshCookie(
            HttpServletResponse response,
            boolean isProd
    ) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
                .httpOnly(true)
                .secure(isProd)
                .sameSite(isProd ? "Strict" : "Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }
}
