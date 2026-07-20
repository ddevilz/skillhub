package com.skillswap.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import static org.assertj.core.api.Assertions.assertThatCode;

class CacheErrorHandlerTest {

    private final CacheErrorHandler handler = new CacheConfig().cacheErrorHandler();
    private final Cache cache = new ConcurrentMapCache("skills");

    @Test
    void swallowsGetAndPutErrors() {
        RuntimeException boom = new RuntimeException("redis down");
        assertThatCode(() -> handler.handleCacheGetError(boom, cache, "k")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCachePutError(boom, cache, "k", "v")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCacheEvictError(boom, cache, "k")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCacheClearError(boom, cache)).doesNotThrowAnyException();
    }
}
