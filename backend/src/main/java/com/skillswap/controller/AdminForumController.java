package com.skillswap.controller;

import com.skillswap.service.CurrentUser;
import com.skillswap.service.ForumService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/forum")
public class AdminForumController {

    private final ForumService forumService;
    private final CurrentUser currentUser;

    public AdminForumController(ForumService forumService, CurrentUser currentUser) {
        this.forumService = forumService;
        this.currentUser = currentUser;
    }

    @PutMapping("/posts/{id}/moderate")
    public ResponseEntity<Void> moderatePost(@PathVariable Long id) {
        currentUser.requireAdmin();
        forumService.moderatePost(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        currentUser.requireAdmin();
        forumService.adminDeletePost(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/comments/{id}/moderate")
    public ResponseEntity<Void> moderateComment(@PathVariable Long id) {
        currentUser.requireAdmin();
        forumService.moderateComment(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id) {
        currentUser.requireAdmin();
        forumService.adminDeleteComment(id);
        return ResponseEntity.noContent().build();
    }
}
