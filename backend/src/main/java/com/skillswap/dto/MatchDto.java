package com.skillswap.dto;

import java.time.LocalDateTime;

public record MatchDto(Long id, Long userAId, Long userBId, String status, LocalDateTime createdDate) {}
