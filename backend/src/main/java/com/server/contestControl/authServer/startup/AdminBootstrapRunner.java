package com.server.contestControl.authServer.startup;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.authServer.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminBootstrapProperties properties;

    @Override
    public void run(ApplicationArguments args) {
        long adminCount = userRepository.countByRole(Role.ADMIN);
        if (adminCount > 1) {
            throw new IllegalStateException("Expected exactly one admin account at most, but found " + adminCount);
        }

        if (adminCount == 1) {
            handleExistingAdmin();
            return;
        }

        String username = resolveAdminUsername();
        String rawPassword = UUID.randomUUID().toString();

        User admin = User.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .role(Role.ADMIN)
                .build();

        userRepository.save(admin);
        writeCredentialsFile(username, rawPassword);

        log.warn("Bootstrapped admin account '{}' and wrote credentials to {}",
                username, getCredentialsPath().toAbsolutePath());
    }

    private void handleExistingAdmin() {
        if (!properties.resetExistingPasswordOrDefault()) {
            User admin = userRepository.findFirstByRole(Role.ADMIN)
                    .orElseThrow(() -> new IllegalStateException("Admin account lookup failed"));
            log.info("Bootstrap admin account '{}' already exists; password was not changed.",
                    admin.getUsername());
            return;
        }

        User admin = userRepository.findFirstByRole(Role.ADMIN)
                .orElseThrow(() -> new IllegalStateException("Admin account lookup failed"));
        String rawPassword = UUID.randomUUID().toString();
        admin.setPassword(passwordEncoder.encode(rawPassword));
        userRepository.save(admin);

        writeCredentialsFile(admin.getUsername(), rawPassword);
        log.warn("Reset password for existing admin '{}' and wrote credentials to {}",
                admin.getUsername(), getCredentialsPath().toAbsolutePath());
    }

    private String resolveAdminUsername() {
        String preferred = properties.usernameOrDefault();
        if (!userRepository.existsByUsername(preferred)) {
            return preferred;
        }

        return preferred + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void writeCredentialsFile(String username, String rawPassword) {
        Path path = getCredentialsPath();
        String content = """
                AuraC2 bootstrap admin account
                Generated at: %s
                Username: %s
                Password: %s

                Delete this file after storing the credentials safely.
                """.formatted(Instant.now(), username, rawPassword);

        try {
            Files.writeString(path, content);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to write admin credentials file to " + path.toAbsolutePath(), ex);
        }
    }

    private Path getCredentialsPath() {
        Path currentDir = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path projectDir = currentDir.getFileName() != null && "backend".equalsIgnoreCase(currentDir.getFileName().toString())
                ? currentDir.getParent()
                : currentDir;
        return projectDir.resolve(properties.credentialsFileOrDefault());
    }
}
