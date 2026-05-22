package com.server.contestControl.authServer.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class NoSecurityProfileGuard implements ApplicationRunner {

    private static final String NO_SECURITY_PROFILE = "no-security";
    private static final String PROD_PROFILE = "prod";
    private static final Set<String> LOCAL_OR_TEST_PROFILES = Set.of("dev", "local", "test");

    private final Environment environment;

    public NoSecurityProfileGuard(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) {
        validate();
    }

    void validate() {
        Set<String> activeProfiles = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase().trim())
                .filter(profile -> !profile.isBlank())
                .collect(Collectors.toSet());

        if (!activeProfiles.contains(NO_SECURITY_PROFILE)) {
            return;
        }

        boolean productionLike = activeProfiles.contains(PROD_PROFILE);
        boolean explicitlyLocalOrTest = activeProfiles.stream().anyMatch(LOCAL_OR_TEST_PROFILES::contains);

        if (productionLike || !explicitlyLocalOrTest) {
            throw new IllegalStateException(
                    "The no-security Spring profile disables AuraC2 authentication and is only allowed with dev, local, or test profiles."
            );
        }
    }
}
