# SkillSwap Hub — Plan 3: Sessions + Credits

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Two matched users can schedule a learning session, confirm/reschedule/cancel/complete it, and completing a session atomically moves credits from learner to teacher with a durable ledger; booking is blocked if the learner can't afford it.

**Architecture:** Extends the Plan-1/2 Spring Boot monolith. New entities `Session` (table `sessions`, avoiding any doubt about reserved words the way Plan 2 did for `matches`) and `CreditTransaction`, added via Flyway. `CreditService` is the sole owner of credit mutation (spend/earn always happen together, in one `@Transactional` method, at session completion) reusing Plan 1's `SkillCredit`/`SkillCreditRepository`. `SessionService` owns the booking state machine and calls `CreditService` only to gate booking (read-only affordability check) and to settle at completion (the one place money actually moves).

**Tech Stack:** Java 17, Spring Boot 3.2.5, Gradle, Spring Data JPA, Flyway, MySQL 8 (H2 for tests). No frontend or Redis changes in this plan.

## Global Constraints

- Base package `com.skillswap`. Java **17**. Spring Boot **3.2.5**. Gradle only (never Maven).
- **Build with JDK 17:** machine default `java` is JDK 26, unsupported by Gradle 8.7. Prefix every gradle command with `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`.
- Schema owned by **Flyway**, append-only. `V1`–`V3` (Plans 1–2) are frozen. This plan adds `V4__sessions.sql` (Task 1) and `V5__credit_transaction.sql` (Task 2).
- Session table is named **`sessions`** (plural), matching the `matches` precedent — no ambiguity about reserved words.
- Business errors via `org.springframework.web.server.ResponseStatusException` only (the existing generic handler in `GlobalExceptionHandler`, added in Plan 2, already renders these as `{ "error": <int>, "message": <text> }` — do not add new exception types or touch `GlobalExceptionHandler`).
- Thin controllers; DTOs at the boundary (never serialize `Session`/`CreditTransaction`/`SkillCredit` entities directly).
- Credits: flat rate of **1 credit per completed session** (`CreditService.SESSION_RATE`), booking checks affordability without mutating balance, completion is the only place balance actually changes (both learner-spend and teacher-earn happen together in one transaction).
- Test profile is H2 (Flyway disabled, Hibernate `create-drop`) — the test schema is built from entities, so `Session`/`CreditTransaction` entities must be complete and correctly annotated for tests to pass without Flyway.
- Git author **Devashish Jadhav <jadhavom24@gmail.com>**. **Never** add `Co-Authored-By` or AI attribution. Conventional Commit messages. Commit at the end of every task.

**Interfaces already available from Plans 1–2:** `User`, `UserRepository`; `SkillCredit(userId, totalCredits, creditsEarned, creditsSpent)`, `SkillCreditRepository.findByUserId(Long)` (Plan 1); `Match(id, userAId, userBId, status)`, `MatchStatus{PENDING,ACCEPTED,REJECTED}`, `MatchRepository` (Plan 2); `CurrentUser.require()` resolving the authenticated `User` (Plan 2); `GlobalExceptionHandler`'s generic `ResponseStatusException` → `{error,message}` handler (Plan 2).

---

### Task 1: Session schema + entity + repository

**Files:**
- Create: `backend/src/main/resources/db/migration/V4__sessions.sql`
- Create: `backend/src/main/java/com/skillswap/entity/SessionStatus.java`
- Create: `backend/src/main/java/com/skillswap/entity/SessionMode.java`
- Create: `backend/src/main/java/com/skillswap/entity/Session.java`
- Create: `backend/src/main/java/com/skillswap/repository/SessionRepository.java`
- Test: `backend/src/test/java/com/skillswap/repository/SessionRepositoryTest.java`

**Interfaces:**
- Consumes: `users`, `matches` tables (Plans 1–2).
- Produces:
  - `Session` (`id, matchId, teacherUserId, learnerUserId, scheduledByUserId, sessionDate, startTime, endTime, mode, locationOrLink, status, createdDate`).
  - `SessionStatus {PENDING, CONFIRMED, COMPLETED, CANCELLED}`, `SessionMode {ONLINE, OFFLINE}`.
  - `SessionRepository extends JpaRepository<Session,Long>` + `List<Session> findByTeacherUserIdOrLearnerUserId(Long, Long)`.

- [ ] **Step 1: Write the Flyway migration**

`backend/src/main/resources/db/migration/V4__sessions.sql`:
```sql
CREATE TABLE sessions (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    match_id             BIGINT NOT NULL,
    teacher_user_id      BIGINT NOT NULL,
    learner_user_id      BIGINT NOT NULL,
    scheduled_by_user_id BIGINT NOT NULL,
    session_date         DATE NOT NULL,
    start_time           TIME NOT NULL,
    end_time             TIME NOT NULL,
    mode                 VARCHAR(20) NOT NULL,
    location_or_link     VARCHAR(255),
    status               VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_date         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_session_match   FOREIGN KEY (match_id)   REFERENCES matches(id),
    CONSTRAINT fk_session_teacher FOREIGN KEY (teacher_user_id) REFERENCES users(id),
    CONSTRAINT fk_session_learner FOREIGN KEY (learner_user_id) REFERENCES users(id),
    CONSTRAINT fk_session_sched   FOREIGN KEY (scheduled_by_user_id) REFERENCES users(id)
);
CREATE INDEX idx_session_teacher ON sessions(teacher_user_id);
CREATE INDEX idx_session_learner ON sessions(learner_user_id);
```

- [ ] **Step 2: Write the failing repository test**

`backend/src/test/java/com/skillswap/repository/SessionRepositoryTest.java`:
```java
package com.skillswap.repository;

import com.skillswap.entity.Session;
import com.skillswap.entity.SessionMode;
import com.skillswap.entity.SessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class SessionRepositoryTest {

    @Autowired SessionRepository sessionRepository;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private Long insertUser(String email) {
        jdbc.update("INSERT INTO users(full_name,email,password_hash,role,active) VALUES (?,?,?,?,?)",
                email, email, "hash", "USER", true);
        return jdbc.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Long insertAcceptedMatch(Long a, Long b) {
        jdbc.update("INSERT INTO matches(user_a_id,user_b_id,status) VALUES (?,?,?)", a, b, "ACCEPTED");
        return jdbc.queryForObject(
                "SELECT id FROM matches WHERE user_a_id = ? AND user_b_id = ?", Long.class, a, b);
    }

    @Test
    void savesAndFindsByTeacherOrLearner() {
        Long teacher = insertUser("teacher@example.com");
        Long learner = insertUser("learner@example.com");
        Long matchId = insertAcceptedMatch(learner, teacher);

        Session s = new Session();
        s.setMatchId(matchId);
        s.setTeacherUserId(teacher);
        s.setLearnerUserId(learner);
        s.setScheduledByUserId(learner);
        s.setSessionDate(LocalDate.of(2026, 8, 1));
        s.setStartTime(LocalTime.of(10, 0));
        s.setEndTime(LocalTime.of(11, 0));
        s.setMode(SessionMode.ONLINE);
        s.setStatus(SessionStatus.PENDING);
        sessionRepository.save(s);

        assertThat(sessionRepository.findByTeacherUserIdOrLearnerUserId(teacher, teacher)).hasSize(1);
        assertThat(sessionRepository.findByTeacherUserIdOrLearnerUserId(learner, learner)).hasSize(1);
        assertThat(sessionRepository.findByTeacherUserIdOrLearnerUserId(999L, 999L)).isEmpty();
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests SessionRepositoryTest`
Expected: FAIL — `Session`, `SessionMode`, `SessionStatus`, `SessionRepository` do not exist (compilation error).

- [ ] **Step 4: Write the enums and entity**

`backend/src/main/java/com/skillswap/entity/SessionStatus.java`:
```java
package com.skillswap.entity;

public enum SessionStatus { PENDING, CONFIRMED, COMPLETED, CANCELLED }
```

`backend/src/main/java/com/skillswap/entity/SessionMode.java`:
```java
package com.skillswap.entity;

public enum SessionMode { ONLINE, OFFLINE }
```

`backend/src/main/java/com/skillswap/entity/Session.java`:
```java
package com.skillswap.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "sessions")
public class Session {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long matchId;

    @Column(nullable = false)
    private Long teacherUserId;

    @Column(nullable = false)
    private Long learnerUserId;

    @Column(nullable = false)
    private Long scheduledByUserId;

    @Column(nullable = false)
    private LocalDate sessionDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionMode mode;

    @Column(length = 255)
    private String locationOrLink;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status = SessionStatus.PENDING;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    void onCreate() { if (createdDate == null) createdDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getMatchId() { return matchId; }
    public void setMatchId(Long v) { this.matchId = v; }
    public Long getTeacherUserId() { return teacherUserId; }
    public void setTeacherUserId(Long v) { this.teacherUserId = v; }
    public Long getLearnerUserId() { return learnerUserId; }
    public void setLearnerUserId(Long v) { this.learnerUserId = v; }
    public Long getScheduledByUserId() { return scheduledByUserId; }
    public void setScheduledByUserId(Long v) { this.scheduledByUserId = v; }
    public LocalDate getSessionDate() { return sessionDate; }
    public void setSessionDate(LocalDate v) { this.sessionDate = v; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime v) { this.startTime = v; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime v) { this.endTime = v; }
    public SessionMode getMode() { return mode; }
    public void setMode(SessionMode v) { this.mode = v; }
    public String getLocationOrLink() { return locationOrLink; }
    public void setLocationOrLink(String v) { this.locationOrLink = v; }
    public SessionStatus getStatus() { return status; }
    public void setStatus(SessionStatus v) { this.status = v; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
```

- [ ] **Step 5: Write the repository**

`backend/src/main/java/com/skillswap/repository/SessionRepository.java`:
```java
package com.skillswap.repository;

import com.skillswap.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SessionRepository extends JpaRepository<Session, Long> {
    List<Session> findByTeacherUserIdOrLearnerUserId(Long teacherUserId, Long learnerUserId);
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests SessionRepositoryTest`
Expected: PASS.

- [ ] **Step 7: Run the full suite**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test`
Expected: PASS — all 40 prior tests (Plans 1–2) plus this task's test.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V4__sessions.sql \
        backend/src/main/java/com/skillswap/entity/SessionStatus.java \
        backend/src/main/java/com/skillswap/entity/SessionMode.java \
        backend/src/main/java/com/skillswap/entity/Session.java \
        backend/src/main/java/com/skillswap/repository/SessionRepository.java \
        backend/src/test/java/com/skillswap/repository/SessionRepositoryTest.java
git commit -m "feat: add sessions schema with entity and repository"
```

---

### Task 2: Credit ledger (CreditTransaction schema + CreditService + credit endpoints)

**Files:**
- Create: `backend/src/main/resources/db/migration/V5__credit_transaction.sql`
- Create: `backend/src/main/java/com/skillswap/entity/TransactionType.java`
- Create: `backend/src/main/java/com/skillswap/entity/CreditTransaction.java`
- Create: `backend/src/main/java/com/skillswap/repository/CreditTransactionRepository.java`
- Create: `backend/src/main/java/com/skillswap/dto/SkillCreditDto.java`
- Create: `backend/src/main/java/com/skillswap/dto/CreditTransactionDto.java`
- Create: `backend/src/main/java/com/skillswap/service/CreditService.java`
- Create: `backend/src/main/java/com/skillswap/controller/MeCreditController.java`
- Test: `backend/src/test/java/com/skillswap/service/CreditServiceTest.java`

**Interfaces:**
- Consumes: `SkillCredit`, `SkillCreditRepository.findByUserId(Long)` (Plan 1); `CurrentUser` (Plan 2).
- Produces:
  - `TransactionType {EARNED, SPENT}`. `CreditTransaction(id, userId, sessionId, transactionType, amount, transactionDate)`.
  - `CreditTransactionRepository extends JpaRepository<CreditTransaction,Long>` + `List<CreditTransaction> findByUserIdOrderByTransactionDateDesc(Long)`.
  - `record SkillCreditDto(int totalCredits, int creditsEarned, int creditsSpent)`.
  - `record CreditTransactionDto(Long id, Long sessionId, String transactionType, int amount, java.time.LocalDateTime transactionDate)`.
  - `CreditService` — `public static final int SESSION_RATE = 1`; `boolean canAfford(Long userId)`; `void settle(Long teacherUserId, Long learnerUserId, Long sessionId)` (atomic: learner spends, teacher earns, both ledgered); `List<CreditTransaction> history(Long userId)`.
  - `GET /api/me/credits` → `SkillCreditDto`; `GET /api/me/credits/transactions` → `List<CreditTransactionDto>`.

- [ ] **Step 1: Write the Flyway migration**

`backend/src/main/resources/db/migration/V5__credit_transaction.sql`:
```sql
CREATE TABLE credit_transaction (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    session_id       BIGINT NOT NULL,
    transaction_type VARCHAR(20) NOT NULL,
    amount           INT NOT NULL,
    transaction_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_credittx_user    FOREIGN KEY (user_id)    REFERENCES users(id),
    CONSTRAINT fk_credittx_session FOREIGN KEY (session_id) REFERENCES sessions(id)
);
CREATE INDEX idx_credittx_user ON credit_transaction(user_id);
```

- [ ] **Step 2: Write the failing service test**

`backend/src/test/java/com/skillswap/service/CreditServiceTest.java`:
```java
package com.skillswap.service;

import com.skillswap.entity.CreditTransaction;
import com.skillswap.entity.SkillCredit;
import com.skillswap.entity.TransactionType;
import com.skillswap.repository.CreditTransactionRepository;
import com.skillswap.repository.SkillCreditRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CreditServiceTest {

    private final SkillCreditRepository skillCreditRepo = mock(SkillCreditRepository.class);
    private final CreditTransactionRepository txRepo = mock(CreditTransactionRepository.class);
    private final CreditService service = new CreditService(skillCreditRepo, txRepo);

    private SkillCredit creditOf(Long userId, int total) {
        SkillCredit c = new SkillCredit(userId);
        c.setTotalCredits(total);
        return c;
    }

    @Test
    void canAffordTrueWhenBalanceSufficient() {
        when(skillCreditRepo.findByUserId(1L)).thenReturn(Optional.of(creditOf(1L, 5)));
        assertThat(service.canAfford(1L)).isTrue();
    }

    @Test
    void canAffordFalseWhenBalanceZero() {
        when(skillCreditRepo.findByUserId(1L)).thenReturn(Optional.of(creditOf(1L, 0)));
        assertThat(service.canAfford(1L)).isFalse();
    }

    @Test
    void canAffordFalseWhenNoAccount() {
        when(skillCreditRepo.findByUserId(1L)).thenReturn(Optional.empty());
        assertThat(service.canAfford(1L)).isFalse();
    }

    @Test
    void settleMovesCreditsAndWritesLedgerForBothParties() {
        SkillCredit teacher = creditOf(10L, 10);
        SkillCredit learner = creditOf(20L, 10);
        when(skillCreditRepo.findByUserId(20L)).thenReturn(Optional.of(learner));
        when(skillCreditRepo.findByUserId(10L)).thenReturn(Optional.of(teacher));

        service.settle(10L, 20L, 99L);

        assertThat(learner.getTotalCredits()).isEqualTo(9);
        assertThat(learner.getCreditsSpent()).isEqualTo(1);
        assertThat(teacher.getTotalCredits()).isEqualTo(11);
        assertThat(teacher.getCreditsEarned()).isEqualTo(1);

        verify(skillCreditRepo).save(learner);
        verify(skillCreditRepo).save(teacher);

        ArgumentCaptor:
        var captor = org.mockito.ArgumentCaptor.forClass(CreditTransaction.class);
        verify(txRepo, times(2)).save(captor.capture());
        List<CreditTransaction> saved = captor.getAllValues();
        assertThat(saved).extracting(CreditTransaction::getTransactionType)
                .containsExactlyInAnyOrder(TransactionType.SPENT, TransactionType.EARNED);
        assertThat(saved).allMatch(t -> t.getSessionId().equals(99L) && t.getAmount() == 1);
    }

    @Test
    void settleThrowsWhenLearnerInsufficientAtSettleTime() {
        when(skillCreditRepo.findByUserId(20L)).thenReturn(Optional.of(creditOf(20L, 0)));
        assertThatThrownBy(() -> service.settle(10L, 20L, 99L))
                .isInstanceOf(ResponseStatusException.class);
        verify(skillCreditRepo, never()).save(any());
    }

    @Test
    void historyReturnsUserTransactionsDescending() {
        CreditTransaction t = new CreditTransaction();
        when(txRepo.findByUserIdOrderByTransactionDateDesc(1L)).thenReturn(List.of(t));
        assertThat(service.history(1L)).containsExactly(t);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests CreditServiceTest`
Expected: FAIL — compile error (`CreditService`, `TransactionType`, `CreditTransaction` do not exist, and the stray `ArgumentCaptor:` label in the test above is a placeholder for you to replace — see Step 3a).

- [ ] **Step 3a: Fix the test's ArgumentCaptor line before running**

Replace the line `ArgumentCaptor:` (a label accidentally left in the draft above) — delete that line entirely so the method reads:
```java
    @Test
    void settleMovesCreditsAndWritesLedgerForBothParties() {
        SkillCredit teacher = creditOf(10L, 10);
        SkillCredit learner = creditOf(20L, 10);
        when(skillCreditRepo.findByUserId(20L)).thenReturn(Optional.of(learner));
        when(skillCreditRepo.findByUserId(10L)).thenReturn(Optional.of(teacher));

        service.settle(10L, 20L, 99L);

        assertThat(learner.getTotalCredits()).isEqualTo(9);
        assertThat(learner.getCreditsSpent()).isEqualTo(1);
        assertThat(teacher.getTotalCredits()).isEqualTo(11);
        assertThat(teacher.getCreditsEarned()).isEqualTo(1);

        verify(skillCreditRepo).save(learner);
        verify(skillCreditRepo).save(teacher);

        var captor = org.mockito.ArgumentCaptor.forClass(CreditTransaction.class);
        verify(txRepo, times(2)).save(captor.capture());
        List<CreditTransaction> saved = captor.getAllValues();
        assertThat(saved).extracting(CreditTransaction::getTransactionType)
                .containsExactlyInAnyOrder(TransactionType.SPENT, TransactionType.EARNED);
        assertThat(saved).allMatch(t -> t.getSessionId().equals(99L) && t.getAmount() == 1);
    }
```
This corrected version is what you actually create the file with in Step 2 — apply this fix before the first compile attempt.

- [ ] **Step 4: Write the enum, entity, repository, DTOs**

`backend/src/main/java/com/skillswap/entity/TransactionType.java`:
```java
package com.skillswap.entity;

public enum TransactionType { EARNED, SPENT }
```

`backend/src/main/java/com/skillswap/entity/CreditTransaction.java`:
```java
package com.skillswap.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "credit_transaction")
public class CreditTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, updatable = false)
    private LocalDateTime transactionDate;

    @PrePersist
    void onCreate() { if (transactionDate == null) transactionDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long v) { this.sessionId = v; }
    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType v) { this.transactionType = v; }
    public int getAmount() { return amount; }
    public void setAmount(int v) { this.amount = v; }
    public LocalDateTime getTransactionDate() { return transactionDate; }
}
```

`backend/src/main/java/com/skillswap/repository/CreditTransactionRepository.java`:
```java
package com.skillswap.repository;

import com.skillswap.entity.CreditTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreditTransactionRepository extends JpaRepository<CreditTransaction, Long> {
    List<CreditTransaction> findByUserIdOrderByTransactionDateDesc(Long userId);
}
```

`backend/src/main/java/com/skillswap/dto/SkillCreditDto.java`:
```java
package com.skillswap.dto;

public record SkillCreditDto(int totalCredits, int creditsEarned, int creditsSpent) {}
```

`backend/src/main/java/com/skillswap/dto/CreditTransactionDto.java`:
```java
package com.skillswap.dto;

import java.time.LocalDateTime;

public record CreditTransactionDto(Long id, Long sessionId, String transactionType,
                                   int amount, LocalDateTime transactionDate) {}
```

- [ ] **Step 5: Write CreditService**

`backend/src/main/java/com/skillswap/service/CreditService.java`:
```java
package com.skillswap.service;

import com.skillswap.entity.CreditTransaction;
import com.skillswap.entity.SkillCredit;
import com.skillswap.entity.TransactionType;
import com.skillswap.repository.CreditTransactionRepository;
import com.skillswap.repository.SkillCreditRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class CreditService {

    /** Flat credit cost/reward per completed session. Upgrade path: per-skill or per-duration pricing. */
    public static final int SESSION_RATE = 1;

    private final SkillCreditRepository skillCreditRepository;
    private final CreditTransactionRepository creditTransactionRepository;

    public CreditService(SkillCreditRepository skillCreditRepository,
                         CreditTransactionRepository creditTransactionRepository) {
        this.skillCreditRepository = skillCreditRepository;
        this.creditTransactionRepository = creditTransactionRepository;
    }

    /** Read-only affordability check used to gate booking; does not mutate balance. */
    public boolean canAfford(Long userId) {
        return skillCreditRepository.findByUserId(userId)
                .map(c -> c.getTotalCredits() >= SESSION_RATE)
                .orElse(false);
    }

    /** The only place credits actually move: learner spends, teacher earns, both ledgered, atomically. */
    @Transactional
    public void settle(Long teacherUserId, Long learnerUserId, Long sessionId) {
        SkillCredit learnerCredit = skillCreditRepository.findByUserId(learnerUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Learner credit account not found"));
        if (learnerCredit.getTotalCredits() < SESSION_RATE) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Learner has insufficient credits");
        }
        SkillCredit teacherCredit = skillCreditRepository.findByUserId(teacherUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher credit account not found"));

        learnerCredit.setTotalCredits(learnerCredit.getTotalCredits() - SESSION_RATE);
        learnerCredit.setCreditsSpent(learnerCredit.getCreditsSpent() + SESSION_RATE);
        skillCreditRepository.save(learnerCredit);

        teacherCredit.setTotalCredits(teacherCredit.getTotalCredits() + SESSION_RATE);
        teacherCredit.setCreditsEarned(teacherCredit.getCreditsEarned() + SESSION_RATE);
        skillCreditRepository.save(teacherCredit);

        creditTransactionRepository.save(ledgerRow(learnerUserId, sessionId, TransactionType.SPENT));
        creditTransactionRepository.save(ledgerRow(teacherUserId, sessionId, TransactionType.EARNED));
    }

    public List<CreditTransaction> history(Long userId) {
        return creditTransactionRepository.findByUserIdOrderByTransactionDateDesc(userId);
    }

    private CreditTransaction ledgerRow(Long userId, Long sessionId, TransactionType type) {
        CreditTransaction t = new CreditTransaction();
        t.setUserId(userId);
        t.setSessionId(sessionId);
        t.setTransactionType(type);
        t.setAmount(SESSION_RATE);
        return t;
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests CreditServiceTest`
Expected: PASS — all six cases green.

- [ ] **Step 7: Write MeCreditController**

`backend/src/main/java/com/skillswap/controller/MeCreditController.java`:
```java
package com.skillswap.controller;

import com.skillswap.dto.CreditTransactionDto;
import com.skillswap.dto.SkillCreditDto;
import com.skillswap.entity.SkillCredit;
import com.skillswap.repository.SkillCreditRepository;
import com.skillswap.service.CreditService;
import com.skillswap.service.CurrentUser;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/me/credits")
public class MeCreditController {

    private final SkillCreditRepository skillCreditRepository;
    private final CreditService creditService;
    private final CurrentUser currentUser;

    public MeCreditController(SkillCreditRepository skillCreditRepository, CreditService creditService,
                              CurrentUser currentUser) {
        this.skillCreditRepository = skillCreditRepository;
        this.creditService = creditService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public SkillCreditDto balance() {
        Long userId = currentUser.require().getId();
        SkillCredit c = skillCreditRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Credit account not found"));
        return new SkillCreditDto(c.getTotalCredits(), c.getCreditsEarned(), c.getCreditsSpent());
    }

    @GetMapping("/transactions")
    public List<CreditTransactionDto> transactions() {
        Long userId = currentUser.require().getId();
        return creditService.history(userId).stream()
                .map(t -> new CreditTransactionDto(t.getId(), t.getSessionId(),
                        t.getTransactionType().name(), t.getAmount(), t.getTransactionDate()))
                .toList();
    }
}
```

- [ ] **Step 8: Run the full suite**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test`
Expected: PASS — all 40 prior tests plus `CreditServiceTest` (6).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/resources/db/migration/V5__credit_transaction.sql \
        backend/src/main/java/com/skillswap/entity/TransactionType.java \
        backend/src/main/java/com/skillswap/entity/CreditTransaction.java \
        backend/src/main/java/com/skillswap/repository/CreditTransactionRepository.java \
        backend/src/main/java/com/skillswap/dto/SkillCreditDto.java \
        backend/src/main/java/com/skillswap/dto/CreditTransactionDto.java \
        backend/src/main/java/com/skillswap/service/CreditService.java \
        backend/src/main/java/com/skillswap/controller/MeCreditController.java \
        backend/src/test/java/com/skillswap/service/CreditServiceTest.java
git commit -m "feat: add credit ledger with settle-on-completion and balance endpoints"
```

---

### Task 3: Session lifecycle (create/confirm/cancel/reschedule/complete) + credit integration

**Files:**
- Create: `backend/src/main/java/com/skillswap/dto/CreateSessionRequest.java`
- Create: `backend/src/main/java/com/skillswap/dto/RescheduleSessionRequest.java`
- Create: `backend/src/main/java/com/skillswap/dto/SessionDto.java`
- Create: `backend/src/main/java/com/skillswap/service/SessionService.java`
- Create: `backend/src/main/java/com/skillswap/controller/SessionController.java`
- Test: `backend/src/test/java/com/skillswap/service/SessionServiceTest.java`
- Test: `backend/src/test/java/com/skillswap/controller/SessionFlowTest.java`

**Interfaces:**
- Consumes: `SessionRepository` (Task 1), `MatchRepository` (Plan 2), `CreditService.canAfford/settle` (Task 2), `CurrentUser` (Plan 2).
- Produces:
  - `record CreateSessionRequest(Long matchId, Long teacherUserId, java.time.LocalDate sessionDate, java.time.LocalTime startTime, java.time.LocalTime endTime, String mode, String locationOrLink)` (validated).
  - `record RescheduleSessionRequest(java.time.LocalDate sessionDate, java.time.LocalTime startTime, java.time.LocalTime endTime)` (validated).
  - `record SessionDto(Long id, Long matchId, Long teacherUserId, Long learnerUserId, Long scheduledByUserId, java.time.LocalDate sessionDate, java.time.LocalTime startTime, java.time.LocalTime endTime, String mode, String locationOrLink, String status, java.time.LocalDateTime createdDate)`.
  - `SessionService` — `create`, `confirm`, `cancel`, `reschedule`, `complete`, `mySessions(userId, filter)`.
  - `POST /api/sessions` (201), `GET /api/sessions?filter=upcoming|past|cancelled`, `PUT /api/sessions/{id}/confirm`, `/cancel`, `/reschedule`, `/complete`.

**Business rules (bind this task):**
- `create`: requester must be a participant of the match; match must be `ACCEPTED`; `teacherUserId` must be one of the two match participants (the other becomes the learner); learner must pass `creditService.canAfford(learnerUserId)` (else 402); session starts `PENDING`, `scheduledByUserId = requester`.
- `confirm`: only a participant who is **not** `scheduledByUserId` may confirm; only from `PENDING`; → `CONFIRMED`.
- `cancel`: either participant; only from `PENDING`/`CONFIRMED` (409 if already `COMPLETED`/`CANCELLED`); → `CANCELLED`.
- `reschedule`: either participant; only from `PENDING`/`CONFIRMED`; updates date/time, sets `scheduledByUserId` to the **rescheduler** (so the *other* party must confirm the new time), resets status to `PENDING`.
- `complete`: either participant; only from `CONFIRMED` (409 otherwise); calls `creditService.settle(teacherUserId, learnerUserId, sessionId)` (which re-checks affordability at this moment — a learner who spent credits elsewhere between booking and now can still be blocked, 402) then → `COMPLETED`.
- All lookups scope to "session exists AND caller is teacher or learner" as a single check — a non-participant gets 404, not 403 (no information leak about session existence).
- *Simplification, documented:* either participant alone can call `complete` — no dual confirmation. Upgrade path: require both parties to acknowledge before settling, if trust becomes a concern.

- [ ] **Step 1: Write the failing service test**

`backend/src/test/java/com/skillswap/service/SessionServiceTest.java`:
```java
package com.skillswap.service;

import com.skillswap.dto.CreateSessionRequest;
import com.skillswap.dto.RescheduleSessionRequest;
import com.skillswap.dto.SessionDto;
import com.skillswap.entity.Match;
import com.skillswap.entity.MatchStatus;
import com.skillswap.entity.Session;
import com.skillswap.entity.SessionStatus;
import com.skillswap.repository.MatchRepository;
import com.skillswap.repository.SessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SessionServiceTest {

    private final SessionRepository sessionRepo = mock(SessionRepository.class);
    private final MatchRepository matchRepo = mock(MatchRepository.class);
    private final CreditService creditService = mock(CreditService.class);
    private final SessionService service = new SessionService(sessionRepo, matchRepo, creditService);

    private Match acceptedMatch(Long a, Long b) {
        Match m = new Match();
        m.setUserAId(a);
        m.setUserBId(b);
        m.setStatus(MatchStatus.ACCEPTED);
        return m;
    }

    private CreateSessionRequest req(Long matchId, Long teacherId) {
        return new CreateSessionRequest(matchId, teacherId, LocalDate.of(2026, 8, 1),
                LocalTime.of(10, 0), LocalTime.of(11, 0), "ONLINE", "https://meet.example/abc");
    }

    @Test
    void createRejectsWhenMatchNotFound() {
        when(matchRepo.findById(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(10L, req(1L, 10L)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createRejectsWhenRequesterNotParticipant() {
        when(matchRepo.findById(1L)).thenReturn(Optional.of(acceptedMatch(20L, 30L)));
        assertThatThrownBy(() -> service.create(10L, req(1L, 20L)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createRejectsWhenMatchNotAccepted() {
        Match m = acceptedMatch(10L, 20L);
        m.setStatus(MatchStatus.PENDING);
        when(matchRepo.findById(1L)).thenReturn(Optional.of(m));
        assertThatThrownBy(() -> service.create(10L, req(1L, 10L)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createRejectsWhenTeacherNotParticipant() {
        when(matchRepo.findById(1L)).thenReturn(Optional.of(acceptedMatch(10L, 20L)));
        assertThatThrownBy(() -> service.create(10L, req(1L, 999L)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createRejectsWhenLearnerCannotAfford() {
        when(matchRepo.findById(1L)).thenReturn(Optional.of(acceptedMatch(10L, 20L)));
        when(creditService.canAfford(20L)).thenReturn(false); // 10 = teacher, 20 = learner
        assertThatThrownBy(() -> service.create(10L, req(1L, 10L)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void createPersistsPendingSessionWithCorrectRoles() {
        when(matchRepo.findById(1L)).thenReturn(Optional.of(acceptedMatch(10L, 20L)));
        when(creditService.canAfford(20L)).thenReturn(true);
        when(sessionRepo.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        SessionDto dto = service.create(10L, req(1L, 10L));

        assertThat(dto.teacherUserId()).isEqualTo(10L);
        assertThat(dto.learnerUserId()).isEqualTo(20L);
        assertThat(dto.scheduledByUserId()).isEqualTo(10L);
        assertThat(dto.status()).isEqualTo("PENDING");
    }

    @Test
    void confirmRejectsWhenCalledByScheduler() {
        Session s = new Session();
        s.setTeacherUserId(10L); s.setLearnerUserId(20L); s.setScheduledByUserId(10L);
        s.setStatus(SessionStatus.PENDING);
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.confirm(10L, 5L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void confirmSucceedsForOtherParticipant() {
        Session s = new Session();
        s.setTeacherUserId(10L); s.setLearnerUserId(20L); s.setScheduledByUserId(10L);
        s.setStatus(SessionStatus.PENDING);
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(s));
        when(sessionRepo.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        SessionDto dto = service.confirm(20L, 5L);
        assertThat(dto.status()).isEqualTo("CONFIRMED");
    }

    @Test
    void cancelRejectsWhenAlreadyFinalized() {
        Session s = new Session();
        s.setTeacherUserId(10L); s.setLearnerUserId(20L); s.setScheduledByUserId(10L);
        s.setStatus(SessionStatus.CANCELLED);
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.cancel(10L, 5L)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rescheduleResetsStatusAndReassignsScheduler() {
        Session s = new Session();
        s.setTeacherUserId(10L); s.setLearnerUserId(20L); s.setScheduledByUserId(10L);
        s.setStatus(SessionStatus.CONFIRMED);
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(s));
        when(sessionRepo.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        RescheduleSessionRequest req = new RescheduleSessionRequest(
                LocalDate.of(2026, 8, 2), LocalTime.of(9, 0), LocalTime.of(10, 0));
        SessionDto dto = service.reschedule(20L, 5L, req);

        assertThat(dto.status()).isEqualTo("PENDING");
        assertThat(dto.scheduledByUserId()).isEqualTo(20L);
        assertThat(dto.sessionDate()).isEqualTo(LocalDate.of(2026, 8, 2));
    }

    @Test
    void completeRejectsWhenNotConfirmed() {
        Session s = new Session();
        s.setTeacherUserId(10L); s.setLearnerUserId(20L); s.setScheduledByUserId(10L);
        s.setStatus(SessionStatus.PENDING);
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(s));
        assertThatThrownBy(() -> service.complete(10L, 5L)).isInstanceOf(ResponseStatusException.class);
        verify(creditService, never()).settle(any(), any(), any());
    }

    @Test
    void completeSettlesCreditsOnSuccess() {
        Session s = new Session();
        s.setTeacherUserId(10L); s.setLearnerUserId(20L); s.setScheduledByUserId(10L);
        s.setStatus(SessionStatus.CONFIRMED);
        when(sessionRepo.findById(5L)).thenReturn(Optional.of(s));
        when(sessionRepo.save(any(Session.class))).thenAnswer(i -> i.getArgument(0));

        SessionDto dto = service.complete(20L, 5L);

        verify(creditService).settle(10L, 20L, 5L);
        assertThat(dto.status()).isEqualTo("COMPLETED");
    }

    @Test
    void mySessionsFiltersByStatus() {
        Session pending = new Session();
        pending.setTeacherUserId(10L); pending.setLearnerUserId(20L);
        pending.setStatus(SessionStatus.PENDING); pending.setSessionDate(LocalDate.of(2026, 8, 1));
        pending.setStartTime(LocalTime.NOON);

        Session cancelled = new Session();
        cancelled.setTeacherUserId(10L); cancelled.setLearnerUserId(20L);
        cancelled.setStatus(SessionStatus.CANCELLED); cancelled.setSessionDate(LocalDate.of(2026, 7, 1));
        cancelled.setStartTime(LocalTime.NOON);

        when(sessionRepo.findByTeacherUserIdOrLearnerUserId(10L, 10L)).thenReturn(List.of(pending, cancelled));

        assertThat(service.mySessions(10L, "upcoming")).hasSize(1);
        assertThat(service.mySessions(10L, "cancelled")).hasSize(1);
        assertThat(service.mySessions(10L, "past")).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests SessionServiceTest`
Expected: FAIL — `SessionService`, DTOs do not exist.

- [ ] **Step 3: Write the DTOs**

`backend/src/main/java/com/skillswap/dto/CreateSessionRequest.java`:
```java
package com.skillswap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record CreateSessionRequest(
        @NotNull Long matchId,
        @NotNull Long teacherUserId,
        @NotNull LocalDate sessionDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime,
        @NotBlank String mode,
        String locationOrLink) {}
```

`backend/src/main/java/com/skillswap/dto/RescheduleSessionRequest.java`:
```java
package com.skillswap.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record RescheduleSessionRequest(
        @NotNull LocalDate sessionDate,
        @NotNull LocalTime startTime,
        @NotNull LocalTime endTime) {}
```

`backend/src/main/java/com/skillswap/dto/SessionDto.java`:
```java
package com.skillswap.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record SessionDto(Long id, Long matchId, Long teacherUserId, Long learnerUserId, Long scheduledByUserId,
                         LocalDate sessionDate, LocalTime startTime, LocalTime endTime,
                         String mode, String locationOrLink, String status, LocalDateTime createdDate) {}
```

- [ ] **Step 4: Write SessionService**

`backend/src/main/java/com/skillswap/service/SessionService.java`:
```java
package com.skillswap.service;

import com.skillswap.dto.CreateSessionRequest;
import com.skillswap.dto.RescheduleSessionRequest;
import com.skillswap.dto.SessionDto;
import com.skillswap.entity.Match;
import com.skillswap.entity.MatchStatus;
import com.skillswap.entity.Session;
import com.skillswap.entity.SessionMode;
import com.skillswap.entity.SessionStatus;
import com.skillswap.repository.MatchRepository;
import com.skillswap.repository.SessionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final MatchRepository matchRepository;
    private final CreditService creditService;

    public SessionService(SessionRepository sessionRepository, MatchRepository matchRepository,
                          CreditService creditService) {
        this.sessionRepository = sessionRepository;
        this.matchRepository = matchRepository;
        this.creditService = creditService;
    }

    public SessionDto create(Long meId, CreateSessionRequest req) {
        Match match = matchRepository.findById(req.matchId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found"));
        if (!match.getUserAId().equals(meId) && !match.getUserBId().equals(meId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Match not found");
        }
        if (match.getStatus() != MatchStatus.ACCEPTED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Match is not accepted");
        }
        Long other = match.getUserAId().equals(meId) ? match.getUserBId() : match.getUserAId();
        if (!req.teacherUserId().equals(meId) && !req.teacherUserId().equals(other)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "teacherUserId must be a match participant");
        }
        Long learnerUserId = req.teacherUserId().equals(meId) ? other : meId;

        if (!creditService.canAfford(learnerUserId)) {
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED, "Learner has insufficient credits");
        }

        Session s = new Session();
        s.setMatchId(match.getId());
        s.setTeacherUserId(req.teacherUserId());
        s.setLearnerUserId(learnerUserId);
        s.setScheduledByUserId(meId);
        s.setSessionDate(req.sessionDate());
        s.setStartTime(req.startTime());
        s.setEndTime(req.endTime());
        s.setMode(parseMode(req.mode()));
        s.setLocationOrLink(req.locationOrLink());
        s.setStatus(SessionStatus.PENDING);
        return toDto(sessionRepository.save(s));
    }

    public SessionDto confirm(Long meId, Long sessionId) {
        Session s = requireParticipant(meId, sessionId);
        if (s.getScheduledByUserId().equals(meId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only the other participant can confirm");
        }
        if (s.getStatus() != SessionStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session is not pending");
        }
        s.setStatus(SessionStatus.CONFIRMED);
        return toDto(sessionRepository.save(s));
    }

    public SessionDto cancel(Long meId, Long sessionId) {
        Session s = requireParticipant(meId, sessionId);
        if (s.getStatus() == SessionStatus.COMPLETED || s.getStatus() == SessionStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Session already finalized");
        }
        s.setStatus(SessionStatus.CANCELLED);
        return toDto(sessionRepository.save(s));
    }

    public SessionDto reschedule(Long meId, Long sessionId, RescheduleSessionRequest req) {
        Session s = requireParticipant(meId, sessionId);
        if (s.getStatus() != SessionStatus.PENDING && s.getStatus() != SessionStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only pending or confirmed sessions can be rescheduled");
        }
        s.setSessionDate(req.sessionDate());
        s.setStartTime(req.startTime());
        s.setEndTime(req.endTime());
        s.setScheduledByUserId(meId); // rescheduler proposes; the other party must reconfirm
        s.setStatus(SessionStatus.PENDING);
        return toDto(sessionRepository.save(s));
    }

    public SessionDto complete(Long meId, Long sessionId) {
        Session s = requireParticipant(meId, sessionId);
        if (s.getStatus() != SessionStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only confirmed sessions can be completed");
        }
        creditService.settle(s.getTeacherUserId(), s.getLearnerUserId(), s.getId());
        s.setStatus(SessionStatus.COMPLETED);
        return toDto(sessionRepository.save(s));
    }

    public List<SessionDto> mySessions(Long meId, String filter) {
        List<Session> all = sessionRepository.findByTeacherUserIdOrLearnerUserId(meId, meId);
        List<Session> filtered = switch (filter == null ? "all" : filter) {
            case "upcoming" -> all.stream()
                    .filter(s -> s.getStatus() == SessionStatus.PENDING || s.getStatus() == SessionStatus.CONFIRMED)
                    .toList();
            case "past" -> all.stream().filter(s -> s.getStatus() == SessionStatus.COMPLETED).toList();
            case "cancelled" -> all.stream().filter(s -> s.getStatus() == SessionStatus.CANCELLED).toList();
            default -> all;
        };
        return filtered.stream()
                .sorted(Comparator.comparing(Session::getSessionDate).thenComparing(Session::getStartTime))
                .map(this::toDto)
                .toList();
    }

    private Session requireParticipant(Long meId, Long sessionId) {
        Session s = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        if (!s.getTeacherUserId().equals(meId) && !s.getLearnerUserId().equals(meId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found");
        }
        return s;
    }

    private SessionMode parseMode(String raw) {
        try {
            return SessionMode.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "mode must be ONLINE or OFFLINE");
        }
    }

    private SessionDto toDto(Session s) {
        return new SessionDto(s.getId(), s.getMatchId(), s.getTeacherUserId(), s.getLearnerUserId(),
                s.getScheduledByUserId(), s.getSessionDate(), s.getStartTime(), s.getEndTime(),
                s.getMode() == null ? null : s.getMode().name(), s.getLocationOrLink(),
                s.getStatus().name(), s.getCreatedDate());
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests SessionServiceTest`
Expected: PASS — all thirteen cases green.

- [ ] **Step 6: Write SessionController**

`backend/src/main/java/com/skillswap/controller/SessionController.java`:
```java
package com.skillswap.controller;

import com.skillswap.dto.CreateSessionRequest;
import com.skillswap.dto.RescheduleSessionRequest;
import com.skillswap.dto.SessionDto;
import com.skillswap.service.CurrentUser;
import com.skillswap.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final CurrentUser currentUser;

    public SessionController(SessionService sessionService, CurrentUser currentUser) {
        this.sessionService = sessionService;
        this.currentUser = currentUser;
    }

    @PostMapping
    public ResponseEntity<SessionDto> create(@Valid @RequestBody CreateSessionRequest req) {
        SessionDto dto = sessionService.create(currentUser.require().getId(), req);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping
    public List<SessionDto> mySessions(@RequestParam(required = false) String filter) {
        return sessionService.mySessions(currentUser.require().getId(), filter);
    }

    @PutMapping("/{id}/confirm")
    public SessionDto confirm(@PathVariable Long id) {
        return sessionService.confirm(currentUser.require().getId(), id);
    }

    @PutMapping("/{id}/cancel")
    public SessionDto cancel(@PathVariable Long id) {
        return sessionService.cancel(currentUser.require().getId(), id);
    }

    @PutMapping("/{id}/reschedule")
    public SessionDto reschedule(@PathVariable Long id, @Valid @RequestBody RescheduleSessionRequest req) {
        return sessionService.reschedule(currentUser.require().getId(), id, req);
    }

    @PutMapping("/{id}/complete")
    public SessionDto complete(@PathVariable Long id) {
        return sessionService.complete(currentUser.require().getId(), id);
    }
}
```

- [ ] **Step 7: Write the end-to-end session + credit flow integration test**

`backend/src/test/java/com/skillswap/controller/SessionFlowTest.java`:
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
class SessionFlowTest {

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

    private Long acceptedMatch(Long userAId, Long userBId) {
        jdbc.update("INSERT INTO matches(user_a_id,user_b_id,status) VALUES (?,?,?)",
                userAId, userBId, "ACCEPTED");
        return jdbc.queryForObject(
                "SELECT id FROM matches WHERE user_a_id = ? AND user_b_id = ?", Long.class, userAId, userBId);
    }

    private Long createSession(String token, Long matchId, Long teacherId) throws Exception {
        String body = json.writeValueAsString(Map.of(
                "matchId", matchId, "teacherUserId", teacherId,
                "sessionDate", "2026-08-01", "startTime", "10:00:00", "endTime", "11:00:00",
                "mode", "ONLINE", "locationOrLink", "https://meet.example/abc"));
        String res = mvc.perform(post("/api/sessions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(res, "$.id")).longValue();
    }

    @Test
    void bookConfirmCompleteSettlesCredits() throws Exception {
        String teacherToken = register("teacher-flow@example.com");
        String learnerToken = register("learner-flow@example.com");
        Long teacherId = meId(teacherToken);
        Long learnerId = meId(learnerToken);
        Long matchId = acceptedMatch(learnerId, teacherId);

        Long sessionId = createSession(learnerToken, matchId, teacherId);

        mvc.perform(put("/api/sessions/{id}/confirm", sessionId).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mvc.perform(put("/api/sessions/{id}/complete", sessionId).header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mvc.perform(get("/api/me/credits").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCredits").value(11))
                .andExpect(jsonPath("$.creditsEarned").value(1));

        mvc.perform(get("/api/me/credits").header("Authorization", "Bearer " + learnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCredits").value(9))
                .andExpect(jsonPath("$.creditsSpent").value(1));

        mvc.perform(get("/api/me/credits/transactions").header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionType").value("EARNED"));
    }

    @Test
    void bookingBlockedWhenLearnerHasNoCredits() throws Exception {
        String teacherToken = register("teacher-poor@example.com");
        String learnerToken = register("learner-poor@example.com");
        Long teacherId = meId(teacherToken);
        Long learnerId = meId(learnerToken);
        Long matchId = acceptedMatch(learnerId, teacherId);

        jdbc.update("UPDATE skill_credit SET total_credits = 0 WHERE user_id = ?", learnerId);

        String body = json.writeValueAsString(Map.of(
                "matchId", matchId, "teacherUserId", teacherId,
                "sessionDate", "2026-08-01", "startTime", "10:00:00", "endTime", "11:00:00",
                "mode", "ONLINE"));
        mvc.perform(post("/api/sessions").header("Authorization", "Bearer " + learnerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isPaymentRequired());
    }

    @Test
    void sessionsRequireAuth() throws Exception {
        mvc.perform(get("/api/sessions")).andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 8: Run the full suite**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test`
Expected: PASS — all prior tests (46 through Task 2) plus `SessionServiceTest` (13) and `SessionFlowTest` (3).

- [ ] **Step 9: Commit**

```bash
git add backend/src/main/java/com/skillswap/dto/CreateSessionRequest.java \
        backend/src/main/java/com/skillswap/dto/RescheduleSessionRequest.java \
        backend/src/main/java/com/skillswap/dto/SessionDto.java \
        backend/src/main/java/com/skillswap/service/SessionService.java \
        backend/src/main/java/com/skillswap/controller/SessionController.java \
        backend/src/test/java/com/skillswap/service/SessionServiceTest.java \
        backend/src/test/java/com/skillswap/controller/SessionFlowTest.java
git commit -m "feat: add session booking lifecycle with credit settlement on completion"
```

---

## Self-Review

**Spec coverage (Plan 3 slice):**
- Create/manage sessions between matched users, view upcoming/past/cancelled, cancel/reschedule → Task 3. ✅
- Session statuses Pending/Confirmed/Completed/Cancelled → Tasks 1, 3. ✅
- Video/meeting = pasted link in `locationOrLink` (per spec's documented simplification, no SDK) → Task 3 (`CreateSessionRequest.locationOrLink`). ✅
- Credits: starter balance (Plan 1), teaching earns/learning spends, ledger, booking blocked without sufficient credits → Tasks 2–3. ✅
- Credit balance + transaction history endpoints → Task 2. ✅
- Deferred to later plans (correctly out of Plan 3): reviews/ratings/badges (Plan 4), forums (Plan 5), admin/reports (Plan 6), automatic reminders (out of scope per spec's cut list).

**Placeholder scan:** Found and fixed one — Task 2 Step 2's test draft contained a stray `ArgumentCaptor:` label; Step 3a supplies the corrected version to actually create the file with. No other TBD/TODO; every other step has complete code. ✅

**Type consistency:** `CreditService(SkillCreditRepository, CreditTransactionRepository)` constructor (Task 2) matches `SessionServiceTest`'s mock and `SessionService`'s constructor injection (Task 3). `creditService.canAfford(Long)` / `creditService.settle(Long,Long,Long)` signatures identical across Task 2's implementation, Task 2's test, and Task 3's `SessionService` usage + test mocks. `SessionRepository.findByTeacherUserIdOrLearnerUserId(Long,Long)` identical in Task 1's repository, Task 3's service, and both tests. `SessionDto` field order/names consistent between `SessionService.toDto` and every assertion across `SessionServiceTest`/`SessionFlowTest`. ✅

**Scope check:** Single cohesive slice (booking + credits are tightly coupled per the spec — completion is the one event that touches both). Appropriately sized for one plan; not decomposed further.

**Deliberate simplifications (flagged for the record, not hidden):**
- Either participant alone can `complete` a session (no dual confirmation). `ponytail: single-party complete, add mutual-ack gate if abuse becomes a concern.`
- `canAfford` is checked at booking (reject early, no mutation) and re-checked authoritatively inside `settle` at completion (the only point of actual mutation) — a learner can still get 402 at completion if they drained credits on other sessions between booking and now. This is intentional, not a bug: no refund logic is needed anywhere since credits never move before completion.
- No overlap/double-booking validation (a user can have two sessions at the same time slot). Out of scope per spec; not requested. `ponytail: add a time-overlap check per user if double-booking becomes a real complaint.`
