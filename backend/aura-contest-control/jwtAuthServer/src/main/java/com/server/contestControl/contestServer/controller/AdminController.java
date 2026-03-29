package com.server.contestControl.contestServer.controller;


import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.service.user.UserService;
import com.server.contestControl.contestServer.dto.UpdatePasswordRequest;
import com.server.contestControl.contestServer.dto.UpdateUserNameRequest;
import com.server.contestControl.contestServer.dto.UserResponse;
import com.server.contestControl.submissionServer.dto.SubmissionResponse;
import com.server.contestControl.submissionServer.service.submission.SubmissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserService userService;
    private final SubmissionService submissionService;
    /* ===================== FETCH ALL USERS ===================== */

    @GetMapping
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    /* ===================== UPDATE USER NAME ===================== */

    @PutMapping("/{userId}/name")
    public ResponseEntity<Void> updateUserName(
            @PathVariable Long userId,
            @RequestBody UpdateUserNameRequest request
    ) {
        userService.updateUserName(userId, request);
        return ResponseEntity.noContent().build();
    }

    /* ===================== UPDATE USER PASSWORD ===================== */

    @PutMapping("/{userId}/password")
    public ResponseEntity<Void> updateUserPassword(
            @PathVariable Long userId,
            @RequestBody UpdatePasswordRequest request
    ) {
        userService.updateUserPassword(userId, request);
        return ResponseEntity.noContent().build();
    }

    /* ===================== DELETE USER ===================== */

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long userId) {
        userService.deleteUser(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/submissions")
    public ResponseEntity<List<SubmissionResponse>> getAllSubmissions() {
        return ResponseEntity.ok(submissionService.getAllSubmission());
    }

}
