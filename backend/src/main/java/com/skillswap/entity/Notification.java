package com.skillswap.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    void onCreate() { if (createdDate == null) createdDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public NotificationType getType() { return type; }
    public void setType(NotificationType v) { this.type = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
    public boolean isRead() { return read; }
    public void setRead(boolean v) { this.read = v; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
