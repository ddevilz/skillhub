package com.skillswap.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private Long reviewerUserId;

    @Column(nullable = false)
    private Long ratedUserId;

    @Column(nullable = false)
    private int rating;

    @Column(length = 255)
    private String comments;

    @Column(nullable = false)
    private boolean flagged = false;

    @Column(nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP") // mirrors V6__reviews.sql DEFAULT CURRENT_TIMESTAMP; only affects
    // Hibernate's ddl-auto=create-drop test schema (Flyway owns the real schema, ddl-auto=none there).
    // Needed so raw-JDBC test inserts (bypassing @PrePersist) satisfy the NOT NULL column.
    private LocalDateTime createdDate;

    @PrePersist
    void onCreate() { if (createdDate == null) createdDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long v) { this.sessionId = v; }
    public Long getReviewerUserId() { return reviewerUserId; }
    public void setReviewerUserId(Long v) { this.reviewerUserId = v; }
    public Long getRatedUserId() { return ratedUserId; }
    public void setRatedUserId(Long v) { this.ratedUserId = v; }
    public int getRating() { return rating; }
    public void setRating(int v) { this.rating = v; }
    public String getComments() { return comments; }
    public void setComments(String v) { this.comments = v; }
    public boolean isFlagged() { return flagged; }
    public void setFlagged(boolean v) { this.flagged = v; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
