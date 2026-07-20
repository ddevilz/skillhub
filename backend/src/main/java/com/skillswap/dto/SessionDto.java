package com.skillswap.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record SessionDto(Long id, Long matchId, Long teacherUserId, Long learnerUserId, Long scheduledByUserId,
                         LocalDate sessionDate, LocalTime startTime, LocalTime endTime,
                         String mode, String locationOrLink, String status, LocalDateTime createdDate) {}
