package com.skillswap.service;

import com.skillswap.entity.Notification;
import com.skillswap.entity.NotificationType;
import com.skillswap.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** Creates the in-app row; the "email" is logged, not sent (no SMTP dependency by design). */
    public void notify(Long userId, NotificationType type, String message) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setMessage(message);
        notificationRepository.save(n);
        log.info("EMAIL (logged, not sent) to user {}: [{}] {}", userId, type, message);
    }

    public List<Notification> list(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedDateDesc(userId);
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndRead(userId, false);
    }

    public void markRead(Long userId, Long notificationId) {
        Notification n = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        n.setRead(true);
        notificationRepository.save(n);
    }
}
