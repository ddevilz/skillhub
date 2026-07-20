package com.skillswap.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
        @Min(1) @Max(5) int rating,
        @Size(max = 255, message = "comments must be at most 255 characters") String comments) {}
