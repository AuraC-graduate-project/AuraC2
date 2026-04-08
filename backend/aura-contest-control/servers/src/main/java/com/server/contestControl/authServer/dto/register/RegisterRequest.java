package com.server.contestControl.authServer.dto.register;

import com.server.contestControl.authServer.enums.Role;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor @Builder
public class RegisterRequest {
    private String username;
    private String password;
    private Role role;
}
