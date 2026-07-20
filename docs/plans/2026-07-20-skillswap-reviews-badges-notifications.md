# SkillSwap Hub — Plan 4: Reviews + Badges + Notifications

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After a completed session, each participant can rate the other once; a teacher's completed-session count per skill auto-awards Beginner/Intermediate/Expert badges; and match/session/review activity generates in-app notifications (with email events logged, not sent).

**Architecture:** Extends the Plan 1–3 Spring Boot monolith. `Review` and `SkillBadge` are new entities (Flyway `V6`/`V7`). A genuine gap surfaced while designing badges: `SkillBadge` requires a `skillId` (per spec's ERD), but `Session` — the only record of a completed teaching interaction — has never recorded which skill was taught. Task 2 closes this by adding a required `skillId` to `Session`/`CreateSessionRequest` (a new migration + entity/DTO/service changes, precisely specified here so no implementer has to improvise). `BadgeService.evaluateAndAward` is called from `SessionService.complete()` right after credit settlement. `NotificationService` is a thin, single-purpose emitter (`notify(userId, type, message)`) wired as one-line calls into the existing `MatchService`, `SessionService`, and this plan's new `ReviewService` — the same "extend an already-shipped service for a new cross-cutting concern" pattern Plan 2 used for Redis caching.

**Tech Stack:** Java 17, Spring Boot 3.2.5, Gradle, Spring Data JPA, Flyway, MySQL 8 (H2 for tests). No frontend or Redis changes in this plan.

## Global Constraints

- Base package `com.skillswap`. Java **17**. Spring Boot **3.2.5**. Gradle only (never Maven).
- **Build with JDK 17:** machine default `java` is JDK 26, unsupported by Gradle 8.7. Prefix every gradle command with `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`.
- Schema owned by **Flyway**, append-only. `V1`–`V5` (Plans 1–3) are frozen. This plan adds `V6__reviews.sql` (Task 1), `V7__session_skill_and_badges.sql` (Task 2), `V8__notifications.sql` (Task 3).
- New tables are named **plural** (`reviews`, `notifications`) — matches the `matches`/`sessions` precedent, sidesteps any reserved-word doubt.
- Boolean entity fields follow the existing `User.active`/`isActive()`/`setActive()` convention (field name without an `is` prefix, `isXxx()` getter) — **not** a field literally named `isRead`. This avoids the Hibernate/Lombok "isXxx field vs isXxx() getter" ambiguity the codebase has consistently avoided so far.
- Business errors via `org.springframework.web.server.ResponseStatusException` only (the existing generic handler in `GlobalExceptionHandler` already renders these as `{ "error": <int>, "message": <text> }` — do not add new exception types or touch `GlobalExceptionHandler`).
- Any lookup where the caller isn't authorized returns 404, not 403 — no existence leaks (same convention as Plan 3's `requireParticipant`).
- Thin controllers; DTOs at the boundary (never serialize `Review`/`SkillBadge`/`Notification` entities directly).
- Real email is out of scope (CLAUDE.md: "real SMTP email (email events are logged, not sent)"). `NotificationService.notify(...)` logs an "email" line via SLF4J alongside creating the in-app row — this is the single place that requirement is satisfied.
- Test profile is H2 (Flyway disabled, Hibernate `create-drop`) — the test schema is built from entities, so every new/changed entity must be complete and correctly annotated for tests to pass without Flyway.
- Git author **Devashish Jadhav <jadhavom24@gmail.com>**. **Never** add `Co-Authored-By` or AI attribution. Conventional Commit messages. Commit at the end of every task.

**Interfaces already available from Plans 1–3:** `User`, `UserRepository`; `Match(id, userAId, userBId, status)`, `MatchStatus`, `MatchRepository`, `MatchService` (Plan 2); `Session(id, matchId, teacherUserId, learnerUserId, scheduledByUserId, sessionDate, startTime, endTime, mode, locationOrLink, status, createdDate)`, `SessionStatus{PENDING,CONFIRMED,COMPLETED,CANCELLED}`, `SessionRepository`, `SessionService` (Plan 3); `SkillRepository` (Plan 2); `CurrentUser.require()` (Plan 2); `GlobalExceptionHandler`'s generic `ResponseStatusException` handler (Plan 2).

---

### Task 1: Reviews (rate the other participant after a completed session)

**Files:**
- Create: `backend/src/main/resources/db/migration/V6__reviews.sql`
- Create: `backend/src/main/java/com/skillswap/entity/Review.java`
- Create: `backend/src/main/java/com/skillswap/repository/ReviewRepository.java`
- Create: `backend/src/main/java/com/skillswap/dto/CreateReviewRequest.java`
- Create: `backend/src/main/java/com/skillswap/dto/ReviewDto.java`
- Create: `backend/src/main/java/com/skillswap/dto/RatingSummaryDto.java`
- Create: `backend/src/main/java/com/skillswap/service/ReviewService.java`
- Create: `backend/src/main/java/com/skillswap/controller/ReviewController.java`
- Test: `backend/src/test/java/com/skillswap/service/ReviewServiceTest.java`
- Test: `backend/src/test/java/com/skillswap/controller/ReviewFlowTest.java`

**Interfaces:**
- Consumes: `SessionRepository` (Plan 3, unmodified in this task), `UserRepository`.
- Produces:
  - `Review(id, sessionId, reviewerUserId, ratedUserId, rating:int, comments:String, flagged:boolean, createdDate)`.
  - `ReviewRepository extends JpaRepository<Review,Long>` + `boolean existsBySessionIdAndReviewerUserId(Long, Long)`, `List<Review> findByRatedUserId(Long)`, `@Query` `Double averageRatingFor(Long userId)`, `long countByRatedUserId(Long)`.
  - `record CreateReviewRequest(int rating, String comments)` (validated `@Min(1) @Max(5)`).
  - `record ReviewDto(Long id, Long sessionId, Long reviewerUserId, Long ratedUserId, int rating, String comments, boolean flagged, java.time.LocalDateTime createdDate)`.
  - `record RatingSummaryDto(double averageRating, long reviewCount)`.
  - `ReviewService` — `ReviewDto create(Long meId, Long sessionId, CreateReviewRequest req)`, `void flag(Long reviewId)`, `RatingSummaryDto ratingSummary(Long userId)`.
  - `POST /api/sessions/{id}/review` (201), `POST /api/reviews/{id}/flag` (204), `GET /api/users/{id}/rating` (200).

**Business rules:**
- `create`: session must exist and the caller must be a participant (teacher or learner) — else 404. Session must be `COMPLETED` — else 409 "Session is not completed yet". The rated user is the *other* participant (never trusted from the request body). A reviewer may review a given session only once — a second attempt is 409 "You have already reviewed this session". Rating is validated `1`–`5` via Bean Validation.
- `flag`: any authenticated user may flag any review by id (sets `flagged = true`); 404 if the review doesn't exist. No further moderation queue in this plan — that's Plan 6 (Admin), which will read `flagged = true` reviews.
- `ratingSummary`: average rating (0.0 if no reviews) and review count for a user — used for profile display.

- [ ] **Step 1: Write the Flyway migration**

`backend/src/main/resources/db/migration/V6__reviews.sql`:
```sql
CREATE TABLE reviews (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id       BIGINT NOT NULL,
    reviewer_user_id BIGINT NOT NULL,
    rated_user_id    BIGINT NOT NULL,
    rating           INT NOT NULL,
    comments         VARCHAR(255),
    flagged          BOOLEAN NOT NULL DEFAULT FALSE,
    created_date     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_review_session  FOREIGN KEY (session_id) REFERENCES sessions(id),
    CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_user_id) REFERENCES users(id),
    CONSTRAINT fk_review_rated    FOREIGN KEY (rated_user_id) REFERENCES users(id),
    CONSTRAINT chk_review_rating  CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT uq_review_once     UNIQUE (session_id, reviewer_user_id)
);
CREATE INDEX idx_review_rated ON reviews(rated_user_id);
```

- [ ] **Step 2: Write the failing service test**

`backend/src/test/java/com/skillswap/service/ReviewServiceTest.java`:
```java
package com.skillswap.service;

import com.skillswap.dto.CreateReviewRequest;
import com.skillswap.dto.RatingSummaryDto;
import com.skillswap.dto.ReviewDto;
import com.skillswap.entity.Review;
import com.skillswap.entity.Session;
import com.skillswap.entity.SessionStatus;
import com.skillswap.repository.ReviewRepository;
import com.skillswap.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReviewServiceTest {

    private final SessionRepository sessionRepo = mock(SessionRepository.class);
    private final ReviewRepository reviewRepo = mock(ReviewRepository.class);
    private final ReviewService service = new ReviewService(sessionRepo, reviewRepo);

    private Session completedSession(Long teacher, Long learner) {
        Session s = new Session();
        s.setTeacherUserId(teacher);
        s.setLearnerUserId(learner);
        s.setStatus(SessionStatus.COMPLETED);
        return s;
    }

    @Test
    void createRejectsWhenSessionNotFound() {
        when(sessionRepo.findById(5L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(10L, 5L, new CreateReviewRequest(5, "great")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createRejectsWhenCallerNotParticipant() {
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(completedSession(10L, 20L)));
        assertThatThrownBy(() -> service.create(999L, 5L, new CreateReviewRequest(5, "great")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createRejectsWhenSessionNotCompleted() {
        Session s = completedSession(10L, 20L);
        s.setStatus(SessionStatus.CONFIRMED);
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.create(10L, 5L, new CreateReviewRequest(5, "great")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createRejectsDuplicateReview() {
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(completedSession(10L, 20L)));
        when(reviewRepo.existsBySessionIdAndReviewerUserId(5L, 10L)).thenReturn(true);
        assertThatThrownBy(() -> service.create(10L, 5L, new CreateReviewRequest(5, "great")))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createPersistsWithRatedUserAsTheOtherParticipant() {
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(completedSession(10L, 20L)));
        when(reviewRepo.existsBySessionIdAndReviewerUserId(5L, 10L)).thenReturn(false);
        when(reviewRepo.save(any(Review.class))).thenAnswer(i -> i.getArgument(0));

        ReviewDto dto = service.create(10L, 5L, new CreateReviewRequest(4, "good session"));

        assertThat(dto.reviewerUserId()).isEqualTo(10L);
        assertThat(dto.ratedUserId()).isEqualTo(20L);
        assertThat(dto.rating()).isEqualTo(4);
    }

    @Test
    void flagSetsFlaggedTrue() {
        Review r = new Review();
        r.setFlagged(false);
        when(reviewRepo.findById(9L)).thenReturn(Optional.of(r));

        service.flag(9L);

        assertThat(r.isFlagged()).isTrue();
        verify(reviewRepo).save(r);
    }

    @Test
    void flagRejectsWhenReviewNotFound() {
        when(reviewRepo.findById(9L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.flag(9L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void ratingSummaryComputesAverageAndCount() {
        when(reviewRepo.averageRatingFor(1L)).thenReturn(4.5);
        when(reviewRepo.countByRatedUserId(1L)).thenReturn(2L);

        RatingSummaryDto dto = service.ratingSummary(1L);

        assertThat(dto.averageRating()).isEqualTo(4.5);
        assertThat(dto.reviewCount()).isEqualTo(2L);
    }

    @Test
    void ratingSummaryDefaultsToZeroWhenNoReviews() {
        when(reviewRepo.averageRatingFor(1L)).thenReturn(null);
        when(reviewRepo.countByRatedUserId(1L)).thenReturn(0L);

        RatingSummaryDto dto = service.ratingSummary(1L);

        assertThat(dto.averageRating()).isEqualTo(0.0);
        assertThat(dto.reviewCount()).isZero();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests ReviewServiceTest`
Expected: FAIL — `Review`, `ReviewService`, DTOs do not exist (compilation error).

- [ ] **Step 4: Write the entity, repository, DTOs**

`backend/src/main/java/com/skillswap/entity/Review.java`:
```java
package com.skillswap.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reviews")
public class Review {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Column(nullable = false)
    private Long reviewerUserId;

    @Column(nullable = false)
    private Long ratedUserId;

    @Column(nullable = false)
    private int rating;

    @Column(length = 255)
    private String comments;

    @Column(nullable = false)
    private boolean flagged = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    void onCreate() { if (createdDate == null) createdDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long v) { this.sessionId = v; }
    public Long getReviewerUserId() { return reviewerUserId; }
    public void setReviewerUserId(Long v) { this.reviewerUserId = v; }
    public Long getRatedUserId() { return ratedUserId; }
    public void setRatedUserId(Long v) { this.ratedUserId = v; }
    public int getRating() { return rating; }
    public void setRating(int v) { this.rating = v; }
    public String getComments() { return comments; }
    public void setComments(String v) { this.comments = v; }
    public boolean isFlagged() { return flagged; }
    public void setFlagged(boolean v) { this.flagged = v; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
```

`backend/src/main/java/com/skillswap/repository/ReviewRepository.java`:
```java
package com.skillswap.repository;

import com.skillswap.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsBySessionIdAndReviewerUserId(Long sessionId, Long reviewerUserId);
    List<Review> findByRatedUserId(Long ratedUserId);
    long countByRatedUserId(Long ratedUserId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.ratedUserId = :userId")
    Double averageRatingFor(@Param("userId") Long userId);
}
```

`backend/src/main/java/com/skillswap/dto/CreateReviewRequest.java`:
```java
package com.skillswap.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateReviewRequest(
        @Min(1) @Max(5) int rating,
        String comments) {}
```

`backend/src/main/java/com/skillswap/dto/ReviewDto.java`:
```java
package com.skillswap.dto;

import java.time.LocalDateTime;

public record ReviewDto(Long id, Long sessionId, Long reviewerUserId, Long ratedUserId,
                        int rating, String comments, boolean flagged, LocalDateTime createdDate) {}
```

`backend/src/main/java/com/skillswap/dto/RatingSummaryDto.java`:
```java
package com.skillswap.dto;

public record RatingSummaryDto(double averageRating, long reviewCount) {}
```

- [ ] **Step 5: Write ReviewService**

`backend/src/main/java/com/skillswap/service/ReviewService.java`:
```java
package com.skillswap.service;

import com.skillswap.dto.CreateReviewRequest;
import com.skillswap.dto.RatingSummaryDto;
import com.skillswap.dto.ReviewDto;
import com.skillswap.entity.Review;
import com.skillswap.entity.Session;
import com.skillswap.entity.SessionStatus;
import com.skillswap.repository.ReviewRepository;
import com.skillswap.repository.SessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {

    private final SessionRepository sessionRepository;
    private final ReviewRepository reviewRepository;

    public ReviewService(SessionRepository sessionRepository, ReviewRepository reviewRepository) {
        this.sessionRepository = sessionRepository;
        this.reviewRepository = reviewRepository;
    }

    public ReviewDto create(Long meId, Long sessionId, CreateReviewRequest req) {
        Session s = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (!s.getTeacherUserId().equals(meId) && !s.getLearnerUserId().equals(meId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        if (s.getStatus() != SessionStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is not completed yet");
        }
        if (reviewRepository.existsBySessionIdAndReviewerUserId(sessionId, meId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You have already reviewed this session");
        }
        Long ratedUserId = s.getTeacherUserId().equals(meId) ? s.getLearnerUserId() : s.getTeacherUserId();

        Review r = new Review();
        r.setSessionId(sessionId);
        r.setReviewerUserId(meId);
        r.setRatedUserId(ratedUserId);
        r.setRating(req.rating());
        r.setComments(req.comments());
        return toDto(reviewRepository.save(r));
    }

    public void flag(Long reviewId) {
        Review r = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        r.setFlagged(true);
        reviewRepository.save(r);
    }

    public RatingSummaryDto ratingSummary(Long userId) {
        Double avg = reviewRepository.averageRatingFor(userId);
        long count = reviewRepository.countByRatedUserId(userId);
        return new RatingSummaryDto(avg == null ? 0.0 : avg, count);
    }

    private ReviewDto toDto(Review r) {
        return new ReviewDto(r.getId(), r.getSessionId(), r.getReviewerUserId(), r.getRatedUserId(),
                r.getRating(), r.getComments(), r.isFlagged(), r.getCreatedDate());
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests ReviewServiceTest`
Expected: PASS — all nine cases green.

- [ ] **Step 7: Write ReviewController**

`backend/src/main/java/com/skillswap/controller/ReviewController.java`:
```java
package com.skillswap.controller;

import com.skillswap.dto.CreateReviewRequest;
import com.skillswap.dto.RatingSummaryDto;
import com.skillswap.dto.ReviewDto;
import com.skillswap.service.CurrentUser;
import com.skillswap.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class ReviewController {

    private final ReviewService reviewService;
    private final CurrentUser currentUser;

    public ReviewController(ReviewService reviewService, CurrentUser currentUser) {
        this.reviewService = reviewService;
        this.currentUser = currentUser;
    }

    @PostMapping("/sessions/{id}/review")
    public ResponseEntity<ReviewDto> createReview(@PathVariable Long id, @Valid @RequestBody CreateReviewRequest req) {
        ReviewDto dto = reviewService.create(currentUser.require().getId(), id, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PostMapping("/reviews/{id}/flag")
    public ResponseEntity<Void> flagReview(@PathVariable Long id) {
        reviewService.flag(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/users/{id}/rating")
    public RatingSummaryDto rating(@PathVariable Long id) {
        return reviewService.ratingSummary(id);
    }
}
```

- [ ] **Step 8: Write the end-to-end review flow integration test**

`backend/src/test/java/com/skillswap/controller/ReviewFlowTest.java`:
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
class ReviewFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String register(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("fullName", email, "email", email, "password", "password1"));
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(res, "$.token");
    }

    private Long meId(String token) throws Exception {
        String res = mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(res, "$.id")).longValue();
    }

    private Long completedSession(Long teacherId, Long learnerId) {
        jdbc.update("INSERT INTO matches(user_a_id,user_b_id,status) VALUES (?,?,?)", learnerId, teacherId, "ACCEPTED");
        Long matchId = jdbc.queryForObject(
                "SELECT id FROM matches WHERE user_a_id = ? AND user_b_id = ?", Long.class, learnerId, teacherId);
        jdbc.update("""
            INSERT INTO sessions(match_id, teacher_user_id, learner_user_id, scheduled_by_user_id,
                                 session_date, start_time, end_time, mode, status)
            VALUES (?,?,?,?,?,?,?,?,?)
            """, matchId, teacherId, learnerId, learnerId,
                java.sql.Date.valueOf("2026-08-01"), java.sql.Time.valueOf("10:00:00"),
                java.sql.Time.valueOf("11:00:00"), "ONLINE", "COMPLETED");
        return jdbc.queryForObject("SELECT id FROM sessions WHERE match_id = ?", Long.class, matchId);
    }

    @Test
    void reviewCompletedSessionThenViewRating() throws Exception {
        String teacherToken = register("teacher-review@example.com");
        String learnerToken = register("learner-review@example.com");
        Long teacherId = meId(teacherToken);
        Long learnerId = meId(learnerToken);
        Long sessionId = completedSession(teacherId, learnerId);

        String body = json.writeValueAsString(Map.of("rating", 5, "comments", "Excellent teacher!"));
        mvc.perform(post("/api/sessions/{id}/review", sessionId)
                        .header("Authorization", "Bearer " + learnerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ratedUserId").value(teacherId.intValue()))
                .andExpect(jsonPath("$.rating").value(5));

        mvc.perform(get("/api/users/{id}/rating", teacherId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(5.0))
                .andExpect(jsonPath("$.reviewCount").value(1));
    }

    @Test
    void duplicateReviewReturns409() throws Exception {
        String teacherToken = register("teacher-dup@example.com");
        String learnerToken = register("learner-dup@example.com");
        Long teacherId = meId(teacherToken);
        Long learnerId = meId(learnerToken);
        Long sessionId = completedSession(teacherId, learnerId);

        String body = json.writeValueAsString(Map.of("rating", 4, "comments", "Good"));
        mvc.perform(post("/api/sessions/{id}/review", sessionId)
                        .header("Authorization", "Bearer " + learnerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/sessions/{id}/review", sessionId)
                        .header("Authorization", "Bearer " + learnerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRatingReturns400() throws Exception {
        String teacherToken = register("teacher-badrating@example.com");
        String learnerToken = register("learner-badrating@example.com");
        Long teacherId = meId(teacherToken);
        Long learnerId = meId(learnerToken);
        Long sessionId = completedSession(teacherId, learnerId);

        String body = json.writeValueAsString(Map.of("rating", 9, "comments", "x"));
        mvc.perform(post("/api/sessions/{id}/review", sessionId)
                        .header("Authorization", "Bearer " + learnerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }
}
```

- [ ] **Step 9: Run the full suite**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test`
Expected: PASS — all 67 prior tests (Plans 1–3) plus `ReviewServiceTest` (9) and `ReviewFlowTest` (3).

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/resources/db/migration/V6__reviews.sql \
        backend/src/main/java/com/skillswap/entity/Review.java \
        backend/src/main/java/com/skillswap/repository/ReviewRepository.java \
        backend/src/main/java/com/skillswap/dto/CreateReviewRequest.java \
        backend/src/main/java/com/skillswap/dto/ReviewDto.java \
        backend/src/main/java/com/skillswap/dto/RatingSummaryDto.java \
        backend/src/main/java/com/skillswap/service/ReviewService.java \
        backend/src/main/java/com/skillswap/controller/ReviewController.java \
        backend/src/test/java/com/skillswap/service/ReviewServiceTest.java \
        backend/src/test/java/com/skillswap/controller/ReviewFlowTest.java
git commit -m "feat: add post-session reviews with flagging and rating summaries"
```

---

### Task 2: Badges (auto-awarded per skill on completed teaching sessions)

**Why this task also touches `Session`:** `SkillBadge` must record *which skill* a badge was earned for (per spec's ERD: `SkillBadge.SkillID`). But nothing in the schema currently records which skill a session is about — `Session` only references `matchId`/`teacherUserId`/`learnerUserId`. This is a genuine gap in the Plan 3 design (badges weren't designed yet when Plan 3 was written), not a Plan 3 defect. This task closes it: `Session` and `CreateSessionRequest` gain a required `skillId`. Every place that constructs a `Session` or calls `POST /api/sessions` must now supply one — this is a deliberate, planned interface change, not an incidental one.

**Files:**
- Create: `backend/src/main/resources/db/migration/V7__session_skill_and_badges.sql`
- Create: `backend/src/main/java/com/skillswap/entity/BadgeType.java`
- Create: `backend/src/main/java/com/skillswap/entity/SkillBadge.java`
- Create: `backend/src/main/java/com/skillswap/repository/SkillBadgeRepository.java`
- Create: `backend/src/main/java/com/skillswap/dto/BadgeDto.java`
- Create: `backend/src/main/java/com/skillswap/service/BadgeService.java`
- Create: `backend/src/main/java/com/skillswap/controller/BadgeController.java`
- Modify: `backend/src/main/java/com/skillswap/entity/Session.java` (add `skillId`)
- Modify: `backend/src/main/java/com/skillswap/dto/SessionDto.java` (add `skillId`)
- Modify: `backend/src/main/java/com/skillswap/dto/CreateSessionRequest.java` (add `skillId`)
- Modify: `backend/src/main/java/com/skillswap/repository/SessionRepository.java` (add a count query)
- Modify: `backend/src/main/java/com/skillswap/service/SessionService.java` (validate+persist `skillId`; call `badgeService.evaluateAndAward` in `complete()`)
- Test: `backend/src/test/java/com/skillswap/service/BadgeServiceTest.java` (new)
- Modify: `backend/src/test/java/com/skillswap/service/SessionServiceTest.java` (thread `skillId` through every `CreateSessionRequest`; mock `SkillRepository`/`BadgeService`)
- Modify: `backend/src/test/java/com/skillswap/controller/SessionFlowTest.java` (add `skillId` to the request body)

**Interfaces:**
- Consumes: `SkillRepository.findById(Long)` (Plan 2, unmodified), `SessionRepository` (this task adds one method).
- Produces:
  - `BadgeType {BEGINNER, INTERMEDIATE, EXPERT, VERIFIED}` (`VERIFIED` is admin-only; not awarded by this task's rule engine — reserved for Plan 6).
  - `SkillBadge(id, userId, skillId, badgeType, awardedDate)`.
  - `SkillBadgeRepository extends JpaRepository<SkillBadge,Long>` + `List<SkillBadge> findByUserId(Long)`, `boolean existsByUserIdAndSkillIdAndBadgeType(Long, Long, BadgeType)`.
  - `record BadgeDto(Long id, Long skillId, String skillName, String badgeType, java.time.LocalDateTime awardedDate)`.
  - `BadgeService` — `public static final int BEGINNER_THRESHOLD = 1`, `INTERMEDIATE_THRESHOLD = 5`, `EXPERT_THRESHOLD = 15`; `void evaluateAndAward(Long teacherUserId, Long skillId)` (cumulative — awards every threshold newly reached, keeps all earned tiers, idempotent via the `existsBy` check); `List<BadgeDto> badgesFor(Long userId)`.
  - `GET /api/users/{id}/badges` → `List<BadgeDto>`.
  - `Session.getSkillId()`/`setSkillId(Long)`. `CreateSessionRequest(matchId, teacherUserId, skillId, sessionDate, startTime, endTime, mode, locationOrLink)`. `SessionDto` gains `skillId` as its 3rd field (after `matchId`, before `teacherUserId`... see exact field order in Step 5 below — copy it exactly, every test asserts on named fields via JSON path, not positional order, so this is safe).
  - `SessionRepository.countByTeacherUserIdAndSkillIdAndStatus(Long, Long, SessionStatus)`.

**Business rule addition to `SessionService.create`:** `skillId` must reference a real `Skill` (404 "Skill not found" otherwise) — validated the same way `SkillService.add` already validates skill existence.

**Business rule addition to `SessionService.complete`:** after credit settlement and before returning, call `badgeService.evaluateAndAward(s.getTeacherUserId(), s.getSkillId())` — inside the same `@Transactional` boundary already added in Plan 3's final fix, so a badge award and the session-completion status change commit or roll back together.

- [ ] **Step 1: Write the Flyway migration**

`backend/src/main/resources/db/migration/V7__session_skill_and_badges.sql`:
```sql
ALTER TABLE sessions ADD COLUMN skill_id BIGINT NOT NULL;
ALTER TABLE sessions ADD CONSTRAINT fk_session_skill FOREIGN KEY (skill_id) REFERENCES skill(id);

CREATE TABLE skill_badge (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NOT NULL,
    skill_id      BIGINT NOT NULL,
    badge_type    VARCHAR(20) NOT NULL,
    awarded_date  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_badge_user  FOREIGN KEY (user_id)  REFERENCES users(id),
    CONSTRAINT fk_badge_skill FOREIGN KEY (skill_id) REFERENCES skill(id),
    CONSTRAINT uq_badge_once  UNIQUE (user_id, skill_id, badge_type)
);
CREATE INDEX idx_badge_user ON skill_badge(user_id);
```
(No production data exists yet for this academic project — `ADD COLUMN ... NOT NULL` with no default is acceptable pre-launch; do not add a backfill step.)

- [ ] **Step 2: Write the failing BadgeServiceTest**

`backend/src/test/java/com/skillswap/service/BadgeServiceTest.java`:
```java
package com.skillswap.service;

import com.skillswap.dto.BadgeDto;
import com.skillswap.entity.BadgeType;
import com.skillswap.entity.SessionStatus;
import com.skillswap.entity.SkillBadge;
import com.skillswap.repository.SessionRepository;
import com.skillswap.repository.SkillBadgeRepository;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BadgeServiceTest {

    private final SessionRepository sessionRepo = mock(SessionRepository.class);
    private final SkillBadgeRepository badgeRepo = mock(SkillBadgeRepository.class);
    private final BadgeService service = new BadgeService(sessionRepo, badgeRepo);

    @Test
    void doesNothingBelowBeginnerThreshold() {
        when(sessionRepo.countByTeacherUserIdAndSkillIdAndStatus(1L, 4L, SessionStatus.COMPLETED)).thenReturn(0L);
        service.evaluateAndAward(1L, 4L);
        verify(badgeRepo, never()).save(any());
    }

    @Test
    void grantsBeginnerAtThreshold() {
        when(sessionRepo.countByTeacherUserIdAndSkillIdAndStatus(1L, 4L, SessionStatus.COMPLETED)).thenReturn(1L);
        when(badgeRepo.existsByUserIdAndSkillIdAndBadgeType(1L, 4L, BadgeType.BEGINNER)).thenReturn(false);

        service.evaluateAndAward(1L, 4L);

        verify(badgeRepo, times(1)).save(any(SkillBadge.class));
    }

    @Test
    void grantsBeginnerAndIntermediateAtIntermediateThreshold() {
        when(sessionRepo.countByTeacherUserIdAndSkillIdAndStatus(1L, 4L, SessionStatus.COMPLETED)).thenReturn(5L);
        when(badgeRepo.existsByUserIdAndSkillIdAndBadgeType(eq(1L), eq(4L), any())).thenReturn(false);

        service.evaluateAndAward(1L, 4L);

        verify(badgeRepo, times(2)).save(any(SkillBadge.class));
    }

    @Test
    void skipsAlreadyAwardedBadge() {
        when(sessionRepo.countByTeacherUserIdAndSkillIdAndStatus(1L, 4L, SessionStatus.COMPLETED)).thenReturn(1L);
        when(badgeRepo.existsByUserIdAndSkillIdAndBadgeType(1L, 4L, BadgeType.BEGINNER)).thenReturn(true);

        service.evaluateAndAward(1L, 4L);

        verify(badgeRepo, never()).save(any());
    }

    @Test
    void badgesForReturnsUserBadges() {
        SkillBadge b = new SkillBadge();
        when(badgeRepo.findByUserId(1L)).thenReturn(List.of(b));
        assertThat(service.badgesFor(1L)).hasSize(1);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests BadgeServiceTest`
Expected: FAIL — `BadgeType`, `SkillBadge`, `BadgeService` do not exist.

- [ ] **Step 4: Write BadgeType, SkillBadge, SkillBadgeRepository, BadgeDto**

`backend/src/main/java/com/skillswap/entity/BadgeType.java`:
```java
package com.skillswap.entity;

public enum BadgeType { BEGINNER, INTERMEDIATE, EXPERT, VERIFIED }
```

`backend/src/main/java/com/skillswap/entity/SkillBadge.java`:
```java
package com.skillswap.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "skill_badge")
public class SkillBadge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long skillId;

    @Enumerated(EnumType.STRING)
    @Column(name = "badge_type", nullable = false, length = 20)
    private BadgeType badgeType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime awardedDate;

    @PrePersist
    void onCreate() { if (awardedDate == null) awardedDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public Long getSkillId() { return skillId; }
    public void setSkillId(Long v) { this.skillId = v; }
    public BadgeType getBadgeType() { return badgeType; }
    public void setBadgeType(BadgeType v) { this.badgeType = v; }
    public LocalDateTime getAwardedDate() { return awardedDate; }
}
```

`backend/src/main/java/com/skillswap/repository/SkillBadgeRepository.java`:
```java
package com.skillswap.repository;

import com.skillswap.entity.BadgeType;
import com.skillswap.entity.SkillBadge;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SkillBadgeRepository extends JpaRepository<SkillBadge, Long> {
    List<SkillBadge> findByUserId(Long userId);
    boolean existsByUserIdAndSkillIdAndBadgeType(Long userId, Long skillId, BadgeType badgeType);
}
```

`backend/src/main/java/com/skillswap/dto/BadgeDto.java`:
```java
package com.skillswap.dto;

import java.time.LocalDateTime;

public record BadgeDto(Long id, Long skillId, String skillName, String badgeType, LocalDateTime awardedDate) {}
```

- [ ] **Step 5: Write BadgeService**

`backend/src/main/java/com/skillswap/service/BadgeService.java`:
```java
package com.skillswap.service;

import com.skillswap.entity.BadgeType;
import com.skillswap.entity.SessionStatus;
import com.skillswap.entity.SkillBadge;
import com.skillswap.repository.SessionRepository;
import com.skillswap.repository.SkillBadgeRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BadgeService {

    /** Completed-teaching-session-count thresholds per skill. Upgrade path: make configurable if the rule ever needs tuning without a redeploy. */
    public static final int BEGINNER_THRESHOLD = 1;
    public static final int INTERMEDIATE_THRESHOLD = 5;
    public static final int EXPERT_THRESHOLD = 15;

    private final SessionRepository sessionRepository;
    private final SkillBadgeRepository skillBadgeRepository;

    public BadgeService(SessionRepository sessionRepository, SkillBadgeRepository skillBadgeRepository) {
        this.sessionRepository = sessionRepository;
        this.skillBadgeRepository = skillBadgeRepository;
    }

    /** Cumulative: awards every threshold newly reached, keeps all earned tiers, safe to call repeatedly. */
    public void evaluateAndAward(Long teacherUserId, Long skillId) {
        long count = sessionRepository.countByTeacherUserIdAndSkillIdAndStatus(
                teacherUserId, skillId, SessionStatus.COMPLETED);
        awardIfReached(teacherUserId, skillId, count, BEGINNER_THRESHOLD, BadgeType.BEGINNER);
        awardIfReached(teacherUserId, skillId, count, INTERMEDIATE_THRESHOLD, BadgeType.INTERMEDIATE);
        awardIfReached(teacherUserId, skillId, count, EXPERT_THRESHOLD, BadgeType.EXPERT);
    }

    public List<SkillBadge> badgesFor(Long userId) {
        return skillBadgeRepository.findByUserId(userId);
    }

    private void awardIfReached(Long userId, Long skillId, long count, int threshold, BadgeType type) {
        if (count < threshold) return;
        if (skillBadgeRepository.existsByUserIdAndSkillIdAndBadgeType(userId, skillId, type)) return;
        SkillBadge b = new SkillBadge();
        b.setUserId(userId);
        b.setSkillId(skillId);
        b.setBadgeType(type);
        skillBadgeRepository.save(b);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests BadgeServiceTest`
Expected: PASS — all five cases green.

- [ ] **Step 7: Extend Session, SessionDto, CreateSessionRequest with `skillId`**

In `backend/src/main/java/com/skillswap/entity/Session.java`, add this field (alongside `matchId`) and its accessors:
```java
    @Column(nullable = false)
    private Long skillId;
```
```java
    public Long getSkillId() { return skillId; }
    public void setSkillId(Long v) { this.skillId = v; }
```

In `backend/src/main/java/com/skillswap/dto/SessionDto.java`, replace the whole record with:
```java
package com.skillswap.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record SessionDto(Long id, Long matchId, Long skillId, Long teacherUserId, Long learnerUserId,
                         Long scheduledByUserId, LocalDate sessionDate, LocalTime startTime, LocalTime endTime,
                         String mode, String locationOrLink, String status, LocalDateTime createdDate) {}
```

In `backend/src/main/java/com/skillswap/dto/CreateSessionRequest.java`, replace the whole record with:
```java
package com.skillswap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateSessionRequest(
        @NotNull Long matchId,
        @NotNull Long teacherUserId,
        @NotNull Long skillId,
        @NotNull LocalDate sessionDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotBlank String mode,
        String locationOrLink) {}
```

- [ ] **Step 8: Add the count query to SessionRepository**

In `backend/src/main/java/com/skillswap/repository/SessionRepository.java`, add this method to the interface (alongside `findByTeacherUserIdOrLearnerUserId`) and the import `com.skillswap.entity.SessionStatus`:
```java
    long countByTeacherUserIdAndSkillIdAndStatus(Long teacherUserId, Long skillId, SessionStatus status);
```

- [ ] **Step 9: Wire skillId validation and badge evaluation into SessionService**

In `backend/src/main/java/com/skillswap/service/SessionService.java`:

Add imports:
```java
import com.skillswap.repository.SkillRepository;
```

Replace the constructor and its fields with:
```java
    private final SessionRepository sessionRepository;
    private final MatchRepository matchRepository;
    private final CreditService creditService;
    private final SkillRepository skillRepository;
    private final BadgeService badgeService;

    public SessionService(SessionRepository sessionRepository, MatchRepository matchRepository,
                          CreditService creditService, SkillRepository skillRepository,
                          BadgeService badgeService) {
        this.sessionRepository = sessionRepository;
        this.matchRepository = matchRepository;
        this.creditService = creditService;
        this.skillRepository = skillRepository;
        this.badgeService = badgeService;
    }
```

In `create(...)`, insert this check right after the existing `canAfford` check (before `Session s = new Session();`), and add `s.setSkillId(req.skillId());` to the object construction:
```java
        if (skillRepository.findById(req.skillId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found");
        }

        Session s = new Session();
        s.setMatchId(match.getId());
        s.setSkillId(req.skillId());
        s.setTeacherUserId(req.teacherUserId());
```
(Keep every other line of `create()` exactly as-is — only these two additions.)

In `complete(...)`, add the badge-award call right after `creditService.settle(...)` and before `s.setStatus(SessionStatus.COMPLETED)`:
```java
        creditService.settle(s.getTeacherUserId(), s.getLearnerUserId(), sessionId);
        badgeService.evaluateAndAward(s.getTeacherUserId(), s.getSkillId());
        s.setStatus(SessionStatus.COMPLETED);
```

In `toDto(...)`, add `s.getSkillId()` as the 3rd constructor argument (matching `SessionDto`'s new field order from Step 7):
```java
    private SessionDto toDto(Session s) {
        return new SessionDto(s.getId(), s.getMatchId(), s.getSkillId(), s.getTeacherUserId(), s.getLearnerUserId(),
                s.getScheduledByUserId(), s.getSessionDate(), s.getStartTime(), s.getEndTime(),
                s.getMode() == null ? null : s.getMode().name(), s.getLocationOrLink(),
                s.getStatus().name(), s.getCreatedDate());
    }
```

- [ ] **Step 10: Update SessionServiceTest for the new constructor and `skillId`**

In `backend/src/test/java/com/skillswap/service/SessionServiceTest.java`:

Add imports:
```java
import com.skillswap.entity.Skill;
import com.skillswap.repository.SkillRepository;
```

Replace the mock/service fields at the top of the class with:
```java
    private final SessionRepository sessionRepo = mock(SessionRepository.class);
    private final MatchRepository matchRepo = mock(MatchRepository.class);
    private final CreditService creditService = mock(CreditService.class);
    private final SkillRepository skillRepo = mock(SkillRepository.class);
    private final BadgeService badgeService = mock(BadgeService.class);
    private final SessionService service = new SessionService(sessionRepo, matchRepo, creditService, skillRepo, badgeService);
```

Replace the `req(...)` helper with a version that includes `skillId`, and stub `skillRepo.findById` to succeed by default in the tests that reach that check:
```java
    private CreateSessionRequest req(Long matchId, Long teacherId) {
        return new CreateSessionRequest(matchId, teacherId, 4L, LocalDate.of(2026, 8, 1),
                LocalTime.of(10, 0), LocalTime.of(11, 0), "ONLINE", "https://meet.example/abc");
    }
```

In `createPersistsPendingSessionWithCorrectRoles`, add this stub before `SessionDto dto = service.create(...)`:
```java
        when(skillRepo.findById(4L)).thenReturn(Optional.of(new Skill()));
```
and add one assertion after the existing ones:
```java
        assertThat(dto.skillId()).isEqualTo(4L);
```

Add one new test method (skill-not-found path):
```java
    @Test
    void createRejectsWhenSkillNotFound() {
        when(matchRepo.findById(1L)).thenReturn(Optional.of(acceptedMatch(10L, 20L)));
        when(creditService.canAfford(20L)).thenReturn(true);
        when(skillRepo.findById(4L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(10L, req(1L, 10L)))
                .isInstanceOf(ResponseStatusException.class);
    }
```

In `completeSettlesCreditsOnSuccess`, add `s.setSkillId(4L);` right after the existing `s.setStatus(SessionStatus.CONFIRMED);` line, and add this verification after the existing `verify(creditService).settle(10L, 20L, 5L);` line:
```java
        verify(badgeService).evaluateAndAward(10L, 4L);
```

(Every other existing test method in this file is unaffected by these changes and needs no further edits — `skillRepo`/`badgeService` are unused mocks in tests that don't reach `create`/`complete`'s new lines, which is fine.)

- [ ] **Step 11: Update SessionFlowTest's request body**

In `backend/src/test/java/com/skillswap/controller/SessionFlowTest.java`, update the `createSession` helper's request body to include `skillId` (using seeded skill id `4` = Python, the same skill both flow tests already implicitly reference by never caring which skill it is):
```java
    private Long createSession(String token, Long matchId, Long teacherId) throws Exception {
        String body = json.writeValueAsString(Map.of(
                "matchId", matchId, "teacherUserId", teacherId, "skillId", 4,
                "sessionDate", "2026-08-01", "startTime", "10:00:00", "endTime", "11:00:00",
                "mode", "ONLINE", "locationOrLink", "https://meet.example/abc"));
        String res = mvc.perform(post("/api/sessions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(res, "$.id")).longValue();
    }
```
Also update `bookingBlockedWhenLearnerHasNoCredits`'s inline request body the same way — add `"skillId", 4,` to its `Map.of(...)` call.

- [ ] **Step 12: Write BadgeController**

`backend/src/main/java/com/skillswap/controller/BadgeController.java`:
```java
package com.skillswap.controller;

import com.skillswap.dto.BadgeDto;
import com.skillswap.entity.Skill;
import com.skillswap.entity.SkillBadge;
import com.skillswap.repository.SkillRepository;
import com.skillswap.service.BadgeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users/{id}/badges")
public class BadgeController {

    private final BadgeService badgeService;
    private final SkillRepository skillRepository;

    public BadgeController(BadgeService badgeService, SkillRepository skillRepository) {
        this.badgeService = badgeService;
        this.skillRepository = skillRepository;
    }

    @GetMapping
    public List<BadgeDto> badges(@PathVariable Long id) {
        Map<Long, Skill> skills = skillRepository.findAll().stream()
                .collect(Collectors.toMap(Skill::getId, s -> s));
        return badgeService.badgesFor(id).stream()
                .map(b -> new BadgeDto(b.getId(), b.getSkillId(),
                        skills.containsKey(b.getSkillId()) ? skills.get(b.getSkillId()).getSkillName() : null,
                        b.getBadgeType().name(), b.getAwardedDate()))
                .toList();
    }
}
```

- [ ] **Step 13: Run the full suite**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test`
Expected: PASS — all 79 prior tests (67 from Plans 1–3, plus Task 1's 12) plus `BadgeServiceTest` (5), with `SessionServiceTest`/`SessionFlowTest` updated and still green.

- [ ] **Step 14: Commit**

```bash
git add backend/src/main/resources/db/migration/V7__session_skill_and_badges.sql \
        backend/src/main/java/com/skillswap/entity/BadgeType.java \
        backend/src/main/java/com/skillswap/entity/SkillBadge.java \
        backend/src/main/java/com/skillswap/repository/SkillBadgeRepository.java \
        backend/src/main/java/com/skillswap/dto/BadgeDto.java \
        backend/src/main/java/com/skillswap/service/BadgeService.java \
        backend/src/main/java/com/skillswap/controller/BadgeController.java \
        backend/src/main/java/com/skillswap/entity/Session.java \
        backend/src/main/java/com/skillswap/dto/SessionDto.java \
        backend/src/main/java/com/skillswap/dto/CreateSessionRequest.java \
        backend/src/main/java/com/skillswap/repository/SessionRepository.java \
        backend/src/main/java/com/skillswap/service/SessionService.java \
        backend/src/test/java/com/skillswap/service/BadgeServiceTest.java \
        backend/src/test/java/com/skillswap/service/SessionServiceTest.java \
        backend/src/test/java/com/skillswap/controller/SessionFlowTest.java
git commit -m "feat: add per-skill teaching badges; require skillId on sessions"
```

---

### Task 3: Notifications (in-app alerts wired into match/session/review activity)

**Files:**
- Create: `backend/src/main/resources/db/migration/V8__notifications.sql`
- Create: `backend/src/main/java/com/skillswap/entity/NotificationType.java`
- Create: `backend/src/main/java/com/skillswap/entity/Notification.java`
- Create: `backend/src/main/java/com/skillswap/repository/NotificationRepository.java`
- Create: `backend/src/main/java/com/skillswap/dto/NotificationDto.java`
- Create: `backend/src/main/java/com/skillswap/dto/UnreadCountDto.java`
- Create: `backend/src/main/java/com/skillswap/service/NotificationService.java`
- Create: `backend/src/main/java/com/skillswap/controller/NotificationController.java`
- Modify: `backend/src/main/java/com/skillswap/service/MatchService.java` (notify on request + respond)
- Modify: `backend/src/main/java/com/skillswap/service/SessionService.java` (notify on confirm/cancel/reschedule)
- Modify: `backend/src/main/java/com/skillswap/service/ReviewService.java` (notify on create)
- Test: `backend/src/test/java/com/skillswap/service/NotificationServiceTest.java` (new)
- Modify: `backend/src/test/java/com/skillswap/service/MatchServiceTest.java` (new constructor param; 2 new assertions)
- Modify: `backend/src/test/java/com/skillswap/service/SessionServiceTest.java` (new constructor param; 3 new assertions)
- Modify: `backend/src/test/java/com/skillswap/service/ReviewServiceTest.java` (new constructor param; 1 new assertion)

**Interfaces:**
- Consumes: nothing new beyond `NotificationRepository` (this task).
- Produces:
  - `NotificationType {MATCH, SESSION, REVIEW, FORUM}` (`FORUM` is unused until Plan 5 — defining it now avoids a later enum migration).
  - `Notification(id, userId, type, message, read:boolean, createdDate)` — boolean field named `read` (not `isRead`), getter `isRead()`, setter `setRead(boolean)`, matching the codebase's established `User.active`/`isActive()` convention; column name `is_read` via `@Column(name = "is_read")`.
  - `NotificationRepository extends JpaRepository<Notification,Long>` + `List<Notification> findByUserIdOrderByCreatedDateDesc(Long)`, `long countByUserIdAndRead(Long, boolean)`, `Optional<Notification> findByIdAndUserId(Long, Long)`.
  - `record NotificationDto(Long id, String type, String message, boolean read, java.time.LocalDateTime createdDate)`, `record UnreadCountDto(long count)`.
  - `NotificationService` — `void notify(Long userId, NotificationType type, String message)` (persists the row AND logs an "email" line via SLF4J — the one place "logged, not sent" is satisfied), `List<Notification> list(Long userId)`, `long unreadCount(Long userId)`, `void markRead(Long userId, Long notificationId)`.
  - `GET /api/notifications`, `GET /api/notifications/unread-count`, `PUT /api/notifications/{id}/read`.

**Notification trigger points (exact wording, copy verbatim):**
- `MatchService.request`: after `matchRepository.save(m)`, notify the target: `notificationService.notify(target.getId(), NotificationType.MATCH, "You have a new match request.")`.
- `MatchService.respond`: after `matchRepository.save(m)`, notify the original requester: `notificationService.notify(m.getUserAId(), NotificationType.MATCH, "Your match request was " + newStatus.name().toLowerCase() + ".")`.
- `SessionService.confirm`: after `sessionRepository.save(s)`, notify the scheduler: `notificationService.notify(s.getScheduledByUserId(), NotificationType.SESSION, "Your session was confirmed.")`.
- `SessionService.cancel`: after `sessionRepository.save(s)`, notify the *other* participant (not the caller): compute `Long other = s.getTeacherUserId().equals(meId) ? s.getLearnerUserId() : s.getTeacherUserId();` then `notificationService.notify(other, NotificationType.SESSION, "Your session was cancelled.")`.
- `SessionService.reschedule`: after `sessionRepository.save(s)`, notify the *other* participant (the one who must now reconfirm): same `other` computation as `cancel`, then `notificationService.notify(other, NotificationType.SESSION, "Your session was rescheduled — please confirm the new time.")`.
- `ReviewService.create`: after `reviewRepository.save(r)`, notify the rated user: `notificationService.notify(ratedUserId, NotificationType.REVIEW, "You received a new review.")`.

- [ ] **Step 1: Write the Flyway migration**

`backend/src/main/resources/db/migration/V8__notifications.sql`:
```sql
CREATE TABLE notifications (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      BIGINT NOT NULL,
    type         VARCHAR(50) NOT NULL,
    message      VARCHAR(255) NOT NULL,
    is_read      BOOLEAN NOT NULL DEFAULT FALSE,
    created_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES users(id)
);
CREATE INDEX idx_notification_user ON notifications(user_id, is_read);
```

- [ ] **Step 2: Write the failing NotificationServiceTest**

`backend/src/test/java/com/skillswap/service/NotificationServiceTest.java`:
```java
package com.skillswap.service;

import com.skillswap.entity.Notification;
import com.skillswap.entity.NotificationType;
import com.skillswap.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
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
```
(Add `import org.mockito.ArgumentCaptor;` — this is the only import beyond what's shown that the test file needs.)

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests NotificationServiceTest`
Expected: FAIL — `Notification`, `NotificationType`, `NotificationService` do not exist.

- [ ] **Step 4: Write the enum, entity, repository, DTOs**

`backend/src/main/java/com/skillswap/entity/NotificationType.java`:
```java
package com.skillswap.entity;

public enum NotificationType { MATCH, SESSION, REVIEW, FORUM }
```

`backend/src/main/java/com/skillswap/entity/Notification.java`:
```java
package com.skillswap.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private NotificationType type;

    @Column(nullable = false, length = 255)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    void onCreate() { if (createdDate == null) createdDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public NotificationType getType() { return type; }
    public void setType(NotificationType v) { this.type = v; }
    public String getMessage() { return message; }
    public void setMessage(String v) { this.message = v; }
    public boolean isRead() { return read; }
    public void setRead(boolean v) { this.read = v; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
```

`backend/src/main/java/com/skillswap/repository/NotificationRepository.java`:
```java
package com.skillswap.repository;

import com.skillswap.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserIdOrderByCreatedDateDesc(Long userId);
    long countByUserIdAndRead(Long userId, boolean read);
    Optional<Notification> findByIdAndUserId(Long id, Long userId);
}
```

`backend/src/main/java/com/skillswap/dto/NotificationDto.java`:
```java
package com.skillswap.dto;

import java.time.LocalDateTime;

public record NotificationDto(Long id, String type, String message, boolean read, LocalDateTime createdDate) {}
```

`backend/src/main/java/com/skillswap/dto/UnreadCountDto.java`:
```java
package com.skillswap.dto;

public record UnreadCountDto(long count) {}
```

- [ ] **Step 5: Write NotificationService**

`backend/src/main/java/com/skillswap/service/NotificationService.java`:
```java
package com.skillswap.service;

import com.skillswap.entity.Notification;
import com.skillswap.entity.NotificationType;
import com.skillswap.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** Creates the in-app row; the "email" is logged, not sent (no SMTP dependency by design). */
    public void notify(Long userId, NotificationType type, String message) {
        Notification n = new Notification();
        n.setUserId(userId);
        n.setType(type);
        n.setMessage(message);
        notificationRepository.save(n);
        log.info("EMAIL (logged, not sent) to user {}: [{}] {}", userId, type, message);
    }

    public List<Notification> list(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedDateDesc(userId);
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndRead(userId, false);
    }

    public void markRead(Long userId, Long notificationId) {
        Notification n = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
        n.setRead(true);
        notificationRepository.save(n);
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests NotificationServiceTest`
Expected: PASS — all five cases green.

- [ ] **Step 7: Wire notifications into MatchService**

In `backend/src/main/java/com/skillswap/service/MatchService.java`:

Add import:
```java
import com.skillswap.entity.NotificationType;
```

Replace the constructor and its fields with:
```java
    private final UserSkillRepository userSkillRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public MatchService(UserSkillRepository userSkillRepository, MatchRepository matchRepository,
                        UserRepository userRepository, NotificationService notificationService) {
        this.userSkillRepository = userSkillRepository;
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }
```

In `request(...)`, replace the final line `return toDto(matchRepository.save(m));` with:
```java
        Match saved = matchRepository.save(m);
        notificationService.notify(target.getId(), NotificationType.MATCH, "You have a new match request.");
        return toDto(saved);
```

In `respond(...)`, replace the final line `return toDto(matchRepository.save(m));` with:
```java
        Match saved = matchRepository.save(m);
        notificationService.notify(m.getUserAId(), NotificationType.MATCH,
                "Your match request was " + newStatus.name().toLowerCase() + ".");
        return toDto(saved);
```

- [ ] **Step 8: Wire notifications into SessionService**

In `backend/src/main/java/com/skillswap/service/SessionService.java`:

Add import:
```java
import com.skillswap.entity.NotificationType;
```

Extend the constructor and its fields (adding `NotificationService` as a 6th parameter):
```java
    private final SessionRepository sessionRepository;
    private final MatchRepository matchRepository;
    private final CreditService creditService;
    private final SkillRepository skillRepository;
    private final BadgeService badgeService;
    private final NotificationService notificationService;

    public SessionService(SessionRepository sessionRepository, MatchRepository matchRepository,
                          CreditService creditService, SkillRepository skillRepository,
                          BadgeService badgeService, NotificationService notificationService) {
        this.sessionRepository = sessionRepository;
        this.matchRepository = matchRepository;
        this.creditService = creditService;
        this.skillRepository = skillRepository;
        this.badgeService = badgeService;
        this.notificationService = notificationService;
    }
```

In `confirm(...)`, replace `return toDto(sessionRepository.save(s));` with:
```java
        Session saved = sessionRepository.save(s);
        notificationService.notify(s.getScheduledByUserId(), NotificationType.SESSION, "Your session was confirmed.");
        return toDto(saved);
```

In `cancel(...)`, replace `return toDto(sessionRepository.save(s));` with:
```java
        Session saved = sessionRepository.save(s);
        Long other = s.getTeacherUserId().equals(meId) ? s.getLearnerUserId() : s.getTeacherUserId();
        notificationService.notify(other, NotificationType.SESSION, "Your session was cancelled.");
        return toDto(saved);
```

In `reschedule(...)`, replace `return toDto(sessionRepository.save(s));` with:
```java
        Session saved = sessionRepository.save(s);
        Long other = s.getTeacherUserId().equals(meId) ? s.getLearnerUserId() : s.getTeacherUserId();
        notificationService.notify(other, NotificationType.SESSION, "Your session was rescheduled — please confirm the new time.");
        return toDto(saved);
```
(`complete()` and `create()` are unchanged in this task — no notification trigger for those per this plan's scope.)

- [ ] **Step 9: Wire notifications into ReviewService**

In `backend/src/main/java/com/skillswap/service/ReviewService.java`:

Add import:
```java
import com.skillswap.entity.NotificationType;
```

Replace the constructor and its fields with:
```java
    private final SessionRepository sessionRepository;
    private final ReviewRepository reviewRepository;
    private final NotificationService notificationService;

    public ReviewService(SessionRepository sessionRepository, ReviewRepository reviewRepository,
                         NotificationService notificationService) {
        this.sessionRepository = sessionRepository;
        this.reviewRepository = reviewRepository;
        this.notificationService = notificationService;
    }
```

In `create(...)`, replace `return toDto(reviewRepository.save(r));` with:
```java
        Review saved = reviewRepository.save(r);
        notificationService.notify(ratedUserId, NotificationType.REVIEW, "You received a new review.");
        return toDto(saved);
```

- [ ] **Step 10: Write NotificationController**

`backend/src/main/java/com/skillswap/controller/NotificationController.java`:
```java
package com.skillswap.controller;

import com.skillswap.dto.NotificationDto;
import com.skillswap.dto.UnreadCountDto;
import com.skillswap.service.CurrentUser;
import com.skillswap.service.NotificationService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentUser currentUser;

    public NotificationController(NotificationService notificationService, CurrentUser currentUser) {
        this.notificationService = notificationService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<NotificationDto> list() {
        Long userId = currentUser.require().getId();
        return notificationService.list(userId).stream()
                .map(n -> new NotificationDto(n.getId(), n.getType().name(), n.getMessage(), n.isRead(), n.getCreatedDate()))
                .toList();
    }

    @GetMapping("/unread-count")
    public UnreadCountDto unreadCount() {
        return new UnreadCountDto(notificationService.unreadCount(currentUser.require().getId()));
    }

    @PutMapping("/{id}/read")
    public void markRead(@PathVariable Long id) {
        notificationService.markRead(currentUser.require().getId(), id);
    }
}
```

- [ ] **Step 11: Update MatchServiceTest for the new constructor and notification assertions**

In `backend/src/test/java/com/skillswap/service/MatchServiceTest.java`:

Add import:
```java
import com.skillswap.entity.NotificationType;
```

Replace the mock/service fields at the top of the class with:
```java
    private final UserSkillRepository userSkillRepo = mock(UserSkillRepository.class);
    private final MatchRepository matchRepo = mock(MatchRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final MatchService service = new MatchService(userSkillRepo, matchRepo, userRepo, notificationService);
```

In `requestCreatesPendingMatch`, add this verification after the existing `assertThat(dto.userBId()).isEqualTo(2L);` line:
```java
        verify(notificationService).notify(eq(2L), eq(NotificationType.MATCH), anyString());
```

In `respondAcceptsMatch`, add this verification after the existing `assertThat(dto.status()).isEqualTo("ACCEPTED");` line:
```java
        verify(notificationService).notify(eq(2L), eq(NotificationType.MATCH), anyString());
```
(`Match m` in that test has `userAId = 2L`, `userBId = 1L` — confirm this matches the existing test body; the notified user on `respond` is `userAId`, i.e. `2L`, so this assertion is correct as written. Add the static imports `org.mockito.ArgumentMatchers.eq` and `org.mockito.ArgumentMatchers.anyString` if not already present via a wildcard Mockito static import.)

- [ ] **Step 12: Update SessionServiceTest for the new constructor and notification assertions**

In `backend/src/test/java/com/skillswap/service/SessionServiceTest.java`:

Add import:
```java
import com.skillswap.entity.NotificationType;
```

Extend the field list and `service` construction (adding `NotificationService`):
```java
    private final SessionRepository sessionRepo = mock(SessionRepository.class);
    private final MatchRepository matchRepo = mock(MatchRepository.class);
    private final CreditService creditService = mock(CreditService.class);
    private final SkillRepository skillRepo = mock(SkillRepository.class);
    private final BadgeService badgeService = mock(BadgeService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final SessionService service = new SessionService(
            sessionRepo, matchRepo, creditService, skillRepo, badgeService, notificationService);
```

In `confirmSucceedsForOtherParticipant`, add this verification after the existing `assertThat(dto.status()).isEqualTo("CONFIRMED");` line:
```java
        verify(notificationService).notify(eq(10L), eq(NotificationType.SESSION), anyString());
```
(That test's session has `scheduledByUserId = 10L` — the scheduler being notified on confirm.)

In `cancelSucceedsFromPending`, add this verification after the existing `assertThat(dto.status()).isEqualTo("CANCELLED");` line:
```java
        verify(notificationService).notify(eq(10L), eq(NotificationType.SESSION), anyString());
```
(That test's session has `teacherUserId = 10L, learnerUserId = 20L`, cancelled by `meId = 20L` — the *other* participant, `10L`, is notified.)

In `rescheduleResetsStatusAndReassignsScheduler`, add this verification after the existing `assertThat(dto.sessionDate()).isEqualTo(LocalDate.of(2026, 8, 2));` line:
```java
        verify(notificationService).notify(eq(10L), eq(NotificationType.SESSION), anyString());
```
(That test's session has `teacherUserId = 10L, learnerUserId = 20L`, rescheduled by `meId = 20L` — the *other* participant, `10L`, is notified.)

- [ ] **Step 13: Update ReviewServiceTest for the new constructor and notification assertion**

In `backend/src/test/java/com/skillswap/service/ReviewServiceTest.java`:

Add import:
```java
import com.skillswap.entity.NotificationType;
```

Replace the mock/service fields at the top of the class with:
```java
    private final SessionRepository sessionRepo = mock(SessionRepository.class);
    private final ReviewRepository reviewRepo = mock(ReviewRepository.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final ReviewService service = new ReviewService(sessionRepo, reviewRepo, notificationService);
```

In `createPersistsWithRatedUserAsTheOtherParticipant`, add this verification after the existing `assertThat(dto.rating()).isEqualTo(4);` line:
```java
        verify(notificationService).notify(eq(20L), eq(NotificationType.REVIEW), anyString());
```

- [ ] **Step 14: Run the full suite**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test`
Expected: PASS — all 84 prior tests (Plans 1–3 plus Task 1's 12 plus Task 2's 5) plus `NotificationServiceTest` (5), with `MatchServiceTest`, `SessionServiceTest`, `ReviewServiceTest` updated and still green.

- [ ] **Step 15: Commit**

```bash
git add backend/src/main/resources/db/migration/V8__notifications.sql \
        backend/src/main/java/com/skillswap/entity/NotificationType.java \
        backend/src/main/java/com/skillswap/entity/Notification.java \
        backend/src/main/java/com/skillswap/repository/NotificationRepository.java \
        backend/src/main/java/com/skillswap/dto/NotificationDto.java \
        backend/src/main/java/com/skillswap/dto/UnreadCountDto.java \
        backend/src/main/java/com/skillswap/service/NotificationService.java \
        backend/src/main/java/com/skillswap/controller/NotificationController.java \
        backend/src/main/java/com/skillswap/service/MatchService.java \
        backend/src/main/java/com/skillswap/service/SessionService.java \
        backend/src/main/java/com/skillswap/service/ReviewService.java \
        backend/src/test/java/com/skillswap/service/NotificationServiceTest.java \
        backend/src/test/java/com/skillswap/service/MatchServiceTest.java \
        backend/src/test/java/com/skillswap/service/SessionServiceTest.java \
        backend/src/test/java/com/skillswap/service/ReviewServiceTest.java
git commit -m "feat: emit in-app notifications for match, session, and review activity"
```

---

## Self-Review

**Spec coverage (Plan 4 slice):**
- Post-session rating 1–5 + comment, one review per participant per session, average rating on profile → Task 1. ✅
- Review moderation flagging → Task 1 (`flag` endpoint; the admin-side queue that *reads* flagged reviews is correctly deferred to Plan 6). ✅
- Skill-specific badges by teaching frequency (Beginner/Intermediate/Expert) → Task 2. Admin-granted `VERIFIED` is modeled in the enum but intentionally not awarded by this plan (Plan 6). ✅
- In-app notifications for match requests, session changes, reviews → Task 3. Forum activity notifications are modeled (`NotificationType.FORUM`) but not yet triggered — Plan 5 will add the trigger when forums exist. ✅
- Email events logged, not sent → Task 3's `NotificationService.notify` (single choke point, SLF4J). ✅

**Placeholder scan:** No TBD/TODO; every step has complete code, including every modified file's exact insertion point and surrounding context. ✅

**Type consistency:** `ReviewRepository`/`ReviewService`/`ReviewController` signatures match across Task 1's test, service, and controller. `Session.skillId`/`SessionDto`'s new field order/`CreateSessionRequest.skillId` are consistent across Task 2's entity, DTOs, service, controller (unchanged), and both modified test files. `BadgeService(SessionRepository, SkillBadgeRepository)` constructor matches its test and `SessionService`'s injection. `NotificationService(NotificationRepository)` constructor and `notify(Long, NotificationType, String)` signature are identical across Task 3's own test and its three call sites in `MatchService`/`SessionService`/`ReviewService` (and their updated tests). ✅

**Scope check:** One cohesive slice — reviews, badges, and notifications are naturally sequenced (badges need session-skill data; notifications need reviews to exist as a trigger source) and sized similarly to Plans 2–3 (3 tasks, cumulative test count 67→89).

**Deliberate simplifications (flagged for the record):**
- Badge tiers are cumulative (all earned thresholds kept, not "highest only") — simpler to implement (no delete/replace logic) and arguably better UX (a teacher's full history stays visible). `ponytail: hardcoded thresholds (1/5/15), make configurable if the rule ever needs tuning without a redeploy.`
- `flag` has no rate-limit or "already flagged" idempotency guard beyond the boolean flip (re-flagging an already-flagged review is a harmless no-op, not an error) — acceptable for MVP; Plan 6's admin queue is where flagged reviews actually get resolved.
- Session `skillId` is `NOT NULL` added via a bare `ALTER TABLE` with no backfill — correct and sufficient because this is a pre-launch academic project with no real production rows yet; would need a backfill strategy before any real deployment with existing data.
- Notifications have no push/websocket delivery — frontend is expected to poll `GET /api/notifications`/`GET /api/notifications/unread-count` (matches the spec's "in-app notification center" language; no real-time transport was ever in scope per CLAUDE.md's guardrails).
