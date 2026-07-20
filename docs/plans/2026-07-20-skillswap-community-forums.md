# SkillSwap Hub — Plan 5: Community Forums

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Users browse predefined forum categories, create posts, comment, upvote once each, and search by keyword; post authors can delete their own content; admins can moderate (hide) or hard-delete any post/comment; commenting on someone else's post notifies them.

**Architecture:** Extends the Plan 1–4 Spring Boot monolith. Four new tables (`forum_categories`, `forum_posts`, `forum_comments`, `forum_post_upvotes`) via Flyway, seeded with a starter category list the same way Plan 2 seeded skills. Upvotes are a join table with a `UNIQUE(post_id, user_id)` constraint rather than a mutable counter — upvote/comment counts are computed via `COUNT` queries, the same "derive, don't cache" choice Plan 4 made for review averages. Admin-only moderation is gated by a new `CurrentUser.requireAdmin()` helper (this plan's first use of the `Role.ADMIN` check anywhere in the app) and lives in a separate `AdminForumController` (`/api/admin/forum/**`), establishing the route prefix Plan 6's admin panel will keep using. `ForumService.addComment` finally exercises `NotificationType.FORUM`, defined but unused since Plan 4.

**Tech Stack:** Java 17, Spring Boot 3.2.5, Gradle, Spring Data JPA, Flyway, MySQL 8 (H2 for tests). No frontend or Redis changes in this plan.

## Global Constraints

- Base package `com.skillswap`. Java **17**. Spring Boot **3.2.5**. Gradle only (never Maven).
- **Build with JDK 17:** machine default `java` is JDK 26, unsupported by Gradle 8.7. Prefix every gradle command with `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`.
- Schema owned by **Flyway**, append-only. `V1`–`V8` (Plans 1–4) are frozen. This plan adds `V9__forums.sql` (Task 1) and `V10__seed_forum_categories.sql` (Task 1).
- New tables are **plural**, `forum_`-prefixed (`forum_categories`, `forum_posts`, `forum_comments`, `forum_post_upvotes`) — matches the `matches`/`sessions`/`reviews`/`notifications` precedent.
- Business errors via `org.springframework.web.server.ResponseStatusException` only (the existing generic handler in `GlobalExceptionHandler` already renders these — do not add new exception types or touch `GlobalExceptionHandler`).
- **Two distinct authorization patterns, don't conflate them:** (1) resource-ownership checks (e.g. "is this your post to delete") return **404** on failure — no existence leak, matching every prior plan's convention. (2) The new admin-only check (`requireAdmin()`) returns **403** on failure — this is a systemic permission tier, not a per-resource ownership question, so revealing "admin access required" leaks nothing sensitive. Do not use 403 for ownership checks or 404 for the admin check.
- Moderated content (`isModerated = true`) is excluded from every read path available to non-admins (list, search, detail, comments) — it 404s for a non-admin trying to view it directly by ID, same as any other hidden resource in this app. This plan does not build an admin "view moderated content" queue — that belongs to Plan 6, the same way Plan 4 left the flagged-reviews queue to Plan 6.
- Thin controllers; DTOs at the boundary (never serialize `ForumCategory`/`ForumPost`/`ForumComment`/`ForumPostUpvote` entities directly).
- Test profile is H2 (Flyway disabled, Hibernate `create-drop`) — every new entity must be complete and correctly annotated for tests to pass without Flyway. Any `@SpringBootTest` flow test needing the seeded category catalog must seed it itself via `TestSkillCatalog`-style helper (see Plan 4's `TestSkillCatalog.java` for the established, already-fixed pattern of a shared, idempotent, order-independent seeder — do not repeat the hardcoded-id mistake that plan found and fixed).
- Git author **Devashish Jadhav <jadhavom24@gmail.com>**. **Never** add `Co-Authored-By` or AI attribution. Conventional Commit messages. Commit at the end of every task.

**Interfaces already available from Plans 1–4:** `User`, `UserRepository`, `Role{USER,ADMIN}`; `CurrentUser.require()` returning the authenticated `User` (Plan 2 — this plan adds `requireAdmin()` alongside it); `NotificationService.notify(Long userId, NotificationType type, String message)` and `NotificationType{MATCH,SESSION,REVIEW,FORUM}` (Plan 4, `FORUM` unused until now); `GlobalExceptionHandler`'s generic `ResponseStatusException` handler (Plan 2).

---

### Task 1: Forum schema (categories, posts, comments, upvotes)

**Files:**
- Create: `backend/src/main/resources/db/migration/V9__forums.sql`
- Create: `backend/src/main/resources/db/migration/V10__seed_forum_categories.sql`
- Create: `backend/src/main/java/com/skillswap/entity/ForumCategory.java`
- Create: `backend/src/main/java/com/skillswap/entity/ForumPost.java`
- Create: `backend/src/main/java/com/skillswap/entity/ForumComment.java`
- Create: `backend/src/main/java/com/skillswap/entity/ForumPostUpvote.java`
- Create: `backend/src/main/java/com/skillswap/repository/ForumCategoryRepository.java`
- Create: `backend/src/main/java/com/skillswap/repository/ForumPostRepository.java`
- Create: `backend/src/main/java/com/skillswap/repository/ForumCommentRepository.java`
- Create: `backend/src/main/java/com/skillswap/repository/ForumPostUpvoteRepository.java`
- Test: `backend/src/test/java/com/skillswap/repository/ForumRepositoryTest.java`

**Interfaces:**
- Consumes: `users` table (Plan 1).
- Produces:
  - `ForumCategory(id, categoryName, description, createdDate)`.
  - `ForumPost(id, categoryId, userId, title, content, isModerated:boolean, createdDate)`.
  - `ForumComment(id, postId, userId, commentText, isModerated:boolean, createdDate)`.
  - `ForumPostUpvote(id, postId, userId, createdDate)`.
  - `ForumCategoryRepository extends JpaRepository<ForumCategory,Long>` (no extra methods needed — `findAll()` covers it).
  - `ForumPostRepository extends JpaRepository<ForumPost,Long>` + `List<ForumPost> findByCategoryIdAndIsModeratedFalse(Long)`, `@Query` `searchByKeyword(String keyword)`.
  - `ForumCommentRepository extends JpaRepository<ForumComment,Long>` + `List<ForumComment> findByPostIdAndIsModeratedFalse(Long)`, `long countByPostIdAndIsModeratedFalse(Long)`.
  - `ForumPostUpvoteRepository extends JpaRepository<ForumPostUpvote,Long>` + `boolean existsByPostIdAndUserId(Long, Long)`, `long countByPostId(Long)`.

- [ ] **Step 1: Write the Flyway migrations**

`backend/src/main/resources/db/migration/V9__forums.sql`:
```sql
CREATE TABLE forum_categories (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL UNIQUE,
    description  VARCHAR(255),
    created_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE forum_posts (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_id  BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    title        VARCHAR(200) NOT NULL,
    content      TEXT NOT NULL,
    is_moderated BOOLEAN NOT NULL DEFAULT FALSE,
    created_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_post_category FOREIGN KEY (category_id) REFERENCES forum_categories(id),
    CONSTRAINT fk_post_user     FOREIGN KEY (user_id)     REFERENCES users(id)
);
CREATE INDEX idx_post_category ON forum_posts(category_id);

CREATE TABLE forum_comments (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id      BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    comment_text TEXT NOT NULL,
    is_moderated BOOLEAN NOT NULL DEFAULT FALSE,
    created_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES forum_posts(id),
    CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_comment_post ON forum_comments(post_id);

CREATE TABLE forum_post_upvotes (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    post_id      BIGINT NOT NULL,
    user_id      BIGINT NOT NULL,
    created_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_upvote_post FOREIGN KEY (post_id) REFERENCES forum_posts(id),
    CONSTRAINT fk_upvote_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uq_upvote_once UNIQUE (post_id, user_id)
);
```

`backend/src/main/resources/db/migration/V10__seed_forum_categories.sql`:
```sql
INSERT INTO forum_categories (category_name, description) VALUES
 ('General Discussion', 'Anything skill-exchange related'),
 ('Programming',        'Coding, web dev, tools, languages'),
 ('Music',              'Instruments, theory, practice tips'),
 ('Design',             'Visual design, UX, illustration'),
 ('Languages',          'Learning and practicing spoken languages'),
 ('Business',           'Public speaking, entrepreneurship, career skills');
```

- [ ] **Step 2: Write the failing repository test**

`backend/src/test/java/com/skillswap/repository/ForumRepositoryTest.java`:
```java
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
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests ForumRepositoryTest`
Expected: FAIL — entities/repositories do not exist (compilation error).

- [ ] **Step 4: Write the entities**

`backend/src/main/java/com/skillswap/entity/ForumCategory.java`:
```java
package com.skillswap.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "forum_categories")
public class ForumCategory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_name", nullable = false, unique = true, length = 100)
    private String categoryName;

    @Column(length = 255)
    private String description;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    void onCreate() { if (createdDate == null) createdDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String v) { this.categoryName = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
```

`backend/src/main/java/com/skillswap/entity/ForumPost.java`:
```java
package com.skillswap.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "forum_posts")
public class ForumPost {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 200)
    private String title;

    @Lob
    @Column(nullable = false)
    private String content;

    @Column(name = "is_moderated", nullable = false)
    private boolean moderated = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    void onCreate() { if (createdDate == null) createdDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long v) { this.categoryId = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public String getTitle() { return title; }
    public void setTitle(String v) { this.title = v; }
    public String getContent() { return content; }
    public void setContent(String v) { this.content = v; }
    public boolean isModerated() { return moderated; }
    public void setModerated(boolean v) { this.moderated = v; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
```

`backend/src/main/java/com/skillswap/entity/ForumComment.java`:
```java
package com.skillswap.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "forum_comments")
public class ForumComment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private Long userId;

    @Lob
    @Column(nullable = false)
    private String commentText;

    @Column(name = "is_moderated", nullable = false)
    private boolean moderated = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    void onCreate() { if (createdDate == null) createdDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getPostId() { return postId; }
    public void setPostId(Long v) { this.postId = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public String getCommentText() { return commentText; }
    public void setCommentText(String v) { this.commentText = v; }
    public boolean isModerated() { return moderated; }
    public void setModerated(boolean v) { this.moderated = v; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
```

`backend/src/main/java/com/skillswap/entity/ForumPostUpvote.java`:
```java
package com.skillswap.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "forum_post_upvotes")
public class ForumPostUpvote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long postId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    void onCreate() { if (createdDate == null) createdDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getPostId() { return postId; }
    public void setPostId(Long v) { this.postId = v; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
```

- [ ] **Step 5: Write the repositories**

`backend/src/main/java/com/skillswap/repository/ForumCategoryRepository.java`:
```java
package com.skillswap.repository;

import com.skillswap.entity.ForumCategory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForumCategoryRepository extends JpaRepository<ForumCategory, Long> {
}
```

`backend/src/main/java/com/skillswap/repository/ForumPostRepository.java`:
```java
package com.skillswap.repository;

import com.skillswap.entity.ForumPost;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ForumPostRepository extends JpaRepository<ForumPost, Long> {
    List<ForumPost> findByCategoryIdAndIsModeratedFalse(Long categoryId);

    @Query("""
        SELECT p FROM ForumPost p
        WHERE p.moderated = false
          AND (LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
               OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')))
        """)
    List<ForumPost> searchByKeyword(@Param("keyword") String keyword);
}
```

`backend/src/main/java/com/skillswap/repository/ForumCommentRepository.java`:
```java
package com.skillswap.repository;

import com.skillswap.entity.ForumComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ForumCommentRepository extends JpaRepository<ForumComment, Long> {
    List<ForumComment> findByPostIdAndIsModeratedFalse(Long postId);
    long countByPostIdAndIsModeratedFalse(Long postId);
}
```

`backend/src/main/java/com/skillswap/repository/ForumPostUpvoteRepository.java`:
```java
package com.skillswap.repository;

import com.skillswap.entity.ForumPostUpvote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ForumPostUpvoteRepository extends JpaRepository<ForumPostUpvote, Long> {
    boolean existsByPostIdAndUserId(Long postId, Long userId);
    long countByPostId(Long postId);
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests ForumRepositoryTest`
Expected: PASS — all three cases green.

- [ ] **Step 7: Run the full suite**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test`
Expected: PASS — all 91 prior tests (Plans 1–4) plus this task's 3.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V9__forums.sql \
        backend/src/main/resources/db/migration/V10__seed_forum_categories.sql \
        backend/src/main/java/com/skillswap/entity/ForumCategory.java \
        backend/src/main/java/com/skillswap/entity/ForumPost.java \
        backend/src/main/java/com/skillswap/entity/ForumComment.java \
        backend/src/main/java/com/skillswap/entity/ForumPostUpvote.java \
        backend/src/main/java/com/skillswap/repository/ForumCategoryRepository.java \
        backend/src/main/java/com/skillswap/repository/ForumPostRepository.java \
        backend/src/main/java/com/skillswap/repository/ForumCommentRepository.java \
        backend/src/main/java/com/skillswap/repository/ForumPostUpvoteRepository.java \
        backend/src/test/java/com/skillswap/repository/ForumRepositoryTest.java
git commit -m "feat: add forum categories, posts, comments, and upvotes schema"
```

---

### Task 2: Post/comment CRUD, search, upvote, and comment notifications

**Files:**
- Create: `backend/src/main/java/com/skillswap/dto/ForumCategoryDto.java`
- Create: `backend/src/main/java/com/skillswap/dto/ForumPostDto.java`
- Create: `backend/src/main/java/com/skillswap/dto/ForumCommentDto.java`
- Create: `backend/src/main/java/com/skillswap/dto/CreateForumPostRequest.java`
- Create: `backend/src/main/java/com/skillswap/dto/CreateForumCommentRequest.java`
- Create: `backend/src/main/java/com/skillswap/service/ForumService.java`
- Create: `backend/src/main/java/com/skillswap/controller/ForumController.java`
- Test: `backend/src/test/java/com/skillswap/service/ForumServiceTest.java`
- Test: `backend/src/test/java/com/skillswap/controller/ForumFlowTest.java`

**Interfaces:**
- Consumes: `ForumCategoryRepository`, `ForumPostRepository`, `ForumCommentRepository`, `ForumPostUpvoteRepository` (Task 1), `UserRepository` (Plan 1), `NotificationService` (Plan 4), `CurrentUser` (Plan 2, unmodified in this task).
- Produces:
  - `record ForumCategoryDto(Long id, String categoryName, String description)`.
  - `record ForumPostDto(Long id, Long categoryId, Long userId, String authorName, String title, String content, long upvoteCount, long commentCount, java.time.LocalDateTime createdDate)`.
  - `record ForumCommentDto(Long id, Long postId, Long userId, String authorName, String commentText, java.time.LocalDateTime createdDate)`.
  - `record CreateForumPostRequest(String title, String content)` (validated `@NotBlank`, `@Size(max=200)` on title, `@NotBlank` on content).
  - `record CreateForumCommentRequest(String commentText)` (validated `@NotBlank`).
  - `ForumService` — `List<ForumCategoryDto> categories()`, `List<ForumPostDto> postsByCategory(Long categoryId)`, `List<ForumPostDto> search(String keyword)`, `ForumPostDto getPost(Long postId)`, `ForumPostDto createPost(Long userId, Long categoryId, CreateForumPostRequest req)`, `void deletePost(Long userId, Long postId)`, `List<ForumCommentDto> comments(Long postId)`, `ForumCommentDto addComment(Long userId, Long postId, CreateForumCommentRequest req)`, `void deleteComment(Long userId, Long commentId)`, `ForumPostDto upvote(Long userId, Long postId)`.
  - `GET /api/forum/categories`, `GET /api/forum/categories/{id}/posts`, `GET /api/forum/posts/search?keyword=`, `GET /api/forum/posts/{id}`, `POST /api/forum/categories/{id}/posts` (201), `DELETE /api/forum/posts/{id}` (204), `GET /api/forum/posts/{id}/comments`, `POST /api/forum/posts/{id}/comments` (201), `DELETE /api/forum/comments/{id}` (204), `POST /api/forum/posts/{id}/upvote` (201).

**Business rules:**
- `createPost`: category must exist (404 otherwise). Author is the authenticated user.
- `getPost`/`postsByCategory`/`search`/`comments`: exclude `moderated = true` rows; `getPost` on a moderated or missing post is 404.
- `deletePost`/`deleteComment`: only the author may delete their own content — 404 if not found or not owned (no existence leak), matching every prior plan's convention. Deleting a post does **not** cascade-delete its comments/upvotes in this plan (no comment/upvote FK-cascade behavior is defined) — deleting a post that already has comments is out of scope for this MVP; note this as a deferred edge case in self-review, not silently handled.
- `addComment`: post must exist and be unmoderated (404 otherwise). After saving, notify the post's author (`NotificationType.FORUM`, "Someone commented on your post.") **unless the commenter is the author** (don't notify yourself).
- `upvote`: post must exist and be unmoderated (404 otherwise). A user may upvote a given post only once — a second attempt is 409 "You have already upvoted this post."

- [ ] **Step 1: Write the failing service test**

`backend/src/test/java/com/skillswap/service/ForumServiceTest.java`:
```java
package com.skillswap.service;

import com.skillswap.dto.CreateForumCommentRequest;
import com.skillswap.dto.CreateForumPostRequest;
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests ForumServiceTest`
Expected: FAIL — `ForumService`, DTOs do not exist.

- [ ] **Step 3: Write the DTOs**

`backend/src/main/java/com/skillswap/dto/ForumCategoryDto.java`:
```java
package com.skillswap.dto;

public record ForumCategoryDto(Long id, String categoryName, String description) {}
```

`backend/src/main/java/com/skillswap/dto/ForumPostDto.java`:
```java
package com.skillswap.dto;

import java.time.LocalDateTime;

public record ForumPostDto(Long id, Long categoryId, Long userId, String authorName, String title,
                           String content, long upvoteCount, long commentCount, LocalDateTime createdDate) {}
```

`backend/src/main/java/com/skillswap/dto/ForumCommentDto.java`:
```java
package com.skillswap.dto;

import java.time.LocalDateTime;

public record ForumCommentDto(Long id, Long postId, Long userId, String authorName,
                              String commentText, LocalDateTime createdDate) {}
```

`backend/src/main/java/com/skillswap/dto/CreateForumPostRequest.java`:
```java
package com.skillswap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateForumPostRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank String content) {}
```

`backend/src/main/java/com/skillswap/dto/CreateForumCommentRequest.java`:
```java
package com.skillswap.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateForumCommentRequest(@NotBlank String commentText) {}
```

- [ ] **Step 4: Write ForumService**

`backend/src/main/java/com/skillswap/service/ForumService.java`:
```java
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
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests ForumServiceTest`
Expected: PASS — all eleven cases green.

- [ ] **Step 6: Write ForumController**

`backend/src/main/java/com/skillswap/controller/ForumController.java`:
```java
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
```

- [ ] **Step 7: Write the end-to-end forum flow integration test**

`backend/src/test/java/com/skillswap/controller/ForumFlowTest.java`:
```java
package com.skillswap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ForumFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String register(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("fullName", email, "email", email, "password", "password1"));
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(res, "$.token");
    }

    private Long seedCategory(String name) {
        jdbc.update("INSERT INTO forum_categories(category_name) VALUES (?)", name);
        return jdbc.queryForObject("SELECT id FROM forum_categories WHERE category_name = ?", Long.class, name);
    }

    @Test
    void createPostCommentAndUpvoteFlow() throws Exception {
        String authorToken = register("forum-author@example.com");
        String otherToken = register("forum-other@example.com");
        Long categoryId = seedCategory("Test Category " + System.identityHashCode(this));

        String postBody = json.writeValueAsString(Map.of("title", "Hello forum", "content", "First post!"));
        String postRes = mvc.perform(post("/api/forum/categories/{id}/posts", categoryId)
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(postBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorName").exists())
                .andReturn().getResponse().getContentAsString();
        Long postId = ((Number) com.jayway.jsonpath.JsonPath.read(postRes, "$.id")).longValue();

        mvc.perform(get("/api/forum/categories/{id}/posts", categoryId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(postId.intValue()));

        String commentBody = json.writeValueAsString(Map.of("commentText", "Nice first post!"));
        mvc.perform(post("/api/forum/posts/{id}/comments", postId)
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON).content(commentBody))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/forum/posts/{id}/upvote", postId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.upvoteCount").value(1))
                .andExpect(jsonPath("$.commentCount").value(1));

        // Duplicate upvote rejected
        mvc.perform(post("/api/forum/posts/{id}/upvote", postId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isConflict());

        // Author got a FORUM notification for the comment
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + authorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("FORUM"));
    }

    @Test
    void deletingSomeoneElsesPostReturns404() throws Exception {
        String authorToken = register("forum-owner@example.com");
        String otherToken = register("forum-intruder@example.com");
        Long categoryId = seedCategory("Delete Test " + System.identityHashCode(this));

        String postBody = json.writeValueAsString(Map.of("title", "Mine", "content", "Do not delete"));
        String postRes = mvc.perform(post("/api/forum/categories/{id}/posts", categoryId)
                        .header("Authorization", "Bearer " + authorToken)
                        .contentType(MediaType.APPLICATION_JSON).content(postBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long postId = ((Number) com.jayway.jsonpath.JsonPath.read(postRes, "$.id")).longValue();

        mvc.perform(delete("/api/forum/posts/{id}", postId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void forumEndpointsRequireAuth() throws Exception {
        mvc.perform(get("/api/forum/categories")).andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 8: Run the full suite**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test`
Expected: PASS — all 94 prior tests (91 baseline + Task 1's 3) plus `ForumServiceTest` (11) and `ForumFlowTest` (3).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/skillswap/dto/ForumCategoryDto.java \
        backend/src/main/java/com/skillswap/dto/ForumPostDto.java \
        backend/src/main/java/com/skillswap/dto/ForumCommentDto.java \
        backend/src/main/java/com/skillswap/dto/CreateForumPostRequest.java \
        backend/src/main/java/com/skillswap/dto/CreateForumCommentRequest.java \
        backend/src/main/java/com/skillswap/service/ForumService.java \
        backend/src/main/java/com/skillswap/controller/ForumController.java \
        backend/src/test/java/com/skillswap/service/ForumServiceTest.java \
        backend/src/test/java/com/skillswap/controller/ForumFlowTest.java
git commit -m "feat: add forum post/comment CRUD, search, upvoting, and comment notifications"
```

---

### Task 3: Admin moderation (moderate/delete any post or comment)

**Files:**
- Modify: `backend/src/main/java/com/skillswap/service/CurrentUser.java` (add `requireAdmin()`)
- Modify: `backend/src/main/java/com/skillswap/service/ForumService.java` (add `moderatePost`/`adminDeletePost`/`moderateComment`/`adminDeleteComment`)
- Create: `backend/src/main/java/com/skillswap/controller/AdminForumController.java`
- Test: `backend/src/test/java/com/skillswap/service/ForumServiceTest.java` (add moderation cases — no constructor change needed, `ForumService` isn't touched at the constructor level)
- Test: `backend/src/test/java/com/skillswap/controller/AdminForumFlowTest.java` (new)

**Interfaces:**
- Consumes: `CurrentUser.require()` (unmodified), `Role` (Plan 1).
- Produces:
  - `CurrentUser.requireAdmin()` — returns the authenticated `User` if `role == ADMIN`, else throws `ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required")`. This is the first 403 in the app — a deliberate, correct departure from the 404-for-ownership convention (see Global Constraints).
  - `ForumService.moderatePost(Long postId)`, `adminDeletePost(Long postId)`, `moderateComment(Long commentId)`, `adminDeleteComment(Long commentId)` — all 404 if the target doesn't exist; **no ownership check** (any admin can act on any post/comment, that's the point).
  - `PUT /api/admin/forum/posts/{id}/moderate` (200), `DELETE /api/admin/forum/posts/{id}` (204), `PUT /api/admin/forum/comments/{id}/moderate` (200), `DELETE /api/admin/forum/comments/{id}` (204) — all gated by `currentUser.requireAdmin()`.

- [ ] **Step 1: Add `requireAdmin()` to CurrentUser**

Modify `backend/src/main/java/com/skillswap/service/CurrentUser.java` — add this method inside the class, after `require()`:
```java
    /** The authenticated user, if they hold the ADMIN role. 403 (not 404) — this is a permission tier, not a per-resource ownership check. */
    public User requireAdmin() {
        User u = require();
        if (u.getRole() != com.skillswap.entity.Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        return u;
    }
```
(No new imports needed — `HttpStatus`, `ResponseStatusException`, `User` are already imported in this file; `Role` is referenced fully-qualified inline to avoid adding an import to a file this small, but you may add `import com.skillswap.entity.Role;` and use `Role.ADMIN` unqualified instead if you prefer — either is fine, keep it consistent within the file.)

- [ ] **Step 2: Write the failing moderation service tests**

Add these test methods to `backend/src/test/java/com/skillswap/service/ForumServiceTest.java` (the class already has all the mocks/fixtures this needs — `postRepo`, `commentRepo`, and the `post(...)` helper):
```java
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
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests ForumServiceTest`
Expected: FAIL — `moderatePost`/`adminDeletePost`/`moderateComment`/`adminDeleteComment` do not exist on `ForumService`.

- [ ] **Step 4: Add the moderation methods to ForumService**

Modify `backend/src/main/java/com/skillswap/service/ForumService.java` — add these four public methods (anywhere after the existing public methods, before the private helpers):
```java
    public void moderatePost(Long postId) {
        ForumPost p = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
        p.setModerated(true);
        postRepository.save(p);
    }

    public void adminDeletePost(Long postId) {
        ForumPost p = postRepository.findById(postId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
        postRepository.delete(p);
    }

    public void moderateComment(Long commentId) {
        ForumComment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
        c.setModerated(true);
        commentRepository.save(c);
    }

    public void adminDeleteComment(Long commentId) {
        ForumComment c = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comment not found"));
        commentRepository.delete(c);
    }
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests ForumServiceTest`
Expected: PASS — all sixteen cases green (11 from Task 2 + 5 new).

- [ ] **Step 6: Write AdminForumController**

`backend/src/main/java/com/skillswap/controller/AdminForumController.java`:
```java
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
```

- [ ] **Step 7: Write the admin moderation flow test**

`backend/src/test/java/com/skillswap/controller/AdminForumFlowTest.java`:
```java
package com.skillswap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminForumFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String register(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("fullName", email, "email", email, "password", "password1"));
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(res, "$.token");
    }

    private String promoteToAdminAndLogin(String email) throws Exception {
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", email);
        String body = json.writeValueAsString(Map.of("email", email, "password", "password1"));
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(res, "$.token");
    }

    private Long seedCategory(String name) {
        jdbc.update("INSERT INTO forum_categories(category_name) VALUES (?)", name);
        return jdbc.queryForObject("SELECT id FROM forum_categories WHERE category_name = ?", Long.class, name);
    }

    @Test
    void nonAdminCannotModerate() throws Exception {
        String userToken = register("plain-user@example.com");
        Long categoryId = seedCategory("Mod Test A " + System.identityHashCode(this));
        String postBody = json.writeValueAsString(Map.of("title", "Post", "content", "Body"));
        String postRes = mvc.perform(post("/api/forum/categories/{id}/posts", categoryId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(postBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long postId = ((Number) com.jayway.jsonpath.JsonPath.read(postRes, "$.id")).longValue();

        mvc.perform(put("/api/admin/forum/posts/{id}/moderate", postId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanModerateAndDeleteAnyonesPost() throws Exception {
        String userToken = register("regular-poster@example.com");
        register("future-admin@example.com");
        String adminToken = promoteToAdminAndLogin("future-admin@example.com");
        Long categoryId = seedCategory("Mod Test B " + System.identityHashCode(this));

        String postBody = json.writeValueAsString(Map.of("title", "Spam", "content", "Buy now"));
        String postRes = mvc.perform(post("/api/forum/categories/{id}/posts", categoryId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(postBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long postId = ((Number) com.jayway.jsonpath.JsonPath.read(postRes, "$.id")).longValue();

        mvc.perform(put("/api/admin/forum/posts/{id}/moderate", postId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        // Moderated post is now hidden from a normal read
        mvc.perform(get("/api/forum/posts/{id}", postId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/admin/forum/posts/{id}", postId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
    }
}
```
- [ ] **Step 8: Run the full suite**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test`
Expected: PASS — 108 tests at the end of Task 2 (91 baseline + Task 1's 3 + Task 2's 11 + Task 2's 3), plus this task's 5 new `ForumServiceTest` moderation cases (Step 2) and 2 new `AdminForumFlowTest` cases = **115 total**.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/skillswap/service/CurrentUser.java \
        backend/src/main/java/com/skillswap/service/ForumService.java \
        backend/src/main/java/com/skillswap/controller/AdminForumController.java \
        backend/src/test/java/com/skillswap/service/ForumServiceTest.java \
        backend/src/test/java/com/skillswap/controller/AdminForumFlowTest.java
git commit -m "feat: add admin-gated forum moderation and hard-delete"
```

---

## Self-Review

**Spec coverage (Plan 5 slice):**
- Predefined categories, create posts, comment/reply, upvote count, search by keyword → Tasks 1–2. ✅
- Admin moderate/delete posts and comments → Task 3. ✅
- Deliberately cut per spec's own "Cut" line for this module: downvote, follow-topic/category. Not built. ✅
- Forum activity notifications (comment on your post) → Task 2, finally exercising `NotificationType.FORUM` from Plan 4. ✅

**Placeholder scan:** No TBD/TODO; every step has complete code (an awkward inline ternary in an early `AdminForumFlowTest` draft was caught and rewritten as a clean two-statement register-then-login during this self-review, not left for the implementer to untangle). ✅

**Type consistency:** `ForumService`'s constructor (`ForumCategoryRepository, ForumPostRepository, ForumCommentRepository, ForumPostUpvoteRepository, UserRepository, NotificationService`) is identical across Task 2's implementation and its test's mock setup. `CurrentUser.requireAdmin()` (Task 3) doesn't change `CurrentUser`'s existing constructor, so no other file that already injects `CurrentUser` needs updating. `moderatePost`/`adminDeletePost`/`moderateComment`/`adminDeleteComment` signatures match between Task 3's service additions, its test, and `AdminForumController`. ✅

**Scope check:** Three tasks, similarly sized to Plans 2–4 (schema → core feature → integration/admin-gating). Cumulative test count 91→115.

**Deliberate simplifications (flagged for the record):**
- Deleting a post with existing comments/upvotes leaves orphaned comment/upvote rows (no cascade delete, no FK `ON DELETE CASCADE`). Acceptable for MVP — a deleted post's stray comments are simply unreachable (no endpoint lists comments for a post that no longer exists), not displayed anywhere, and not a data-integrity risk beyond disk space. `ponytail: no cascade delete, add ON DELETE CASCADE or an explicit cleanup step if reclaiming that data ever matters.`
- No edit endpoints for posts/comments (spec's functional requirement mentions "edit" only in the context of what *admins* can do to moderate; regular users get create/delete, not edit, in this MVP — matches the spec module's actual feature list which never asks for author-side editing).
- `requireAdmin()`'s 403 is the app's first non-404 authorization failure — deliberately, not an oversight; see Global Constraints for the reasoning. Every other authorization check in the app remains 404-for-ownership.
- No rate-limiting on post/comment creation or upvoting — matches the project's existing posture (Review's `flag` endpoint has the same absence, accepted in Plan 4).
