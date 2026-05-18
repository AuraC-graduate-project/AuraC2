package com.server.contestControl.authServer.service.user;

import com.server.contestControl.authServer.entity.User;
import com.server.contestControl.authServer.enums.Role;
import com.server.contestControl.authServer.exception.api.DuplicateTeamUsernamesException;
import com.server.contestControl.authServer.repository.UserRepository;
import com.server.contestControl.contestServer.dto.BulkTeamGenerationRequest;
import com.server.contestControl.contestServer.dto.GeneratedTeamCredentialResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceBulkTeamGenerationTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    void adminCanGenerateTeamAccounts() {
        when(userRepository.findByUsernameIn(List.of("team1", "team2", "team3"))).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenAnswer(invocation -> "hashed-" + invocation.getArgument(0));

        List<GeneratedTeamCredentialResponse> generated = userService.generateTeamAccounts(
                request("team", 1, 3, 10)
        );

        assertThat(generated)
                .extracting(GeneratedTeamCredentialResponse::getUsername)
                .containsExactly("team1", "team2", "team3");
        assertThat(generated)
                .allSatisfy(credential -> {
                    assertThat(credential.getRole()).isEqualTo("TEAM");
                    assertThat(credential.getPassword()).hasSize(10);
                });

        ArgumentCaptor<Iterable<User>> usersCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(userRepository).saveAll(usersCaptor.capture());
        assertThat(usersCaptor.getValue())
                .extracting(User::getUsername)
                .containsExactly("team1", "team2", "team3");
    }

    @Test
    void duplicateUsernamesCauseFailureAndNoPartialAccounts() {
        User existing = User.builder()
                .username("team2")
                .password("hashed")
                .role(Role.TEAM)
                .build();
        when(userRepository.findByUsernameIn(List.of("team1", "team2", "team3"))).thenReturn(List.of(existing));

        assertThatThrownBy(() -> userService.generateTeamAccounts(request("team", 1, 3, 10)))
                .isInstanceOf(DuplicateTeamUsernamesException.class)
                .hasMessageContaining("team2");

        verify(userRepository, never()).saveAll(org.mockito.ArgumentMatchers.<Collection<User>>any());
    }

    @Test
    void generatedPasswordsUseRequestedLength() {
        when(userRepository.findByUsernameIn(List.of("team1"))).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-password");

        List<GeneratedTeamCredentialResponse> generated = userService.generateTeamAccounts(
                request("team", 1, 1, 10)
        );

        assertThat(generated).singleElement()
                .extracting(GeneratedTeamCredentialResponse::getPassword)
                .asString()
                .hasSize(10);
    }

    @Test
    void generatedPasswordsAreHashedBeforeSave() {
        when(userRepository.findByUsernameIn(List.of("team1"))).thenReturn(List.of());
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-secret");

        List<GeneratedTeamCredentialResponse> generated = userService.generateTeamAccounts(
                request("team", 1, 1, 10)
        );

        ArgumentCaptor<Iterable<User>> usersCaptor = ArgumentCaptor.forClass(Iterable.class);
        verify(userRepository).saveAll(usersCaptor.capture());

        User savedUser = usersCaptor.getValue().iterator().next();
        assertThat(savedUser.getPassword()).isEqualTo("encoded-secret");
        assertThat(savedUser.getPassword()).isNotEqualTo(generated.getFirst().getPassword());
        verify(passwordEncoder).encode(generated.getFirst().getPassword());
    }

    private BulkTeamGenerationRequest request(
            String prefix,
            int startNumber,
            int endNumber,
            int passwordLength
    ) {
        return BulkTeamGenerationRequest.builder()
                .prefix(prefix)
                .startNumber(startNumber)
                .endNumber(endNumber)
                .passwordLength(passwordLength)
                .build();
    }
}
