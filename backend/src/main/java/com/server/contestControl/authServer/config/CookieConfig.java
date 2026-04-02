package com.server.contestControl.authServer.config;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
public class CookieConfig {

    private final Environment env;

    public boolean isProd() {
        return Arrays.asList(env.getActiveProfiles()).contains("prod");
    }
}