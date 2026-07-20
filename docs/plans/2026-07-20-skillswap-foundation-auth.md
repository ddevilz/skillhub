# SkillSwap Hub — Plan 1: Foundation + Auth

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A runnable full-stack skeleton where a user can register and log in with JWT, receiving a starter credit account.

**Architecture:** Spring Boot monolith (layered controller→service→repository→MySQL) with stateless JWT auth via a servlet filter. Flyway owns the schema. React + Vite SPA with an auth context that stores the JWT and calls the API through an Axios client. Business logic (register/login) lives in a service tested with Mockito; the HTTP layer is tested with MockMvc against H2.

**Tech Stack:** Java 17, Spring Boot 3.2.5, Gradle, Spring Security, Spring Data JPA, Flyway, MySQL 8 (H2 for tests), jjwt 0.12.5, React 18, Vite, React Router, Axios, Vitest + React Testing Library.

## Global Constraints

- Backend base package: `com.skillswap`. Java 17. Spring Boot **3.2.5**.
- Build tool: **Gradle** (Groovy DSL). Never generate Maven files.
- Schema is owned by **Flyway** (`src/main/resources/db/migration/V*.sql`). Migrations are append-only; never edit a committed migration.
- API prefix: `/api`. Auth endpoints under `/api/auth/**` are public; everything else requires a valid JWT. Admin-only routes (later plans) require role `ADMIN`.
- DTOs at the HTTP boundary — never serialize JPA entities directly.
- Passwords hashed with BCrypt. JWT is HMAC-SHA256; secret ≥32 bytes from config.
- Git author is **Devashish Jadhav <jadhavom24@gmail.com>**. **Never** add a `Co-Authored-By` line or any AI attribution to commits.
- Conventional commit messages. Commit at the end of every task.

---

### Task 1: Backend scaffold + Flyway schema (users, skill_credit)

**Files:**
- Create: `backend/build.gradle`
- Create: `backend/settings.gradle`
- Create: `backend/gradle/wrapper/gradle-wrapper.properties`
- Create: `backend/src/main/java/com/skillswap/SkillSwapApplication.java`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/test/resources/application-test.yml`
- Create: `backend/src/main/resources/db/migration/V1__init.sql`
- Test: `backend/src/test/java/com/skillswap/SkillSwapApplicationTests.java`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: a bootable Spring Boot app on port 8080; MySQL schema `skillswap` with tables `users` and `skill_credit`; a `test` profile backed by H2 with Flyway disabled and Hibernate `create-drop`.

- [ ] **Step 1: Create the Gradle build files**

`backend/settings.gradle`:
```groovy
rootProject.name = 'skillswap-hub'
```

`backend/build.gradle`:
```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '3.2.5'
    id 'io.spring.dependency-management' version '1.1.4'
}

group = 'com.skillswap'
version = '0.0.1'
java { sourceCompatibility = '17' }

repositories { mavenCentral() }

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-web'
    implementation 'org.springframework.boot:spring-boot-starter-security'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    implementation 'org.flywaydb:flyway-core'
    implementation 'org.flywaydb:flyway-mysql'
    implementation 'io.jsonwebtoken:jjwt-api:0.12.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-impl:0.12.5'
    runtimeOnly 'io.jsonwebtoken:jjwt-jackson:0.12.5'
    runtimeOnly 'com.mysql:mysql-connector-j'

    testImplementation 'org.springframework.boot:spring-boot-starter-test'
    testImplementation 'org.springframework.security:spring-security-test'
    testRuntimeOnly 'com.h2database:h2'
}

tasks.named('test') { useJUnitPlatform() }
```

`backend/gradle/wrapper/gradle-wrapper.properties`:
```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.7-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

Then generate the wrapper scripts once: `cd backend && gradle wrapper` (requires a local Gradle; if absent, run `gradle wrapper --gradle-version 8.7`). This creates `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar`.

- [ ] **Step 2: Create the application entrypoint and config**

`backend/src/main/java/com/skillswap/SkillSwapApplication.java`:
```java
package com.skillswap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SkillSwapApplication {
    public static void main(String[] args) {
        SpringApplication.run(SkillSwapApplication.class, args);
    }
}
```

`backend/src/main/resources/application.yml`:
```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/skillswap?createDatabaseIfNotExist=true&serverTimezone=UTC
    username: root
    password: root
  jpa:
    hibernate:
      ddl-auto: none          # Flyway owns the schema
    open-in-view: false
    properties:
      hibernate.format_sql: true
  flyway:
    enabled: true
jwt:
  secret: dev-secret-change-me-please-32-bytes-minimum-000
  expiration-ms: 86400000     # 24h
server:
  port: 8080
```

`backend/src/test/resources/application-test.yml`:
```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop   # Hibernate builds the test schema from entities
    database-platform: org.hibernate.dialect.H2Dialect
  flyway:
    enabled: false
jwt:
  secret: test-secret-test-secret-test-secret-32bytes
  expiration-ms: 3600000
```

- [ ] **Step 3: Write the Flyway migration**

`backend/src/main/resources/db/migration/V1__init.sql`:
```sql
CREATE TABLE users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    city          VARCHAR(50),
    about         VARCHAR(255),
    profile_image VARCHAR(255),
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_date  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE skill_credit (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id        BIGINT NOT NULL UNIQUE,
    total_credits  INT    NOT NULL DEFAULT 10,
    credits_earned INT    NOT NULL DEFAULT 0,
    credits_spent  INT    NOT NULL DEFAULT 0,
    last_updated   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_credit_user FOREIGN KEY (user_id) REFERENCES users(id)
);
```

- [ ] **Step 4: Write the context-load smoke test**

`backend/src/test/java/com/skillswap/SkillSwapApplicationTests.java`:
```java
package com.skillswap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class SkillSwapApplicationTests {
    @Test
    void contextLoads() { }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && ./gradlew test --tests SkillSwapApplicationTests`
Expected: PASS — context loads against H2.

- [ ] **Step 6: Commit**

```bash
git add backend/build.gradle backend/settings.gradle backend/gradlew* backend/gradle \
        backend/src/main/java/com/skillswap/SkillSwapApplication.java \
        backend/src/main/resources/application.yml \
        backend/src/test/resources/application-test.yml \
        backend/src/main/resources/db/migration/V1__init.sql \
        backend/src/test/java/com/skillswap/SkillSwapApplicationTests.java
git commit -m "chore: scaffold Spring Boot backend with Flyway schema"
```

---

### Task 2: User + SkillCredit entities and repositories

**Files:**
- Create: `backend/src/main/java/com/skillswap/entity/Role.java`
- Create: `backend/src/main/java/com/skillswap/entity/User.java`
- Create: `backend/src/main/java/com/skillswap/entity/SkillCredit.java`
- Create: `backend/src/main/java/com/skillswap/repository/UserRepository.java`
- Create: `backend/src/main/java/com/skillswap/repository/SkillCreditRepository.java`
- Test: `backend/src/test/java/com/skillswap/repository/UserRepositoryTest.java`

**Interfaces:**
- Consumes: tables `users`, `skill_credit` from Task 1.
- Produces:
  - `User` entity with getters/setters; fields `id:Long, fullName:String, email:String, passwordHash:String, city:String, about:String, profileImage:String, role:Role, active:boolean, createdDate:LocalDateTime`.
  - `SkillCredit` entity; fields `id:Long, userId:Long, totalCredits:int, creditsEarned:int, creditsSpent:int, lastUpdated:LocalDateTime`.
  - `Role` enum `{ USER, ADMIN }`.
  - `UserRepository extends JpaRepository<User,Long>` with `Optional<User> findByEmail(String)`, `boolean existsByEmail(String)`.
  - `SkillCreditRepository extends JpaRepository<SkillCredit,Long>` with `Optional<SkillCredit> findByUserId(Long)`.

- [ ] **Step 1: Write the failing repository test**

`backend/src/test/java/com/skillswap/repository/UserRepositoryTest.java`:
```java
package com.skillswap.repository;

import com.skillswap.entity.Role;
import com.skillswap.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired UserRepository userRepository;

    @Test
    void savesAndFindsByEmail() {
        User u = new User();
        u.setFullName("Deva");
        u.setEmail("deva@example.com");
        u.setPasswordHash("hash");
        u.setRole(Role.USER);
        u.setActive(true);
        userRepository.save(u);

        assertThat(userRepository.findByEmail("deva@example.com")).isPresent();
        assertThat(userRepository.existsByEmail("deva@example.com")).isTrue();
        assertThat(userRepository.existsByEmail("nope@example.com")).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests UserRepositoryTest`
Expected: FAIL — `User`, `Role`, `UserRepository` do not exist (compilation error).

- [ ] **Step 3: Write the entities, enum, and repositories**

`backend/src/main/java/com/skillswap/entity/Role.java`:
```java
package com.skillswap.entity;

public enum Role { USER, ADMIN }
```

`backend/src/main/java/com/skillswap/entity/User.java`:
```java
package com.skillswap.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    private String city;
    private String about;
    private String profileImage;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role = Role.USER;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    void onCreate() { if (createdDate == null) createdDate = LocalDateTime.now(); }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public void setFullName(String v) { this.fullName = v; }
    public String getEmail() { return email; }
    public void setEmail(String v) { this.email = v; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String v) { this.passwordHash = v; }
    public String getCity() { return city; }
    public void setCity(String v) { this.city = v; }
    public String getAbout() { return about; }
    public void setAbout(String v) { this.about = v; }
    public String getProfileImage() { return profileImage; }
    public void setProfileImage(String v) { this.profileImage = v; }
    public Role getRole() { return role; }
    public void setRole(Role v) { this.role = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
    public LocalDateTime getCreatedDate() { return createdDate; }
}
```

`backend/src/main/java/com/skillswap/entity/SkillCredit.java`:
```java
package com.skillswap.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "skill_credit")
public class SkillCredit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private int totalCredits = 10;

    @Column(nullable = false)
    private int creditsEarned = 0;

    @Column(nullable = false)
    private int creditsSpent = 0;

    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    public SkillCredit() {}
    public SkillCredit(Long userId) { this.userId = userId; }

    @PrePersist @PreUpdate
    void touch() { this.lastUpdated = LocalDateTime.now(); }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long v) { this.userId = v; }
    public int getTotalCredits() { return totalCredits; }
    public void setTotalCredits(int v) { this.totalCredits = v; }
    public int getCreditsEarned() { return creditsEarned; }
    public void setCreditsEarned(int v) { this.creditsEarned = v; }
    public int getCreditsSpent() { return creditsSpent; }
    public void setCreditsSpent(int v) { this.creditsSpent = v; }
    public LocalDateTime getLastUpdated() { return lastUpdated; }
}
```

`backend/src/main/java/com/skillswap/repository/UserRepository.java`:
```java
package com.skillswap.repository;

import com.skillswap.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

`backend/src/main/java/com/skillswap/repository/SkillCreditRepository.java`:
```java
package com.skillswap.repository;

import com.skillswap.entity.SkillCredit;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SkillCreditRepository extends JpaRepository<SkillCredit, Long> {
    Optional<SkillCredit> findByUserId(Long userId);
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests UserRepositoryTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/skillswap/entity backend/src/main/java/com/skillswap/repository \
        backend/src/test/java/com/skillswap/repository/UserRepositoryTest.java
git commit -m "feat: add User and SkillCredit entities with repositories"
```

---

### Task 3: JWT service

**Files:**
- Create: `backend/src/main/java/com/skillswap/security/JwtService.java`
- Test: `backend/src/test/java/com/skillswap/security/JwtServiceTest.java`

**Interfaces:**
- Consumes: `jwt.secret` and `jwt.expiration-ms` config.
- Produces: `JwtService` with `String generateToken(String email)`, `String extractEmail(String token)`, `boolean isValid(String token)`. Constructor: `JwtService(String secret, long expirationMs)` (values injected via `@Value`).

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/skillswap/security/JwtServiceTest.java`:
```java
package com.skillswap.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwt =
        new JwtService("unit-test-secret-unit-test-secret-32bytes!", 3600000L);

    @Test
    void roundTripsEmail() {
        String token = jwt.generateToken("deva@example.com");
        assertThat(jwt.extractEmail(token)).isEqualTo("deva@example.com");
        assertThat(jwt.isValid(token)).isTrue();
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwt.generateToken("deva@example.com");
        assertThat(jwt.isValid(token + "x")).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests JwtServiceTest`
Expected: FAIL — `JwtService` does not exist.

- [ ] **Step 3: Implement JwtService**

`backend/src/main/java/com/skillswap/security/JwtService.java`:
```java
package com.skillswap.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;

    public JwtService(@Value("${jwt.secret}") String secret,
                      @Value("${jwt.expiration-ms}") long expirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(String email) {
        Date now = new Date();
        return Jwts.builder()
                .subject(email)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }

    public boolean isValid(String token) {
        try {
            extractEmail(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests JwtServiceTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/skillswap/security/JwtService.java \
        backend/src/test/java/com/skillswap/security/JwtServiceTest.java
git commit -m "feat: add JWT generation and validation service"
```

---

### Task 4: Security config + JWT filter + UserDetailsService

**Files:**
- Create: `backend/src/main/java/com/skillswap/security/AppUserDetailsService.java`
- Create: `backend/src/main/java/com/skillswap/security/JwtAuthFilter.java`
- Create: `backend/src/main/java/com/skillswap/config/SecurityConfig.java`
- Test: (covered by the integration test in Task 6 — no isolated test here; this task is scaffolding for the auth endpoints and is validated when Task 6's 401/200 assertions pass.)

**Interfaces:**
- Consumes: `UserRepository` (Task 2), `JwtService` (Task 3).
- Produces:
  - `AppUserDetailsService implements UserDetailsService` — `loadUserByUsername(email)` returns a Spring `UserDetails` with authority `ROLE_<role>`; throws `UsernameNotFoundException` if absent or inactive.
  - `JwtAuthFilter extends OncePerRequestFilter` — reads `Authorization: Bearer`, validates via `JwtService`, sets the `SecurityContext`.
  - `SecurityConfig` — `SecurityFilterChain` bean (stateless, CSRF off, `/api/auth/**` permitted, all else authenticated, `JwtAuthFilter` before `UsernamePasswordAuthenticationFilter`) and a `PasswordEncoder` (`BCryptPasswordEncoder`) bean.

- [ ] **Step 1: Implement AppUserDetailsService**

`backend/src/main/java/com/skillswap/security/AppUserDetailsService.java`:
```java
package com.skillswap.security;

import com.skillswap.entity.User;
import com.skillswap.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public AppUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User u = userRepository.findByEmail(email)
                .filter(User::isActive)
                .orElseThrow(() -> new UsernameNotFoundException("No active user: " + email));
        return new org.springframework.security.core.userdetails.User(
                u.getEmail(), u.getPasswordHash(),
                List.of(new SimpleGrantedAuthority("ROLE_" + u.getRole().name())));
    }
}
```

- [ ] **Step 2: Implement JwtAuthFilter**

`backend/src/main/java/com/skillswap/security/JwtAuthFilter.java`:
```java
package com.skillswap.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AppUserDetailsService userDetailsService;

    public JwtAuthFilter(JwtService jwtService, AppUserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtService.isValid(token)
                    && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails details = userDetailsService.loadUserByUsername(jwtService.extractEmail(token));
                var auth = new UsernamePasswordAuthenticationToken(
                        details, null, details.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }
}
```

- [ ] **Step 3: Implement SecurityConfig**

`backend/src/main/java/com/skillswap/config/SecurityConfig.java`:
```java
package com.skillswap.config;

import com.skillswap.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

- [ ] **Step 4: Verify it compiles**

Run: `cd backend && ./gradlew compileJava`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/skillswap/security/AppUserDetailsService.java \
        backend/src/main/java/com/skillswap/security/JwtAuthFilter.java \
        backend/src/main/java/com/skillswap/config/SecurityConfig.java
git commit -m "feat: add stateless JWT security filter chain"
```

---

### Task 5: Auth service + DTOs (register/login logic)

**Files:**
- Create: `backend/src/main/java/com/skillswap/dto/RegisterRequest.java`
- Create: `backend/src/main/java/com/skillswap/dto/LoginRequest.java`
- Create: `backend/src/main/java/com/skillswap/dto/AuthResponse.java`
- Create: `backend/src/main/java/com/skillswap/service/EmailAlreadyUsedException.java`
- Create: `backend/src/main/java/com/skillswap/service/AuthService.java`
- Test: `backend/src/test/java/com/skillswap/service/AuthServiceTest.java`

**Interfaces:**
- Consumes: `UserRepository`, `SkillCreditRepository`, `PasswordEncoder`, `JwtService`.
- Produces:
  - `record RegisterRequest(String fullName, String email, String password, String city, String about)` with Bean Validation annotations.
  - `record LoginRequest(String email, String password)`.
  - `record AuthResponse(String token, String fullName, String email, String role)`.
  - `EmailAlreadyUsedException extends RuntimeException`.
  - `AuthService` with `AuthResponse register(RegisterRequest)` and `AuthResponse login(LoginRequest)`. `register` creates the user (role USER, active) + a `SkillCredit` row with 10 credits, then returns a token; duplicate email throws `EmailAlreadyUsedException`. `login` throws `org.springframework.security.authentication.BadCredentialsException` on unknown email or wrong password.

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/skillswap/service/AuthServiceTest.java`:
```java
package com.skillswap.service;

import com.skillswap.dto.AuthResponse;
import com.skillswap.dto.LoginRequest;
import com.skillswap.dto.RegisterRequest;
import com.skillswap.entity.Role;
import com.skillswap.entity.SkillCredit;
import com.skillswap.entity.User;
import com.skillswap.repository.SkillCreditRepository;
import com.skillswap.repository.UserRepository;
import com.skillswap.security.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private final UserRepository userRepo = mock(UserRepository.class);
    private final SkillCreditRepository creditRepo = mock(SkillCreditRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final JwtService jwt = mock(JwtService.class);
    private final AuthService service = new AuthService(userRepo, creditRepo, encoder, jwt);

    private RegisterRequest req() {
        return new RegisterRequest("Deva", "deva@example.com", "password1", null, null);
    }

    @Test
    void registerCreatesUserWithTenCredits() {
        when(userRepo.existsByEmail("deva@example.com")).thenReturn(false);
        when(encoder.encode("password1")).thenReturn("hashed");
        when(userRepo.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            // simulate DB id assignment
            try { var f = User.class.getDeclaredField("id"); f.setAccessible(true); f.set(u, 1L); }
            catch (Exception e) { throw new RuntimeException(e); }
            return u;
        });
        when(jwt.generateToken("deva@example.com")).thenReturn("tok");

        AuthResponse res = service.register(req());

        assertThat(res.token()).isEqualTo("tok");
        assertThat(res.role()).isEqualTo("USER");

        ArgumentCaptor<SkillCredit> credit = ArgumentCaptor.forClass(SkillCredit.class);
        verify(creditRepo).save(credit.capture());
        assertThat(credit.getValue().getTotalCredits()).isEqualTo(10);
        assertThat(credit.getValue().getUserId()).isEqualTo(1L);
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepo.existsByEmail("deva@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.register(req()))
                .isInstanceOf(EmailAlreadyUsedException.class);
        verify(userRepo, never()).save(any());
    }

    @Test
    void loginRejectsWrongPassword() {
        User u = new User();
        u.setEmail("deva@example.com");
        u.setPasswordHash("hashed");
        u.setRole(Role.USER);
        when(userRepo.findByEmail("deva@example.com")).thenReturn(Optional.of(u));
        when(encoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> service.login(new LoginRequest("deva@example.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests AuthServiceTest`
Expected: FAIL — `AuthService`, DTOs, `EmailAlreadyUsedException` do not exist.

- [ ] **Step 3: Write the DTOs and exception**

`backend/src/main/java/com/skillswap/dto/RegisterRequest.java`:
```java
package com.skillswap.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password,
        String city,
        String about) {}
```

`backend/src/main/java/com/skillswap/dto/LoginRequest.java`:
```java
package com.skillswap.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password) {}
```

`backend/src/main/java/com/skillswap/dto/AuthResponse.java`:
```java
package com.skillswap.dto;

public record AuthResponse(String token, String fullName, String email, String role) {}
```

`backend/src/main/java/com/skillswap/service/EmailAlreadyUsedException.java`:
```java
package com.skillswap.service;

public class EmailAlreadyUsedException extends RuntimeException {
    public EmailAlreadyUsedException(String message) { super(message); }
}
```

- [ ] **Step 4: Write AuthService**

`backend/src/main/java/com/skillswap/service/AuthService.java`:
```java
package com.skillswap.service;

import com.skillswap.dto.AuthResponse;
import com.skillswap.dto.LoginRequest;
import com.skillswap.dto.RegisterRequest;
import com.skillswap.entity.Role;
import com.skillswap.entity.SkillCredit;
import com.skillswap.entity.User;
import com.skillswap.repository.SkillCreditRepository;
import com.skillswap.repository.UserRepository;
import com.skillswap.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final SkillCreditRepository creditRepository;
    private final PasswordEncoder encoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, SkillCreditRepository creditRepository,
                       PasswordEncoder encoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.creditRepository = creditRepository;
        this.encoder = encoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            throw new EmailAlreadyUsedException("Email already registered: " + req.email());
        }
        User u = new User();
        u.setFullName(req.fullName());
        u.setEmail(req.email());
        u.setPasswordHash(encoder.encode(req.password()));
        u.setCity(req.city());
        u.setAbout(req.about());
        u.setRole(Role.USER);
        u.setActive(true);
        User saved = userRepository.save(u);

        creditRepository.save(new SkillCredit(saved.getId())); // defaults to 10 credits

        return toResponse(saved);
    }

    public AuthResponse login(LoginRequest req) {
        User u = userRepository.findByEmail(req.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        if (!encoder.matches(req.password(), u.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return toResponse(u);
    }

    private AuthResponse toResponse(User u) {
        return new AuthResponse(jwtService.generateToken(u.getEmail()),
                u.getFullName(), u.getEmail(), u.getRole().name());
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests AuthServiceTest`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/skillswap/dto \
        backend/src/main/java/com/skillswap/service \
        backend/src/test/java/com/skillswap/service/AuthServiceTest.java
git commit -m "feat: add auth service with register and login"
```

---

### Task 6: Auth + Me controllers, exception handler, integration tests

**Files:**
- Create: `backend/src/main/java/com/skillswap/controller/AuthController.java`
- Create: `backend/src/main/java/com/skillswap/controller/MeController.java`
- Create: `backend/src/main/java/com/skillswap/dto/UserProfile.java`
- Create: `backend/src/main/java/com/skillswap/config/GlobalExceptionHandler.java`
- Test: `backend/src/test/java/com/skillswap/controller/AuthControllerTest.java`

**Interfaces:**
- Consumes: `AuthService` (Task 5), `UserRepository` (Task 2), security chain (Task 4).
- Produces:
  - `POST /api/auth/register` → 201 + `AuthResponse`; duplicate → 409; invalid body → 400.
  - `POST /api/auth/login` → 200 + `AuthResponse`; bad creds → 401.
  - `GET /api/me` → 200 + `UserProfile` for the authenticated user; no/invalid token → 401.
  - `record UserProfile(Long id, String fullName, String email, String city, String about, String role)`.
  - `GlobalExceptionHandler` mapping validation→400, `EmailAlreadyUsedException`→409, `BadCredentialsException`→401, fallback→500, all as `{ "error": <status>, "message": <text> }`.

- [ ] **Step 1: Write the failing integration test**

`backend/src/test/java/com/skillswap/controller/AuthControllerTest.java`:
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
class AuthControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    private String body(Map<String, Object> m) throws Exception { return json.writeValueAsString(m); }

    @Test
    void registerThenLoginThenAccessMe() throws Exception {
        String reg = body(Map.of("fullName", "Deva", "email", "deva@example.com", "password", "password1"));

        String token = com.jayway.jsonpath.JsonPath.read(
            mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(reg))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.token").exists())
               .andReturn().getResponse().getContentAsString(), "$.token");

        mvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
           .andExpect(status().isOk())
           .andExpect(jsonPath("$.email").value("deva@example.com"));
    }

    @Test
    void duplicateRegistrationReturns409() throws Exception {
        String reg = body(Map.of("fullName", "Deva", "email", "dup@example.com", "password", "password1"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(reg))
           .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(reg))
           .andExpect(status().isConflict());
    }

    @Test
    void invalidBodyReturns400() throws Exception {
        String bad = body(Map.of("fullName", "", "email", "notanemail", "password", "x"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(bad))
           .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        String reg = body(Map.of("fullName", "Deva", "email", "wrongpw@example.com", "password", "password1"));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(reg))
           .andExpect(status().isCreated());
        String login = body(Map.of("email", "wrongpw@example.com", "password", "nope"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
           .andExpect(status().isUnauthorized());
    }

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./gradlew test --tests AuthControllerTest`
Expected: FAIL — controllers, `UserProfile`, exception handler do not exist.

- [ ] **Step 3: Write the UserProfile DTO**

`backend/src/main/java/com/skillswap/dto/UserProfile.java`:
```java
package com.skillswap.dto;

public record UserProfile(Long id, String fullName, String email,
                          String city, String about, String role) {}
```

- [ ] **Step 4: Write the controllers**

`backend/src/main/java/com/skillswap/controller/AuthController.java`:
```java
package com.skillswap.controller;

import com.skillswap.dto.AuthResponse;
import com.skillswap.dto.LoginRequest;
import com.skillswap.dto.RegisterRequest;
import com.skillswap.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req);
    }
}
```

`backend/src/main/java/com/skillswap/controller/MeController.java`:
```java
package com.skillswap.controller;

import com.skillswap.dto.UserProfile;
import com.skillswap.entity.User;
import com.skillswap.repository.UserRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRepository userRepository;

    public MeController(UserRepository userRepository) { this.userRepository = userRepository; }

    @GetMapping
    public UserProfile me(@AuthenticationPrincipal UserDetails principal) {
        User u = userRepository.findByEmail(principal.getUsername()).orElseThrow();
        return new UserProfile(u.getId(), u.getFullName(), u.getEmail(),
                u.getCity(), u.getAbout(), u.getRole().name());
    }
}
```

- [ ] **Step 5: Write the global exception handler**

`backend/src/main/java/com/skillswap/config/GlobalExceptionHandler.java`:
```java
package com.skillswap.config;

import com.skillswap.service.EmailAlreadyUsedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", status.value(), "message", message));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> onValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst().map(f -> f.getField() + ": " + f.getDefaultMessage())
                .orElse("Validation failed");
        return body(HttpStatus.BAD_REQUEST, msg);
    }

    @ExceptionHandler(EmailAlreadyUsedException.class)
    public ResponseEntity<Map<String, Object>> onDuplicate(EmailAlreadyUsedException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> onBadCreds(BadCredentialsException ex) {
        return body(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `cd backend && ./gradlew test --tests AuthControllerTest`
Expected: PASS — all five cases green.

- [ ] **Step 7: Run the full backend suite**

Run: `cd backend && ./gradlew test`
Expected: PASS — every test from Tasks 1–6.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/skillswap/controller \
        backend/src/main/java/com/skillswap/dto/UserProfile.java \
        backend/src/main/java/com/skillswap/config/GlobalExceptionHandler.java \
        backend/src/test/java/com/skillswap/controller/AuthControllerTest.java
git commit -m "feat: add auth and me endpoints with error handling"
```

---

### Task 7: Frontend scaffold — login, register, protected dashboard

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.js`
- Create: `frontend/index.html`
- Create: `frontend/src/main.jsx`
- Create: `frontend/src/App.jsx`
- Create: `frontend/src/api/client.js`
- Create: `frontend/src/auth/AuthContext.jsx`
- Create: `frontend/src/components/ProtectedRoute.jsx`
- Create: `frontend/src/pages/Login.jsx`
- Create: `frontend/src/pages/Register.jsx`
- Create: `frontend/src/pages/Dashboard.jsx`
- Test: `frontend/src/pages/Login.test.jsx`
- Create: `frontend/src/test/setup.js`

**Interfaces:**
- Consumes: backend `POST /api/auth/register`, `POST /api/auth/login`, `GET /api/me`.
- Produces:
  - `api` — Axios instance (`baseURL: '/api'`) that attaches `Authorization: Bearer <token>` from `localStorage`.
  - `AuthContext` exposing `{ user, token, login(email,password), register(payload), logout() }`; persists token in `localStorage`.
  - `ProtectedRoute` — redirects to `/login` if no token.
  - Routes: `/login`, `/register`, `/` (Dashboard, protected).

- [ ] **Step 1: Create package.json and Vite config**

`frontend/package.json`:
```json
{
  "name": "skillswap-frontend",
  "private": true,
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "vite build",
    "preview": "vite preview",
    "test": "vitest run"
  },
  "dependencies": {
    "axios": "^1.7.2",
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.24.0"
  },
  "devDependencies": {
    "@testing-library/jest-dom": "^6.4.6",
    "@testing-library/react": "^16.0.0",
    "@testing-library/user-event": "^14.5.2",
    "@vitejs/plugin-react": "^4.3.1",
    "jsdom": "^24.1.0",
    "vite": "^5.3.1",
    "vitest": "^1.6.0"
  }
}
```

`frontend/vite.config.js`:
```js
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { '/api': 'http://localhost:8080' },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.js',
  },
});
```

`frontend/src/test/setup.js`:
```js
import '@testing-library/jest-dom';
```

`frontend/index.html`:
```html
<!doctype html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>SkillSwap Hub</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.jsx"></script>
  </body>
</html>
```

- [ ] **Step 2: Create the API client and auth context**

`frontend/src/api/client.js`:
```js
import axios from 'axios';

const api = axios.create({ baseURL: '/api' });

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  return config;
});

export default api;
```

`frontend/src/auth/AuthContext.jsx`:
```jsx
import { createContext, useContext, useState } from 'react';
import api from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem('token'));
  const [user, setUser] = useState(null);

  function persist(res) {
    const { token: t, ...profile } = res.data;
    localStorage.setItem('token', t);
    setToken(t);
    setUser(profile);
    return profile;
  }

  async function login(email, password) {
    return persist(await api.post('/auth/login', { email, password }));
  }

  async function register(payload) {
    return persist(await api.post('/auth/register', payload));
  }

  function logout() {
    localStorage.removeItem('token');
    setToken(null);
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, token, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
```

- [ ] **Step 3: Create routes, ProtectedRoute, and pages**

`frontend/src/components/ProtectedRoute.jsx`:
```jsx
import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function ProtectedRoute({ children }) {
  const { token } = useAuth();
  return token ? children : <Navigate to="/login" replace />;
}
```

`frontend/src/pages/Login.jsx`:
```jsx
import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    try {
      await login(email, password);
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.message ?? 'Login failed');
    }
  }

  return (
    <form onSubmit={onSubmit}>
      <h1>Log in</h1>
      <label>Email
        <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
      </label>
      <label>Password
        <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
      </label>
      {error && <p role="alert">{error}</p>}
      <button type="submit">Log in</button>
      <p>No account? <Link to="/register">Register</Link></p>
    </form>
  );
}
```

`frontend/src/pages/Register.jsx`:
```jsx
import { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [form, setForm] = useState({ fullName: '', email: '', password: '' });
  const [error, setError] = useState('');

  function update(field) {
    return (e) => setForm({ ...form, [field]: e.target.value });
  }

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    try {
      await register(form);
      navigate('/');
    } catch (err) {
      setError(err.response?.data?.message ?? 'Registration failed');
    }
  }

  return (
    <form onSubmit={onSubmit}>
      <h1>Create account</h1>
      <label>Full name
        <input value={form.fullName} onChange={update('fullName')} required />
      </label>
      <label>Email
        <input type="email" value={form.email} onChange={update('email')} required />
      </label>
      <label>Password
        <input type="password" value={form.password} onChange={update('password')} minLength={8} required />
      </label>
      {error && <p role="alert">{error}</p>}
      <button type="submit">Register</button>
      <p>Have an account? <Link to="/login">Log in</Link></p>
    </form>
  );
}
```

`frontend/src/pages/Dashboard.jsx`:
```jsx
import { useEffect, useState } from 'react';
import api from '../api/client';
import { useAuth } from '../auth/AuthContext';

export default function Dashboard() {
  const { logout } = useAuth();
  const [profile, setProfile] = useState(null);

  useEffect(() => {
    api.get('/me').then((res) => setProfile(res.data)).catch(() => {});
  }, []);

  return (
    <div>
      <h1>Dashboard</h1>
      {profile && <p>Welcome, {profile.fullName}</p>}
      <button onClick={logout}>Log out</button>
    </div>
  );
}
```

`frontend/src/App.jsx`:
```jsx
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './auth/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}
```

`frontend/src/main.jsx`:
```jsx
import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
```

- [ ] **Step 4: Write the failing component test**

`frontend/src/pages/Login.test.jsx`:
```jsx
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../auth/AuthContext';
import Login from './Login';

test('renders the login form', () => {
  render(
    <AuthProvider>
      <MemoryRouter>
        <Login />
      </MemoryRouter>
    </AuthProvider>
  );
  expect(screen.getByRole('heading', { name: /log in/i })).toBeInTheDocument();
  expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
  expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /log in/i })).toBeInTheDocument();
});
```

- [ ] **Step 5: Install deps and run the test**

Run: `cd frontend && npm install && npm test`
Expected: PASS — the login form renders. (First run fails until `npm install` completes; this is the red→green: it fails to resolve modules before install, passes after.)

- [ ] **Step 6: Manual smoke check (optional but recommended)**

Start MySQL, then in two terminals: `cd backend && ./gradlew bootRun` and `cd frontend && npm run dev`. Visit `http://localhost:5173/register`, create an account, confirm redirect to the dashboard showing your name.

- [ ] **Step 7: Commit**

```bash
git add frontend
git commit -m "feat: add React frontend with auth pages and protected dashboard"
```

---

## Self-Review

**Spec coverage (Plan 1 slice only):**
- Auth register/login/JWT/bcrypt → Tasks 3–6. ✅
- `User` + `SkillCredit` (10 starter credits) → Tasks 2, 5. ✅
- Flyway schema ownership → Task 1. ✅
- Layered controller→service→repository + DTOs at boundary → Tasks 2, 5, 6. ✅
- Central error handling → Task 6. ✅
- Frontend scaffold + login/register/dashboard per §5.6 → Task 7. ✅
- Deferred to later plans (correctly out of Plan 1): forgot-password reset, skills, matching, sessions, credits spend/earn, reviews, forums, notifications, admin, Redis. Redis enters in Plan 2 (matching cache).

**Placeholder scan:** No TBD/TODO; every code step has complete code. ✅

**Type consistency:** `AuthResponse(token, fullName, email, role)` used identically in Tasks 5–6 and consumed by the frontend `persist()` (destructures `token`, spreads rest as profile). `UserProfile` fields match `MeController`. `SkillCredit(Long userId)` constructor used in Task 5 matches Task 2 definition. `JwtService` method names (`generateToken`, `extractEmail`, `isValid`) consistent across Tasks 3, 4, 5(mock). ✅

**Note on `login` return status:** `POST /api/auth/login` returns 200 (default) with `AuthResponse`; `register` returns 201. Consistent with the integration test.

---

## Execution Handoff

Plan complete and saved to `docs/plans/2026-07-20-skillswap-foundation-auth.md`.
