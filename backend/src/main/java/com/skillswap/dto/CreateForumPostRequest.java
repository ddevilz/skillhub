package com.skillswap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateForumPostRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content) {}
