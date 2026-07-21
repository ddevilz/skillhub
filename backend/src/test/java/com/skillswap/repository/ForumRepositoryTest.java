package com.skillswap.repository;

import com.skillswap.entity.ForumCategory;
import com.skillswap.entity.ForumComment;
import com.skillswap.entity.ForumPost;
import com.skillswap.entity.ForumPostUpvote;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ForumRepositoryTest {

    @Autowired ForumCategoryRepository categoryRepository;
    @Autowired ForumPostRepository postRepository;
    @Autowired ForumCommentRepository commentRepository;
    @Autowired ForumPostUpvoteRepository upvoteRepository;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Long insertUser(String email) {
        jdbc.update("INSERT INTO users(full_name,email,password_hash,role,active) VALUES (?,?,?,?,?)",
                email, email, "hash", "USER", true);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    @Test
    void findsPostsByCategoryExcludingModerated() {
        ForumCategory cat = new ForumCategory();
        cat.setCategoryName("Programming");
        Long categoryId = categoryRepository.save(cat).getId();
        Long authorId = insertUser("author@example.com");

        ForumPost visible = new ForumPost();
        visible.setCategoryId(categoryId); visible.setUserId(authorId);
        visible.setTitle("Hello"); visible.setContent("World");
        postRepository.save(visible);

        ForumPost hidden = new ForumPost();
        hidden.setCategoryId(categoryId); hidden.setUserId(authorId);
        hidden.setTitle("Spam"); hidden.setContent("Buy now"); hidden.setModerated(true);
        postRepository.save(hidden);

        assertThat(postRepository.findByCategoryIdAndIsModeratedFalse(categoryId)).hasSize(1);
    }

    @Test
    void searchByKeywordFindsTitleOrContentMatchExcludingModerated() {
        ForumCategory cat = new ForumCategory();
        cat.setCategoryName("Music");
        Long categoryId = categoryRepository.save(cat).getId();
        Long authorId = insertUser("musician@example.com");

        ForumPost p = new ForumPost();
        p.setCategoryId(categoryId); p.setUserId(authorId);
        p.setTitle("Guitar tips"); p.setContent("Practice daily");
        postRepository.save(p);

        assertThat(postRepository.searchByKeyword("guitar")).hasSize(1);
        assertThat(postRepository.searchByKeyword("PRACTICE")).hasSize(1);
        assertThat(postRepository.searchByKeyword("nonexistent")).isEmpty();
    }

    @Test
    void commentsAndUpvotesTrackByPost() {
        ForumCategory cat = new ForumCategory();
        cat.setCategoryName("Design");
        Long categoryId = categoryRepository.save(cat).getId();
        Long authorId = insertUser("designer@example.com");
        Long fanId = insertUser("fan@example.com");

        ForumPost p = new ForumPost();
        p.setCategoryId(categoryId); p.setUserId(authorId);
        p.setTitle("Feedback wanted"); p.setContent("Thoughts?");
        Long postId = postRepository.save(p).getId();

        ForumComment c = new ForumComment();
        c.setPostId(postId); c.setUserId(fanId); c.setCommentText("Looks great!");
        commentRepository.save(c);

        ForumPostUpvote u = new ForumPostUpvote();
        u.setPostId(postId); u.setUserId(fanId);
        upvoteRepository.save(u);

        assertThat(commentRepository.findByPostIdAndIsModeratedFalse(postId)).hasSize(1);
        assertThat(commentRepository.countByPostIdAndIsModeratedFalse(postId)).isEqualTo(1);
        assertThat(upvoteRepository.countByPostId(postId)).isEqualTo(1);
        assertThat(upvoteRepository.existsByPostIdAndUserId(postId, fanId)).isTrue();
        assertThat(upvoteRepository.existsByPostIdAndUserId(postId, authorId)).isFalse();
    }
}
