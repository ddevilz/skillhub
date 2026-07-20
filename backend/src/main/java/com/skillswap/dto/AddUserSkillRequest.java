package com.skillswap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddUserSkillRequest(
        @NotNull Long skillId,
        @NotBlank String skillType,
        String experience,
        String proficiency) {}
