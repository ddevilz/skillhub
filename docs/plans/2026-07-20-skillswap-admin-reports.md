# SkillSwap Hub — Plan 6: Admin Panel + Reports

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Admins can list/search/filter users and activate/deactivate accounts, manage the master skill and forum-category catalogs, grant `VERIFIED` skill badges, resolve the moderation queues Plans 4–5 deliberately left open (flagged reviews, moderated forum content), and view five platform reports.

**Architecture:** Extends the Plan 1–5 Spring Boot monolith. No new entities — every capability here is CRUD/aggregation over tables that already exist. Admin actions extend already-shipped services (`SkillService`, `ForumService`, `BadgeService`, `ReviewService`) the same way Plan 5 added moderation methods to `ForumService`, rather than duplicating logic in parallel admin-only services. A new `AdminUserService`/`AdminUserController` handles the one genuinely new surface (user administration — nothing owns `UserRepository` beyond `AuthService`/`CurrentUser` today). A new `AdminReportService`/`AdminReportController` computes all five reports via in-memory `Stream`/`Collectors.groupingBy` aggregation over `findAll()` rather than cross-dialect SQL date/aggregate functions — this project's data volumes are small (academic MVP, no scale requirement) and it sidesteps H2-vs-MySQL date-function portability entirely, which is a real risk this plan's own Task 1 review will re-confirm was the right call after Plan 5 hit two dialect-portability bugs in one task. Every endpoint in this plan is gated by `CurrentUser.requireAdmin()` (added in Plan 5) — this plan is the first to make heavy use of it.

**Tech Stack:** Java 17, Spring Boot 3.2.5, Gradle, Spring Data JPA, Flyway (no new migrations — genuinely none needed), MySQL 8 (H2 for tests). No frontend or Redis changes in this plan.

## Global Constraints

- Base package `com.skillswap`. Java **17**. Spring Boot **3.2.5**. Gradle only (never Maven).
- **Build with JDK 17:** machine default `java` is JDK 26, unsupported by Gradle 8.7. Prefix every gradle command with `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`.
- **No new Flyway migrations in this plan.** V1–V10 (Plans 1–5) are frozen. Every capability here operates on existing tables/columns.
- All admin routes live under `/api/admin/**` and call `currentUser.requireAdmin()` as their first statement — 403 (not 404) for a non-admin, matching Plan 5's established, deliberate distinction (see Plan 5's Global Constraints for the reasoning; it hasn't changed).
- Deleting master data (a `Skill`, a `ForumCategory`) that's still referenced elsewhere must be **blocked with a 409**, not left to a bare-FK `DataIntegrityViolationException` surfacing as a 500 — this is exactly the class of bug Plan 5's final review found and fixed for forum post/comment deletion. Check-before-delete in the service layer; do not add `ON DELETE CASCADE` to these particular FKs (cascading a skill delete would silently destroy historical session/review/badge context, which is never what "delete a catalog entry" should mean).
- **Derived Spring Data query method names must match the entity's real field name, not its boolean getter spelling.** `Review.flagged` (getter `isFlagged()`) and `ForumPost.moderated`/`ForumComment.moderated` (getter `isModerated()`) are the two recurring traps in this codebase (Plan 5 hit this exact issue with `IsModeratedFalse`). `findByFlaggedTrue()` and `findByModeratedTrue()` are the CORRECT forms (property `flagged`/`moderated`, condition `True`) — verify this reasoning applies before adding any new derived query in this plan; if in doubt, use an explicit `@Query` like Plan 5 ultimately did.
- Business errors via `ResponseStatusException` only; do not touch `GlobalExceptionHandler`.
- Thin controllers; DTOs at the boundary (never serialize `User`/`Skill`/`ForumCategory`/`Review` entities directly from an admin endpoint either).
- Test profile is H2 (Flyway disabled, Hibernate `create-drop`). Every entity already has `@ColumnDefault` on its date fields as of Plan 5's systemic fix — no new raw-JDBC test fixture in this plan should need a workaround for that class of bug.
- Git author **Devashish Jadhav <jadhavom24@gmail.com>**. **Never** add `Co-Authored-By` or AI attribution. Conventional Commit messages. Commit at the end of every task.

**Interfaces already available from Plans 1–5:** `User(id, fullName, email, city, role:Role, active:boolean, createdDate)`, `UserRepository`, `Role{USER,ADMIN}`, `CurrentUser.require()`/`requireAdmin()`; `Skill(id, skillName, category, description)`, `SkillRepository`, `SkillService(SkillRepository, UserSkillRepository)` with `catalog()`, `categories()`, `mySkills`, `add`, `remove`; `ForumCategory`/`ForumPost`/`ForumComment`, `ForumCategoryRepository`, `ForumPostRepository`, `ForumCommentRepository`, `ForumService(ForumCategoryRepository, ForumPostRepository, ForumCommentRepository, ForumPostUpvoteRepository, UserRepository, NotificationService)` with `categories/postsByCategory/search/getPost/createPost/deletePost/comments/addComment/deleteComment/upvote/moderatePost/adminDeletePost/moderateComment/adminDeleteComment`; `Review(id, sessionId, reviewerUserId, ratedUserId, rating, comments, flagged:boolean, createdDate)`, `ReviewRepository`, `ReviewService(SessionRepository, ReviewRepository, NotificationService)` with `create/flag/ratingSummary`; `SkillBadge`, `BadgeType{BEGINNER,INTERMEDIATE,EXPERT,VERIFIED}`, `SkillBadgeRepository`, `BadgeService(SessionRepository, SkillBadgeRepository)` with `evaluateAndAward/badgesFor`; `Session`, `SessionStatus{PENDING,CONFIRMED,COMPLETED,CANCELLED}`, `SessionRepository`; `UserSkill`, `UserSkillRepository`.

---

### Task 1: Admin user management (list/search/filter, activate/deactivate)

**Files:**
- Create: `backend/src/main/java/com/skillswap/dto/AdminUserDto.java`
- Create: `backend/src/main/java/com/skillswap/dto/UpdateUserStatusRequest.java`
- Create: `backend/src/main/java/com/skillswap/service/AdminUserService.java`
- Create: `backend/src/main/java/com/skillswap/controller/AdminUserController.java`
- Test: `backend/src/test/java/com/skillswap/service/AdminUserServiceTest.java`
- Test: `backend/src/test/java/com/skillswap/controller/AdminUserFlowTest.java`

**Interfaces:**
- Consumes: `UserRepository` (Plan 1, unmodified), `CurrentUser.requireAdmin()` (Plan 5, unmodified).
- Produces:
  - `record AdminUserDto(Long id, String fullName, String email, String city, String role, boolean active, java.time.LocalDateTime createdDate)`.
  - `record UpdateUserStatusRequest(boolean active)`.
  - `AdminUserService` — `List<AdminUserDto> listUsers(String search, Boolean activeOnly)` (both nullable — no filter applied when null; `search` matches full name or email, case-insensitive substring), `AdminUserDto updateStatus(Long userId, boolean active)` (404 if user not found).
  - `GET /api/admin/users?search=&active=`, `PUT /api/admin/users/{id}/status`.

- [ ] **Step 1: Write the failing service test**

`backend/src/test/java/com/skillswap/service/AdminUserServiceTest.java`:
```java
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests AdminUserServiceTest`
Expected: FAIL — `AdminUserDto`, `AdminUserService` do not exist.

- [ ] **Step 3: Write the DTOs**

`backend/src/main/java/com/skillswap/dto/AdminUserDto.java`:
```java
package com.skillswap.dto;

import java.time.LocalDateTime;

public record AdminUserDto(Long id, String fullName, String email, String city,
                           String role, boolean active, LocalDateTime createdDate) {}
```

`backend/src/main/java/com/skillswap/dto/UpdateUserStatusRequest.java`:
```java
package com.skillswap.dto;

public record UpdateUserStatusRequest(boolean active) {}
```

- [ ] **Step 4: Write AdminUserService**

`backend/src/main/java/com/skillswap/service/AdminUserService.java`:
```java
package com.skillswap.service;

import com.skillswap.dto.AdminUserDto;
import com.skillswap.entity.User;
import com.skillswap.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class AdminUserService {

    private final UserRepository userRepository;

    public AdminUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<AdminUserDto> listUsers(String search, Boolean activeOnly) {
        String needle = search == null ? null : search.toLowerCase();
        return userRepository.findAll().stream()
                .filter(u -> activeOnly == null || u.isActive() == activeOnly)
                .filter(u -> needle == null || needle.isBlank()
                        || u.getFullName().toLowerCase().contains(needle)
                        || u.getEmail().toLowerCase().contains(needle))
                .map(this::toDto)
                .toList();
    }

    public AdminUserDto updateStatus(Long userId, boolean active) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        u.setActive(active);
        return toDto(userRepository.save(u));
    }

    private AdminUserDto toDto(User u) {
        return new AdminUserDto(u.getId(), u.getFullName(), u.getEmail(), u.getCity(),
                u.getRole().name(), u.isActive(), u.getCreatedDate());
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests AdminUserServiceTest`
Expected: PASS — all five cases green.

- [ ] **Step 6: Write AdminUserController**

`backend/src/main/java/com/skillswap/controller/AdminUserController.java`:
```java
package com.skillswap.controller;

import com.skillswap.dto.AdminUserDto;
import com.skillswap.dto.UpdateUserStatusRequest;
import com.skillswap.service.AdminUserService;
import com.skillswap.service.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final CurrentUser currentUser;

    public AdminUserController(AdminUserService adminUserService, CurrentUser currentUser) {
        this.adminUserService = adminUserService;
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
}
```

- [ ] **Step 7: Write the admin user flow test**

`backend/src/test/java/com/skillswap/controller/AdminUserFlowTest.java`:
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
class AdminUserFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String register(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("fullName", email, "email", email, "password", "password1"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("email", email, "password", "password1"));
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(res, "$.token");
    }

    private Long meId(String token) throws Exception {
        String res = mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(res, "$.id")).longValue();
    }

    @Test
    void nonAdminCannotListUsers() throws Exception {
        String userToken = register("plain-user-p6@example.com");
        mvc.perform(get("/api/admin/users").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListAndDeactivateUser() throws Exception {
        String targetToken = register("target-user-p6@example.com");
        Long targetId = meId(targetToken);
        register("admin-user-p6@example.com");
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", "admin-user-p6@example.com");
        String adminToken = login("admin-user-p6@example.com");

        mvc.perform(get("/api/admin/users").param("search", "target-user-p6")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(targetId.intValue()));

        String body = json.writeValueAsString(Map.of("active", false));
        mvc.perform(put("/api/admin/users/{id}/status", targetId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }
}
```
- [ ] **Step 8: Run the full suite**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test`
Expected: PASS — all 117 prior tests (Plans 1–5) plus `AdminUserServiceTest` (5) and `AdminUserFlowTest` (2).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/skillswap/dto/AdminUserDto.java \
        backend/src/main/java/com/skillswap/dto/UpdateUserStatusRequest.java \
        backend/src/main/java/com/skillswap/service/AdminUserService.java \
        backend/src/main/java/com/skillswap/controller/AdminUserController.java \
        backend/src/test/java/com/skillswap/service/AdminUserServiceTest.java \
        backend/src/test/java/com/skillswap/controller/AdminUserFlowTest.java
git commit -m "feat: add admin user listing, search/filter, and activate/deactivate"
```

---

### Task 2: Admin catalog management (skill CRUD, forum category CRUD, verified badges)

**Files:**
- Create: `backend/src/main/java/com/skillswap/dto/AdminSkillRequest.java`
- Create: `backend/src/main/java/com/skillswap/dto/CreateForumCategoryRequest.java`
- Create: `backend/src/main/java/com/skillswap/controller/AdminSkillController.java`
- Modify: `backend/src/main/java/com/skillswap/repository/UserSkillRepository.java` (add `existsBySkillId`)
- Modify: `backend/src/main/java/com/skillswap/repository/SessionRepository.java` (add `existsBySkillId`)
- Modify: `backend/src/main/java/com/skillswap/repository/ForumPostRepository.java` (add `existsByCategoryId`)
- Modify: `backend/src/main/java/com/skillswap/service/SkillService.java` (add create/update/delete)
- Modify: `backend/src/main/java/com/skillswap/service/ForumService.java` (add category create/update/delete)
- Modify: `backend/src/main/java/com/skillswap/service/BadgeService.java` (add `awardVerified`)
- Modify: `backend/src/main/java/com/skillswap/controller/AdminUserController.java` (add verify-skill endpoint)
- Modify: `backend/src/main/java/com/skillswap/controller/AdminForumController.java` (add category CRUD endpoints)
- Test: `backend/src/test/java/com/skillswap/service/SkillServiceTest.java` (add cases)
- Test: `backend/src/test/java/com/skillswap/service/ForumServiceTest.java` (add cases)
- Test: `backend/src/test/java/com/skillswap/service/BadgeServiceTest.java` (add cases)
- Test: `backend/src/test/java/com/skillswap/controller/AdminCatalogFlowTest.java` (new)

**Interfaces:**
- Consumes: `SkillRepository`, `UserSkillRepository`, `SessionRepository`, `ForumCategoryRepository`, `ForumPostRepository`, `SkillBadgeRepository` (all pre-existing).
- Produces:
  - `record AdminSkillRequest(@NotBlank String skillName, @NotBlank String category, String description)`.
  - `record CreateForumCategoryRequest(@NotBlank String categoryName, String description)`.
  - `SkillService.createSkill(AdminSkillRequest)`, `updateSkill(Long, AdminSkillRequest)`, `deleteSkill(Long)` (409 if any `UserSkill` or `Session` references it — checked via the two new `existsBySkillId` repo methods).
  - `ForumService.createCategory(CreateForumCategoryRequest)`, `updateCategory(Long, CreateForumCategoryRequest)`, `deleteCategory(Long)` (409 if any `ForumPost` references it, moderated or not).
  - `BadgeService.awardVerified(Long userId, Long skillId)` — idempotent no-op if already granted.
  - `POST/PUT/DELETE /api/admin/skills[/{id}]`, `POST/PUT/DELETE /api/admin/forum/categories[/{id}]`, `POST /api/admin/users/{id}/skills/{skillId}/verify`.

- [ ] **Step 1: Write the failing tests (append to existing test files)**

Add to `backend/src/test/java/com/skillswap/service/SkillServiceTest.java` (the class already has `skillRepo`, `userSkillRepo`, `service` fields — reuse them; add `sessionRepo` as a new mock and update the `service` construction):
```java
    private final com.skillswap.repository.SessionRepository sessionRepo = mock(com.skillswap.repository.SessionRepository.class);
```
Change the existing `service` field declaration to:
```java
    private final SkillService service = new SkillService(skillRepo, userSkillRepo, sessionRepo);
```
Then add these test methods:
```java
    @Test
    void createSkillPersistsAndReturnsDto() {
        when(skillRepo.save(any(Skill.class))).thenAnswer(i -> {
            Skill s = i.getArgument(0);
            try { var f = Skill.class.getDeclaredField("id"); f.setAccessible(true); f.set(s, 1L); }
            catch (Exception e) { throw new RuntimeException(e); }
            return s;
        });
        SkillDto dto = service.createSkill(new com.skillswap.dto.AdminSkillRequest("Ukulele", "Music", "Small guitar"));
        assertThat(dto.skillName()).isEqualTo("Ukulele");
    }

    @Test
    void updateSkillRejectsWhenNotFound() {
        when(skillRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateSkill(99L, new com.skillswap.dto.AdminSkillRequest("X", "Y", null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void deleteSkillRejectsWhenInUseByUserSkill() {
        Skill s = new Skill();
        when(skillRepo.findById(1L)).thenReturn(Optional.of(s));
        when(userSkillRepo.existsBySkillId(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteSkill(1L)).isInstanceOf(ResponseStatusException.class);
        verify(skillRepo, never()).delete(any());
    }

    @Test
    void deleteSkillRejectsWhenInUseBySession() {
        Skill s = new Skill();
        when(skillRepo.findById(1L)).thenReturn(Optional.of(s));
        when(userSkillRepo.existsBySkillId(1L)).thenReturn(false);
        when(sessionRepo.existsBySkillId(1L)).thenReturn(true);
        assertThatThrownBy(() -> service.deleteSkill(1L)).isInstanceOf(ResponseStatusException.class);
        verify(skillRepo, never()).delete(any());
    }

    @Test
    void deleteSkillSucceedsWhenUnused() {
        Skill s = new Skill();
        when(skillRepo.findById(1L)).thenReturn(Optional.of(s));
        when(userSkillRepo.existsBySkillId(1L)).thenReturn(false);
        when(sessionRepo.existsBySkillId(1L)).thenReturn(false);
        service.deleteSkill(1L);
        verify(skillRepo).delete(s);
    }
```
(Add `import java.util.Optional;` and `import static org.assertj.core.api.Assertions.assertThatThrownBy;` and `import org.springframework.web.server.ResponseStatusException;` if not already present in the file — check first, this test file already imports most Mockito/AssertJ statics via wildcard per the established pattern from Plan 2.)

Add to `backend/src/test/java/com/skillswap/service/ForumServiceTest.java` (reuse existing `categoryRepo`, `postRepo`, `service` fields):
```java
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
```

Add to `backend/src/test/java/com/skillswap/service/BadgeServiceTest.java` (reuse existing `badgeRepo`, `service` fields):
```java
    @Test
    void awardVerifiedGrantsBadge() {
        when(badgeRepo.existsByUserIdAndSkillIdAndBadgeType(1L, 4L, BadgeType.VERIFIED)).thenReturn(false);
        service.awardVerified(1L, 4L);
        verify(badgeRepo).save(any(SkillBadge.class));
    }

    @Test
    void awardVerifiedIsIdempotent() {
        when(badgeRepo.existsByUserIdAndSkillIdAndBadgeType(1L, 4L, BadgeType.VERIFIED)).thenReturn(true);
        service.awardVerified(1L, 4L);
        verify(badgeRepo, never()).save(any());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests SkillServiceTest --tests ForumServiceTest --tests BadgeServiceTest`
Expected: FAIL — new methods/DTOs/repo methods don't exist, and `SkillService`'s constructor signature changed so the existing test file won't even compile until Step 1's constructor-call update and Step 4 below land together.

- [ ] **Step 3: Write the new DTOs**

`backend/src/main/java/com/skillswap/dto/AdminSkillRequest.java`:
```java
package com.skillswap.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminSkillRequest(
        @NotBlank String skillName,
        @NotBlank String category,
        String description) {}
```

`backend/src/main/java/com/skillswap/dto/CreateForumCategoryRequest.java`:
```java
package com.skillswap.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateForumCategoryRequest(
        @NotBlank String categoryName,
        String description) {}
```

- [ ] **Step 4: Add the new repository methods**

In `backend/src/main/java/com/skillswap/repository/UserSkillRepository.java`, add this method to the interface:
```java
    boolean existsBySkillId(Long skillId);
```

In `backend/src/main/java/com/skillswap/repository/SessionRepository.java`, add this method to the interface:
```java
    boolean existsBySkillId(Long skillId);
```

In `backend/src/main/java/com/skillswap/repository/ForumPostRepository.java`, add this method to the interface:
```java
    boolean existsByCategoryId(Long categoryId);
```

- [ ] **Step 5: Modify SkillService**

In `backend/src/main/java/com/skillswap/service/SkillService.java`, add the import `com.skillswap.dto.AdminSkillRequest` and `com.skillswap.repository.SessionRepository`. Replace the constructor and its fields with:
```java
    private final SkillRepository skillRepository;
    private final UserSkillRepository userSkillRepository;
    private final SessionRepository sessionRepository;

    public SkillService(SkillRepository skillRepository, UserSkillRepository userSkillRepository,
                        SessionRepository sessionRepository) {
        this.skillRepository = skillRepository;
        this.userSkillRepository = userSkillRepository;
        this.sessionRepository = sessionRepository;
    }
```
Then add these three public methods (anywhere after the existing public methods):
```java
    @CacheEvict(value = "skills", allEntries = true)
    public SkillDto createSkill(AdminSkillRequest req) {
        Skill s = new Skill();
        s.setSkillName(req.skillName());
        s.setCategory(req.category());
        s.setDescription(req.description());
        Skill saved = skillRepository.save(s);
        return new SkillDto(saved.getId(), saved.getSkillName(), saved.getCategory(), saved.getDescription());
    }

    @CacheEvict(value = "skills", allEntries = true)
    public SkillDto updateSkill(Long skillId, AdminSkillRequest req) {
        Skill s = skillRepository.findById(skillId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found"));
        s.setSkillName(req.skillName());
        s.setCategory(req.category());
        s.setDescription(req.description());
        Skill saved = skillRepository.save(s);
        return new SkillDto(saved.getId(), saved.getSkillName(), saved.getCategory(), saved.getDescription());
    }

    @CacheEvict(value = "skills", allEntries = true)
    public void deleteSkill(Long skillId) {
        Skill s = skillRepository.findById(skillId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found"));
        if (userSkillRepository.existsBySkillId(skillId) || sessionRepository.existsBySkillId(skillId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Skill is in use and cannot be deleted");
        }
        skillRepository.delete(s);
    }
```

- [ ] **Step 6: Modify ForumService**

In `backend/src/main/java/com/skillswap/service/ForumService.java`, add these three public methods (anywhere after the existing public methods — no constructor change needed, `categoryRepository`/`postRepository` are already injected):
```java
    public ForumCategoryDto createCategory(CreateForumCategoryRequest req) {
        ForumCategory c = new ForumCategory();
        c.setCategoryName(req.categoryName());
        c.setDescription(req.description());
        ForumCategory saved = categoryRepository.save(c);
        return new ForumCategoryDto(saved.getId(), saved.getCategoryName(), saved.getDescription());
    }

    public ForumCategoryDto updateCategory(Long categoryId, CreateForumCategoryRequest req) {
        ForumCategory c = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        c.setCategoryName(req.categoryName());
        c.setDescription(req.description());
        ForumCategory saved = categoryRepository.save(c);
        return new ForumCategoryDto(saved.getId(), saved.getCategoryName(), saved.getDescription());
    }

    public void deleteCategory(Long categoryId) {
        ForumCategory c = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        if (postRepository.existsByCategoryId(categoryId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category has posts and cannot be deleted");
        }
        categoryRepository.delete(c);
    }
```
(The file already has `import com.skillswap.dto.*;` and `import com.skillswap.entity.*;` wildcards from Plan 5, so `CreateForumCategoryRequest`/`ForumCategoryDto`/`ForumCategory` resolve without new imports.)

- [ ] **Step 7: Modify BadgeService**

In `backend/src/main/java/com/skillswap/service/BadgeService.java`, add this public method (after `badgesFor`, before the private `awardIfReached`):
```java
    /** Admin-only grant — VERIFIED is never awarded by evaluateAndAward's rule engine. Idempotent. */
    public void awardVerified(Long userId, Long skillId) {
        if (skillBadgeRepository.existsByUserIdAndSkillIdAndBadgeType(userId, skillId, BadgeType.VERIFIED)) {
            return;
        }
        SkillBadge b = new SkillBadge();
        b.setUserId(userId);
        b.setSkillId(skillId);
        b.setBadgeType(BadgeType.VERIFIED);
        skillBadgeRepository.save(b);
    }
```

- [ ] **Step 8: Run tests to verify they pass**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests SkillServiceTest --tests ForumServiceTest --tests BadgeServiceTest`
Expected: PASS — all cases in all three files green (existing cases plus the new ones).

- [ ] **Step 9: Write AdminSkillController**

`backend/src/main/java/com/skillswap/controller/AdminSkillController.java`:
```java
package com.skillswap.controller;

import com.skillswap.dto.AdminSkillRequest;
import com.skillswap.dto.SkillDto;
import com.skillswap.service.CurrentUser;
import com.skillswap.service.SkillService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/skills")
public class AdminSkillController {

    private final SkillService skillService;
    private final CurrentUser currentUser;

    public AdminSkillController(SkillService skillService, CurrentUser currentUser) {
        this.skillService = skillService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<SkillDto> create(@Valid @RequestBody AdminSkillRequest req) {
        currentUser.requireAdmin();
        return ResponseEntity.status(HttpStatus.CREATED).body(skillService.createSkill(req));
    }

    @PutMapping("/{id}")
    public SkillDto update(@PathVariable Long id, @Valid @RequestBody AdminSkillRequest req) {
        currentUser.requireAdmin();
        return skillService.updateSkill(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        currentUser.requireAdmin();
        skillService.deleteSkill(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 10: Add category CRUD to AdminForumController**

In `backend/src/main/java/com/skillswap/controller/AdminForumController.java`, add the import `com.skillswap.dto.CreateForumCategoryRequest`, `com.skillswap.dto.ForumCategoryDto`, `jakarta.validation.Valid`, and `org.springframework.http.HttpStatus`. Add these three endpoints (anywhere after the existing four):
```java
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
```

- [ ] **Step 11: Add verify-skill endpoint to AdminUserController**

In `backend/src/main/java/com/skillswap/controller/AdminUserController.java`, add the import `com.skillswap.service.BadgeService`. Add `BadgeService badgeService` as a new constructor parameter/field:
```java
    private final AdminUserService adminUserService;
    private final BadgeService badgeService;
    private final CurrentUser currentUser;

    public AdminUserController(AdminUserService adminUserService, BadgeService badgeService, CurrentUser currentUser) {
        this.adminUserService = adminUserService;
        this.badgeService = badgeService;
        this.currentUser = currentUser;
    }
```
Add this endpoint (after `updateStatus`):
```java
    @PostMapping("/{id}/skills/{skillId}/verify")
    public ResponseEntity<Void> verifySkill(@PathVariable Long id, @PathVariable Long skillId) {
        currentUser.requireAdmin();
        badgeService.awardVerified(id, skillId);
        return ResponseEntity.ok().build();
    }
```

- [ ] **Step 12: Write the admin catalog flow test**

`backend/src/test/java/com/skillswap/controller/AdminCatalogFlowTest.java`:
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
class AdminCatalogFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String register(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("fullName", email, "email", email, "password", "password1"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("email", email, "password", "password1"));
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(res, "$.token");
    }

    private Long meId(String token) throws Exception {
        String res = mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(res, "$.id")).longValue();
    }

    @Test
    void adminCreatesSkillThenGrantsVerifiedBadge() throws Exception {
        String userToken = register("catalog-user@example.com");
        Long userId = meId(userToken);
        register("catalog-admin@example.com");
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", "catalog-admin@example.com");
        String adminToken = login("catalog-admin@example.com");

        String skillBody = json.writeValueAsString(Map.of("skillName", "Chess", "category", "Games", "description", "Strategy board game"));
        String skillRes = mvc.perform(post("/api/admin/skills")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(skillBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long skillId = ((Number) com.jayway.jsonpath.JsonPath.read(skillRes, "$.id")).longValue();

        mvc.perform(post("/api/admin/users/{id}/skills/{skillId}/verify", userId, skillId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mvc.perform(get("/api/users/{id}/badges", userId).header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].badgeType").value("VERIFIED"));
    }

    @Test
    void adminManagesForumCategory() throws Exception {
        register("category-admin@example.com");
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", "category-admin@example.com");
        String adminToken = login("category-admin@example.com");

        String body = json.writeValueAsString(Map.of("categoryName", "E-Sports " + System.identityHashCode(this), "description", "Competitive gaming"));
        mvc.perform(post("/api/admin/forum/categories")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }
}
```

- [ ] **Step 13: Run the full suite**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test`
Expected: PASS — 124 tests at the end of Task 1, plus this task's additions to `SkillServiceTest` (5), `ForumServiceTest` (4), `BadgeServiceTest` (2), and `AdminCatalogFlowTest` (2) = **137 total**.

- [ ] **Step 14: Commit**

```bash
git add backend/src/main/java/com/skillswap/dto/AdminSkillRequest.java \
        backend/src/main/java/com/skillswap/dto/CreateForumCategoryRequest.java \
        backend/src/main/java/com/skillswap/controller/AdminSkillController.java \
        backend/src/main/java/com/skillswap/repository/UserSkillRepository.java \
        backend/src/main/java/com/skillswap/repository/SessionRepository.java \
        backend/src/main/java/com/skillswap/repository/ForumPostRepository.java \
        backend/src/main/java/com/skillswap/service/SkillService.java \
        backend/src/main/java/com/skillswap/service/ForumService.java \
        backend/src/main/java/com/skillswap/service/BadgeService.java \
        backend/src/main/java/com/skillswap/controller/AdminUserController.java \
        backend/src/main/java/com/skillswap/controller/AdminForumController.java \
        backend/src/test/java/com/skillswap/service/SkillServiceTest.java \
        backend/src/test/java/com/skillswap/service/ForumServiceTest.java \
        backend/src/test/java/com/skillswap/service/BadgeServiceTest.java \
        backend/src/test/java/com/skillswap/controller/AdminCatalogFlowTest.java
git commit -m "feat: add admin skill/category CRUD and verified-badge granting"
```

---

### Task 3: Moderation queues (flagged reviews, moderated forum content) + review admin actions

**Files:**
- Modify: `backend/src/main/java/com/skillswap/repository/ReviewRepository.java` (add `findByFlaggedTrue`)
- Modify: `backend/src/main/java/com/skillswap/repository/ForumPostRepository.java` (add `findByModeratedTrue`)
- Modify: `backend/src/main/java/com/skillswap/repository/ForumCommentRepository.java` (add `findByModeratedTrue`)
- Modify: `backend/src/main/java/com/skillswap/service/ReviewService.java` (add `flaggedReviews`/`unflag`/`adminDelete`)
- Modify: `backend/src/main/java/com/skillswap/service/ForumService.java` (add `moderatedPosts`/`moderatedComments`)
- Create: `backend/src/main/java/com/skillswap/controller/AdminReviewController.java`
- Modify: `backend/src/main/java/com/skillswap/controller/AdminForumController.java` (add moderated-content list endpoints)
- Test: `backend/src/test/java/com/skillswap/service/ReviewServiceTest.java` (add cases)
- Test: `backend/src/test/java/com/skillswap/service/ForumServiceTest.java` (add cases)
- Test: `backend/src/test/java/com/skillswap/controller/AdminModerationFlowTest.java` (new)

**Interfaces:**
- Consumes: `ReviewRepository`, `ForumPostRepository`, `ForumCommentRepository` (all pre-existing, no constructor changes to any service in this task).
- Produces:
  - `ReviewRepository.findByFlaggedTrue(): List<Review>` — safe derived query (the real field is named `flagged`; `FlaggedTrue` matches it exactly, unlike the `IsModeratedFalse` trap).
  - `ForumPostRepository.findByModeratedTrue(): List<ForumPost>`, `ForumCommentRepository.findByModeratedTrue(): List<ForumComment>` — same reasoning; field is `moderated`, `ModeratedTrue` matches it exactly.
  - `ReviewService.flaggedReviews(): List<ReviewDto>`, `unflag(Long reviewId)`, `adminDelete(Long reviewId)`.
  - `ForumService.moderatedPosts(): List<ForumPostDto>`, `moderatedComments(): List<ForumCommentDto>`.
  - `GET /api/admin/reviews/flagged`, `PUT /api/admin/reviews/{id}/unflag`, `DELETE /api/admin/reviews/{id}`, `GET /api/admin/forum/posts/moderated`, `GET /api/admin/forum/comments/moderated`.

- [ ] **Step 1: Write the failing tests (append to existing test files)**

Add to `backend/src/test/java/com/skillswap/service/ReviewServiceTest.java` (reuse existing `reviewRepo`, `service` fields — no constructor change):
```java
    @Test
    void flaggedReviewsReturnsOnlyFlagged() {
        Review r = new Review();
        when(reviewRepo.findByFlaggedTrue()).thenReturn(java.util.List.of(r));
        assertThat(service.flaggedReviews()).hasSize(1);
    }

    @Test
    void unflagClearsFlag() {
        Review r = new Review();
        r.setFlagged(true);
        when(reviewRepo.findById(9L)).thenReturn(Optional.of(r));
        service.unflag(9L);
        assertThat(r.isFlagged()).isFalse();
        verify(reviewRepo).save(r);
    }

    @Test
    void adminDeleteRemovesReview() {
        Review r = new Review();
        when(reviewRepo.findById(9L)).thenReturn(Optional.of(r));
        service.adminDelete(9L);
        verify(reviewRepo).delete(r);
    }

    @Test
    void unflagRejectsWhenNotFound() {
        when(reviewRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.unflag(99L)).isInstanceOf(ResponseStatusException.class);
    }
```

Add to `backend/src/test/java/com/skillswap/service/ForumServiceTest.java` (reuse existing `postRepo`, `commentRepo`, `service` fields):
```java
    @Test
    void moderatedPostsReturnsOnlyModerated() {
        when(postRepo.findByModeratedTrue()).thenReturn(java.util.List.of(post(5L, 10L, true)));
        assertThat(service.moderatedPosts()).hasSize(1);
    }

    @Test
    void moderatedCommentsReturnsOnlyModerated() {
        ForumComment c = new ForumComment();
        c.setModerated(true);
        when(commentRepo.findByModeratedTrue()).thenReturn(java.util.List.of(c));
        assertThat(service.moderatedComments()).hasSize(1);
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests ReviewServiceTest --tests ForumServiceTest`
Expected: FAIL — `findByFlaggedTrue`/`findByModeratedTrue`/`flaggedReviews`/`unflag`/`adminDelete`/`moderatedPosts`/`moderatedComments` do not exist.

- [ ] **Step 3: Add the new repository methods**

In `backend/src/main/java/com/skillswap/repository/ReviewRepository.java`, add:
```java
    List<Review> findByFlaggedTrue();
```

In `backend/src/main/java/com/skillswap/repository/ForumPostRepository.java`, add:
```java
    List<ForumPost> findByModeratedTrue();
```

In `backend/src/main/java/com/skillswap/repository/ForumCommentRepository.java`, add:
```java
    List<ForumComment> findByModeratedTrue();
```

- [ ] **Step 4: Modify ReviewService**

In `backend/src/main/java/com/skillswap/service/ReviewService.java`, add these three public methods (after `ratingSummary`):
```java
    public List<ReviewDto> flaggedReviews() {
        return reviewRepository.findByFlaggedTrue().stream().map(this::toDto).toList();
    }

    public void unflag(Long reviewId) {
        Review r = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        r.setFlagged(false);
        reviewRepository.save(r);
    }

    public void adminDelete(Long reviewId) {
        Review r = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        reviewRepository.delete(r);
    }
```
Add the import `java.util.List` if not already present in this file (check first).

- [ ] **Step 5: Modify ForumService**

In `backend/src/main/java/com/skillswap/service/ForumService.java`, add these two public methods (after `adminDeleteComment`):
```java
    public List<ForumPostDto> moderatedPosts() {
        return postRepository.findByModeratedTrue().stream().map(this::toDto).toList();
    }

    public List<ForumCommentDto> moderatedComments() {
        return commentRepository.findByModeratedTrue().stream().map(this::toDto).toList();
    }
```

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests ReviewServiceTest --tests ForumServiceTest`
Expected: PASS — all cases green (existing plus new).

- [ ] **Step 7: Write AdminReviewController**

`backend/src/main/java/com/skillswap/controller/AdminReviewController.java`:
```java
package com.skillswap.controller;

import com.skillswap.dto.ReviewDto;
import com.skillswap.service.CurrentUser;
import com.skillswap.service.ReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/reviews")
public class AdminReviewController {

    private final ReviewService reviewService;
    private final CurrentUser currentUser;

    public AdminReviewController(ReviewService reviewService, CurrentUser currentUser) {
        this.reviewService = reviewService;
        this.currentUser = currentUser;
    }

    @GetMapping("/flagged")
    public List<ReviewDto> flagged() {
        currentUser.requireAdmin();
        return reviewService.flaggedReviews();
    }

    @PutMapping("/{id}/unflag")
    public ResponseEntity<Void> unflag(@PathVariable Long id) {
        currentUser.requireAdmin();
        reviewService.unflag(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        currentUser.requireAdmin();
        reviewService.adminDelete(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 8: Add moderated-content list endpoints to AdminForumController**

In `backend/src/main/java/com/skillswap/controller/AdminForumController.java`, add the import `com.skillswap.dto.ForumPostDto`, `com.skillswap.dto.ForumCommentDto`, `java.util.List`. Add these two endpoints:
```java
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
```

- [ ] **Step 9: Write the admin moderation flow test**

`backend/src/test/java/com/skillswap/controller/AdminModerationFlowTest.java`:
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
class AdminModerationFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String register(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("fullName", email, "email", email, "password", "password1"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return login(email);
    }

    private String login(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("email", email, "password", "password1"));
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(res, "$.token");
    }

    private String promoteAndLogin(String email) throws Exception {
        register(email);
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", email);
        return login(email);
    }

    private Long insertFlaggedReview() {
        jdbc.update("INSERT INTO users(full_name,email,password_hash,role,active) VALUES (?,?,?,?,?)",
                "flag-reviewer", "flag-reviewer@example.com", "hash", "USER", true);
        jdbc.update("INSERT INTO users(full_name,email,password_hash,role,active) VALUES (?,?,?,?,?)",
                "flag-rated", "flag-rated@example.com", "hash", "USER", true);
        Long reviewerId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, "flag-reviewer@example.com");
        Long ratedId = jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, "flag-rated@example.com");
        jdbc.update("INSERT INTO matches(user_a_id,user_b_id,status) VALUES (?,?,?)", reviewerId, ratedId, "ACCEPTED");
        Long matchId = jdbc.queryForObject("SELECT id FROM matches WHERE user_a_id = ?", Long.class, reviewerId);
        jdbc.update("""
            INSERT INTO sessions(match_id, skill_id, teacher_user_id, learner_user_id, scheduled_by_user_id,
                                 session_date, start_time, end_time, mode, status)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """, matchId, 1L, ratedId, reviewerId, reviewerId,
                java.sql.Date.valueOf("2026-08-01"), java.sql.Time.valueOf("10:00:00"),
                java.sql.Time.valueOf("11:00:00"), "ONLINE", "COMPLETED");
        Long sessionId = jdbc.queryForObject("SELECT id FROM sessions WHERE match_id = ?", Long.class, matchId);
        jdbc.update("INSERT INTO reviews(session_id, reviewer_user_id, rated_user_id, rating, flagged) VALUES (?,?,?,?,?)",
                sessionId, reviewerId, ratedId, 1, true);
        return jdbc.queryForObject("SELECT id FROM reviews WHERE session_id = ?", Long.class, sessionId);
    }

    @Test
    void adminSeesAndResolvesFlaggedReview() throws Exception {
        String adminToken = promoteAndLogin("moderation-admin@example.com");
        Long reviewId = insertFlaggedReview();

        mvc.perform(get("/api/admin/reviews/flagged").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + reviewId + ")]").exists());

        mvc.perform(put("/api/admin/reviews/{id}/unflag", reviewId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/reviews/flagged").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + reviewId + ")]").doesNotExist());
    }

    @Test
    void adminSeesModeratedForumPosts() throws Exception {
        String userToken = register("mod-queue-user@example.com");
        String adminToken = promoteAndLogin("mod-queue-admin@example.com");

        jdbc.update("INSERT INTO forum_categories(category_name) VALUES (?)", "Queue Test " + System.identityHashCode(this));
        Long categoryId = jdbc.queryForObject(
                "SELECT id FROM forum_categories WHERE category_name = ?", Long.class, "Queue Test " + System.identityHashCode(this));

        String postBody = json.writeValueAsString(Map.of("title", "Hide me", "content", "Body"));
        String postRes = mvc.perform(post("/api/forum/categories/{id}/posts", categoryId)
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON).content(postBody))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long postId = ((Number) com.jayway.jsonpath.JsonPath.read(postRes, "$.id")).longValue();

        mvc.perform(put("/api/admin/forum/posts/{id}/moderate", postId).header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());

        mvc.perform(get("/api/admin/forum/posts/moderated").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + postId + ")]").exists());
    }
}
```

- [ ] **Step 10: Run the full suite**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test`
Expected: PASS — 137 tests at the end of Task 2, plus this task's additions to `ReviewServiceTest` (4), `ForumServiceTest` (2), and `AdminModerationFlowTest` (2) = **145 total**.

- [ ] **Step 11: Commit**

```bash
git add backend/src/main/java/com/skillswap/repository/ReviewRepository.java \
        backend/src/main/java/com/skillswap/repository/ForumPostRepository.java \
        backend/src/main/java/com/skillswap/repository/ForumCommentRepository.java \
        backend/src/main/java/com/skillswap/service/ReviewService.java \
        backend/src/main/java/com/skillswap/service/ForumService.java \
        backend/src/main/java/com/skillswap/controller/AdminReviewController.java \
        backend/src/main/java/com/skillswap/controller/AdminForumController.java \
        backend/src/test/java/com/skillswap/service/ReviewServiceTest.java \
        backend/src/test/java/com/skillswap/service/ForumServiceTest.java \
        backend/src/test/java/com/skillswap/controller/AdminModerationFlowTest.java
git commit -m "feat: add admin moderation queues for flagged reviews and moderated forum content"
```

---

### Task 4: Platform reports

**Files:**
- Create: `backend/src/main/java/com/skillswap/dto/DailyCountDto.java`
- Create: `backend/src/main/java/com/skillswap/dto/SkillPopularityDto.java`
- Create: `backend/src/main/java/com/skillswap/dto/SessionStatsDto.java`
- Create: `backend/src/main/java/com/skillswap/dto/TopMentorDto.java`
- Create: `backend/src/main/java/com/skillswap/dto/CategoryActivityDto.java`
- Create: `backend/src/main/java/com/skillswap/service/AdminReportService.java`
- Create: `backend/src/main/java/com/skillswap/controller/AdminReportController.java`
- Test: `backend/src/test/java/com/skillswap/service/AdminReportServiceTest.java`
- Test: `backend/src/test/java/com/skillswap/controller/AdminReportFlowTest.java`

**Interfaces:**
- Consumes: `UserRepository`, `UserSkillRepository`, `SkillRepository`, `SessionRepository`, `ReviewRepository`, `ForumPostRepository`, `ForumCategoryRepository` (all pre-existing, read-only `findAll()` usage — no new repository methods needed).
- Produces:
  - `record DailyCountDto(java.time.LocalDate date, long count)`.
  - `record SkillPopularityDto(Long skillId, String skillName, long count)`.
  - `record SessionStatsDto(long pending, long confirmed, long completed, long cancelled)`.
  - `record TopMentorDto(Long userId, String fullName, double avgRating, long reviewCount)`.
  - `record CategoryActivityDto(Long categoryId, String categoryName, long postCount)`.
  - `AdminReportService` — `usersOverTime()`, `popularSkills()` (top 10), `sessionStats()`, `topMentors()` (top 10, excludes flagged reviews), `activeCategories()` (excludes moderated posts).
  - `GET /api/admin/reports/users-over-time`, `/popular-skills`, `/session-stats`, `/top-mentors`, `/active-categories`.

- [ ] **Step 1: Write the failing service test**

`backend/src/test/java/com/skillswap/service/AdminReportServiceTest.java`:
```java
package com.skillswap.service;

import com.skillswap.dto.*;
import com.skillswap.entity.*;
import com.skillswap.repository.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AdminReportServiceTest {

    private final UserRepository userRepo = mock(UserRepository.class);
    private final UserSkillRepository userSkillRepo = mock(UserSkillRepository.class);
    private final SkillRepository skillRepo = mock(SkillRepository.class);
    private final SessionRepository sessionRepo = mock(SessionRepository.class);
    private final ReviewRepository reviewRepo = mock(ReviewRepository.class);
    private final ForumPostRepository postRepo = mock(ForumPostRepository.class);
    private final ForumCategoryRepository categoryRepo = mock(ForumCategoryRepository.class);
    private final AdminReportService service = new AdminReportService(
            userRepo, userSkillRepo, skillRepo, sessionRepo, reviewRepo, postRepo, categoryRepo);

    private User user(Long id, String name) {
        User u = new User();
        u.setFullName(name);
        try {
            var idField = User.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(u, id);
            var dateField = User.class.getDeclaredField("createdDate");
            dateField.setAccessible(true);
            dateField.set(u, LocalDateTime.of(2026, 7, 1, 12, 0));
        } catch (Exception e) { throw new RuntimeException(e); }
        return u;
    }

    @Test
    void usersOverTimeGroupsByDay() {
        when(userRepo.findAll()).thenReturn(List.of(user(1L, "A"), user(2L, "B")));
        List<DailyCountDto> result = service.usersOverTime();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).date()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(result.get(0).count()).isEqualTo(2);
    }

    @Test
    void popularSkillsRanksByUsageDescending() {
        UserSkill us1 = new UserSkill(); us1.setSkillId(4L);
        UserSkill us2 = new UserSkill(); us2.setSkillId(4L);
        UserSkill us3 = new UserSkill(); us3.setSkillId(7L);
        when(userSkillRepo.findAll()).thenReturn(List.of(us1, us2, us3));
        Skill python = new Skill(); python.setSkillName("Python"); python.setCategory("Technology");
        when(skillRepo.findAll()).thenReturn(List.of(python));

        List<SkillPopularityDto> result = service.popularSkills();

        assertThat(result.get(0).skillId()).isEqualTo(4L);
        assertThat(result.get(0).count()).isEqualTo(2);
    }

    @Test
    void sessionStatsCountsEachStatus() {
        Session pending = new Session(); pending.setStatus(SessionStatus.PENDING);
        Session confirmed = new Session(); confirmed.setStatus(SessionStatus.CONFIRMED);
        Session completed1 = new Session(); completed1.setStatus(SessionStatus.COMPLETED);
        Session completed2 = new Session(); completed2.setStatus(SessionStatus.COMPLETED);
        when(sessionRepo.findAll()).thenReturn(List.of(pending, confirmed, completed1, completed2));

        SessionStatsDto dto = service.sessionStats();

        assertThat(dto.pending()).isEqualTo(1);
        assertThat(dto.confirmed()).isEqualTo(1);
        assertThat(dto.completed()).isEqualTo(2);
        assertThat(dto.cancelled()).isZero();
    }

    @Test
    void topMentorsExcludesFlaggedReviewsAndRanksByAverage() {
        Review good = new Review(); good.setRatedUserId(1L); good.setRating(5); good.setFlagged(false);
        Review flagged = new Review(); flagged.setRatedUserId(1L); flagged.setRating(1); flagged.setFlagged(true);
        when(reviewRepo.findAll()).thenReturn(List.of(good, flagged));
        when(userRepo.findAll()).thenReturn(List.of(user(1L, "Mentor")));

        List<TopMentorDto> result = service.topMentors();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).avgRating()).isEqualTo(5.0);
        assertThat(result.get(0).reviewCount()).isEqualTo(1);
    }

    @Test
    void activeCategoriesExcludesModeratedPosts() {
        ForumPost visible = new ForumPost(); visible.setCategoryId(1L);
        ForumPost hidden = new ForumPost(); hidden.setCategoryId(1L); hidden.setModerated(true);
        when(postRepo.findAll()).thenReturn(List.of(visible, hidden));
        ForumCategory cat = new ForumCategory(); cat.setCategoryName("Music");
        when(categoryRepo.findAll()).thenReturn(List.of(cat));

        List<CategoryActivityDto> result = service.activeCategories();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).postCount()).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests AdminReportServiceTest`
Expected: FAIL — DTOs and `AdminReportService` do not exist. (No entity changes needed: `User.createdDate` has no public setter — the test's `user()` helper sets it via reflection, the same pattern already used elsewhere in this codebase for the private `id` field; `ForumCategory`/`ForumPost`/`Skill`/`UserSkill`/`Review`/`Session` all already have the public setters the test uses directly.)

- [ ] **Step 3: Write the DTOs**

`backend/src/main/java/com/skillswap/dto/DailyCountDto.java`:
```java
package com.skillswap.dto;

import java.time.LocalDate;

public record DailyCountDto(LocalDate date, long count) {}
```

`backend/src/main/java/com/skillswap/dto/SkillPopularityDto.java`:
```java
package com.skillswap.dto;

public record SkillPopularityDto(Long skillId, String skillName, long count) {}
```

`backend/src/main/java/com/skillswap/dto/SessionStatsDto.java`:
```java
package com.skillswap.dto;

public record SessionStatsDto(long pending, long confirmed, long completed, long cancelled) {}
```

`backend/src/main/java/com/skillswap/dto/TopMentorDto.java`:
```java
package com.skillswap.dto;

public record TopMentorDto(Long userId, String fullName, double avgRating, long reviewCount) {}
```

`backend/src/main/java/com/skillswap/dto/CategoryActivityDto.java`:
```java
package com.skillswap.dto;

public record CategoryActivityDto(Long categoryId, String categoryName, long postCount) {}
```

- [ ] **Step 4: Write AdminReportService**

`backend/src/main/java/com/skillswap/service/AdminReportService.java`:
```java
package com.skillswap.service;

import com.skillswap.dto.*;
import com.skillswap.entity.*;
import com.skillswap.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdminReportService {

    private final UserRepository userRepository;
    private final UserSkillRepository userSkillRepository;
    private final SkillRepository skillRepository;
    private final SessionRepository sessionRepository;
    private final ReviewRepository reviewRepository;
    private final ForumPostRepository forumPostRepository;
    private final ForumCategoryRepository forumCategoryRepository;

    public AdminReportService(UserRepository userRepository, UserSkillRepository userSkillRepository,
                              SkillRepository skillRepository, SessionRepository sessionRepository,
                              ReviewRepository reviewRepository, ForumPostRepository forumPostRepository,
                              ForumCategoryRepository forumCategoryRepository) {
        this.userRepository = userRepository;
        this.userSkillRepository = userSkillRepository;
        this.skillRepository = skillRepository;
        this.sessionRepository = sessionRepository;
        this.reviewRepository = reviewRepository;
        this.forumPostRepository = forumPostRepository;
        this.forumCategoryRepository = forumCategoryRepository;
    }

    public List<DailyCountDto> usersOverTime() {
        Map<LocalDate, Long> byDay = userRepository.findAll().stream()
                .collect(Collectors.groupingBy(u -> u.getCreatedDate().toLocalDate(), Collectors.counting()));
        return byDay.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new DailyCountDto(e.getKey(), e.getValue()))
                .toList();
    }

    public List<SkillPopularityDto> popularSkills() {
        Map<Long, Long> countBySkill = userSkillRepository.findAll().stream()
                .collect(Collectors.groupingBy(UserSkill::getSkillId, Collectors.counting()));
        Map<Long, Skill> skills = skillRepository.findAll().stream()
                .collect(Collectors.toMap(Skill::getId, s -> s));
        return countBySkill.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> new SkillPopularityDto(e.getKey(),
                        skills.containsKey(e.getKey()) ? skills.get(e.getKey()).getSkillName() : null,
                        e.getValue()))
                .toList();
    }

    public SessionStatsDto sessionStats() {
        List<Session> all = sessionRepository.findAll();
        long pending = all.stream().filter(s -> s.getStatus() == SessionStatus.PENDING).count();
        long confirmed = all.stream().filter(s -> s.getStatus() == SessionStatus.CONFIRMED).count();
        long completed = all.stream().filter(s -> s.getStatus() == SessionStatus.COMPLETED).count();
        long cancelled = all.stream().filter(s -> s.getStatus() == SessionStatus.CANCELLED).count();
        return new SessionStatsDto(pending, confirmed, completed, cancelled);
    }

    public List<TopMentorDto> topMentors() {
        Map<Long, User> users = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, List<Review>> byRated = reviewRepository.findAll().stream()
                .filter(r -> !r.isFlagged())
                .collect(Collectors.groupingBy(Review::getRatedUserId));
        return byRated.entrySet().stream()
                .map(e -> {
                    double avg = e.getValue().stream().mapToInt(Review::getRating).average().orElse(0.0);
                    User u = users.get(e.getKey());
                    return new TopMentorDto(e.getKey(), u != null ? u.getFullName() : null, avg, e.getValue().size());
                })
                .sorted(Comparator.comparingDouble(TopMentorDto::avgRating).reversed())
                .limit(10)
                .toList();
    }

    public List<CategoryActivityDto> activeCategories() {
        Map<Long, Long> countByCategory = forumPostRepository.findAll().stream()
                .filter(p -> !p.isModerated())
                .collect(Collectors.groupingBy(ForumPost::getCategoryId, Collectors.counting()));
        Map<Long, ForumCategory> categories = forumCategoryRepository.findAll().stream()
                .collect(Collectors.toMap(ForumCategory::getId, c -> c));
        return countByCategory.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .map(e -> new CategoryActivityDto(e.getKey(),
                        categories.containsKey(e.getKey()) ? categories.get(e.getKey()).getCategoryName() : null,
                        e.getValue()))
                .toList();
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests AdminReportServiceTest`
Expected: PASS — all five cases green.

- [ ] **Step 6: Write AdminReportController**

`backend/src/main/java/com/skillswap/controller/AdminReportController.java`:
```java
package com.skillswap.controller;

import com.skillswap.dto.*;
import com.skillswap.service.AdminReportService;
import com.skillswap.service.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    private final AdminReportService adminReportService;
    private final CurrentUser currentUser;

    public AdminReportController(AdminReportService adminReportService, CurrentUser currentUser) {
        this.adminReportService = adminReportService;
        this.currentUser = currentUser;
    }

    @GetMapping("/users-over-time")
    public List<DailyCountDto> usersOverTime() {
        currentUser.requireAdmin();
        return adminReportService.usersOverTime();
    }

    @GetMapping("/popular-skills")
    public List<SkillPopularityDto> popularSkills() {
        currentUser.requireAdmin();
        return adminReportService.popularSkills();
    }

    @GetMapping("/session-stats")
    public SessionStatsDto sessionStats() {
        currentUser.requireAdmin();
        return adminReportService.sessionStats();
    }

    @GetMapping("/top-mentors")
    public List<TopMentorDto> topMentors() {
        currentUser.requireAdmin();
        return adminReportService.topMentors();
    }

    @GetMapping("/active-categories")
    public List<CategoryActivityDto> activeCategories() {
        currentUser.requireAdmin();
        return adminReportService.activeCategories();
    }
}
```

- [ ] **Step 7: Write the admin report flow test**

`backend/src/test/java/com/skillswap/controller/AdminReportFlowTest.java`:
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
class AdminReportFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private String register(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("fullName", email, "email", email, "password", "password1"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        String loginBody = json.writeValueAsString(Map.of("email", email, "password", "password1"));
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(res, "$.token");
    }

    @Test
    void nonAdminCannotViewReports() throws Exception {
        String userToken = register("report-viewer@example.com");
        mvc.perform(get("/api/admin/reports/session-stats").header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminViewsAllFiveReports() throws Exception {
        String userToken = register("report-subject@example.com");
        register("report-admin@example.com");
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE email = ?", "report-admin@example.com");
        String loginBody = json.writeValueAsString(Map.of("email", "report-admin@example.com", "password", "password1"));
        String res = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String adminToken = com.jayway.jsonpath.JsonPath.read(res, "$.token");

        mvc.perform(get("/api/admin/reports/users-over-time").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/reports/popular-skills").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/reports/session-stats").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").isNumber());
        mvc.perform(get("/api/admin/reports/top-mentors").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/reports/active-categories").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }
}
```

- [ ] **Step 8: Run the full suite**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test`
Expected: PASS — 145 tests at the end of Task 3, plus `AdminReportServiceTest` (5) and `AdminReportFlowTest` (2) = **152 total**.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/skillswap/dto/DailyCountDto.java \
        backend/src/main/java/com/skillswap/dto/SkillPopularityDto.java \
        backend/src/main/java/com/skillswap/dto/SessionStatsDto.java \
        backend/src/main/java/com/skillswap/dto/TopMentorDto.java \
        backend/src/main/java/com/skillswap/dto/CategoryActivityDto.java \
        backend/src/main/java/com/skillswap/service/AdminReportService.java \
        backend/src/main/java/com/skillswap/controller/AdminReportController.java \
        backend/src/test/java/com/skillswap/service/AdminReportServiceTest.java \
        backend/src/test/java/com/skillswap/controller/AdminReportFlowTest.java
git commit -m "feat: add admin platform reports (users, skills, sessions, mentors, categories)"
```

---

## Self-Review

**Spec coverage (Plan 6 slice, §4.9 Admin Panel):**
- View users with search/filter → Task 1. ✅
- Activate/deactivate accounts → Task 1. ✅
- Manage master skill list and categories (add/edit/delete) → Task 2. ✅
- Admin can mark profiles "Verified" (§4.6's deferred item) → Task 2. ✅
- Monitor/moderate forum content + resolve flagged reviews → Task 3 (queues; the act-endpoints themselves were already built in Plans 4–5). ✅
- Platform statistics / reports (users over time, popular skills, session completion rates, top-rated mentors, active forum categories) → Task 4. ✅
- Explicitly NOT built: "handle user complaints and dispute resolution" — no complaint/dispute entity exists anywhere in the spec's data model; this was always vague/unscoped, consistent with CLAUDE.md's guardrail against building undefined features. CSV export — spec calls it optional; cut (YAGNI).

**Placeholder scan:** No TBD/TODO; every step has complete code (a redundant `jdbc.update` line in an early `AdminUserFlowTest` draft was caught and removed during this self-review, not left for the implementer to untangle).

**Type consistency:** `SkillService`'s constructor gains a third parameter (`SessionRepository`) in Task 2 — every test in `SkillServiceTest` after Task 2 must construct `service` with all three args; Step 1 of Task 2 explicitly shows the updated field/constructor. `ForumService`/`ReviewService`/`BadgeService` constructors are UNCHANGED across Tasks 2–3 (only new methods added), so no existing test in those files needs constructor updates — verified against each file's actual current constructor before writing this plan. `AdminUserController`'s constructor gains a `BadgeService` parameter in Task 2 — `AdminUserFlowTest` (written in Task 1, before this change) never constructs `AdminUserController` directly (it's a `@SpringBootTest`, Spring wires it), so no test breakage there. `currentUser.requireAdmin()` signature (`Plan 5`) used identically across all four tasks' controllers.

**Scope check:** Four tasks — one more than the typical three, because Admin Panel's spec section is unusually dense (7 distinct bullets) — each task has a single cohesive theme (users / catalog / moderation / reports) and its own testable deliverable, consistent with "Task Right-Sizing." Cumulative test count 117→152.

**Deliberate simplifications (flagged for the record):**
- Reports use in-memory `Stream`/`Collectors.groupingBy` aggregation over `findAll()` rather than SQL-level `GROUP BY`/date functions — deliberate, to sidestep H2-vs-MySQL date-function dialect portability entirely (Plan 5 hit two such dialect issues in a single task). Fine at this project's data volumes; `ponytail: naive O(n) in-memory aggregation, move to SQL GROUP BY if the user/session table ever grows past what fits comfortably in memory.`
- Skill/category delete-blocked-when-in-use checks two tables each (UserSkill+Session for skills; ForumPost for categories) rather than adding `ON DELETE CASCADE` — deliberate: cascading a skill or category delete would silently destroy historical session/review/badge/post context, which is never the right default for master-data deletion (unlike the forum post→comment/upvote cascade, which Plan 5 correctly cascades, since a comment/upvote has no meaning without its parent post).
- `topMentors`/`activeCategories`/`popularSkills` all cap at top 10 with no pagination — consistent with this codebase's existing no-pagination convention throughout (matches, sessions, notifications all just return full lists).
