package com.skillswap.service;

import com.skillswap.dto.*;
import com.skillswap.entity.*;
import com.skillswap.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ForumService {

    private final ForumCategoryRepository categoryRepository;
    private final ForumPostRepository postRepository;
    private final ForumCommentRepository commentRepository;
    private final ForumPostUpvoteRepository upvoteRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ForumService(ForumCategoryRepository categoryRepository, ForumPostRepository postRepository,
                        ForumCommentRepository commentRepository, ForumPostUpvoteRepository upvoteRepository,
                        UserRepository userRepository, NotificationService notificationService) {
        this.categoryRepository = categoryRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.upvoteRepository = upvoteRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    public List<ForumCategoryDto> categories() {
        return categoryRepository.findAll().stream()
                .map(c -> new ForumCategoryDto(c.getId(), c.getCategoryName(), c.getDescription()))
                .toList();
    }

    public List<ForumPostDto> postsByCategory(Long categoryId) {
        return postRepository.findByCategoryIdAndIsModeratedFalse(categoryId).stream()
                .map(this::toDto).toList();
    }

    public List<ForumPostDto> search(String keyword) {
        return postRepository.searchByKeyword(keyword).stream().map(this::toDto).toList();
    }

    public ForumPostDto getPost(Long postId) {
        return toDto(findVisiblePost(postId));
    }

    public ForumPostDto createPost(Long userId, Long categoryId, CreateForumPostRequest req) {
        if (categoryRepository.findById(categoryId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
        }
        ForumPost p = new ForumPost();
        p.setCategoryId(categoryId);
        p.setUserId(userId);
        p.setTitle(req.title());
        p.setContent(req.content());
        return toDto(postRepository.save(p));
    }

    public void deletePost(Long userId, Long postId) {
        ForumPost p = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
        if (!p.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }
        postRepository.delete(p);
    }

    public List<ForumCommentDto> comments(Long postId) {
        return commentRepository.findByPostIdAndIsModeratedFalse(postId).stream()
                .map(this::toDto).toList();
    }

    public ForumCommentDto addComment(Long userId, Long postId, CreateForumCommentRequest req) {
        ForumPost post = findVisiblePost(postId);
        ForumComment c = new ForumComment();
        c.setPostId(postId);
        c.setUserId(userId);
        c.setCommentText(req.commentText());
        ForumComment saved = commentRepository.save(c);
        if (!post.getUserId().equals(userId)) {
            notificationService.notify(post.getUserId(), NotificationType.FORUM, "Someone commented on your post.");
        }
        return toDto(saved);
    }

    public void deleteComment(Long userId, Long commentId) {
        ForumComment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
        if (!c.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found");
        }
        commentRepository.delete(c);
    }

    public ForumPostDto upvote(Long userId, Long postId) {
        findVisiblePost(postId);
        if (upvoteRepository.existsByPostIdAndUserId(postId, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already upvoted this post");
        }
        ForumPostUpvote u = new ForumPostUpvote();
        u.setPostId(postId);
        u.setUserId(userId);
        upvoteRepository.save(u);
        return toDto(postRepository.findById(postId).orElseThrow());
    }

    private ForumPost findVisiblePost(Long postId) {
        ForumPost p = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
        if (p.isModerated()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }
        return p;
    }

    private String authorName(Long userId) {
        return userRepository.findById(userId).map(User::getFullName).orElse(null);
    }

    private ForumPostDto toDto(ForumPost p) {
        long upvotes = upvoteRepository.countByPostId(p.getId());
        long comments = commentRepository.countByPostIdAndIsModeratedFalse(p.getId());
        return new ForumPostDto(p.getId(), p.getCategoryId(), p.getUserId(), authorName(p.getUserId()),
                p.getTitle(), p.getContent(), upvotes, comments, p.getCreatedDate());
    }

    private ForumCommentDto toDto(ForumComment c) {
        return new ForumCommentDto(c.getId(), c.getPostId(), c.getUserId(), authorName(c.getUserId()),
                c.getCommentText(), c.getCreatedDate());
    }
}
