package com.skillswap.repository;

import com.skillswap.entity.ForumPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ForumPostRepository extends JpaRepository<ForumPost, Long> {

    // Spring Data can't derive this from method name: the entity field is
    // `moderated` (getter isModerated()), not a literal `isModerated` property,
    // so an explicit @Query replaces name-derivation here (same fix shape as
    // searchByKeyword below). See NotificationRepository for the same boolean-
    // field shape already handled this way elsewhere in this codebase.
    @Query("SELECT p FROM ForumPost p WHERE p.categoryId = :categoryId AND p.moderated = false")
    List<ForumPost> findByCategoryIdAndIsModeratedFalse(@Param("categoryId") Long categoryId);

    @Query("""
        SELECT p FROM ForumPost p
        WHERE p.moderated = false
          AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(CAST(p.content AS string)) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
    List<ForumPost> searchByKeyword(@Param("keyword") String keyword);
}
