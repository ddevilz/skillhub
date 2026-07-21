package com.skillswap.repository;

import com.skillswap.entity.ForumComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ForumCommentRepository extends JpaRepository<ForumComment, Long> {

    // See ForumPostRepository.findByCategoryIdAndIsModeratedFalse for why this
    // needs an explicit @Query instead of name-derivation.
    @Query("SELECT c FROM ForumComment c WHERE c.postId = :postId AND c.moderated = false")
    List<ForumComment> findByPostIdAndIsModeratedFalse(@Param("postId") Long postId);

    @Query("SELECT COUNT(c) FROM ForumComment c WHERE c.postId = :postId AND c.moderated = false")
    long countByPostIdAndIsModeratedFalse(@Param("postId") Long postId);
}
