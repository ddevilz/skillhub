package com.skillswap.service;

import com.skillswap.entity.User;
import com.skillswap.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) { this.userRepository = userRepository; }

    /** The authenticated user (principal username is the email). */
    public User require() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }

    /** The authenticated user, if they hold the ADMIN role. 403 (not 404) — this is a permission tier, not a per-resource ownership check. */
    public User requireAdmin() {
        User u = require();
        if (u.getRole() != com.skillswap.entity.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return u;
    }
}
