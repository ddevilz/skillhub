package com.skillswap.dto;

import java.time.LocalDateTime;

public record ReviewDto(Long id, Long sessionId, Long reviewerUserId, Long ratedUserId,
                        int rating, String comments, boolean flagged, LocalDateTime createdDate) {}
