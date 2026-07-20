package com.skillswap.dto;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the DTOs cached via @Cacheable round-trip through the same
 * JdkSerializationRedisSerializer Spring's default RedisCacheConfiguration uses.
 * The 'simple' test-profile cache never serializes at all, so this is the only
 * test that would have caught a non-Serializable cached type before it reached
 * a real Redis deployment.
 */
class CachedDtoSerializationTest {

    private final RedisSerializer<Object> serializer = RedisSerializer.java();

    @Test
    void skillDtoRoundTripsThroughJdkSerialization() {
        SkillDto original = new SkillDto(1L, "Guitar", "Music", "Acoustic and electric guitar");
        byte[] bytes = serializer.serialize(original);
        Object restored = serializer.deserialize(bytes);
        assertThat(restored).isEqualTo(original);
    }

    @Test
    void matchSuggestionDtoRoundTripsThroughJdkSerialization() {
        MatchSuggestionDto original = new MatchSuggestionDto(2L, "Teacher", "Pune", 3L, 75);
        byte[] bytes = serializer.serialize(original);
        Object restored = serializer.deserialize(bytes);
        assertThat(restored).isEqualTo(original);
    }
}
