# SkillSwap Hub

Peer-to-peer skill exchange platform. Users list skills they **can teach** and
**want to learn**, get matched with complementary users, schedule sessions,
review each other, and exchange **credits** (no money). BCA final-year project
(IGNOU BCSP-064). See [docs/spec.md](docs/spec.md) for the full design.

## Stack

- **Backend:** Java 17, Spring Boot 3, Gradle — layered `controller → service → repository (JPA/Hibernate) → MySQL`
- **Auth:** Spring Security + JWT, bcrypt passwords, roles `USER` / `ADMIN`
- **DB:** MySQL 8, schema + seed managed by **Flyway** migrations (`db/migration/V*.sql`)
- **Cache:** Redis via Spring Cache (`@Cacheable`) on read-heavy paths only; app runs without it
- **Frontend:** React + Vite, React Router, Axios, Context for auth, Tailwind (or MUI)

## Layout

```
skillswap-hub/
├── backend/    Spring Boot app (Gradle)
├── frontend/   React + Vite app
├── docs/       spec.md, ERD, DFD
└── CLAUDE.md
```

## Build / Run

```bash
# DB (once): create empty schema `skillswap` — Flyway runs migrations on boot
# Redis (optional): redis-server            # app degrades gracefully if absent

# Backend  → http://localhost:8080  (Flyway migrates on startup)
cd backend && ./gradlew bootRun

# Frontend → http://localhost:5173  (proxies /api to :8080)
cd frontend && npm install && npm run dev

# Tests
cd backend && ./gradlew test
cd frontend && npm test
```

## Conventions

- **Layered, thin controllers.** Business logic in services, not controllers or repos.
- **DTOs at the boundary.** Never expose JPA entities directly in API responses; map to DTOs.
- **Validation** with Bean Validation (`@Valid` + annotations) on request DTOs.
- **Errors:** central `@RestControllerAdvice` → consistent `{error, message}` JSON + right status.
- **Credit math and matching logic are the parts that break** — they get real unit tests.
- Match existing file style; keep files focused (one clear responsibility).

## Scope guardrails (do NOT build these)

Deliberately cut to keep the project solo-buildable. Do not add them unless asked:
microservices, CDN, load-balancing, Docker/CI-CD, Prometheus/Grafana, 2FA,
Google/Facebook OAuth, real SMTP email (email events are **logged**, not sent),
i18n/multi-language, Selenium/JMeter. Video calls = user **pastes a meeting link**
(Zoom/Meet URL string), no SDK. Matching is a **SQL query**, not ML.

## Git

- Author: **Devashish Jadhav <jadhavom24@gmail.com>**
- **Never** add a `Co-Authored-By` line or any Claude/AI attribution to commits or PRs.
