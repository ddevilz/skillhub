package com.skillswap.controller;

import com.skillswap.dto.UserProfile;
import com.skillswap.entity.User;
import com.skillswap.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRepository userRepository;

    public MeController(UserRepository userRepository) { this.userRepository = userRepository; }

    @GetMapping
    public UserProfile me(@AuthenticationPrincipal UserDetails principal) {
        User u = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        return new UserProfile(u.getId(), u.getFullName(), u.getEmail(),
                u.getCity(), u.getAbout(), u.getRole().name());
    }
}
