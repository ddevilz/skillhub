# SkillSwap Hub — Plan 2: Skills + Matching + Redis

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Users manage the skills they teach/learn, get SQL-based match suggestions with a compatibility score, send/accept match requests, and read-heavy endpoints are cached in Redis (degrading gracefully when Redis is absent).

**Architecture:** Extends the Plan-1 Spring Boot monolith. New entities `Skill`, `UserSkill`, `Match` (table `matches`) added via Flyway. Matching is a native SQL query (candidate teaches what the current user wants to learn), scored in the service. Redis backs Spring Cache via `@Cacheable` on the skill catalog and match suggestions, with a `CacheErrorHandler` that swallows Redis errors so the app falls back to the database.

**Tech Stack:** Java 17, Spring Boot 3.2.5, Gradle, Spring Data JPA, Flyway, MySQL 8 (H2 for tests), Spring Data Redis + Spring Cache (ConcurrentMap for tests). React frontend is out of scope for this plan (backend only).

## Global Constraints

- Base package `com.skillswap`. Java **17**. Spring Boot **3.2.5**. Build tool **Gradle** (never Maven).
- **Build with JDK 17:** the machine default `java` is JDK 26, which Gradle 8.7 does not support. Prefix every gradle command with `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`.
- Schema is owned by **Flyway** (`backend/src/main/resources/db/migration/V*.sql`), append-only. `V1__init.sql` already exists — never edit it. This plan adds `V2__skills_matching.sql` and `V3__seed_skills.sql`.
- **`match` is a reserved word in MySQL** — the table is named **`matches`**, the entity is `Match` with `@Table(name = "matches")`.
- **Redis is optional at runtime:** the app must start and function with Redis down. Cache errors are swallowed by a `CacheErrorHandler` (fall back to DB). Tests use `spring.cache.type: simple` (in-memory, no Redis).
- Thin controllers; DTOs at the HTTP boundary (never serialize JPA entities). Business errors are raised as `org.springframework.web.server.ResponseStatusException` and rendered by `GlobalExceptionHandler` as `{ "error": <int>, "message": <text> }`.
- Test profile is H2 (Flyway disabled, Hibernate `create-drop`); native queries must run on H2 in `MODE=MySQL`.
- Git author is **Devashish Jadhav <jadhavom24@gmail.com>**. **Never** add a `Co-Authored-By` line or any AI attribution. Conventional Commit messages. Commit at the end of every task.

**Interfaces already available from Plan 1:** `User` entity (`id, fullName, email, city, role, active, ...`), `UserRepository.findByEmail/existsByEmail`, JWT security (authenticated principal's username is the email; `/api/**` requires a valid token except `/api/auth/**`), `GlobalExceptionHandler` (`@RestControllerAdvice` with a shared `body(HttpStatus, String)` helper and handlers for validation→400, `EmailAlreadyUsedException`→409, `BadCredentialsException`→401, `Exception`→500).

---

### Task 1: Skills & Matching schema + entities + repositories

**Files:**
- Create: `backend/src/main/resources/db/migration/V2__skills_matching.sql`
- Create: `backend/src/main/resources/db/migration/V3__seed_skills.sql`
- Create: `backend/src/main/java/com/skillswap/entity/Skill.java`
- Create: `backend/src/main/java/com/skillswap/entity/SkillType.java`
- Create: `backend/src/main/java/com/skillswap/entity/UserSkill.java`
- Create: `backend/src/main/java/com/skillswap/entity/MatchStatus.java`
- Create: `backend/src/main/java/com/skillswap/entity/Match.java`
- Create: `backend/src/main/java/com/skillswap/repository/SkillRepository.java`
- Create: `backend/src/main/java/com/skillswap/repository/UserSkillRepository.java`
- Create: `backend/src/main/java/com/skillswap/repository/MatchRepository.java`
- Test: `backend/src/test/java/com/skillswap/repository/UserSkillRepositoryTest.java`

**Interfaces:**
- Consumes: `users` table (Plan 1).
- Produces:
  - `Skill` (`id, skillName, category, description`), `SkillType {CAN_TEACH, WANT_TO_LEARN}`, `UserSkill` (`id, userId, skillId, skillType, experience, proficiency`), `MatchStatus {PENDING, ACCEPTED, REJECTED}`, `Match` (`id, userAId, userBId, status, createdDate`).
  - `SkillRepository extends JpaRepository<Skill,Long>` + `List<String> findDistinctCategories()`.
  - `UserSkillRepository extends JpaRepository<UserSkill,Long>` + `findByUserId`, `findByUserIdAndSkillType`, `countByUserIdAndSkillType`, `findByIdAndUserId`, `existsByUserIdAndSkillIdAndSkillType`, and the native `findSuggestions(...)` returning `MatchProjection`.
  - `MatchRepository extends JpaRepository<Match,Long>` + `existsByUserAIdAndUserBIdAndStatus`, `findByUserAIdOrUserBId`, `findByIdAndUserBId`.
  - `MatchProjection` interface (`getUserId, getFullName, getCity, getMatchedSkills`).

- [ ] **Step 1: Write the Flyway migrations**

`backend/src/main/resources/db/migration/V2__skills_matching.sql`:
```sql
CREATE TABLE skill (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    skill_name  VARCHAR(100) NOT NULL,
    category    VARCHAR(50)  NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE user_skill (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    skill_id    BIGINT NOT NULL,
    skill_type  VARCHAR(20) NOT NULL,
    experience  VARCHAR(50),
    proficiency VARCHAR(20),
    CONSTRAINT fk_us_user  FOREIGN KEY (user_id)  REFERENCES users(id),
    CONSTRAINT fk_us_skill FOREIGN KEY (skill_id) REFERENCES skill(id),
    CONSTRAINT uq_user_skill UNIQUE (user_id, skill_id, skill_type)
);
CREATE INDEX idx_user_skill_user  ON user_skill(user_id);
CREATE INDEX idx_user_skill_skill ON user_skill(skill_id);

CREATE TABLE matches (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_a_id    BIGINT NOT NULL,
    user_b_id    BIGINT NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_match_a FOREIGN KEY (user_a_id) REFERENCES users(id),
    CONSTRAINT fk_match_b FOREIGN KEY (user_b_id) REFERENCES users(id)
);
CREATE INDEX idx_match_user_b ON matches(user_b_id);
```

`backend/src/main/resources/db/migration/V3__seed_skills.sql`:
```sql
INSERT INTO skill (skill_name, category, description) VALUES
 ('Guitar',          'Music',      'Acoustic and electric guitar'),
 ('Piano',           'Music',      'Keyboard fundamentals'),
 ('Web Development',  'Technology', 'HTML, CSS, JavaScript'),
 ('Python',          'Technology', 'Python programming'),
 ('Spoken English',  'Languages',  'Conversational English'),
 ('Spanish',         'Languages',  'Beginner Spanish'),
 ('Sketching',       'Arts',       'Pencil sketching'),
 ('Public Speaking', 'Business',   'Presentation skills');
```

- [ ] **Step 2: Write the failing repository test**

`backend/src/test/java/com/skillswap/repository/UserSkillRepositoryTest.java`:
```java
package com.skillswap.repository;

import com.skillswap.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserSkillRepositoryTest {

    @Autowired UserSkillRepository userSkillRepository;
    @Autowired SkillRepository skillRepository;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Long insertUser(String email, boolean active) {
        jdbc.update("INSERT INTO users(full_name,email,password_hash,role,active) VALUES (?,?,?,?,?)",
                email, email, "hash", "USER", active);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    @Test
    void findsSkillsByUserAndType() {
        Long uid = insertUser("teacher@example.com", true);
        Skill s = new Skill(); s.setSkillName("Guitar"); s.setCategory("Music");
        Long sid = skillRepository.save(s).getId();

        UserSkill us = new UserSkill();
        us.setUserId(uid); us.setSkillId(sid); us.setSkillType(SkillType.CAN_TEACH);
        userSkillRepository.save(us);

        assertThat(userSkillRepository.findByUserId(uid)).hasSize(1);
        assertThat(userSkillRepository.findByUserIdAndSkillType(uid, SkillType.CAN_TEACH)).hasSize(1);
        assertThat(userSkillRepository.countByUserIdAndSkillType(uid, SkillType.WANT_TO_LEARN)).isZero();
        assertThat(userSkillRepository.existsByUserIdAndSkillIdAndSkillType(uid, sid, SkillType.CAN_TEACH)).isTrue();
    }

    @Test
    void findsSuggestionsWhenCandidateTeachesWhatUserWants() {
        Long learner = insertUser("learner@example.com", true);
        Long teacher = insertUser("teacher2@example.com", true);
        Skill s = new Skill(); s.setSkillName("Python"); s.setCategory("Technology");
        Long sid = skillRepository.save(s).getId();

        UserSkill wants = new UserSkill();
        wants.setUserId(learner); wants.setSkillId(sid); wants.setSkillType(SkillType.WANT_TO_LEARN);
        userSkillRepository.save(wants);
        UserSkill teaches = new UserSkill();
        teaches.setUserId(teacher); teaches.setSkillId(sid); teaches.setSkillType(SkillType.CAN_TEACH);
        userSkillRepository.save(teaches);

        List<MatchProjection> hits = userSkillRepository.findSuggestions(learner, "", "");
        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getUserId()).isEqualTo(teacher);
        assertThat(hits.get(0).getMatchedSkills()).isEqualTo(1L);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests UserSkillRepositoryTest`
Expected: FAIL — entities/repositories/`MatchProjection` do not exist (compilation error).

- [ ] **Step 4: Write the enums and entities**

`backend/src/main/java/com/skillswap/entity/SkillType.java`:
```java
package com.skillswap.entity;

public enum SkillType { CAN_TEACH, WANT_TO_LEARN }
```

`backend/src/main/java/com/skillswap/entity/MatchStatus.java`:
```java
package com.skillswap.entity;

public enum MatchStatus { PENDING, ACCEPTED, REJECTED }
```

`backend/src/main/java/com/skillswap/entity/Skill.java`:
```java
package com.skillswap.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "skill")
public class Skill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String skillName;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(length = 255)
    private String description;

    public Long getId() { return id; }
    public String getSkillName() { return skillName; }
    public void setSkillName(String v) { this.skillName = v; }
    public String getCategory() { return category; }
    public void setCategory(String v) { this.category = v; }
    public String getDescription() { return description; }
    public void setDescription(String v) { this.description = v; }
}
```

`backend/src/main/java/com/skillswap/entity/UserSkill.java`:
```java
package com.skillswap.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_skill")
public class UserSkill {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long skillId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SkillType skillType;

    @Column(length = 50)
    private String experience;

    @Column(length = 20)
    private String proficiency;

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public Long getSkillId() { return skillId; }
    public void setSkillId(Long v) { this.skillId = v; }
    public SkillType getSkillType() { return skillType; }
    public void setSkillType(SkillType v) { this.skillType = v; }
    public String getExperience() { return experience; }
    public void setExperience(String v) { this.experience = v; }
    public String getProficiency() { return proficiency; }
    public void setProficiency(String v) { this.proficiency = v; }
}
```

`backend/src/main/java/com/skillswap/entity/Match.java`:
```java
package com.skillswap.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "matches")
public class Match {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userAId;

    @Column(nullable = false)
    private Long userBId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MatchStatus status = MatchStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    void onCreate() { if (createdDate == null) createdDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserAId() { return userAId; }
    public void setUserAId(Long v) { this.userAId = v; }
    public Long getUserBId() { return userBId; }
    public void setUserBId(Long v) { this.userBId = v; }
    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus v) { this.status = v; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
```

- [ ] **Step 5: Write the projection and repositories**

`backend/src/main/java/com/skillswap/repository/MatchProjection.java`:
```java
package com.skillswap.repository;

public interface MatchProjection {
    Long getUserId();
    String getFullName();
    String getCity();
    long getMatchedSkills();
}
```

`backend/src/main/java/com/skillswap/repository/SkillRepository.java`:
```java
package com.skillswap.repository;

import com.skillswap.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface SkillRepository extends JpaRepository<Skill, Long> {
    @Query("SELECT DISTINCT s.category FROM Skill s ORDER BY s.category")
    List<String> findDistinctCategories();
}
```

`backend/src/main/java/com/skillswap/repository/UserSkillRepository.java`:
```java
package com.skillswap.repository;

import com.skillswap.entity.SkillType;
import com.skillswap.entity.UserSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserSkillRepository extends JpaRepository<UserSkill, Long> {

    List<UserSkill> findByUserId(Long userId);
    List<UserSkill> findByUserIdAndSkillType(Long userId, SkillType skillType);
    long countByUserIdAndSkillType(Long userId, SkillType skillType);
    Optional<UserSkill> findByIdAndUserId(Long id, Long userId);
    boolean existsByUserIdAndSkillIdAndSkillType(Long userId, Long skillId, SkillType skillType);

    // Candidates who CAN_TEACH a skill the current user WANT_TO_LEARN.
    // Empty-string sentinels are used instead of NULL so the bind params stay typed on H2.
    @Query(value = """
        SELECT v.id AS userId, v.full_name AS fullName, v.city AS city,
               COUNT(DISTINCT vt.skill_id) AS matchedSkills
        FROM users v
        JOIN user_skill vt ON vt.user_id = v.id AND vt.skill_type = 'CAN_TEACH'
        JOIN user_skill ul ON ul.skill_id = vt.skill_id
             AND ul.user_id = :userId AND ul.skill_type = 'WANT_TO_LEARN'
        JOIN skill s ON s.id = vt.skill_id
        WHERE v.id <> :userId AND v.active = TRUE
          AND (:city = '' OR v.city = :city)
          AND (:category = '' OR s.category = :category)
        GROUP BY v.id, v.full_name, v.city
        ORDER BY matchedSkills DESC
        """, nativeQuery = true)
    List<MatchProjection> findSuggestions(@Param("userId") Long userId,
                                          @Param("city") String city,
                                          @Param("category") String category);
}
```

`backend/src/main/java/com/skillswap/repository/MatchRepository.java`:
```java
package com.skillswap.repository;

import com.skillswap.entity.Match;
import com.skillswap.entity.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {
    boolean existsByUserAIdAndUserBIdAndStatus(Long userAId, Long userBId, MatchStatus status);
    List<Match> findByUserAIdOrUserBId(Long userAId, Long userBId);
    Optional<Match> findByIdAndUserBId(Long id, Long userBId);
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests UserSkillRepositoryTest`
Expected: PASS — both cases green (the native suggestion query runs on H2/MySQL mode).

- [ ] **Step 7: Run the full suite**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test`
Expected: PASS — Plan-1 tests (16) plus the new repository test.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V2__skills_matching.sql \
        backend/src/main/resources/db/migration/V3__seed_skills.sql \
        backend/src/main/java/com/skillswap/entity/Skill.java \
        backend/src/main/java/com/skillswap/entity/SkillType.java \
        backend/src/main/java/com/skillswap/entity/UserSkill.java \
        backend/src/main/java/com/skillswap/entity/MatchStatus.java \
        backend/src/main/java/com/skillswap/entity/Match.java \
        backend/src/main/java/com/skillswap/repository/MatchProjection.java \
        backend/src/main/java/com/skillswap/repository/SkillRepository.java \
        backend/src/main/java/com/skillswap/repository/UserSkillRepository.java \
        backend/src/main/java/com/skillswap/repository/MatchRepository.java \
        backend/src/test/java/com/skillswap/repository/UserSkillRepositoryTest.java
git commit -m "feat: add skill, user_skill, matches schema with entities and repositories"
```

---

### Task 2: Skill catalog + user-skill CRUD

**Files:**
- Create: `backend/src/main/java/com/skillswap/dto/SkillDto.java`
- Create: `backend/src/main/java/com/skillswap/dto/UserSkillDto.java`
- Create: `backend/src/main/java/com/skillswap/dto/AddUserSkillRequest.java`
- Create: `backend/src/main/java/com/skillswap/service/SkillService.java`
- Create: `backend/src/main/java/com/skillswap/service/CurrentUser.java`
- Create: `backend/src/main/java/com/skillswap/controller/SkillController.java`
- Create: `backend/src/main/java/com/skillswap/controller/MeSkillController.java`
- Test: `backend/src/test/java/com/skillswap/service/SkillServiceTest.java`

**Interfaces:**
- Consumes: `SkillRepository`, `UserSkillRepository`, `UserRepository` (Plan 1).
- Produces:
  - `record SkillDto(Long id, String skillName, String category, String description)`.
  - `record UserSkillDto(Long id, Long skillId, String skillName, String category, String skillType, String experience, String proficiency)`.
  - `record AddUserSkillRequest(Long skillId, String skillType, String experience, String proficiency)` (validated).
  - `CurrentUser` helper — resolves the authenticated `User` from the security context (by email). Method: `User require()`.
  - `SkillService` — `List<SkillDto> catalog()`, `List<String> categories()`, `List<UserSkillDto> mySkills(Long userId)`, `UserSkillDto add(Long userId, AddUserSkillRequest req)`, `void remove(Long userId, Long userSkillId)`.
  - `GET /api/skills`, `GET /api/categories`, `GET /api/me/skills`, `POST /api/me/skills` (201), `DELETE /api/me/skills/{id}` (204).

- [ ] **Step 1: Write the failing service test**

`backend/src/test/java/com/skillswap/service/SkillServiceTest.java`:
```java
package com.skillswap.service;

import com.skillswap.dto.AddUserSkillRequest;
import com.skillswap.dto.UserSkillDto;
import com.skillswap.entity.Skill;
import com.skillswap.entity.SkillType;
import com.skillswap.entity.UserSkill;
import com.skillswap.repository.SkillRepository;
import com.skillswap.repository.UserSkillRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SkillServiceTest {

    private final SkillRepository skillRepo = mock(SkillRepository.class);
    private final UserSkillRepository userSkillRepo = mock(UserSkillRepository.class);
    private final SkillService service = new SkillService(skillRepo, userSkillRepo);

    @Test
    void addRejectsUnknownSkill() {
        when(skillRepo.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.add(1L, new AddUserSkillRequest(99L, "CAN_TEACH", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Skill not found");
    }

    @Test
    void addRejectsDuplicate() {
        Skill s = new Skill(); s.setSkillName("Guitar"); s.setCategory("Music");
        when(skillRepo.findById(1L)).thenReturn(Optional.of(s));
        when(userSkillRepo.existsByUserIdAndSkillIdAndSkillType(1L, 1L, SkillType.CAN_TEACH)).thenReturn(true);
        assertThatThrownBy(() -> service.add(1L, new AddUserSkillRequest(1L, "CAN_TEACH", null, null)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already");
    }

    @Test
    void addPersistsAndReturnsDto() {
        Skill s = new Skill(); s.setSkillName("Guitar"); s.setCategory("Music");
        when(skillRepo.findById(1L)).thenReturn(Optional.of(s));
        when(userSkillRepo.existsByUserIdAndSkillIdAndSkillType(1L, 1L, SkillType.CAN_TEACH)).thenReturn(false);
        when(userSkillRepo.save(any(UserSkill.class))).thenAnswer(i -> i.getArgument(0));

        UserSkillDto dto = service.add(1L, new AddUserSkillRequest(1L, "CAN_TEACH", "2 years", "Advanced"));

        assertThat(dto.skillName()).isEqualTo("Guitar");
        assertThat(dto.skillType()).isEqualTo("CAN_TEACH");
        verify(userSkillRepo).save(any(UserSkill.class));
    }

    @Test
    void addRejectsInvalidSkillType() {
        Skill s = new Skill(); s.setSkillName("Guitar"); s.setCategory("Music");
        when(skillRepo.findById(1L)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.add(1L, new AddUserSkillRequest(1L, "BOGUS", null, null)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void removeRejectsForeignUserSkill() {
        when(userSkillRepo.findByIdAndUserId(5L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.remove(1L, 5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests SkillServiceTest`
Expected: FAIL — `SkillService`, DTOs do not exist.

- [ ] **Step 3: Write the DTOs**

`backend/src/main/java/com/skillswap/dto/SkillDto.java`:
```java
package com.skillswap.dto;

public record SkillDto(Long id, String skillName, String category, String description) {}
```

`backend/src/main/java/com/skillswap/dto/UserSkillDto.java`:
```java
package com.skillswap.dto;

public record UserSkillDto(Long id, Long skillId, String skillName, String category,
                           String skillType, String experience, String proficiency) {}
```

`backend/src/main/java/com/skillswap/dto/AddUserSkillRequest.java`:
```java
package com.skillswap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddUserSkillRequest(
        @NotNull Long skillId,
        @NotBlank String skillType,
        String experience,
        String proficiency) {}
```

- [ ] **Step 4: Write the SkillService**

`backend/src/main/java/com/skillswap/service/SkillService.java`:
```java
package com.skillswap.service;

import com.skillswap.dto.AddUserSkillRequest;
import com.skillswap.dto.SkillDto;
import com.skillswap.dto.UserSkillDto;
import com.skillswap.entity.Skill;
import com.skillswap.entity.SkillType;
import com.skillswap.entity.UserSkill;
import com.skillswap.repository.SkillRepository;
import com.skillswap.repository.UserSkillRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SkillService {

    private final SkillRepository skillRepository;
    private final UserSkillRepository userSkillRepository;

    public SkillService(SkillRepository skillRepository, UserSkillRepository userSkillRepository) {
        this.skillRepository = skillRepository;
        this.userSkillRepository = userSkillRepository;
    }

    public List<SkillDto> catalog() {
        return skillRepository.findAll().stream()
                .map(s -> new SkillDto(s.getId(), s.getSkillName(), s.getCategory(), s.getDescription()))
                .toList();
    }

    public List<String> categories() {
        return skillRepository.findDistinctCategories();
    }

    public List<UserSkillDto> mySkills(Long userId) {
        Map<Long, Skill> skills = skillRepository.findAll().stream()
                .collect(Collectors.toMap(Skill::getId, s -> s));
        return userSkillRepository.findByUserId(userId).stream()
                .map(us -> toDto(us, skills.get(us.getSkillId())))
                .toList();
    }

    public UserSkillDto add(Long userId, AddUserSkillRequest req) {
        SkillType type = parseType(req.skillType());
        Skill skill = skillRepository.findById(req.skillId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill not found"));
        if (userSkillRepository.existsByUserIdAndSkillIdAndSkillType(userId, req.skillId(), type)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Skill already added for this type");
        }
        UserSkill us = new UserSkill();
        us.setUserId(userId);
        us.setSkillId(req.skillId());
        us.setSkillType(type);
        us.setExperience(req.experience());
        us.setProficiency(req.proficiency());
        return toDto(userSkillRepository.save(us), skill);
    }

    public void remove(Long userId, Long userSkillId) {
        UserSkill us = userSkillRepository.findByIdAndUserId(userSkillId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Skill entry not found"));
        userSkillRepository.delete(us);
    }

    private SkillType parseType(String raw) {
        try {
            return SkillType.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "skillType must be CAN_TEACH or WANT_TO_LEARN");
        }
    }

    private UserSkillDto toDto(UserSkill us, Skill skill) {
        String name = skill != null ? skill.getSkillName() : null;
        String category = skill != null ? skill.getCategory() : null;
        return new UserSkillDto(us.getId(), us.getSkillId(), name, category,
                us.getSkillType().name(), us.getExperience(), us.getProficiency());
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests SkillServiceTest`
Expected: PASS — all five cases green.

- [ ] **Step 6: Write CurrentUser and the controllers**

`backend/src/main/java/com/skillswap/service/CurrentUser.java`:
```java
package com.skillswap.service;

import com.skillswap.entity.User;
import com.skillswap.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CurrentUser {

    private final UserRepository userRepository;

    public CurrentUser(UserRepository userRepository) { this.userRepository = userRepository; }

    /** The authenticated user (principal username is the email). */
    public User require() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated"));
    }
}
```

`backend/src/main/java/com/skillswap/controller/SkillController.java`:
```java
package com.skillswap.controller;

import com.skillswap.dto.SkillDto;
import com.skillswap.service.SkillService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) { this.skillService = skillService; }

    @GetMapping("/skills")
    public List<SkillDto> skills() { return skillService.catalog(); }

    @GetMapping("/categories")
    public List<String> categories() { return skillService.categories(); }
}
```

`backend/src/main/java/com/skillswap/controller/MeSkillController.java`:
```java
package com.skillswap.controller;

import com.skillswap.dto.AddUserSkillRequest;
import com.skillswap.dto.UserSkillDto;
import com.skillswap.service.CurrentUser;
import com.skillswap.service.SkillService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/me/skills")
public class MeSkillController {

    private final SkillService skillService;
    private final CurrentUser currentUser;

    public MeSkillController(SkillService skillService, CurrentUser currentUser) {
        this.skillService = skillService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public List<UserSkillDto> mySkills() {
        return skillService.mySkills(currentUser.require().getId());
    }

    @PostMapping
    public ResponseEntity<UserSkillDto> add(@Valid @RequestBody AddUserSkillRequest req) {
        UserSkillDto dto = skillService.add(currentUser.require().getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long id) {
        skillService.remove(currentUser.require().getId(), id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 7: Add the `ResponseStatusException` handler to GlobalExceptionHandler**

Modify `backend/src/main/java/com/skillswap/config/GlobalExceptionHandler.java` — add this handler (and the import `org.springframework.web.server.ResponseStatusException`) alongside the existing handlers, keeping the existing ones unchanged:
```java
@org.springframework.web.bind.annotation.ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
public ResponseEntity<Map<String, Object>> onResponseStatus(org.springframework.web.server.ResponseStatusException ex) {
    return body(HttpStatus.valueOf(ex.getStatusCode().value()), ex.getReason());
}
```

- [ ] **Step 8: Run the full suite**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test`
Expected: PASS — Plan-1 tests + Task-1 test + `SkillServiceTest`.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/skillswap/dto/SkillDto.java \
        backend/src/main/java/com/skillswap/dto/UserSkillDto.java \
        backend/src/main/java/com/skillswap/dto/AddUserSkillRequest.java \
        backend/src/main/java/com/skillswap/service/SkillService.java \
        backend/src/main/java/com/skillswap/service/CurrentUser.java \
        backend/src/main/java/com/skillswap/controller/SkillController.java \
        backend/src/main/java/com/skillswap/controller/MeSkillController.java \
        backend/src/main/java/com/skillswap/config/GlobalExceptionHandler.java \
        backend/src/test/java/com/skillswap/service/SkillServiceTest.java
git commit -m "feat: add skill catalog and user-skill management endpoints"
```

---

### Task 3: Matching suggestions + match requests

**Files:**
- Create: `backend/src/main/java/com/skillswap/dto/MatchSuggestionDto.java`
- Create: `backend/src/main/java/com/skillswap/dto/MatchDto.java`
- Create: `backend/src/main/java/com/skillswap/dto/CreateMatchRequest.java`
- Create: `backend/src/main/java/com/skillswap/dto/UpdateMatchRequest.java`
- Create: `backend/src/main/java/com/skillswap/service/MatchService.java`
- Create: `backend/src/main/java/com/skillswap/controller/MatchController.java`
- Test: `backend/src/test/java/com/skillswap/service/MatchServiceTest.java`
- Test: `backend/src/test/java/com/skillswap/controller/MatchFlowTest.java`

**Interfaces:**
- Consumes: `UserSkillRepository` (`findSuggestions`, `countByUserIdAndSkillType`), `MatchRepository`, `UserRepository`, `CurrentUser`.
- Produces:
  - `record MatchSuggestionDto(Long userId, String fullName, String city, long matchedSkills, int compatibilityScore)`.
  - `record MatchDto(Long id, Long userAId, Long userBId, String status, java.time.LocalDateTime createdDate)`.
  - `record CreateMatchRequest(Long targetUserId)` and `record UpdateMatchRequest(String status)` (validated).
  - `MatchService` — `List<MatchSuggestionDto> suggestions(Long userId, String city, String category)`, `MatchDto request(Long meId, Long targetId)`, `MatchDto respond(Long meId, Long matchId, String status)`, `List<MatchDto> myMatches(Long meId)`.
  - `GET /api/matches/suggestions?city=&category=`, `POST /api/matches/request` (201), `PUT /api/matches/{id}` (200), `GET /api/matches`.

- [ ] **Step 1: Write the failing service test**

`backend/src/test/java/com/skillswap/service/MatchServiceTest.java`:
```java
package com.skillswap.service;

import com.skillswap.dto.MatchDto;
import com.skillswap.dto.MatchSuggestionDto;
import com.skillswap.entity.*;
import com.skillswap.repository.MatchProjection;
import com.skillswap.repository.MatchRepository;
import com.skillswap.repository.UserSkillRepository;
import com.skillswap.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MatchServiceTest {

    private final UserSkillRepository userSkillRepo = mock(UserSkillRepository.class);
    private final MatchRepository matchRepo = mock(MatchRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final MatchService service = new MatchService(userSkillRepo, matchRepo, userRepo);

    private MatchProjection projection(Long userId, long matched) {
        return new MatchProjection() {
            public Long getUserId() { return userId; }
            public String getFullName() { return "Teacher"; }
            public String getCity() { return "Pune"; }
            public long getMatchedSkills() { return matched; }
        };
    }

    @Test
    void suggestionsComputeCompatibilityScore() {
        when(userSkillRepo.findSuggestions(1L, "", "")).thenReturn(List.of(projection(2L, 2L)));
        when(userSkillRepo.countByUserIdAndSkillType(1L, SkillType.WANT_TO_LEARN)).thenReturn(4L);

        List<MatchSuggestionDto> out = service.suggestions(1L, null, null);

        assertThat(out).hasSize(1);
        assertThat(out.get(0).matchedSkills()).isEqualTo(2L);
        assertThat(out.get(0).compatibilityScore()).isEqualTo(50); // 2 of 4 wanted = 50%
    }

    @Test
    void suggestionsScoreZeroWhenNoLearnSkills() {
        when(userSkillRepo.findSuggestions(1L, "", "")).thenReturn(List.of(projection(2L, 1L)));
        when(userSkillRepo.countByUserIdAndSkillType(1L, SkillType.WANT_TO_LEARN)).thenReturn(0L);

        List<MatchSuggestionDto> out = service.suggestions(1L, null, null);
        assertThat(out.get(0).compatibilityScore()).isZero(); // guard against divide-by-zero
    }

    @Test
    void requestRejectsSelfMatch() {
        assertThatThrownBy(() -> service.request(1L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("yourself");
    }

    @Test
    void requestRejectsUnknownTarget() {
        when(userRepo.findById(2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.request(1L, 2L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void requestRejectsDuplicatePending() {
        User target = activeUser(2L);
        when(userRepo.findById(2L)).thenReturn(Optional.of(target));
        when(matchRepo.existsByUserAIdAndUserBIdAndStatus(1L, 2L, MatchStatus.PENDING)).thenReturn(true);
        assertThatThrownBy(() -> service.request(1L, 2L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already");
    }

    @Test
    void requestCreatesPendingMatch() {
        User target = activeUser(2L);
        when(userRepo.findById(2L)).thenReturn(Optional.of(target));
        when(matchRepo.existsByUserAIdAndUserBIdAndStatus(1L, 2L, MatchStatus.PENDING)).thenReturn(false);
        when(matchRepo.save(any(Match.class))).thenAnswer(i -> i.getArgument(0));

        MatchDto dto = service.request(1L, 2L);
        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.userAId()).isEqualTo(1L);
        assertThat(dto.userBId()).isEqualTo(2L);
    }

    @Test
    void respondRejectsWhenNotRecipient() {
        when(matchRepo.findByIdAndUserBId(9L, 1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.respond(1L, 9L, "ACCEPTED"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void respondRejectsInvalidStatus() {
        Match m = new Match(); m.setUserAId(2L); m.setUserBId(1L);
        when(matchRepo.findByIdAndUserBId(9L, 1L)).thenReturn(Optional.of(m));
        assertThatThrownBy(() -> service.respond(1L, 9L, "MAYBE"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void respondAcceptsMatch() {
        Match m = new Match(); m.setUserAId(2L); m.setUserBId(1L);
        when(matchRepo.findByIdAndUserBId(9L, 1L)).thenReturn(Optional.of(m));
        when(matchRepo.save(any(Match.class))).thenAnswer(i -> i.getArgument(0));

        MatchDto dto = service.respond(1L, 9L, "ACCEPTED");
        assertThat(dto.status()).isEqualTo("ACCEPTED");
    }

    private User activeUser(Long id) {
        User u = new User();
        u.setEmail("u" + id + "@example.com");
        u.setActive(true);
        try { var f = User.class.getDeclaredField("id"); f.setAccessible(true); f.set(u, id); }
        catch (Exception e) { throw new RuntimeException(e); }
        return u;
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests MatchServiceTest`
Expected: FAIL — `MatchService`, DTOs do not exist.

- [ ] **Step 3: Write the DTOs**

`backend/src/main/java/com/skillswap/dto/MatchSuggestionDto.java`:
```java
package com.skillswap.dto;

public record MatchSuggestionDto(Long userId, String fullName, String city,
                                 long matchedSkills, int compatibilityScore) {}
```

`backend/src/main/java/com/skillswap/dto/MatchDto.java`:
```java
package com.skillswap.dto;

import java.time.LocalDateTime;

public record MatchDto(Long id, Long userAId, Long userBId, String status, LocalDateTime createdDate) {}
```

`backend/src/main/java/com/skillswap/dto/CreateMatchRequest.java`:
```java
package com.skillswap.dto;

import jakarta.validation.constraints.NotNull;

public record CreateMatchRequest(@NotNull Long targetUserId) {}
```

`backend/src/main/java/com/skillswap/dto/UpdateMatchRequest.java`:
```java
package com.skillswap.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateMatchRequest(@NotBlank String status) {}
```

- [ ] **Step 4: Write the MatchService**

`backend/src/main/java/com/skillswap/service/MatchService.java`:
```java
package com.skillswap.service;

import com.skillswap.dto.MatchDto;
import com.skillswap.dto.MatchSuggestionDto;
import com.skillswap.entity.Match;
import com.skillswap.entity.MatchStatus;
import com.skillswap.entity.SkillType;
import com.skillswap.entity.User;
import com.skillswap.repository.MatchProjection;
import com.skillswap.repository.MatchRepository;
import com.skillswap.repository.UserRepository;
import com.skillswap.repository.UserSkillRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class MatchService {

    private final UserSkillRepository userSkillRepository;
    private final MatchRepository matchRepository;
    private final UserRepository userRepository;

    public MatchService(UserSkillRepository userSkillRepository, MatchRepository matchRepository,
                        UserRepository userRepository) {
        this.userSkillRepository = userSkillRepository;
        this.matchRepository = matchRepository;
        this.userRepository = userRepository;
    }

    public List<MatchSuggestionDto> suggestions(Long userId, String city, String category) {
        long wanted = userSkillRepository.countByUserIdAndSkillType(userId, SkillType.WANT_TO_LEARN);
        List<MatchProjection> rows = userSkillRepository.findSuggestions(
                userId, city == null ? "" : city, category == null ? "" : category);
        return rows.stream().map(r -> new MatchSuggestionDto(
                r.getUserId(), r.getFullName(), r.getCity(), r.getMatchedSkills(),
                score(r.getMatchedSkills(), wanted))).toList();
    }

    public MatchDto request(Long meId, Long targetId) {
        if (meId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot match with yourself");
        }
        User target = userRepository.findById(targetId)
                .filter(User::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (matchRepository.existsByUserAIdAndUserBIdAndStatus(meId, target.getId(), MatchStatus.PENDING)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Match request already pending");
        }
        Match m = new Match();
        m.setUserAId(meId);
        m.setUserBId(target.getId());
        m.setStatus(MatchStatus.PENDING);
        return toDto(matchRepository.save(m));
    }

    public MatchDto respond(Long meId, Long matchId, String status) {
        MatchStatus newStatus = parseStatus(status);
        Match m = matchRepository.findByIdAndUserBId(matchId, meId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
        m.setStatus(newStatus);
        return toDto(matchRepository.save(m));
    }

    public List<MatchDto> myMatches(Long meId) {
        return matchRepository.findByUserAIdOrUserBId(meId, meId).stream().map(this::toDto).toList();
    }

    private int score(long matched, long wanted) {
        if (wanted <= 0) return 0;
        return (int) Math.round(100.0 * matched / wanted);
    }

    private MatchStatus parseStatus(String raw) {
        MatchStatus s;
        try {
            s = MatchStatus.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ACCEPTED or REJECTED");
        }
        if (s != MatchStatus.ACCEPTED && s != MatchStatus.REJECTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be ACCEPTED or REJECTED");
        }
        return s;
    }

    private MatchDto toDto(Match m) {
        return new MatchDto(m.getId(), m.getUserAId(), m.getUserBId(), m.getStatus().name(), m.getCreatedDate());
    }
}
```

- [ ] **Step 5: Run the service test to verify it passes**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests MatchServiceTest`
Expected: PASS — all cases green.

- [ ] **Step 6: Write the MatchController**

`backend/src/main/java/com/skillswap/controller/MatchController.java`:
```java
package com.skillswap.controller;

import com.skillswap.dto.CreateMatchRequest;
import com.skillswap.dto.MatchDto;
import com.skillswap.dto.MatchSuggestionDto;
import com.skillswap.dto.UpdateMatchRequest;
import com.skillswap.service.CurrentUser;
import com.skillswap.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;
    private final CurrentUser currentUser;

    public MatchController(MatchService matchService, CurrentUser currentUser) {
        this.matchService = matchService;
        this.currentUser = currentUser;
    }

    @GetMapping("/suggestions")
    public List<MatchSuggestionDto> suggestions(@RequestParam(required = false) String city,
                                                @RequestParam(required = false) String category) {
        return matchService.suggestions(currentUser.require().getId(), city, category);
    }

    @GetMapping
    public List<MatchDto> myMatches() {
        return matchService.myMatches(currentUser.require().getId());
    }

    @PostMapping("/request")
    public ResponseEntity<MatchDto> request(@Valid @RequestBody CreateMatchRequest req) {
        MatchDto dto = matchService.request(currentUser.require().getId(), req.targetUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @PutMapping("/{id}")
    public MatchDto respond(@PathVariable Long id, @Valid @RequestBody UpdateMatchRequest req) {
        return matchService.respond(currentUser.require().getId(), id, req.status());
    }
}
```

- [ ] **Step 7: Write the end-to-end match flow integration test**

`backend/src/test/java/com/skillswap/controller/MatchFlowTest.java`:
```java
package com.skillswap.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String register(String email) throws Exception {
        String body = json.writeValueAsString(Map.of("fullName", email, "email", email, "password", "password1"));
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(res, "$.token");
    }

    private Long addSkill(String token, long skillId, String type) throws Exception {
        String body = json.writeValueAsString(Map.of("skillId", skillId, "skillType", type));
        String res = mvc.perform(post("/api/me/skills").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(res, "$.id")).longValue();
    }

    @Test
    void learnerSeesTeacherAndCanRequestMatch() throws Exception {
        // Seeded skill id 4 = 'Python' (from V3). Learner wants it, teacher teaches it.
        String learner = register("learner@example.com");
        String teacher = register("teacher@example.com");
        addSkill(learner, 4L, "WANT_TO_LEARN");
        addSkill(teacher, 4L, "CAN_TEACH");

        // teacher's numeric id (via /api/me)
        String teacherMe = mvc.perform(get("/api/me").header("Authorization", "Bearer " + teacher))
                .andReturn().getResponse().getContentAsString();
        Long teacherId = ((Number) com.jayway.jsonpath.JsonPath.read(teacherMe, "$.id")).longValue();

        mvc.perform(get("/api/matches/suggestions").header("Authorization", "Bearer " + learner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(teacherId.intValue()))
                .andExpect(jsonPath("$[0].compatibilityScore").value(100));

        String reqBody = json.writeValueAsString(Map.of("targetUserId", teacherId));
        mvc.perform(post("/api/matches/request").header("Authorization", "Bearer " + learner)
                        .contentType(MediaType.APPLICATION_JSON).content(reqBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void suggestionsRequireAuth() throws Exception {
        mvc.perform(get("/api/matches/suggestions")).andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 8: Run the full suite**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test`
Expected: PASS — all prior tests plus `MatchServiceTest` and `MatchFlowTest`.

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/skillswap/dto/MatchSuggestionDto.java \
        backend/src/main/java/com/skillswap/dto/MatchDto.java \
        backend/src/main/java/com/skillswap/dto/CreateMatchRequest.java \
        backend/src/main/java/com/skillswap/dto/UpdateMatchRequest.java \
        backend/src/main/java/com/skillswap/service/MatchService.java \
        backend/src/main/java/com/skillswap/controller/MatchController.java \
        backend/src/test/java/com/skillswap/service/MatchServiceTest.java \
        backend/src/test/java/com/skillswap/controller/MatchFlowTest.java
git commit -m "feat: add SQL-based match suggestions and match request flow"
```

---

### Task 4: Redis caching (graceful degrade)

**Files:**
- Modify: `backend/build.gradle` (add `spring-boot-starter-data-redis`)
- Create: `backend/src/main/java/com/skillswap/config/CacheConfig.java`
- Modify: `backend/src/main/java/com/skillswap/service/SkillService.java` (add `@Cacheable`/`@CacheEvict`)
- Modify: `backend/src/main/java/com/skillswap/service/MatchService.java` (add `@Cacheable`/`@CacheEvict`)
- Modify: `backend/src/main/resources/application.yml` (redis + cache config)
- Modify: `backend/src/test/resources/application-test.yml` (cache type simple)
- Test: `backend/src/test/java/com/skillswap/config/CachingTest.java`
- Test: `backend/src/test/java/com/skillswap/config/CacheErrorHandlerTest.java`

**Interfaces:**
- Consumes: `SkillService`, `MatchService`.
- Produces: `@EnableCaching` app-wide; caches `skills`, `categories`, `suggestions`; a `CacheErrorHandler` that logs and swallows Redis errors (app falls back to the DB). Cache names and keys: `catalog()`→`skills`, `categories()`→`categories`, `suggestions(userId,city,category)`→`suggestions` keyed by `userId + '-' + city + '-' + category`. Writes evict: `add`/`remove` user-skill → `@CacheEvict(value="suggestions", allEntries=true)`.

- [ ] **Step 1: Add the Redis dependency**

In `backend/build.gradle`, add to the `dependencies` block (alongside the existing starters):
```groovy
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
```

- [ ] **Step 2: Write the cache configuration + error handler**

`backend/src/main/java/com/skillswap/config/CacheConfig.java`:
```java
package com.skillswap.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManagerBuilderCustomizer;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /** 10-minute TTL for all Redis-backed caches. Ignored when cache type is 'simple' (tests). */
    @Bean
    public RedisCacheManagerBuilderCustomizer redisTtl() {
        return builder -> builder.cacheDefaults(
                RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofMinutes(10)));
    }

    /** Swallow Redis errors so a Redis outage degrades to a DB hit instead of a 500. */
    @Bean
    public CacheErrorHandler cacheErrorHandler() {
        return new CacheErrorHandler() {
            private final SimpleCacheErrorHandler delegate = new SimpleCacheErrorHandler();
            @Override public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache GET failed ({}), falling back to source: {}", cache.getName(), e.getMessage());
            }
            @Override public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed ({}): {}", cache.getName(), e.getMessage());
            }
            @Override public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache EVICT failed ({}): {}", cache.getName(), e.getMessage());
            }
            @Override public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache CLEAR failed ({}): {}", cache.getName(), e.getMessage());
            }
        };
    }
}
```

- [ ] **Step 3: Annotate the service read/write methods**

In `SkillService.java`, add imports and annotations:
```java
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
```
- Annotate `catalog()` with `@Cacheable("skills")`.
- Annotate `categories()` with `@Cacheable("categories")`.
- Annotate `add(...)` and `remove(...)` each with `@CacheEvict(value = "suggestions", allEntries = true)`.

In `MatchService.java`, add:
```java
import org.springframework.cache.annotation.Cacheable;
```
- Annotate `suggestions(Long userId, String city, String category)` with
  `@Cacheable(value = "suggestions", key = "#userId + '-' + (#city == null ? '' : #city) + '-' + (#category == null ? '' : #category)")`.

(Do not change method bodies — only add annotations/imports.)

- [ ] **Step 4: Configure cache backends per profile**

In `backend/src/main/resources/application.yml`, add under `spring:`:
```yaml
  cache:
    type: redis
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
```

In `backend/src/test/resources/application-test.yml`, add under `spring:`:
```yaml
  cache:
    type: simple
```
(Tests use an in-memory `ConcurrentMapCacheManager` — no Redis required.)

- [ ] **Step 5: Write the caching behavior test**

`backend/src/test/java/com/skillswap/config/CachingTest.java`:
```java
package com.skillswap.config;

import com.skillswap.entity.Skill;
import com.skillswap.repository.SkillRepository;
import com.skillswap.service.SkillService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class CachingTest {

    @Autowired SkillService skillService;
    @Autowired CacheManager cacheManager;
    @MockBean SkillRepository skillRepository;

    @Test
    void catalogIsCachedAfterFirstCall() {
        Skill s = new Skill(); s.setSkillName("Guitar"); s.setCategory("Music");
        when(skillRepository.findAll()).thenReturn(List.of(s));

        skillService.catalog();
        skillService.catalog();

        // Repository hit only once; second call served from the 'skills' cache.
        verify(skillRepository, times(1)).findAll();
        assertThat(cacheManager.getCache("skills")).isNotNull();
    }
}
```

- [ ] **Step 6: Write the error-handler unit test**

`backend/src/test/java/com/skillswap/config/CacheErrorHandlerTest.java`:
```java
package com.skillswap.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.interceptor.CacheErrorHandler;

import static org.assertj.core.api.Assertions.assertThatCode;

class CacheErrorHandlerTest {

    private final CacheErrorHandler handler = new CacheConfig().cacheErrorHandler();
    private final Cache cache = new ConcurrentMapCache("skills");

    @Test
    void swallowsGetAndPutErrors() {
        RuntimeException boom = new RuntimeException("redis down");
        assertThatCode(() -> handler.handleCacheGetError(boom, cache, "k")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCachePutError(boom, cache, "k", "v")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCacheEvictError(boom, cache, "k")).doesNotThrowAnyException();
        assertThatCode(() -> handler.handleCacheClearError(boom, cache)).doesNotThrowAnyException();
    }
}
```

- [ ] **Step 7: Run the full suite**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test`
Expected: PASS — all prior tests plus `CachingTest` and `CacheErrorHandlerTest`. (Tests use `cache.type: simple`; no Redis needed.)

- [ ] **Step 8: Commit**

```bash
git add backend/build.gradle \
        backend/src/main/java/com/skillswap/config/CacheConfig.java \
        backend/src/main/java/com/skillswap/service/SkillService.java \
        backend/src/main/java/com/skillswap/service/MatchService.java \
        backend/src/main/resources/application.yml \
        backend/src/test/resources/application-test.yml \
        backend/src/test/java/com/skillswap/config/CachingTest.java \
        backend/src/test/java/com/skillswap/config/CacheErrorHandlerTest.java
git commit -m "feat: cache skill catalog and match suggestions in Redis with graceful degrade"
```

---

## Self-Review

**Spec coverage (Plan 2 slice):**
- Add/edit/delete teach & learn skills, categories → Task 1 (schema), Task 2 (CRUD). ✅
- Smart matching by complementary skills, compatibility score %, filters (city, category) → Task 3. ✅ (mode filter belongs to sessions — deferred to Plan 3, noted.)
- Send/accept/reject match requests → Task 3. ✅
- Redis cache on read-heavy paths (catalog, suggestions) + evict on write, graceful degrade → Task 4. ✅

**Placeholder scan:** No TBD/TODO; every code step is complete. ✅

**Type consistency:** `MatchProjection` (Task 1) consumed by `MatchService` (Task 3) and `MatchServiceTest`. `findSuggestions(userId, city, category)` signature identical in repo (Task 1), service (Task 3), test mocks. `CurrentUser.require()` (Task 2) used by `MeSkillController` (Task 2) and `MatchController` (Task 3). `SkillType`/`MatchStatus` enum names match DB `VARCHAR` values. `@Cacheable("suggestions")` key expression (Task 4) matches the `suggestions` cache evicted by `SkillService` writes. ✅

**Decisions / simplifications:**
- Matching direction is "candidate teaches what I want to learn" (primary), scored as `matched / my-want-count`. The spec's "and vice versa" symmetric view is deferred; this covers the "Best Matches For You" use case. (`ponytail:` scored heuristic — swap `score()` / the query for a richer model later.)
- Empty-string sentinels (not NULL) for optional `city`/`category` params keep the native query typed on H2.
- Cache eviction on any user-skill change uses `allEntries=true` on `suggestions` (a user's teach-skill change can affect other users' suggestions; targeted eviction isn't worth the bookkeeping). `ponytail: allEntries evict, targeted eviction if cache churn matters`.

---

## Execution Handoff

Plan complete and saved to `docs/plans/2026-07-20-skillswap-skills-matching.md`.
