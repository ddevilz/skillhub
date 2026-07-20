package com.skillswap.dto;

public record MatchSuggestionDto(Long userId, String fullName, String city,
                                 long matchedSkills, int compatibilityScore) {}
