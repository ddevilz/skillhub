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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {

    private final SessionRepository sessionRepository;
    private final ReviewRepository reviewRepository;
    private final NotificationService notificationService;

    public ReviewService(SessionRepository sessionRepository, ReviewRepository reviewRepository,
                         NotificationService notificationService) {
        this.sessionRepository = sessionRepository;
        this.reviewRepository = reviewRepository;
        this.notificationService = notificationService;
    }

    public ReviewDto create(Long meId, Long sessionId, CreateReviewRequest req) {
        Session s = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (!s.getTeacherUserId().equals(meId) && !s.getLearnerUserId().equals(meId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        if (s.getStatus() != SessionStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is not completed yet");
        }
        if (reviewRepository.existsBySessionIdAndReviewerUserId(sessionId, meId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this session");
        }
        Long ratedUserId = s.getTeacherUserId().equals(meId) ? s.getLearnerUserId() : s.getTeacherUserId();

        Review r = new Review();
        r.setSessionId(sessionId);
        r.setReviewerUserId(meId);
        r.setRatedUserId(ratedUserId);
        r.setRating(req.rating());
        r.setComments(req.comments());
        Review saved = reviewRepository.save(r);
        notificationService.notify(ratedUserId, NotificationType.REVIEW, "You received a new review.");
        return toDto(saved);
    }

    public void flag(Long reviewId) {
        Review r = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        r.setFlagged(true);
        reviewRepository.save(r);
    }

    public RatingSummaryDto ratingSummary(Long userId) {
        Double avg = reviewRepository.averageRatingFor(userId);
        long count = reviewRepository.countByRatedUserId(userId);
        return new RatingSummaryDto(avg == null ? 0.0 : avg, count);
    }

    private ReviewDto toDto(Review r) {
        return new ReviewDto(r.getId(), r.getSessionId(), r.getReviewerUserId(), r.getRatedUserId(),
                r.getRating(), r.getComments(), r.isFlagged(), r.getCreatedDate());
    }
}
