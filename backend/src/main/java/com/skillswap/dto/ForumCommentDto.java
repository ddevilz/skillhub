package com.skillswap.dto;

import java.time.LocalDateTime;

public record ForumCommentDto(Long id, Long postId, Long userId, String authorName,
                              String commentText, LocalDateTime createdDate) {}
