package com.skillswap.service;

import com.skillswap.entity.Notification;
import com.skillswap.entity.NotificationType;
import com.skillswap.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationServiceTest {

    private final NotificationRepository repo = mock(NotificationRepository.class);
    private final NotificationService service = new NotificationService(repo);

    @Test
    void notifyPersistsRow() {
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        service.notify(1L, NotificationType.MATCH, "hello");
        verify(repo).save(captor.capture());
        Notification n = captor.getValue();
        assertThat(n.getUserId()).isEqualTo(1L);
        assertThat(n.getType()).isEqualTo(NotificationType.MATCH);
        assertThat(n.getMessage()).isEqualTo("hello");
        assertThat(n.isRead()).isFalse();
    }

    @Test
    void listReturnsRepositoryResultUnmodified() {
        Notification n = new Notification();
        when(repo.findByUserIdOrderByCreatedDateDesc(1L)).thenReturn(List.of(n));
        assertThat(service.list(1L)).containsExactly(n);
    }

    @Test
    void unreadCountDelegatesToRepository() {
        when(repo.countByUserIdAndRead(1L, false)).thenReturn(3L);
        assertThat(service.unreadCount(1L)).isEqualTo(3L);
    }

    @Test
    void markReadSetsReadTrue() {
        Notification n = new Notification();
        n.setRead(false);
        when(repo.findByIdAndUserId(9L, 1L)).thenReturn(Optional.of(n));

        service.markRead(1L, 9L);

        assertThat(n.isRead()).isTrue();
        verify(repo).save(n);
    }

    @Test
    void markReadRejectsWhenNotFoundOrNotOwned() {
        when(repo.findByIdAndUserId(9L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.markRead(1L, 9L)).isInstanceOf(ResponseStatusException.class);
    }
}
