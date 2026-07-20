package com.skillswap.dto;

import java.time.LocalDateTime;

public record BadgeDto(Long id, Long skillId, String skillName, String badgeType, LocalDateTime awardedDate) {}
