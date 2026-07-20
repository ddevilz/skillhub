package com.skillswap.service;

import com.skillswap.dto.CreateReviewRequest;
import com.skillswap.dto.RatingSummaryDto;
import com.skillswap.dto.ReviewDto;
import com.skillswap.entity.NotificationType;
import com.skillswap.entity.Review;
import com.skillswap.entity.Session;
import com.skillswap.entity.SessionStatus;
import com.skillswap.repository.ReviewRepository;
import com.skillswap.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewServiceTest {

    private final SessionRepository sessionRepo = mock(SessionRepository.class);
    private final ReviewRepository reviewRepo = mock(ReviewRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final ReviewService service = new ReviewService(sessionRepo, reviewRepo, notificationService);

    private Session completedSession(Long teacher, Long learner) {
        Session s = new Session();
        s.setTeacherUserId(teacher);
        s.setLearnerUserId(learner);
        s.setStatus(SessionStatus.COMPLETED);
        return s;
    }

    @Test
    void createRejectsWhenSessionNotFound() {
        when(sessionRepo.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(10L, 5L, new CreateReviewRequest(5, "great")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createRejectsWhenCallerNotParticipant() {
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(completedSession(10L, 20L)));
        assertThatThrownBy(() -> service.create(999L, 5L, new CreateReviewRequest(5, "great")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createRejectsWhenSessionNotCompleted() {
        Session s = completedSession(10L, 20L);
        s.setStatus(SessionStatus.CONFIRMED);
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.create(10L, 5L, new CreateReviewRequest(5, "great")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createRejectsDuplicateReview() {
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(completedSession(10L, 20L)));
        when(reviewRepo.existsBySessionIdAndReviewerUserId(5L, 10L)).thenReturn(true);
        assertThatThrownBy(() -> service.create(10L, 5L, new CreateReviewRequest(5, "great")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createPersistsWithRatedUserAsTheOtherParticipant() {
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(completedSession(10L, 20L)));
        when(reviewRepo.existsBySessionIdAndReviewerUserId(5L, 10L)).thenReturn(false);
        when(reviewRepo.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        ReviewDto dto = service.create(10L, 5L, new CreateReviewRequest(4, "good session"));

        assertThat(dto.reviewerUserId()).isEqualTo(10L);
        assertThat(dto.ratedUserId()).isEqualTo(20L);
        assertThat(dto.rating()).isEqualTo(4);
        verify(notificationService).notify(eq(20L), eq(NotificationType.REVIEW), anyString());
    }

    @Test
    void flagSetsFlaggedTrue() {
        Review r = new Review();
        r.setFlagged(false);
        when(reviewRepo.findById(9L)).thenReturn(Optional.of(r));

        service.flag(9L);

        assertThat(r.isFlagged()).isTrue();
        verify(reviewRepo).save(r);
    }

    @Test
    void flagRejectsWhenReviewNotFound() {
        when(reviewRepo.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.flag(9L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void ratingSummaryComputesAverageAndCount() {
        when(reviewRepo.averageRatingFor(1L)).thenReturn(4.5);
        when(reviewRepo.countByRatedUserId(1L)).thenReturn(2L);

        RatingSummaryDto dto = service.ratingSummary(1L);

        assertThat(dto.averageRating()).isEqualTo(4.5);
        assertThat(dto.reviewCount()).isEqualTo(2L);
    }

    @Test
    void ratingSummaryDefaultsToZeroWhenNoReviews() {
        when(reviewRepo.averageRatingFor(1L)).thenReturn(null);
        when(reviewRepo.countByRatedUserId(1L)).thenReturn(0L);

        RatingSummaryDto dto = service.ratingSummary(1L);

        assertThat(dto.averageRating()).isEqualTo(0.0);
        assertThat(dto.reviewCount()).isZero();
    }
}
