package com.server.contestControl.authServer.service.user;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.exception.api.DuplicateUsernameException;
import com.server.contestControl.authServer.exception.api.UserNotFoundException;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.contestServer.dto.UpdatePasswordRequest;
import com.server.contestControl.contestServer.dto.UpdateUserNameRequest;
import com.server.contestControl.contestServer.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;


    public Optional<User> findByEmail(String email) {
        return userRepository.findByUsername(email);
    }

    public void assertUsernameNotExists(String username) {
        if (userRepository.existsByUsername(username)) {
            throw new DuplicateUsernameException(username);
        }
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
    /* ===================== UPDATE NAME ===================== */

    public void updateUserName(Long userId, UpdateUserNameRequest request) {
        User user = getUserOrThrow(userId);
        user.setUsername(request.getUsername());
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
        if (!userRepository.existsById(userId)) {
            throw new RuntimeException("User not found");
        }
        userRepository.deleteById(userId);
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
