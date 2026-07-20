package com.skillswap.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwt =
        new JwtService("unit-test-secret-unit-test-secret-32bytes!", 3600000L);

    @Test
    void roundTripsEmail() {
        String token = jwt.generateToken("deva@example.com");
        assertThat(jwt.extractEmail(token)).isEqualTo("deva@example.com");
        assertThat(jwt.isValid(token)).isTrue();
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwt.generateToken("deva@example.com");
        assertThat(jwt.isValid(token + "x")).isFalse();
    }
}
