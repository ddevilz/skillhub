package com.skillswap.repository;

import com.skillswap.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedDateDesc(Long userId);
    long countByUserIdAndRead(Long userId, boolean read);
    Optional<Notification> findByIdAndUserId(Long id, Long userId);
}
