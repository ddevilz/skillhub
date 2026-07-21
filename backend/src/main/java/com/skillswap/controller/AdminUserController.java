package com.skillswap.controller;

import com.skillswap.dto.AdminUserDto;
import com.skillswap.dto.UpdateUserStatusRequest;
import com.skillswap.service.AdminUserService;
import com.skillswap.service.BadgeService;
import com.skillswap.service.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final BadgeService badgeService;
    private final CurrentUser currentUser;

    public AdminUserController(AdminUserService adminUserService, BadgeService badgeService, CurrentUser currentUser) {
        this.adminUserService = adminUserService;
        this.badgeService = badgeService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<AdminUserDto> listUsers(@RequestParam(required = false) String search,
                                        @RequestParam(required = false) Boolean active) {
        currentUser.requireAdmin();
        return adminUserService.listUsers(search, active);
    }

    @PutMapping("/{id}/status")
    public AdminUserDto updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateUserStatusRequest req) {
        currentUser.requireAdmin();
        return adminUserService.updateStatus(id, req.active());
    }

    @PostMapping("/{id}/skills/{skillId}/verify")
    public ResponseEntity<Void> verifySkill(@PathVariable Long id, @PathVariable Long skillId) {
        currentUser.requireAdmin();
        badgeService.awardVerified(id, skillId);
        return ResponseEntity.ok().build();
    }
}
