package com.skillswap.service;

import com.skillswap.dto.AuthResponse;
import com.skillswap.dto.LoginRequest;
import com.skillswap.dto.RegisterRequest;
import com.skillswap.entity.Role;
import com.skillswap.entity.SkillCredit;
import com.skillswap.entity.User;
import com.skillswap.repository.SkillCreditRepository;
import com.skillswap.repository.UserRepository;
import com.skillswap.security.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private final UserRepository userRepo = mock(UserRepository.class);
    private final SkillCreditRepository creditRepo = mock(SkillCreditRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtService jwt = mock(JwtService.class);
    private final AuthService service = new AuthService(userRepo, creditRepo, encoder, jwt);

    private RegisterRequest req() {
        return new RegisterRequest("Deva", "deva@example.com", "password1", null, null);
    }

    @Test
    void registerCreatesUserWithTenCredits() {
        when(userRepo.existsByEmail("deva@example.com")).thenReturn(false);
        when(encoder.encode("password1")).thenReturn("hashed");
        when(userRepo.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            // simulate DB id assignment
            try { var f = User.class.getDeclaredField("id"); f.setAccessible(true); f.set(u, 1L); }
            catch (Exception e) { throw new RuntimeException(e); }
            return u;
        });
        when(jwt.generateToken("deva@example.com")).thenReturn("tok");

        AuthResponse res = service.register(req());

        assertThat(res.token()).isEqualTo("tok");
        assertThat(res.role()).isEqualTo("USER");

        ArgumentCaptor<SkillCredit> credit = ArgumentCaptor.forClass(SkillCredit.class);
        verify(creditRepo).save(credit.capture());
        assertThat(credit.getValue().getTotalCredits()).isEqualTo(10);
        assertThat(credit.getValue().getUserId()).isEqualTo(1L);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepo).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getPasswordHash()).isEqualTo("hashed");   // encoder.encode(...) stub returns "hashed", NOT the raw password
        assertThat(savedUser.getRole()).isEqualTo(Role.USER);
        assertThat(savedUser.isActive()).isTrue();
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepo.existsByEmail("deva@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.register(req()))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(userRepo, never()).save(any());
    }

    @Test
    void loginRejectsWrongPassword() {
        User u = new User();
        u.setEmail("deva@example.com");
        u.setPasswordHash("hashed");
        u.setRole(Role.USER);
        when(userRepo.findByEmail("deva@example.com")).thenReturn(Optional.of(u));
        when(encoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("deva@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }

    @Test
    void loginRejectsUnknownEmail() {
        when(userRepo.findByEmail("ghost@example.com")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.login(new LoginRequest("ghost@example.com", "whatever")))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessageContaining("Invalid email or password");
    }
}
