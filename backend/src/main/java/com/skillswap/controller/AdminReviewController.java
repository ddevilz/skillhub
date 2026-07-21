package com.skillswap.controller;

import com.skillswap.dto.ReviewDto;
import com.skillswap.service.CurrentUser;
import com.skillswap.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final ReviewService reviewService;
    private final CurrentUser currentUser;

    public AdminReviewController(ReviewService reviewService, CurrentUser currentUser) {
        this.reviewService = reviewService;
        this.currentUser = currentUser;
    }

    @GetMapping("/flagged")
    public List<ReviewDto> flagged() {
        currentUser.requireAdmin();
        return reviewService.flaggedReviews();
    }

    @PutMapping("/{id}/unflag")
    public ResponseEntity<Void> unflag(@PathVariable Long id) {
        currentUser.requireAdmin();
        reviewService.unflag(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        currentUser.requireAdmin();
        reviewService.adminDelete(id);
        return ResponseEntity.noContent().build();
    }
}
