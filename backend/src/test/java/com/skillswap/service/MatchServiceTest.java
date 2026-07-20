package com.skillswap.service;

import com.skillswap.dto.MatchDto;
import com.skillswap.dto.MatchSuggestionDto;
import com.skillswap.entity.*;
import com.skillswap.entity.NotificationType;
import com.skillswap.repository.MatchProjection;
import com.skillswap.repository.MatchRepository;
import com.skillswap.repository.UserSkillRepository;
import com.skillswap.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MatchServiceTest {

    private final UserSkillRepository userSkillRepo = mock(UserSkillRepository.class);
    private final MatchRepository matchRepo = mock(MatchRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final MatchService service = new MatchService(userSkillRepo, matchRepo, userRepo, notificationService);

    private MatchProjection projection(Long userId, long matched) {
        return new MatchProjection() {
            public Long getUserId() { return userId; }
            public String getFullName() { return "Teacher"; }
            public String getCity() { return "Pune"; }
            public long getMatchedSkills() { return matched; }
        };
    }

    @Test
    void suggestionsComputeCompatibilityScore() {
        when(userSkillRepo.findSuggestions(1L, "", "")).thenReturn(List.of(projection(2L, 2L)));
        when(userSkillRepo.countByUserIdAndSkillType(1L, SkillType.WANT_TO_LEARN)).thenReturn(4L);

        List<MatchSuggestionDto> out = service.suggestions(1L, null, null);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).matchedSkills()).isEqualTo(2L);
        assertThat(out.get(0).compatibilityScore()).isEqualTo(50); // 2 of 4 wanted = 50%
    }

    @Test
    void suggestionsScoreZeroWhenNoLearnSkills() {
        when(userSkillRepo.findSuggestions(1L, "", "")).thenReturn(List.of(projection(2L, 1L)));
        when(userSkillRepo.countByUserIdAndSkillType(1L, SkillType.WANT_TO_LEARN)).thenReturn(0L);

        List<MatchSuggestionDto> out = service.suggestions(1L, null, null);
        assertThat(out.get(0).compatibilityScore()).isZero(); // guard against divide-by-zero
    }

    @Test
    void requestRejectsSelfMatch() {
        assertThatThrownBy(() -> service.request(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("yourself");
    }

    @Test
    void requestRejectsUnknownTarget() {
        when(userRepo.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.request(1L, 2L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void requestRejectsDuplicatePending() {
        User target = activeUser(2L);
        when(userRepo.findById(2L)).thenReturn(Optional.of(target));
        when(matchRepo.existsByUserAIdAndUserBIdAndStatus(1L, 2L, MatchStatus.PENDING)).thenReturn(true);
        assertThatThrownBy(() -> service.request(1L, 2L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already");
    }

    @Test
    void requestCreatesPendingMatch() {
        User target = activeUser(2L);
        when(userRepo.findById(2L)).thenReturn(Optional.of(target));
        when(matchRepo.existsByUserAIdAndUserBIdAndStatus(1L, 2L, MatchStatus.PENDING)).thenReturn(false);
        when(matchRepo.save(any(Match.class))).thenAnswer(i -> i.getArgument(0));

        MatchDto dto = service.request(1L, 2L);
        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.userAId()).isEqualTo(1L);
        assertThat(dto.userBId()).isEqualTo(2L);
        verify(notificationService).notify(eq(2L), eq(NotificationType.MATCH), anyString());
    }

    @Test
    void respondRejectsWhenNotRecipient() {
        when(matchRepo.findByIdAndUserBId(9L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.respond(1L, 9L, "ACCEPTED"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void respondRejectsInvalidStatus() {
        Match m = new Match(); m.setUserAId(2L); m.setUserBId(1L);
        when(matchRepo.findByIdAndUserBId(9L, 1L)).thenReturn(Optional.of(m));
        assertThatThrownBy(() -> service.respond(1L, 9L, "MAYBE"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void respondRejectsPendingStatus() {
        Match m = new Match(); m.setUserAId(2L); m.setUserBId(1L);
        when(matchRepo.findByIdAndUserBId(9L, 1L)).thenReturn(Optional.of(m));
        assertThatThrownBy(() -> service.respond(1L, 9L, "PENDING"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void respondAcceptsMatch() {
        Match m = new Match(); m.setUserAId(2L); m.setUserBId(1L);
        when(matchRepo.findByIdAndUserBId(9L, 1L)).thenReturn(Optional.of(m));
        when(matchRepo.save(any(Match.class))).thenAnswer(i -> i.getArgument(0));

        MatchDto dto = service.respond(1L, 9L, "ACCEPTED");
        assertThat(dto.status()).isEqualTo("ACCEPTED");
        verify(notificationService).notify(eq(2L), eq(NotificationType.MATCH), anyString());
    }

    private User activeUser(Long id) {
        User u = new User();
        u.setEmail("u" + id + "@example.com");
        u.setActive(true);
        try { var f = User.class.getDeclaredField("id"); f.setAccessible(true); f.set(u, id); }
        catch (Exception e) { throw new RuntimeException(e); }
        return u;
    }
}
