package com.skillswap.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    private String city;
    private String about;
    private String profileImage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    @ColumnDefault("CURRENT_TIMESTAMP") // mirrors V1__init.sql DEFAULT CURRENT_TIMESTAMP; only affects
    // Hibernate's ddl-auto=create-drop test schema (Flyway owns the real schema, ddl-auto=none there).
    // Needed so raw-JDBC test inserts (bypassing @PrePersist) satisfy the NOT NULL column.
    private LocalDateTime createdDate;

    @PrePersist
    void onCreate() { if (createdDate == null) createdDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public void setFullName(String v) { this.fullName = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String v) { this.passwordHash = v; }
    public String getCity() { return city; }
    public void setCity(String v) { this.city = v; }
    public String getAbout() { return about; }
    public void setAbout(String v) { this.about = v; }
    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String v) { this.profileImage = v; }
    public Role getRole() { return role; }
    public void setRole(Role v) { this.role = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
