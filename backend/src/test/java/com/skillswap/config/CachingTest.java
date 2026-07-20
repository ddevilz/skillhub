package com.skillswap.config;

import com.skillswap.entity.Skill;
import com.skillswap.repository.SkillRepository;
import com.skillswap.service.SkillService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class CachingTest {

    @Autowired SkillService skillService;
    @Autowired CacheManager cacheManager;
    @MockBean SkillRepository skillRepository;

    @Test
    void catalogIsCachedAfterFirstCall() {
        Skill s = new Skill(); s.setSkillName("Guitar"); s.setCategory("Music");
        when(skillRepository.findAll()).thenReturn(List.of(s));

        skillService.catalog();
        skillService.catalog();

        // Repository hit only once; second call served from the 'skills' cache.
        verify(skillRepository, times(1)).findAll();
        assertThat(cacheManager.getCache("skills")).isNotNull();
    }
}
