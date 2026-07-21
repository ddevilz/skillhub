package com.skillswap.service;

import com.skillswap.dto.AdminUserDto;
import com.skillswap.entity.User;
import com.skillswap.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<AdminUserDto> listUsers(String search, Boolean activeOnly) {
        String needle = search == null ? null : search.toLowerCase();
        return userRepository.findAll().stream()
                .filter(u -> activeOnly == null || u.isActive() == activeOnly)
                .filter(u -> needle == null || needle.isBlank()
                        || u.getFullName().toLowerCase().contains(needle)
                        || u.getEmail().toLowerCase().contains(needle))
                .map(this::toDto)
                .toList();
    }

    public AdminUserDto updateStatus(Long userId, boolean active) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        u.setActive(active);
        return toDto(userRepository.save(u));
    }

    private AdminUserDto toDto(User u) {
        return new AdminUserDto(u.getId(), u.getFullName(), u.getEmail(), u.getCity(),
                u.getRole().name(), u.isActive(), u.getCreatedDate());
    }
}
