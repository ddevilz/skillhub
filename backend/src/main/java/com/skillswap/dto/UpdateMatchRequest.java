package com.skillswap.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateMatchRequest(@NotBlank String status) {}
