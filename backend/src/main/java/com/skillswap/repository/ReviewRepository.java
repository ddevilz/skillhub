package com.skillswap.repository;

import com.skillswap.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsBySessionIdAndReviewerUserId(Long sessionId, Long reviewerUserId);
    List<Review> findByRatedUserId(Long ratedUserId);
    long countByRatedUserId(Long ratedUserId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.ratedUserId = :userId")
    Double averageRatingFor(@Param("userId") Long userId);
}
