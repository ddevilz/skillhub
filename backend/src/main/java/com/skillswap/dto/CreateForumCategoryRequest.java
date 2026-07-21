package com.skillswap.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateForumCategoryRequest(
        @NotBlank String categoryName,
        String description) {}
