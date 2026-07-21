package com.skillswap.service;

import com.skillswap.dto.AddUserSkillRequest;
import com.skillswap.dto.SkillDto;
import com.skillswap.dto.UserSkillDto;
import com.skillswap.entity.Skill;
import com.skillswap.entity.SkillType;
import com.skillswap.entity.UserSkill;
import com.skillswap.repository.SkillRepository;
import com.skillswap.repository.UserSkillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillServiceTest {

    private final SkillRepository skillRepo = mock(SkillRepository.class);
    private final UserSkillRepository userSkillRepo = mock(UserSkillRepository.class);
    private final com.skillswap.repository.SessionRepository sessionRepo = mock(com.skillswap.repository.SessionRepository.class);
    private final com.skillswap.repository.SkillBadgeRepository skillBadgeRepo = mock(com.skillswap.repository.SkillBadgeRepository.class);
    private final SkillService service = new SkillService(skillRepo, userSkillRepo, sessionRepo, skillBadgeRepo);

    @Test
    void addRejectsUnknownSkill() {
        when(skillRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.add(1L, new AddUserSkillRequest(99L, "CAN_TEACH", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Skill not found");
    }

    @Test
    void addRejectsDuplicate() {
        Skill s = new Skill(); s.setSkillName("Guitar"); s.setCategory("Music");
        when(skillRepo.findById(1L)).thenReturn(Optional.of(s));
        when(userSkillRepo.existsByUserIdAndSkillIdAndSkillType(1L, 1L, SkillType.CAN_TEACH)).thenReturn(true);
        assertThatThrownBy(() -> service.add(1L, new AddUserSkillRequest(1L, "CAN_TEACH", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already");
    }

    @Test
    void addPersistsAndReturnsDto() {
        Skill s = new Skill(); s.setSkillName("Guitar"); s.setCategory("Music");
        when(skillRepo.findById(1L)).thenReturn(Optional.of(s));
        when(userSkillRepo.existsByUserIdAndSkillIdAndSkillType(1L, 1L, SkillType.CAN_TEACH)).thenReturn(false);
        when(userSkillRepo.save(any(UserSkill.class))).thenAnswer(i -> i.getArgument(0));

        UserSkillDto dto = service.add(1L, new AddUserSkillRequest(1L, "CAN_TEACH", "2 years", "Advanced"));

        assertThat(dto.skillName()).isEqualTo("Guitar");
        assertThat(dto.skillType()).isEqualTo("CAN_TEACH");
        verify(userSkillRepo).save(any(UserSkill.class));
    }

    @Test
    void addRejectsInvalidSkillType() {
        Skill s = new Skill(); s.setSkillName("Guitar"); s.setCategory("Music");
        when(skillRepo.findById(1L)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.add(1L, new AddUserSkillRequest(1L, "BOGUS", null, null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void removeRejectsForeignUserSkill() {
        when(userSkillRepo.findByIdAndUserId(5L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.remove(1L, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void createSkillPersistsAndReturnsDto() {
        when(skillRepo.save(any(Skill.class))).thenAnswer(i -> {
            Skill s = i.getArgument(0);
            try { var f = Skill.class.getDeclaredField("id"); f.setAccessible(true); f.set(s, 1L); }
            catch (Exception e) { throw new RuntimeException(e); }
            return s;
        });
        SkillDto dto = service.createSkill(new com.skillswap.dto.AdminSkillRequest("Ukulele", "Music", "Small guitar"));
        assertThat(dto.skillName()).isEqualTo("Ukulele");
    }

    @Test
    void updateSkillRejectsWhenNotFound() {
        when(skillRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateSkill(99L, new com.skillswap.dto.AdminSkillRequest("X", "Y", null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deleteSkillRejectsWhenInUseByUserSkill() {
        Skill s = new Skill();
        when(skillRepo.findById(1L)).thenReturn(Optional.of(s));
        when(userSkillRepo.existsBySkillId(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteSkill(1L)).isInstanceOf(ResponseStatusException.class);
        verify(skillRepo, never()).delete(any());
    }

    @Test
    void deleteSkillRejectsWhenInUseBySession() {
        Skill s = new Skill();
        when(skillRepo.findById(1L)).thenReturn(Optional.of(s));
        when(userSkillRepo.existsBySkillId(1L)).thenReturn(false);
        when(sessionRepo.existsBySkillId(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteSkill(1L)).isInstanceOf(ResponseStatusException.class);
        verify(skillRepo, never()).delete(any());
    }

    @Test
    void deleteSkillRejectsWhenInUseByBadge() {
        Skill s = new Skill();
        when(skillRepo.findById(1L)).thenReturn(Optional.of(s));
        when(userSkillRepo.existsBySkillId(1L)).thenReturn(false);
        when(sessionRepo.existsBySkillId(1L)).thenReturn(false);
        when(skillBadgeRepo.existsBySkillId(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteSkill(1L)).isInstanceOf(ResponseStatusException.class);
        verify(skillRepo, never()).delete(any());
    }

    @Test
    void deleteSkillSucceedsWhenUnused() {
        Skill s = new Skill();
        when(skillRepo.findById(1L)).thenReturn(Optional.of(s));
        when(userSkillRepo.existsBySkillId(1L)).thenReturn(false);
        when(sessionRepo.existsBySkillId(1L)).thenReturn(false);
        when(skillBadgeRepo.existsBySkillId(anyLong())).thenReturn(false);
        service.deleteSkill(1L);
        verify(skillRepo).delete(s);
    }
}
