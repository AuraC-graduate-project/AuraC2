package com.server.contestControl.authServer.util;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class CookieUtilTest {

    @Test
    void refreshCookieUsesSharedAuthPathAndDevelopmentCookieFlags() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CookieUtil.addRefreshToCookie(response, "refresh-token-value", false);

        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie)
                .contains("refresh_token=refresh-token-value")
                .contains("Path=/auth")
                .contains("Max-Age=604800")
                .contains("HttpOnly")
                .contains("SameSite=Lax")
                .doesNotContain("Secure");
    }

    @Test
    void clearRefreshCookieUsesSamePathAndProductionCookieFlags() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        CookieUtil.clearRefreshCookie(response, true);

        String cookie = response.getHeader(HttpHeaders.SET_COOKIE);
        assertThat(cookie)
                .contains("refresh_token=")
                .contains("Path=/auth")
                .contains("Max-Age=0")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict");
    }
}
