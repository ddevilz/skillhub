package com.skillswap.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateForumCommentRequest(@NotBlank String commentText) {}
