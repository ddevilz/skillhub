package com.skillswap.dto;

public record UserProfile(Long id, String fullName, String email,
                          String city, String about, String role) {}
