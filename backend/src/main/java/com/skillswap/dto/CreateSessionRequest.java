package com.skillswap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateSessionRequest(
        @NotNull Long matchId,
        @NotNull Long teacherUserId,
        @NotNull LocalDate sessionDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotBlank String mode,
        String locationOrLink) {}
