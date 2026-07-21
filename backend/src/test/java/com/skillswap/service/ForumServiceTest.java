package com.skillswap.service;

import com.skillswap.dto.CreateForumCategoryRequest;
import com.skillswap.dto.CreateForumCommentRequest;
import com.skillswap.dto.CreateForumPostRequest;
import com.skillswap.dto.ForumCategoryDto;
import com.skillswap.dto.ForumCommentDto;
import com.skillswap.dto.ForumPostDto;
import com.skillswap.entity.*;
import com.skillswap.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ForumServiceTest {

    private final ForumCategoryRepository categoryRepo = mock(ForumCategoryRepository.class);
    private final ForumPostRepository postRepo = mock(ForumPostRepository.class);
    private final ForumCommentRepository commentRepo = mock(ForumCommentRepository.class);
    private final ForumPostUpvoteRepository upvoteRepo = mock(ForumPostUpvoteRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final ForumService service = new ForumService(
            categoryRepo, postRepo, commentRepo, upvoteRepo, userRepo, notificationService);

    private User user(Long id, String name) {
        User u = new User();
        u.setFullName(name);
        try { var f = User.class.getDeclaredField("id"); f.setAccessible(true); f.set(u, id); }
        catch (Exception e) { throw new RuntimeException(e); }
        return u;
    }

    private ForumPost post(Long id, Long userId, boolean moderated) {
        ForumPost p = new ForumPost();
        try { var f = ForumPost.class.getDeclaredField("id"); f.setAccessible(true); f.set(p, id); }
        catch (Exception e) { throw new RuntimeException(e); }
        p.setUserId(userId);
        p.setModerated(moderated);
        return p;
    }

    @Test
    void createPostRejectsWhenCategoryNotFound() {
        when(categoryRepo.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createPost(10L, 1L, new CreateForumPostRequest("Hi", "Body")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createPostPersistsWithAuthor() {
        ForumCategory cat = new ForumCategory();
        cat.setCategoryName("General Discussion");
        when(categoryRepo.findById(1L)).thenReturn(Optional.of(cat));
        when(postRepo.save(any(ForumPost.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepo.findById(10L)).thenReturn(Optional.of(user(10L, "Deva")));
        when(commentRepo.countByPostIdAndIsModeratedFalse(any())).thenReturn(0L);
        when(upvoteRepo.countByPostId(any())).thenReturn(0L);

        ForumPostDto dto = service.createPost(10L, 1L, new CreateForumPostRequest("Hi", "Body"));

        assertThat(dto.userId()).isEqualTo(10L);
        assertThat(dto.authorName()).isEqualTo("Deva");
        assertThat(dto.title()).isEqualTo("Hi");
    }

    @Test
    void getPostRejectsWhenModerated() {
        when(postRepo.findById(5L)).thenReturn(Optional.of(post(5L, 10L, true)));
        assertThatThrownBy(() -> service.getPost(5L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getPostRejectsWhenNotFound() {
        when(postRepo.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getPost(5L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deletePostRejectsWhenNotAuthor() {
        when(postRepo.findById(5L)).thenReturn(Optional.of(post(5L, 10L, false)));
        assertThatThrownBy(() -> service.deletePost(999L, 5L)).isInstanceOf(ResponseStatusException.class);
        verify(postRepo, never()).delete(any());
    }

    @Test
    void deletePostSucceedsForAuthor() {
        ForumPost p = post(5L, 10L, false);
        when(postRepo.findById(5L)).thenReturn(Optional.of(p));
        service.deletePost(10L, 5L);
        verify(postRepo).delete(p);
    }

    @Test
    void addCommentRejectsWhenPostModerated() {
        when(postRepo.findById(5L)).thenReturn(Optional.of(post(5L, 10L, true)));
        assertThatThrownBy(() -> service.addComment(20L, 5L, new CreateForumCommentRequest("Nice")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void addCommentNotifiesAuthorWhenCommenterIsDifferent() {
        when(postRepo.findById(5L)).thenReturn(Optional.of(post(5L, 10L, false)));
        when(commentRepo.save(any(ForumComment.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepo.findById(20L)).thenReturn(Optional.of(user(20L, "Commenter")));

        ForumCommentDto dto = service.addComment(20L, 5L, new CreateForumCommentRequest("Nice post!"));

        assertThat(dto.commentText()).isEqualTo("Nice post!");
        verify(notificationService).notify(eq(10L), eq(NotificationType.FORUM), anyString());
    }

    @Test
    void addCommentDoesNotNotifySelf() {
        when(postRepo.findById(5L)).thenReturn(Optional.of(post(5L, 10L, false)));
        when(commentRepo.save(any(ForumComment.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepo.findById(10L)).thenReturn(Optional.of(user(10L, "Author")));

        service.addComment(10L, 5L, new CreateForumCommentRequest("Adding my own thought"));

        verify(notificationService, never()).notify(any(), any(), any());
    }

    @Test
    void upvoteRejectsDuplicate() {
        when(postRepo.findById(5L)).thenReturn(Optional.of(post(5L, 10L, false)));
        when(upvoteRepo.existsByPostIdAndUserId(5L, 20L)).thenReturn(true);
        assertThatThrownBy(() -> service.upvote(20L, 5L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void upvoteSucceedsOnce() {
        when(postRepo.findById(5L)).thenReturn(Optional.of(post(5L, 10L, false)));
        when(upvoteRepo.existsByPostIdAndUserId(5L, 20L)).thenReturn(false);
        when(upvoteRepo.save(any(ForumPostUpvote.class))).thenAnswer(i -> i.getArgument(0));
        when(userRepo.findById(10L)).thenReturn(Optional.of(user(10L, "Author")));
        when(commentRepo.countByPostIdAndIsModeratedFalse(5L)).thenReturn(0L);
        when(upvoteRepo.countByPostId(5L)).thenReturn(1L);

        ForumPostDto dto = service.upvote(20L, 5L);

        assertThat(dto.upvoteCount()).isEqualTo(1L);
    }

    @Test
    void moderatePostSetsModeratedTrue() {
        ForumPost p = post(5L, 10L, false);
        when(postRepo.findById(5L)).thenReturn(Optional.of(p));

        service.moderatePost(5L);

        assertThat(p.isModerated()).isTrue();
        verify(postRepo).save(p);
    }

    @Test
    void moderatePostRejectsWhenNotFound() {
        when(postRepo.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.moderatePost(5L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void adminDeletePostRemovesAnyPostRegardlessOfOwner() {
        ForumPost p = post(5L, 10L, false);
        when(postRepo.findById(5L)).thenReturn(Optional.of(p));
        service.adminDeletePost(5L);
        verify(postRepo).delete(p);
    }

    @Test
    void moderateCommentSetsModeratedTrue() {
        ForumComment c = new ForumComment();
        c.setUserId(20L);
        when(commentRepo.findById(9L)).thenReturn(Optional.of(c));

        service.moderateComment(9L);

        assertThat(c.isModerated()).isTrue();
        verify(commentRepo).save(c);
    }

    @Test
    void adminDeleteCommentRemovesAnyCommentRegardlessOfOwner() {
        ForumComment c = new ForumComment();
        c.setUserId(20L);
        when(commentRepo.findById(9L)).thenReturn(Optional.of(c));
        service.adminDeleteComment(9L);
        verify(commentRepo).delete(c);
    }

    @Test
    void createCategoryPersists() {
        when(categoryRepo.save(any(ForumCategory.class))).thenAnswer(i -> i.getArgument(0));
        ForumCategoryDto dto = service.createCategory(new CreateForumCategoryRequest("Gaming", "Video games"));
        assertThat(dto.categoryName()).isEqualTo("Gaming");
    }

    @Test
    void updateCategoryRejectsWhenNotFound() {
        when(categoryRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateCategory(99L, new CreateForumCategoryRequest("X", null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deleteCategoryRejectsWhenPostsExist() {
        ForumCategory cat = new ForumCategory();
        when(categoryRepo.findById(1L)).thenReturn(Optional.of(cat));
        when(postRepo.existsByCategoryId(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteCategory(1L)).isInstanceOf(ResponseStatusException.class);
        verify(categoryRepo, never()).delete(any());
    }

    @Test
    void deleteCategorySucceedsWhenEmpty() {
        ForumCategory cat = new ForumCategory();
        when(categoryRepo.findById(1L)).thenReturn(Optional.of(cat));
        when(postRepo.existsByCategoryId(1L)).thenReturn(false);
        service.deleteCategory(1L);
        verify(categoryRepo).delete(cat);
    }
}
