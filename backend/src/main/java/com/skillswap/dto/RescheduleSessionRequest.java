package com.skillswap.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record RescheduleSessionRequest(
        @NotNull LocalDate sessionDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime) {}
