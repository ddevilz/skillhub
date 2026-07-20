package com.skillswap.controller;

import com.skillswap.dto.NotificationDto;
import com.skillswap.dto.UnreadCountDto;
import com.skillswap.service.CurrentUser;
import com.skillswap.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService notificationService, CurrentUser currentUser) {
        this.notificationService = notificationService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<NotificationDto> list() {
        Long userId = currentUser.require().getId();
        return notificationService.list(userId).stream()
                .map(n -> new NotificationDto(n.getId(), n.getType().name(), n.getMessage(), n.isRead(), n.getCreatedDate()))
                .toList();
    }

    @GetMapping("/unread-count")
    public UnreadCountDto unreadCount() {
        return new UnreadCountDto(notificationService.unreadCount(currentUser.require().getId()));
    }

    @PutMapping("/{id}/read")
    public void markRead(@PathVariable Long id) {
        notificationService.markRead(currentUser.require().getId(), id);
    }
}
