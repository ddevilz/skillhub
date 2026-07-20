package com.skillswap.controller;

import com.skillswap.dto.*;
import com.skillswap.service.CurrentUser;
import com.skillswap.service.ForumService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/forum")
public class ForumController {

    private final ForumService forumService;
    private final CurrentUser currentUser;

    public ForumController(ForumService forumService, CurrentUser currentUser) {
        this.forumService = forumService;
        this.currentUser = currentUser;
    }

    @GetMapping("/categories")
    public List<ForumCategoryDto> categories() {
        return forumService.categories();
    }

    @GetMapping("/categories/{id}/posts")
    public List<ForumPostDto> postsByCategory(@PathVariable Long id) {
        return forumService.postsByCategory(id);
    }

    @PostMapping("/categories/{id}/posts")
    public ResponseEntity<ForumPostDto> createPost(@PathVariable Long id, @Valid @RequestBody CreateForumPostRequest req) {
        ForumPostDto dto = forumService.createPost(currentUser.require().getId(), id, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/posts/search")
    public List<ForumPostDto> search(@RequestParam String keyword) {
        return forumService.search(keyword);
    }

    @GetMapping("/posts/{id}")
    public ForumPostDto getPost(@PathVariable Long id) {
        return forumService.getPost(id);
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Void> deletePost(@PathVariable Long id) {
        forumService.deletePost(currentUser.require().getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/posts/{id}/comments")
    public List<ForumCommentDto> comments(@PathVariable Long id) {
        return forumService.comments(id);
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<ForumCommentDto> addComment(@PathVariable Long id, @Valid @RequestBody CreateForumCommentRequest req) {
        ForumCommentDto dto = forumService.addComment(currentUser.require().getId(), id, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/comments/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable Long id) {
        forumService.deleteComment(currentUser.require().getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/posts/{id}/upvote")
    public ResponseEntity<ForumPostDto> upvote(@PathVariable Long id) {
        ForumPostDto dto = forumService.upvote(currentUser.require().getId(), id);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}
