package com.skillswap.service;

import com.skillswap.dto.AdminUserDto;
import com.skillswap.entity.Role;
import com.skillswap.entity.User;
import com.skillswap.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminUserServiceTest {

    private final UserRepository userRepo = mock(UserRepository.class);
    private final AdminUserService service = new AdminUserService(userRepo);

    private User user(Long id, String name, String email, boolean active) {
        User u = new User();
        u.setFullName(name);
        u.setEmail(email);
        u.setRole(Role.USER);
        u.setActive(active);
        try { var f = User.class.getDeclaredField("id"); f.setAccessible(true); f.set(u, id); }
        catch (Exception e) { throw new RuntimeException(e); }
        return u;
    }

    @Test
    void listUsersReturnsAllWhenNoFilter() {
        when(userRepo.findAll()).thenReturn(List.of(
                user(1L, "Deva", "deva@example.com", true),
                user(2L, "Sam", "sam@example.com", false)));
        assertThat(service.listUsers(null, null)).hasSize(2);
    }

    @Test
    void listUsersFiltersBySearchTermCaseInsensitive() {
        when(userRepo.findAll()).thenReturn(List.of(
                user(1L, "Deva Jadhav", "deva@example.com", true),
                user(2L, "Sam Patel", "sam@example.com", true)));
        List<AdminUserDto> result = service.listUsers("DEVA", null);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).fullName()).isEqualTo("Deva Jadhav");
    }

    @Test
    void listUsersFiltersByActiveStatus() {
        when(userRepo.findAll()).thenReturn(List.of(
                user(1L, "Deva", "deva@example.com", true),
                user(2L, "Sam", "sam@example.com", false)));
        assertThat(service.listUsers(null, false)).hasSize(1).extracting(AdminUserDto::id).containsExactly(2L);
    }

    @Test
    void updateStatusDeactivatesUser() {
        User u = user(1L, "Deva", "deva@example.com", true);
        when(userRepo.findById(1L)).thenReturn(Optional.of(u));
        when(userRepo.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        AdminUserDto dto = service.updateStatus(1L, false);

        assertThat(dto.active()).isFalse();
    }

    @Test
    void updateStatusRejectsWhenUserNotFound() {
        when(userRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateStatus(99L, false)).isInstanceOf(ResponseStatusException.class);
    }
}
