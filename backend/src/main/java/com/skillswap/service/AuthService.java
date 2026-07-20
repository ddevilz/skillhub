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
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final SkillCreditRepository creditRepository;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, SkillCreditRepository creditRepository,
                       PasswordEncoder encoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.creditRepository = creditRepository;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new EmailAlreadyUsedException("Email already registered: " + req.email());
        }
        User u = new User();
        u.setFullName(req.fullName());
        u.setEmail(req.email());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setCity(req.city());
        u.setAbout(req.about());
        u.setRole(Role.USER);
        u.setActive(true);
        User saved = userRepository.save(u);

        creditRepository.save(new SkillCredit(saved.getId())); // defaults to 10 credits

        return toResponse(saved);
    }

    public AuthResponse login(LoginRequest req) {
        User u = userRepository.findByEmail(req.email())
                .filter(User::isActive)
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return toResponse(u);
    }

    private AuthResponse toResponse(User u) {
        return new AuthResponse(jwtService.generateToken(u.getEmail()),
                u.getFullName(), u.getEmail(), u.getRole().name());
    }
}
