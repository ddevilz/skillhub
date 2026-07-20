package com.skillswap.config;

import com.skillswap.entity.Skill;
import com.skillswap.repository.SkillRepository;
import com.skillswap.service.SkillService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class CacheDegradeIntegrationTest {

    /** A cache whose backend operations always throw, simulating a Redis outage. */
    @TestConfiguration
    static class BrokenCacheConfig {
        @Bean
        @Primary
        CacheManager brokenCacheManager() {
            // CacheManager has two abstract methods (getCache, getCacheNames), so it isn't a
            // functional interface -- implement it directly rather than as a lambda.
            return new CacheManager() {
                private final Map<String, Cache> caches = new ConcurrentHashMap<>();

                @Override
                public Cache getCache(String name) {
                    return caches.computeIfAbsent(name, this::brokenCache);
                }

                @Override
                public Collection<String> getCacheNames() {
                    return caches.keySet();
                }

                private Cache brokenCache(String cacheName) {
                    return new ConcurrentMapCache(cacheName) {
                        @Override
                        public ValueWrapper get(Object key) {
                            throw new RuntimeException("Simulated Redis outage");
                        }
                        @Override
                        public void put(Object key, Object value) {
                            throw new RuntimeException("Simulated Redis outage");
                        }
                        @Override
                        public void evict(Object key) {
                            throw new RuntimeException("Simulated Redis outage");
                        }
                        @Override
                        public void clear() {
                            throw new RuntimeException("Simulated Redis outage");
                        }
                    };
                }
            };
        }
    }

    @Autowired SkillService skillService;
    @MockBean SkillRepository skillRepository;

    @Test
    void catalogDoesNotThrowWhenCacheBackendFails() {
        Skill s = new Skill();
        s.setSkillName("Guitar");
        s.setCategory("Music");
        when(skillRepository.findAll()).thenReturn(List.of(s));

        assertThatCode(() -> {
            var result = skillService.catalog();
            assertThat(result).hasSize(1);
            assertThat(result.get(0).skillName()).isEqualTo("Guitar");
        }).doesNotThrowAnyException();
    }
}
