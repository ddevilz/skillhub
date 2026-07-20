package com.skillswap.dto;

import java.time.LocalDateTime;

public record NotificationDto(Long id, String type, String message, boolean read, LocalDateTime createdDate) {}
