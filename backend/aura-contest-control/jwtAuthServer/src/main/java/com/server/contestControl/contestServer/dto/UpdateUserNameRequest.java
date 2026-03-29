package com.server.contestControl.contestServer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateUserNameRequest {
    @NotBlank
    private String username;
}