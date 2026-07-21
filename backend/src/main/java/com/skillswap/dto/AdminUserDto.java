package com.skillswap.dto;

import java.time.LocalDateTime;

public record AdminUserDto(Long id, String fullName, String email, String city,
                           String role, boolean active, LocalDateTime createdDate) {}
