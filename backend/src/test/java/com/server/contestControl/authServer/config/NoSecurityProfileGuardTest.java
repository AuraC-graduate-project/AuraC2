package com.server.contestControl.authServer.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoSecurityProfileGuardTest {

    @Test
    void allowsNormalProfilesWithoutNoSecurity() {
        NoSecurityProfileGuard guard = guardWithProfiles("prod");

        assertThatCode(guard::validate).doesNotThrowAnyException();
    }

    @Test
    void allowsNoSecurityForLocalDevelopmentOnly() {
        NoSecurityProfileGuard guard = guardWithProfiles("dev", "no-security");

        assertThatCode(guard::validate).doesNotThrowAnyException();
    }

    @Test
    void rejectsNoSecurityWhenProdIsActive() {
        NoSecurityProfileGuard guard = guardWithProfiles("prod", "no-security");

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no-security");
    }

    @Test
    void rejectsNoSecurityWithoutLocalOrTestProfile() {
        NoSecurityProfileGuard guard = guardWithProfiles("no-security");

        assertThatThrownBy(guard::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dev, local, or test");
    }

    private NoSecurityProfileGuard guardWithProfiles(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new NoSecurityProfileGuard(environment);
    }
}
