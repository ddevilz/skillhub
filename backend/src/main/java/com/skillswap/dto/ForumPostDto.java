package com.skillswap.dto;

import java.time.LocalDateTime;

public record ForumPostDto(Long id, Long categoryId, Long userId, String authorName, String title,
                           String content, long upvoteCount, long commentCount, LocalDateTime createdDate) {}
