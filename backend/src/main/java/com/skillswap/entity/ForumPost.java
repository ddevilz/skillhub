package com.skillswap.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Table(name = "forum_posts")
public class ForumPost {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(name = "is_moderated", nullable = false)
    private boolean moderated = false;

    @Column(nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP") // mirrors V9__forums.sql DEFAULT CURRENT_TIMESTAMP; only affects
    // Hibernate's ddl-auto=create-drop test schema (Flyway owns the real schema, ddl-auto=none there).
    // Needed so raw-JDBC test inserts (bypassing @PrePersist) satisfy the NOT NULL column.
    private LocalDateTime createdDate;

    @PrePersist
    void onCreate() { if (createdDate == null) createdDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long v) { this.categoryId = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getContent() { return content; }
    public void setContent(String v) { this.content = v; }
    public boolean isModerated() { return moderated; }
    public void setModerated(boolean v) { this.moderated = v; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
