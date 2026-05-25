package com.server.contestControl.authServer.service.user;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.authServer.exception.api.AdminAccountDeletionNotAllowedException;
import com.server.contestControl.authServer.exception.api.DuplicateTeamUsernamesException;
import com.server.contestControl.authServer.exception.api.DuplicateUsernameException;
import com.server.contestControl.authServer.exception.api.InvalidTeamGenerationRequestException;
import com.server.contestControl.authServer.exception.api.UserNotFoundException;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.contestServer.dto.BulkTeamGenerationRequest;
import com.server.contestControl.contestServer.dto.GeneratedTeamCredentialResponse;
import com.server.contestControl.contestServer.dto.UpdatePasswordRequest;
import com.server.contestControl.contestServer.dto.UpdateUserNameRequest;
import com.server.contestControl.contestServer.dto.UserResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private static final int DEFAULT_BULK_PASSWORD_LENGTH = 10;
    private static final int MIN_BULK_PASSWORD_LENGTH = 8;
    private static final char[] PASSWORD_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%+-_".toCharArray();
    private static final char[] PASSWORD_FIRST_CHARS =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!#$%_".toCharArray();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();


    public Optional<User> findByEmail(String email) {
        return userRepository.findByUsername(email);
    }

    public void assertUsernameNotExists(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUsernameException(username);
        }
    }

    public boolean adminExists() {
        return userRepository.existsByRole(Role.ADMIN);
    }

    public User getUserByUsernameOrThrow(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UserNotFoundException(username));
    }

    public User save(User user) {
        return userRepository.save(user);
    }

    public List<UserResponse> getAllUsers() {
        return userRepository.findAll()
                .stream()
                .map(user -> new UserResponse(
                        user.getId(),
                        user.getUsername(),
                        user.getRole().name()
                ))
                .toList();
    }

    @Transactional
    public List<GeneratedTeamCredentialResponse> generateTeamAccounts(BulkTeamGenerationRequest request) {
        if (request == null) {
            throw new InvalidTeamGenerationRequestException("Request body is required.");
        }

        String prefix = request.getPrefix() == null ? "" : request.getPrefix().trim();
        int startNumber = request.getStartNumber() == null ? 1 : request.getStartNumber();
        int endNumber = request.getEndNumber() == null ? 20 : request.getEndNumber();
        int passwordLength = request.getPasswordLength() == null
                ? DEFAULT_BULK_PASSWORD_LENGTH
                : request.getPasswordLength();

        validateTeamGenerationRequest(prefix, startNumber, endNumber, passwordLength);

        List<String> usernames = new ArrayList<>();
        for (int number = startNumber; number <= endNumber; number++) {
            usernames.add(prefix + number);
        }

        List<String> duplicateUsernames = userRepository.findByUsernameIn(usernames)
                .stream()
                .map(User::getUsername)
                .sorted(Comparator.naturalOrder())
                .toList();
        if (!duplicateUsernames.isEmpty()) {
            throw new DuplicateTeamUsernamesException(duplicateUsernames);
        }

        List<GeneratedTeamCredentialResponse> generated = new ArrayList<>();
        List<User> users = new ArrayList<>();

        for (String username : usernames) {
            String password = generatePassword(passwordLength);
            users.add(User.builder()
                    .username(username)
                    .password(passwordEncoder.encode(password))
                    .role(Role.TEAM)
                    .build());
            generated.add(new GeneratedTeamCredentialResponse(username, password, Role.TEAM.name()));
        }

        userRepository.saveAll(users);
        return generated;
    }

    private void validateTeamGenerationRequest(String prefix, int startNumber, int endNumber, int passwordLength) {
        if (prefix.isBlank()) {
            throw new InvalidTeamGenerationRequestException("Prefix must not be blank.");
        }
        if (startNumber > endNumber) {
            throw new InvalidTeamGenerationRequestException("startNumber must be less than or equal to endNumber.");
        }
        if (passwordLength < MIN_BULK_PASSWORD_LENGTH) {
            throw new InvalidTeamGenerationRequestException("passwordLength must be at least 8.");
        }
    }

    private String generatePassword(int length) {
        StringBuilder password = new StringBuilder(length);
        password.append(PASSWORD_FIRST_CHARS[secureRandom.nextInt(PASSWORD_FIRST_CHARS.length)]);
        for (int i = 1; i < length; i++) {
            password.append(PASSWORD_CHARS[secureRandom.nextInt(PASSWORD_CHARS.length)]);
        }
        return password.toString();
    }

    /* ===================== UPDATE NAME ===================== */

    public void updateUserName(Long userId, UpdateUserNameRequest request) {
        User user = getUserOrThrow(userId);
        String newUsername = request.getUsername();
        if (newUsername != null && !newUsername.equals(user.getUsername())) {
            assertUsernameNotExists(newUsername);
        }
        user.setUsername(newUsername);
        userRepository.save(user);
    }

    /* ===================== UPDATE PASSWORD ===================== */

    public void updateUserPassword(Long userId, UpdatePasswordRequest request) {
        User user = getUserOrThrow(userId);
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    /* ===================== DELETE USER ===================== */

    public void deleteUser(Long userId) {
        User user = getUserOrThrow(userId);
        if (user.getRole() == Role.ADMIN) {
            throw new AdminAccountDeletionNotAllowedException();
        }
        userRepository.deleteById(userId);
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
