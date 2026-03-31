package com.server.contestControl.authServer.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

public final class CookieUtil {

    private CookieUtil() {}

    public static void addRefreshToCookie(
            HttpServletResponse response,
            String refreshToken,
            boolean isProd
    ) {
        ResponseCookie cookie = ResponseCookie.from("refresh_token", refreshToken)
                .httpOnly(true)
                .secure(false) // 🔥 TRUE in prod, FALSE locally
                .sameSite(isProd ? "Strict" : "Lax")
                .path("/auth/refresh") // 🔥 CRITICAL FIX
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
                .secure(false)
                .sameSite(isProd ? "Strict" : "Lax")
                .path("/auth/refresh")
                .maxAge(0)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }
}
