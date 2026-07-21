package com.skillswap.controller;

import com.skillswap.dto.CreateForumCategoryRequest;
import com.skillswap.dto.ForumCategoryDto;
import com.skillswap.dto.ForumCommentDto;
import com.skillswap.dto.ForumPostDto;
import com.skillswap.service.CurrentUser;
import com.skillswap.service.ForumService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/forum")
public class AdminForumController {

    private final ForumService forumService;
    private final CurrentUser currentUser;

    public AdminForumController(ForumService forumService, CurrentUser currentUser) {
        this.forumService = forumService;
        this.currentUser = currentUser;
    }

    @GetMapping("/posts/moderated")
    public List<ForumPostDto> moderatedPosts() {
        currentUser.requireAdmin();
        return forumService.moderatedPosts();
    }

    @GetMapping("/comments/moderated")
    public List<ForumCommentDto> moderatedComments() {
        currentUser.requireAdmin();
        return forumService.moderatedComments();
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

    @PostMapping("/categories")
    public ResponseEntity<ForumCategoryDto> createCategory(@Valid @RequestBody CreateForumCategoryRequest req) {
        currentUser.requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(forumService.createCategory(req));
    }

    @PutMapping("/categories/{id}")
    public ForumCategoryDto updateCategory(@PathVariable Long id, @Valid @RequestBody CreateForumCategoryRequest req) {
        currentUser.requireAdmin();
        return forumService.updateCategory(id, req);
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        currentUser.requireAdmin();
        forumService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}
