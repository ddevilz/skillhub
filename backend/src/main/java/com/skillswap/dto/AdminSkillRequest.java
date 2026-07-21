package com.skillswap.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminSkillRequest(
        @NotBlank String skillName,
        @NotBlank String category,
        String description) {}
