package com.skillswap.controller;

import com.skillswap.dto.CreateReviewRequest;
import com.skillswap.dto.RatingSummaryDto;
import com.skillswap.dto.ReviewDto;
import com.skillswap.service.CurrentUser;
import com.skillswap.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUser currentUser;

    public ReviewController(ReviewService reviewService, CurrentUser currentUser) {
        this.reviewService = reviewService;
        this.currentUser = currentUser;
    }

    @PostMapping("/sessions/{id}/review")
    public ResponseEntity<ReviewDto> createReview(@PathVariable Long id, @Valid @RequestBody CreateReviewRequest req) {
        ReviewDto dto = reviewService.create(currentUser.require().getId(), id, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/reviews/{id}/flag")
    public ResponseEntity<Void> flagReview(@PathVariable Long id) {
        reviewService.flag(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{id}/rating")
    public RatingSummaryDto rating(@PathVariable Long id) {
        return reviewService.ratingSummary(id);
    }
}
