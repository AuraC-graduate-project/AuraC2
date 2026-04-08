package com.server.contestControl.authServer.startup;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bootstrap.admin")
public record AdminBootstrapProperties(
        String username,
        String credentialsFile
) {
    public String usernameOrDefault() {
        return (username == null || username.isBlank()) ? "admin" : username;
    }

    public String credentialsFileOrDefault() {
        return (credentialsFile == null || credentialsFile.isBlank())
                ? "admin-account.txt"
                : credentialsFile;
    }
}
