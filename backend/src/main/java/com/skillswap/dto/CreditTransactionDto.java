package com.skillswap.dto;

import java.time.LocalDateTime;

public record CreditTransactionDto(Long id, Long sessionId, String transactionType,
                                   int amount, LocalDateTime transactionDate) {}
