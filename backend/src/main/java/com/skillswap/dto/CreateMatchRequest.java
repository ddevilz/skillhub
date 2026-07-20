package com.skillswap.dto;

import jakarta.validation.constraints.NotNull;

public record CreateMatchRequest(@NotNull Long targetUserId) {}
