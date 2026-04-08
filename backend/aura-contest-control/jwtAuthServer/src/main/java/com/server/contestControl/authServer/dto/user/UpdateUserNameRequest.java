package com.server.contestControl.authServer.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateUserNameRequest {
    @NotBlank
    private String username;
}
