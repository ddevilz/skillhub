package com.skillswap.repository;

import com.skillswap.entity.ForumPostUpvote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForumPostUpvoteRepository extends JpaRepository<ForumPostUpvote, Long> {
    boolean existsByPostIdAndUserId(Long postId, Long userId);
    long countByPostId(Long postId);
}
