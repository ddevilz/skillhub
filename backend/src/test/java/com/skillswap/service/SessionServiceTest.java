package com.skillswap.service;

import com.skillswap.dto.CreateSessionRequest;
import com.skillswap.dto.RescheduleSessionRequest;
import com.skillswap.dto.SessionDto;
import com.skillswap.entity.Match;
import com.skillswap.entity.MatchStatus;
import com.skillswap.entity.Session;
import com.skillswap.entity.SessionStatus;
import com.skillswap.repository.MatchRepository;
import com.skillswap.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionServiceTest {

    private final SessionRepository sessionRepo = mock(SessionRepository.class);
    private final MatchRepository matchRepo = mock(MatchRepository.class);
    private final CreditService creditService = mock(CreditService.class);
    private final SessionService service = new SessionService(sessionRepo, matchRepo, creditService);

    private Match acceptedMatch(Long a, Long b) {
        Match m = new Match();
        m.setUserAId(a);
        m.setUserBId(b);
        m.setStatus(MatchStatus.ACCEPTED);
        return m;
    }

    private CreateSessionRequest req(Long matchId, Long teacherId) {
        return new CreateSessionRequest(matchId, teacherId, LocalDate.of(2026, 8, 1),
                LocalTime.of(10, 0), LocalTime.of(11, 0), "ONLINE", "https://meet.example/abc");
    }

    @Test
    void createRejectsWhenMatchNotFound() {
        when(matchRepo.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(10L, req(1L, 10L)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createRejectsWhenRequesterNotParticipant() {
        when(matchRepo.findById(1L)).thenReturn(Optional.of(acceptedMatch(20L, 30L)));
        assertThatThrownBy(() -> service.create(10L, req(1L, 20L)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createRejectsWhenMatchNotAccepted() {
        Match m = acceptedMatch(10L, 20L);
        m.setStatus(MatchStatus.PENDING);
        when(matchRepo.findById(1L)).thenReturn(Optional.of(m));
        assertThatThrownBy(() -> service.create(10L, req(1L, 10L)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createRejectsWhenTeacherNotParticipant() {
        when(matchRepo.findById(1L)).thenReturn(Optional.of(acceptedMatch(10L, 20L)));
        assertThatThrownBy(() -> service.create(10L, req(1L, 999L)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createRejectsWhenLearnerCannotAfford() {
        when(matchRepo.findById(1L)).thenReturn(Optional.of(acceptedMatch(10L, 20L)));
        when(creditService.canAfford(20L)).thenReturn(false); // 10 = teacher, 20 = learner
        assertThatThrownBy(() -> service.create(10L, req(1L, 10L)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createPersistsPendingSessionWithCorrectRoles() {
        when(matchRepo.findById(1L)).thenReturn(Optional.of(acceptedMatch(10L, 20L)));
        when(creditService.canAfford(20L)).thenReturn(true);
        when(sessionRepo.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        SessionDto dto = service.create(10L, req(1L, 10L));

        assertThat(dto.teacherUserId()).isEqualTo(10L);
        assertThat(dto.learnerUserId()).isEqualTo(20L);
        assertThat(dto.scheduledByUserId()).isEqualTo(10L);
        assertThat(dto.status()).isEqualTo("PENDING");
    }

    @Test
    void confirmRejectsWhenCalledByScheduler() {
        Session s = new Session();
        s.setTeacherUserId(10L); s.setLearnerUserId(20L); s.setScheduledByUserId(10L);
        s.setStatus(SessionStatus.PENDING);
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.confirm(10L, 5L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void confirmSucceedsForOtherParticipant() {
        Session s = new Session();
        s.setTeacherUserId(10L); s.setLearnerUserId(20L); s.setScheduledByUserId(10L);
        s.setStatus(SessionStatus.PENDING);
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(s));
        when(sessionRepo.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        SessionDto dto = service.confirm(20L, 5L);
        assertThat(dto.status()).isEqualTo("CONFIRMED");
    }

    @Test
    void cancelRejectsWhenAlreadyFinalized() {
        Session s = new Session();
        s.setTeacherUserId(10L); s.setLearnerUserId(20L); s.setScheduledByUserId(10L);
        s.setStatus(SessionStatus.CANCELLED);
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.cancel(10L, 5L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rescheduleResetsStatusAndReassignsScheduler() {
        Session s = new Session();
        s.setTeacherUserId(10L); s.setLearnerUserId(20L); s.setScheduledByUserId(10L);
        s.setStatus(SessionStatus.CONFIRMED);
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(s));
        when(sessionRepo.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        RescheduleSessionRequest req = new RescheduleSessionRequest(
                LocalDate.of(2026, 8, 2), LocalTime.of(9, 0), LocalTime.of(10, 0));
        SessionDto dto = service.reschedule(20L, 5L, req);

        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.scheduledByUserId()).isEqualTo(20L);
        assertThat(dto.sessionDate()).isEqualTo(LocalDate.of(2026, 8, 2));
    }

    @Test
    void completeRejectsWhenNotConfirmed() {
        Session s = new Session();
        s.setTeacherUserId(10L); s.setLearnerUserId(20L); s.setScheduledByUserId(10L);
        s.setStatus(SessionStatus.PENDING);
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.complete(10L, 5L)).isInstanceOf(ResponseStatusException.class);
        verify(creditService, never()).settle(any(), any(), any());
    }

    @Test
    void completeSettlesCreditsOnSuccess() {
        Session s = new Session();
        s.setTeacherUserId(10L); s.setLearnerUserId(20L); s.setScheduledByUserId(10L);
        s.setStatus(SessionStatus.CONFIRMED);
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(s));
        when(sessionRepo.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        SessionDto dto = service.complete(20L, 5L);

        verify(creditService).settle(10L, 20L, 5L);
        assertThat(dto.status()).isEqualTo("COMPLETED");
    }

    @Test
    void mySessionsFiltersByStatus() {
        Session pending = new Session();
        pending.setTeacherUserId(10L); pending.setLearnerUserId(20L);
        pending.setStatus(SessionStatus.PENDING); pending.setSessionDate(LocalDate.of(2026, 8, 1));
        pending.setStartTime(LocalTime.NOON);

        Session cancelled = new Session();
        cancelled.setTeacherUserId(10L); cancelled.setLearnerUserId(20L);
        cancelled.setStatus(SessionStatus.CANCELLED); cancelled.setSessionDate(LocalDate.of(2026, 7, 1));
        cancelled.setStartTime(LocalTime.NOON);

        when(sessionRepo.findByTeacherUserIdOrLearnerUserId(10L, 10L)).thenReturn(List.of(pending, cancelled));

        assertThat(service.mySessions(10L, "upcoming")).hasSize(1);
        assertThat(service.mySessions(10L, "cancelled")).hasSize(1);
        assertThat(service.mySessions(10L, "past")).isEmpty();
    }

    @Test
    void confirmRejectsNonParticipant() {
        Session s = new Session();
        s.setTeacherUserId(10L); s.setLearnerUserId(20L); s.setScheduledByUserId(10L);
        s.setStatus(SessionStatus.PENDING);
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.confirm(999L, 5L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void cancelSucceedsFromPending() {
        Session s = new Session();
        s.setTeacherUserId(10L); s.setLearnerUserId(20L); s.setScheduledByUserId(10L);
        s.setStatus(SessionStatus.PENDING);
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(s));
        when(sessionRepo.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        SessionDto dto = service.cancel(20L, 5L);
        assertThat(dto.status()).isEqualTo("CANCELLED");
    }

    @Test
    void confirmRejectsWhenAlreadyFinalized() {
        Session s = new Session();
        s.setTeacherUserId(10L); s.setLearnerUserId(20L); s.setScheduledByUserId(10L);
        s.setStatus(SessionStatus.CANCELLED);
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.confirm(20L, 5L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rescheduleRejectsWhenAlreadyFinalized() {
        Session s = new Session();
        s.setTeacherUserId(10L); s.setLearnerUserId(20L); s.setScheduledByUserId(10L);
        s.setStatus(SessionStatus.COMPLETED);
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(s));
        RescheduleSessionRequest req = new RescheduleSessionRequest(
                LocalDate.of(2026, 8, 2), LocalTime.of(9, 0), LocalTime.of(10, 0));
        assertThatThrownBy(() -> service.reschedule(10L, 5L, req)).isInstanceOf(ResponseStatusException.class);
    }
}
