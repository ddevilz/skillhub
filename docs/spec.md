# SkillSwap Hub — Design Spec

**Author:** Devashish Jadhav (Enrollment 2106993186) · BCA · BCSP-064
**Date:** 2026-07-20
**Stack:** Java Spring Boot 3 (Gradle) · React (Vite) · MySQL 8 (Flyway) · Redis (cache)

---

## 1. Overview

SkillSwap Hub is a peer-to-peer skill exchange platform. Each user lists skills
they **can teach** and skills they **want to learn**. The system matches
complementary users, lets them schedule learning sessions, review each other,
and settle exchanges through a **credit** economy instead of money. Teaching
earns credits; learning spends them. Community forums and an admin panel round
out the platform.

**Categories:** E-learning / peer-to-peer education · social collaboration ·
skill matching & recommendation · scheduling · credit economy · review &
reputation.

**Stakeholders:**
- **User (learner/teacher):** profile + skills, match, schedule, review, earn/spend credits, post in forums.
- **Admin:** manage users/skills/categories, moderate forums, resolve disputes, view reports.

**Objectives:** free peer learning without money; complementary-skill matching;
community knowledge sharing; quality via reviews/badges; a secure, responsive web app.

---

## 2. Architecture

Single-repo monolith. Layered backend, SPA frontend, MySQL for durable state,
Redis for read-heavy caching.

```
React SPA (Vite)  ──HTTP/JSON──►  Spring Boot REST API
                                    controller
                                       │
                                    service   ──►  Redis (@Cacheable)
                                       │
                                    repository (JPA/Hibernate)
                                       │
                                    MySQL 8
```

- **Backend layers:** `controller` (thin, HTTP + DTO mapping) → `service` (business logic, transactions) → `repository` (Spring Data JPA) → MySQL.
- **Auth:** Spring Security filter chain + JWT bearer tokens. Passwords hashed with bcrypt. `USER` / `ADMIN` roles enforced with method/URL security.
- **Cache:** Spring Cache abstraction backed by Redis. `@Cacheable` on match suggestions, skill/category catalog, and report aggregates; `@CacheEvict` on the corresponding writes. TTL ~10 min. If Redis is unreachable the app falls back to DB — no functional dependency.
- **DB init:** **Flyway** migrations under `db/migration` (`V1__schema.sql`, `V2__seed.sql`, …) run automatically on startup. Versioned, repeatable, safe for schema evolution.

### Repo layout
```
skillswap-hub/
├── backend/    Spring Boot (Gradle)
│   └── src/main/java/.../{config,security,controller,service,repository,entity,dto}
│   └── src/main/resources/{application.yml,db/migration/V*.sql}
├── frontend/   React + Vite (pages, components, api client, auth context)
├── docs/       spec.md, ERD, DFD
└── CLAUDE.md
```

---

## 3. Data Model

Thirteen tables, taken from the synopsis ERD. Keys and constraints:

| Table | Purpose | Key columns / notes |
|---|---|---|
| `User` | account | PK UserID; Email UNIQUE; PasswordHash (bcrypt); Role default USER; Active flag |
| `Skill` | master skill list | PK SkillID; SkillName; Category |
| `UserSkill` | user↔skill link | FK UserID, SkillID; SkillType `CAN_TEACH`/`WANT_TO_LEARN`; Proficiency; Experience |
| `Match` | pairing of two users | FK UserAID, UserBID; Status `PENDING`/`ACCEPTED`/`REJECTED` |
| `Session` | scheduled learning session | FK MatchID, ScheduledByID; date/start/end; Mode; LocationOrLink; Status `PENDING`/`CONFIRMED`/`COMPLETED`/`CANCELLED` |
| `Review` | post-session feedback | FK SessionID, ReviewerUserID, RatedUserID; Rating 1–5 (CHECK); Comments |
| `ForumCategory` | forum sections | PK CategoryID; CategoryName UNIQUE |
| `ForumPost` | forum posts | FK CategoryID, UserID; Title; Content; IsModerated |
| `ForumComment` | replies | FK PostID, UserID; CommentText; IsModerated |
| `Notification` | in-app alerts | FK UserID; Type; Message; IsRead |
| `SkillCredit` | per-user credit account (1:1) | FK UserID; TotalCredits default 10; Earned; Spent |
| `CreditTransaction` | credit ledger | FK UserID, SessionID; Type `EARNED`/`SPENT`; Amount |
| `SkillBadge` | earned badges | FK UserID, SkillID; BadgeType `BEGINNER`/`INTERMEDIATE`/`EXPERT`/`VERIFIED` |

Relationships: User 1:M UserSkill; Skill 1:M UserSkill; User M:M User via Match;
Match 1:M Session; Session 1:M Review; ForumCategory 1:M ForumPost; ForumPost
1:M ForumComment; User 1:M Notification; User 1:1 SkillCredit; User 1:M SkillBadge.

---

## 4. Modules

### 4.1 Authentication
Register (email, full name, password), login → JWT, logout, forgot-password via
reset token. Bcrypt hashing. New user gets a `SkillCredit` row with 10 credits.
*Cut: 2FA, Google/Facebook OAuth, email verification (token generated + logged, not emailed).*

### 4.2 Profile & Skills
CRUD on the user's teach/learn skills (skill from master list, proficiency,
experience). Categories: Music, Technology, Languages, Arts, Business, etc.
Profile shows avg rating, badges, completion %.

### 4.3 Smart Matching (SQL, not ML)
For current user **U**, a candidate **V** matches when:
`(V.CAN_TEACH ∩ U.WANT_TO_LEARN) ≠ ∅` **or** `(U.CAN_TEACH ∩ V.WANT_TO_LEARN) ≠ ∅`.
**Compatibility score** = matched skills / U's want-to-learn count, as a %.
Filters: skill name, category, city, proficiency, mode (online/offline). Results
sorted by score. User sends a match request → other accepts/rejects (`Match.Status`).
*Cut: ML/behavioral ranking. Upgrade path: swap the scoring function for a learned model, interface unchanged.*
Result set cached in Redis per (user, filter) key; evicted when U edits skills.

### 4.4 Session Scheduler
On an `ACCEPTED` match, either user proposes a session (date, start/end, mode,
location-or-link). Other accepts/rejects/reschedules. Statuses: PENDING →
CONFIRMED → COMPLETED / CANCELLED. "My sessions" list (upcoming / past / cancelled).
Video = user **pastes** a Zoom/Meet/Teams URL into `LocationOrLink`; shown only to
participants; one-click open. *Cut: calendar-SDK sync, video SDK, auto SMS/email reminders (in-app reminder only).*

### 4.5 Credits
`SkillCredit` per user (start 10). Booking a learning session **spends** credits;
completing a teaching session **earns** them (configurable rate, default 1/session).
Every change writes a `CreditTransaction` row (ledger = source of truth for balance
audits). **Booking is blocked if the learner lacks credits.** Dashboard shows balance
+ transaction history. This is money-like logic → gets unit tests.

### 4.6 Reviews & Badges
After a `COMPLETED` session both participants may rate 1–5 + comment (one review
each). Profile shows average rating. Badges awarded by rule (e.g. teach-count
thresholds → Beginner/Intermediate/Expert) plus admin-granted `VERIFIED`. Reviews
flaggable for moderation.

### 4.7 Community Forums
Predefined `ForumCategory` list. Users create posts, comment/reply, upvote (count).
Search posts by keyword. Admin can moderate/delete (`IsModerated`).
*Cut: follow-topic, downvote weighting — keep a simple upvote count.*

### 4.8 Notifications
In-app `Notification` rows for match requests, session changes, reviews, forum
activity. Frontend fetches unread count + list; mark-as-read. Email equivalents are
**logged**, not sent (no SMTP dependency).

### 4.9 Admin Panel
User list with search/filter; activate/deactivate accounts; master skill &
category CRUD; forum moderation queue; **reports** (aggregate SQL → JSON, rendered
as charts): registered users over time, most popular skills, session
completed/cancelled/pending counts, top-rated mentors, active forum categories.
CSV export optional.

---

## 5. Key API Surface (representative)

```
POST   /api/auth/register            POST /api/auth/login
POST   /api/auth/forgot-password     POST /api/auth/reset-password
GET    /api/me                       PUT  /api/me
GET/POST/DELETE /api/me/skills
GET    /api/skills                   GET  /api/categories
GET    /api/matches/suggestions?category&city&mode&proficiency
POST   /api/matches/request          PUT  /api/matches/{id}   (accept/reject)
POST   /api/sessions                 PUT  /api/sessions/{id}  (accept/reschedule/cancel/complete)
GET    /api/sessions?filter=upcoming|past|cancelled
POST   /api/sessions/{id}/review
GET    /api/me/credits               GET  /api/me/credits/transactions
GET/POST /api/forum/categories/{id}/posts   POST /api/forum/posts/{id}/comments
POST   /api/forum/posts/{id}/upvote
GET    /api/notifications            PUT  /api/notifications/{id}/read
# admin
GET    /api/admin/users              PUT  /api/admin/users/{id}/status
CRUD   /api/admin/skills             /api/admin/categories
GET    /api/admin/reports/*
```

JWT bearer on all but `/api/auth/*`. Admin routes require `ADMIN` role.

---

## 6. Frontend

React + Vite SPA. Pages mirror synopsis §5.6: Login/Register, Dashboard (credits,
upcoming sessions, match requests, rating, recommended matches), Skills manager,
Match search/results, Session scheduler + list, Review prompt, Forum
(list/detail), Admin dashboard. Auth via Context (JWT in memory/localStorage);
Axios client with auth interceptor; React Router; Tailwind (or MUI) for responsive
mobile-first layout.

---

## 7. Non-Functional (realistic for scope)

- **Security:** bcrypt hashing; JWT; Bean Validation on inputs; parameterized JPA (no raw SQL concat) → SQLi/XSS/CSRF resistant; RBAC; session/token timeout.
- **Performance:** indexed FKs and lookup columns; Redis cache on hot reads; pagination on lists.
- **Reliability:** central error handling → friendly messages; credit changes logged in ledger.
- **Usability:** responsive, ≤3 clicks to major features, clear feedback states.
- *Explicitly out of scope: 1000-concurrent-user load target, 99.5% SLA, microservices, CDN, load balancing, horizontal scaling, monitoring stack. Documented as future work.*

---

## 8. Testing

- **Backend unit (JUnit 5 + Mockito):** credit math (earn/spend/insufficient-block), matching score, badge-award rules, auth token handling.
- **Backend integration (Spring Boot Test + MockMvc):** auth flow, a matching endpoint, a session-booking-with-credits endpoint, an admin-only access check.
- **Frontend (Vitest + React Testing Library):** a couple of component/render + form-validation tests.
- Focus on logic that breaks, not a coverage percentage.

---

## 9. Out of Scope → Upgrade Paths

| Cut | Add later by |
|---|---|
| ML matching | Replace scoring function with a learned model; matching interface unchanged. |
| Real email (SMTP) | Wire JavaMail/SendGrid into the existing notification service (already an abstraction point). |
| 2FA / social login | Add Spring Security providers; auth flow already isolated. |
| Video SDK | Replace pasted-link field with Zoom/Meet API session creation. |
| Microservices/Redis-scale/CDN/CI-CD/monitoring | Ops concerns; add when traffic is real. |

---

## 10. Future Scope (from synopsis §5.7)

AI matching, skill-assessment tests, group sessions, gamification, native mobile
apps, in-app messaging/video, premium tiers, referral rewards, calendar sync,
content library, local meetups. Not part of this build.

---

## 11. Deliverables (this phase)

- `CLAUDE.md` (repo root) — coding guardrails.
- `docs/spec.md` (this file).
- Implementation plan follows (writing-plans skill) before any code.
- **Git:** author *Devashish Jadhav <jadhavom24@gmail.com>*; no co-author / AI attribution on commits.
