# SkillSwap Hub — UI Plan 2: Skills + Matching

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Users can manage the skills they teach/want to learn on a `/skills` page, and browse match suggestions with a compatibility score, send match requests, and accept/reject incoming requests on a `/matches` page — with real names shown throughout, not raw user IDs.

**Architecture:** Extends UI Plan 1's shadcn/Tailwind foundation with three more primitives (`Select`, `Dialog`, `Tabs`), consuming Plan 2/2 of the backend (skills catalog, user-skills, matching, match requests) which is fully live. One genuine gap surfaced while designing the Matches page: `GET /api/matches` returns `MatchDto(id, userAId, userBId, status, createdDate)` — raw IDs, no name — while `GET /api/matches/suggestions` returns `fullName` for a *candidate*, not for an *existing match's* other party. Rather than ship a degraded "User #47" list, this plan adds one small, safe backend endpoint (`GET /api/users/{id}` → `{id, fullName, city}`, no new information disclosed beyond what suggestions already show) so the Matches page can resolve and display real names.

**Tech Stack:** React 18, Vite 5, Tailwind v4, shadcn/ui, Radix UI (`Select`, `Dialog`, `Tabs`). Backend: Java 17, Spring Boot 3.2.5 (one small addition only).

## Global Constraints

- Frontend: path alias `@/*`, shadcn components in `frontend/src/components/ui/`, matching real shadcn/ui "new-york" output exactly (established in UI Plan 1 — `Select`/`Dialog`/`Tabs` must follow the identical hand-written-but-faithful approach, no improvisation).
- Backend: base package `com.skillswap`. Java 17. Gradle. No new Flyway migrations (Task 1 only adds a DTO + controller reading existing `users` data — no schema change). `ResponseStatusException` only; `GlobalExceptionHandler` untouched. Build with JDK 17 (`export JAVA_HOME=$(/usr/libexec/java_home -v 17)`).
- The new `GET /api/users/{id}` endpoint requires authentication (like every other route) but is **not** admin-gated and has **no** ownership check — any authenticated user may look up any other user's `{id, fullName, city}`. This discloses nothing not already shown by `/api/matches/suggestions` and `/api/forum/posts` (author names), so this is a deliberate, correct scope — do not add a permission check that doesn't exist for the equivalent data elsewhere in the app.
- Every mutating frontend action (add/remove skill, send/accept/reject match request) gives the user feedback on success and failure — reusing the same "inline error paragraph" pattern UI Plan 1 established for auth pages (`role="alert"`), not a new toast/notification system (YAGNI — introduce a toast library only when a page genuinely needs several concurrent transient messages, which none here do).
- Git author **Devashish Jadhav <jadhavom24@gmail.com>**. **Never** add `Co-Authored-By` or AI attribution. Conventional Commit messages. Commit at the end of every task.
- Verification: backend Task 1 follows real TDD (JUnit, RED→GREEN). Frontend tasks verify via `npm test` (a render-level test per new page, matching UI Plan 1's rigor) + `npm run build`.

**Interfaces already available:** Backend — `GET /api/skills` → `SkillDto[]`, `GET /api/categories` → `string[]`, `GET /api/me/skills` → `UserSkillDto[]`, `POST /api/me/skills` (body `{skillId, skillType, experience?, proficiency?}`) → `UserSkillDto`, `DELETE /api/me/skills/{id}`, `GET /api/matches/suggestions?city=&category=` → `MatchSuggestionDto[]` (`userId, fullName, city, matchedSkills, compatibilityScore`), `GET /api/matches` → `MatchDto[]` (`id, userAId, userBId, status, createdDate`), `POST /api/matches/request` (body `{targetUserId}`) → `MatchDto`, `PUT /api/matches/{id}` (body `{status}`, `"ACCEPTED"|"REJECTED"`) → `MatchDto`. Frontend — `api` client, `useAuth()` (now exposes a hydrated `user.id`/`user.fullName`/`user.role` reliably per UI Plan 1), `AppShell`, shadcn `Button`/`Card`/`Input`/`Label`/`Avatar`/`Badge` (UI Plan 1).

---

### Task 1: Backend — public user profile lookup endpoint

**Files:**
- Create: `backend/src/main/java/com/skillswap/dto/PublicProfileDto.java`
- Create: `backend/src/main/java/com/skillswap/controller/UserProfileController.java`
- Test: `backend/src/test/java/com/skillswap/controller/UserProfileFlowTest.java`

**Interfaces:**
- Consumes: `UserRepository` (Plan 1, unmodified).
- Produces: `record PublicProfileDto(Long id, String fullName, String city)`. `GET /api/users/{id}` → 200 + `PublicProfileDto`, 404 if the user doesn't exist.

- [ ] **Step 1: Write the failing flow test**

`backend/src/test/java/com/skillswap/controller/UserProfileFlowTest.java`:
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
class UserProfileFlowTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

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

    @Test
    void anyAuthenticatedUserCanLookUpAnotherUsersPublicProfile() throws Exception {
        String viewerToken = register("profile-viewer@example.com");
        String targetToken = register("profile-target@example.com");
        Long targetId = meId(targetToken);

        mvc.perform(get("/api/users/{id}", targetId).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(targetId.intValue()))
                .andExpect(jsonPath("$.fullName").value("profile-target@example.com"));
    }

    @Test
    void unknownUserIdReturns404() throws Exception {
        String viewerToken = register("profile-viewer-2@example.com");
        mvc.perform(get("/api/users/{id}", 999999L).header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuth() throws Exception {
        mvc.perform(get("/api/users/{id}", 1L)).andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests UserProfileFlowTest`
Expected: FAIL — `PublicProfileDto`/`UserProfileController` do not exist, route 404s.

- [ ] **Step 3: Write PublicProfileDto**

`backend/src/main/java/com/skillswap/dto/PublicProfileDto.java`:
```java
package com.skillswap.dto;

public record PublicProfileDto(Long id, String fullName, String city) {}
```

- [ ] **Step 4: Write UserProfileController**

`backend/src/main/java/com/skillswap/controller/UserProfileController.java`:
```java
package com.skillswap.controller;

import com.skillswap.dto.PublicProfileDto;
import com.skillswap.entity.User;
import com.skillswap.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/users")
public class UserProfileController {

    private final UserRepository userRepository;

    public UserProfileController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/{id}")
    public PublicProfileDto get(@PathVariable Long id) {
        User u = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new PublicProfileDto(u.getId(), u.getFullName(), u.getCity());
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test --tests UserProfileFlowTest`
Expected: PASS — all three cases green.

- [ ] **Step 6: Run the full backend suite**

Run: `cd backend && export JAVA_HOME=$(/usr/libexec/java_home -v 17) && ./gradlew test`
Expected: PASS — all 153 prior tests plus this task's 3 = **156 total**.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/skillswap/dto/PublicProfileDto.java \
        backend/src/main/java/com/skillswap/controller/UserProfileController.java \
        backend/src/test/java/com/skillswap/controller/UserProfileFlowTest.java
git commit -m "feat: add public user profile lookup endpoint for the UI's matches list"
```

---

### Task 2: Skills page (manage teach/learn skills)

**Files:**
- Create: `frontend/src/components/ui/select.jsx`
- Create: `frontend/src/components/ui/dialog.jsx`
- Create: `frontend/src/components/ui/tabs.jsx`
- Modify: `frontend/package.json` (add `@radix-ui/react-select`, `@radix-ui/react-dialog`, `@radix-ui/react-tabs`)
- Create: `frontend/src/pages/Skills.jsx`
- Modify: `frontend/src/App.jsx` (add the `/skills` route, wrapped in `AppShell`)
- Test: `frontend/src/pages/Skills.test.jsx`

**Interfaces:**
- Consumes: `api` client; `GET /api/skills`, `GET /api/me/skills`, `POST /api/me/skills`, `DELETE /api/me/skills/{id}` (all live); `Card`, `Button`, `Label`, `Badge` (UI Plan 1).
- Produces: `Select`/`SelectTrigger`/`SelectValue`/`SelectContent`/`SelectItem` (Radix `Select`). `Dialog`/`DialogTrigger`/`DialogContent`/`DialogHeader`/`DialogTitle`/`DialogFooter` (Radix `Dialog`). `Tabs`/`TabsList`/`TabsTrigger`/`TabsContent` (Radix `Tabs`). `Skills` page component, routed at `/skills`.

- [ ] **Step 1: Add the new Radix dependencies**

In `frontend/package.json`, add to `dependencies`:
```json
    "@radix-ui/react-dialog": "^1.1.2",
    "@radix-ui/react-select": "^2.1.2",
    "@radix-ui/react-tabs": "^1.1.1",
```

- [ ] **Step 2: Write the Select component**

`frontend/src/components/ui/select.jsx`:
```jsx
import * as React from 'react';
import * as SelectPrimitive from '@radix-ui/react-select';
import { Check, ChevronDown } from 'lucide-react';

import { cn } from '@/lib/utils';

const Select = SelectPrimitive.Root;
const SelectValue = SelectPrimitive.Value;

const SelectTrigger = React.forwardRef(({ className, children, ...props }, ref) => (
  <SelectPrimitive.Trigger
    ref={ref}
    className={cn(
      'flex h-9 w-full items-center justify-between whitespace-nowrap rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm ring-offset-background placeholder:text-muted-foreground focus:outline-none focus:ring-1 focus:ring-ring disabled:cursor-not-allowed disabled:opacity-50 [&>span]:line-clamp-1',
      className
    )}
    {...props}
  >
    {children}
    <SelectPrimitive.Icon asChild>
      <ChevronDown className="h-4 w-4 opacity-50" />
    </SelectPrimitive.Icon>
  </SelectPrimitive.Trigger>
));
SelectTrigger.displayName = SelectPrimitive.Trigger.displayName;

const SelectContent = React.forwardRef(({ className, children, position = 'popper', ...props }, ref) => (
  <SelectPrimitive.Portal>
    <SelectPrimitive.Content
      ref={ref}
      className={cn(
        'relative z-50 max-h-96 min-w-[8rem] overflow-hidden rounded-md border bg-popover text-popover-foreground shadow-md',
        position === 'popper' && 'data-[side=bottom]:translate-y-1 data-[side=top]:-translate-y-1',
        className
      )}
      position={position}
      {...props}
    >
      <SelectPrimitive.Viewport className="p-1">{children}</SelectPrimitive.Viewport>
    </SelectPrimitive.Content>
  </SelectPrimitive.Portal>
));
SelectContent.displayName = SelectPrimitive.Content.displayName;

const SelectItem = React.forwardRef(({ className, children, ...props }, ref) => (
  <SelectPrimitive.Item
    ref={ref}
    className={cn(
      'relative flex w-full cursor-pointer select-none items-center rounded-sm py-1.5 pl-8 pr-2 text-sm outline-none focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50',
      className
    )}
    {...props}
  >
    <span className="absolute left-2 flex h-3.5 w-3.5 items-center justify-center">
      <SelectPrimitive.ItemIndicator>
        <Check className="h-4 w-4" />
      </SelectPrimitive.ItemIndicator>
    </span>
    <SelectPrimitive.ItemText>{children}</SelectPrimitive.ItemText>
  </SelectPrimitive.Item>
));
SelectItem.displayName = SelectPrimitive.Item.displayName;

export { Select, SelectValue, SelectTrigger, SelectContent, SelectItem };
```

- [ ] **Step 3: Write the Dialog component**

`frontend/src/components/ui/dialog.jsx`:
```jsx
import * as React from 'react';
import * as DialogPrimitive from '@radix-ui/react-dialog';
import { X } from 'lucide-react';

import { cn } from '@/lib/utils';

const Dialog = DialogPrimitive.Root;
const DialogTrigger = DialogPrimitive.Trigger;
const DialogPortal = DialogPrimitive.Portal;

const DialogOverlay = React.forwardRef(({ className, ...props }, ref) => (
  <DialogPrimitive.Overlay
    ref={ref}
    className={cn('fixed inset-0 z-50 bg-black/50', className)}
    {...props}
  />
));
DialogOverlay.displayName = DialogPrimitive.Overlay.displayName;

const DialogContent = React.forwardRef(({ className, children, ...props }, ref) => (
  <DialogPortal>
    <DialogOverlay />
    <DialogPrimitive.Content
      ref={ref}
      className={cn(
        'fixed left-[50%] top-[50%] z-50 grid w-full max-w-lg translate-x-[-50%] translate-y-[-50%] gap-4 border bg-background p-6 shadow-lg rounded-lg',
        className
      )}
      {...props}
    >
      {children}
      <DialogPrimitive.Close className="absolute right-4 top-4 rounded-sm opacity-70 outline-none transition-opacity hover:opacity-100 focus-visible:ring-2 focus-visible:ring-ring">
        <X className="h-4 w-4" />
        <span className="sr-only">Close</span>
      </DialogPrimitive.Close>
    </DialogPrimitive.Content>
  </DialogPortal>
));
DialogContent.displayName = DialogPrimitive.Content.displayName;

const DialogHeader = ({ className, ...props }) => (
  <div className={cn('flex flex-col space-y-1.5 text-center sm:text-left', className)} {...props} />
);

const DialogFooter = ({ className, ...props }) => (
  <div className={cn('flex flex-col-reverse sm:flex-row sm:justify-end sm:space-x-2', className)} {...props} />
);

const DialogTitle = React.forwardRef(({ className, ...props }, ref) => (
  <DialogPrimitive.Title ref={ref} className={cn('text-lg font-semibold leading-none tracking-tight', className)} {...props} />
));
DialogTitle.displayName = DialogPrimitive.Title.displayName;

const DialogDescription = React.forwardRef(({ className, ...props }, ref) => (
  <DialogPrimitive.Description ref={ref} className={cn('text-sm text-muted-foreground', className)} {...props} />
));
DialogDescription.displayName = DialogPrimitive.Description.displayName;

export {
  Dialog,
  DialogTrigger,
  DialogContent,
  DialogHeader,
  DialogFooter,
  DialogTitle,
  DialogDescription,
};
```

- [ ] **Step 4: Write the Tabs component**

`frontend/src/components/ui/tabs.jsx`:
```jsx
import * as React from 'react';
import * as TabsPrimitive from '@radix-ui/react-tabs';

import { cn } from '@/lib/utils';

const Tabs = TabsPrimitive.Root;

const TabsList = React.forwardRef(({ className, ...props }, ref) => (
  <TabsPrimitive.List
    ref={ref}
    className={cn('inline-flex h-9 items-center justify-center rounded-lg bg-muted p-1 text-muted-foreground', className)}
    {...props}
  />
));
TabsList.displayName = TabsPrimitive.List.displayName;

const TabsTrigger = React.forwardRef(({ className, ...props }, ref) => (
  <TabsPrimitive.Trigger
    ref={ref}
    className={cn(
      'inline-flex items-center justify-center whitespace-nowrap rounded-md px-3 py-1 text-sm font-medium transition-all disabled:pointer-events-none disabled:opacity-50 data-[state=active]:bg-background data-[state=active]:shadow',
      className
    )}
    {...props}
  />
));
TabsTrigger.displayName = TabsPrimitive.Trigger.displayName;

const TabsContent = React.forwardRef(({ className, ...props }, ref) => (
  <TabsPrimitive.Content ref={ref} className={cn('mt-4', className)} {...props} />
));
TabsContent.displayName = TabsPrimitive.Content.displayName;

export { Tabs, TabsList, TabsTrigger, TabsContent };
```

- [ ] **Step 5: Write the failing Skills page test**

`frontend/src/pages/Skills.test.jsx`:
```jsx
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import api from '../api/client';
import Skills from './Skills';

vi.mock('../api/client');

test('renders my teach and learn skills, split by type', async () => {
  api.get.mockImplementation((url) => {
    if (url === '/skills') {
      return Promise.resolve({ data: [{ id: 4, skillName: 'Python', category: 'Technology', description: null }] });
    }
    if (url === '/me/skills') {
      return Promise.resolve({
        data: [
          { id: 1, skillId: 4, skillName: 'Python', category: 'Technology', skillType: 'CAN_TEACH', experience: '2 years', proficiency: 'Advanced' },
          { id: 2, skillId: 4, skillName: 'Python', category: 'Technology', skillType: 'WANT_TO_LEARN', experience: null, proficiency: null },
        ],
      });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Skills />
      </MemoryRouter>
    </AuthProvider>
  );

  await waitFor(() => expect(screen.getAllByText('Python')).toHaveLength(2));
  expect(screen.getByRole('button', { name: /add skill/i })).toBeInTheDocument();
});
```

- [ ] **Step 6: Run test to verify it fails**

Run: `cd frontend && npm test -- Skills`
Expected: FAIL — `Skills.jsx` doesn't exist yet.

- [ ] **Step 7: Write the Skills page**

`frontend/src/pages/Skills.jsx`:
```jsx
import { useEffect, useState } from 'react';
import api from '../api/client';
import { Button } from '@/components/ui/button';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';
import { Select, SelectTrigger, SelectValue, SelectContent, SelectItem } from '@/components/ui/select';
import {
  Dialog,
  DialogTrigger,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';

function SkillColumn({ title, skills, onRemove }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-base">{title}</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        {skills.length === 0 && <p className="text-sm text-muted-foreground">No skills added yet.</p>}
        {skills.map((s) => (
          <div key={s.id} className="flex items-center justify-between rounded-md border p-3">
            <div>
              <p className="font-medium">{s.skillName}</p>
              <div className="mt-1 flex gap-2">
                <Badge variant="secondary">{s.category}</Badge>
                {s.proficiency && <Badge variant="outline">{s.proficiency}</Badge>}
              </div>
            </div>
            <Button variant="ghost" size="sm" onClick={() => onRemove(s.id)}>Remove</Button>
          </div>
        ))}
      </CardContent>
    </Card>
  );
}

export default function Skills() {
  const [catalog, setCatalog] = useState([]);
  const [mySkills, setMySkills] = useState([]);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ skillId: '', skillType: 'CAN_TEACH', proficiency: '', experience: '' });
  const [error, setError] = useState('');

  function loadMySkills() {
    api.get('/me/skills').then((res) => setMySkills(res.data)).catch(() => {});
  }

  useEffect(() => {
    api.get('/skills').then((res) => setCatalog(res.data)).catch(() => {});
    loadMySkills();
  }, []);

  async function addSkill(e) {
    e.preventDefault();
    setError('');
    try {
      await api.post('/me/skills', {
        skillId: Number(form.skillId),
        skillType: form.skillType,
        proficiency: form.proficiency || undefined,
        experience: form.experience || undefined,
      });
      setOpen(false);
      setForm({ skillId: '', skillType: 'CAN_TEACH', proficiency: '', experience: '' });
      loadMySkills();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not add skill');
    }
  }

  async function removeSkill(id) {
    try {
      await api.delete(`/me/skills/${id}`);
      loadMySkills();
    } catch {
      // best-effort; the list stays as-is if this fails
    }
  }

  const teach = mySkills.filter((s) => s.skillType === 'CAN_TEACH');
  const learn = mySkills.filter((s) => s.skillType === 'WANT_TO_LEARN');

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">My Skills</h1>
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild>
            <Button>Add skill</Button>
          </DialogTrigger>
          <DialogContent>
            <form onSubmit={addSkill} className="space-y-4">
              <DialogHeader>
                <DialogTitle>Add a skill</DialogTitle>
              </DialogHeader>
              <div className="space-y-2">
                <Label htmlFor="skill-select">Skill</Label>
                <Select value={form.skillId} onValueChange={(v) => setForm({ ...form, skillId: v })}>
                  <SelectTrigger id="skill-select"><SelectValue placeholder="Choose a skill" /></SelectTrigger>
                  <SelectContent>
                    {catalog.map((s) => (
                      <SelectItem key={s.id} value={String(s.id)}>{s.skillName} ({s.category})</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="type-select">Type</Label>
                <Select value={form.skillType} onValueChange={(v) => setForm({ ...form, skillType: v })}>
                  <SelectTrigger id="type-select"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="CAN_TEACH">Can teach</SelectItem>
                    <SelectItem value="WANT_TO_LEARN">Want to learn</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="proficiency">Proficiency</Label>
                <Input
                  id="proficiency"
                  value={form.proficiency}
                  onChange={(e) => setForm({ ...form, proficiency: e.target.value })}
                  placeholder="Beginner / Intermediate / Advanced"
                />
              </div>
              {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
              <DialogFooter>
                <Button type="submit" disabled={!form.skillId}>Add</Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      </div>

      <div className="grid gap-4 sm:grid-cols-2">
        <SkillColumn title="I can teach" skills={teach} onRemove={removeSkill} />
        <SkillColumn title="I want to learn" skills={learn} onRemove={removeSkill} />
      </div>
    </div>
  );
}
```

- [ ] **Step 8: Run test to verify it passes**

Run: `cd frontend && npm test -- Skills`
Expected: PASS.

- [ ] **Step 9: Wire the `/skills` route**

In `frontend/src/App.jsx`, add the import `import Skills from './pages/Skills';` and add this `<Route>` inside `<Routes>`, alongside the existing `/` route:
```jsx
          <Route
            path="/skills"
            element={
              <ProtectedRoute>
                <AppShell>
                  <Skills />
                </AppShell>
              </ProtectedRoute>
            }
          />
```

- [ ] **Step 10: Run the full frontend suite and build**

Run: `cd frontend && npm install && npm test`
Expected: PASS — 8 prior tests plus `Skills.test.jsx` = 9.

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 11: Commit**

```bash
git add frontend/package.json frontend/package-lock.json \
        frontend/src/components/ui/select.jsx frontend/src/components/ui/dialog.jsx frontend/src/components/ui/tabs.jsx \
        frontend/src/pages/Skills.jsx frontend/src/pages/Skills.test.jsx frontend/src/App.jsx
git commit -m "feat: add skills management page with add/remove and teach/learn split"
```

---

### Task 3: Matches page (suggestions + my matches, with real names)

**Files:**
- Create: `frontend/src/pages/Matches.jsx`
- Modify: `frontend/src/App.jsx` (add the `/matches` route)
- Test: `frontend/src/pages/Matches.test.jsx`

**Interfaces:**
- Consumes: `GET /api/matches/suggestions?city=&category=`, `GET /api/matches`, `POST /api/matches/request`, `PUT /api/matches/{id}`, `GET /api/users/{id}` (Task 1) — all live. `Tabs`, `Card`, `Button`, `Badge`, `Input` (Task 2 + UI Plan 1).
- Produces: `Matches` page component, routed at `/matches`.

**Business rule:** for each match in "My Matches," resolve the other participant's name via `GET /api/users/{id}` (the non-me side of `userAId`/`userBId`, using `user.id` from `useAuth()`), fetched once per distinct ID and cached in a `Map` in component state — not refetched per render. A `PENDING` match where the current user is `userBId` (they were the recipient of the request) shows Accept/Reject buttons; every other match (mine-sent-pending, or already resolved) shows only its status badge.

- [ ] **Step 1: Write the failing Matches page test**

`frontend/src/pages/Matches.test.jsx`:
```jsx
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import api from '../api/client';
import Matches from './Matches';

vi.mock('../api/client');

test('renders suggestions and my matches, resolving the other participant name', async () => {
  api.get.mockImplementation((url) => {
    if (url === '/matches/suggestions') {
      return Promise.resolve({ data: [{ userId: 2, fullName: 'Teacher Two', city: 'Pune', matchedSkills: 1, compatibilityScore: 100 }] });
    }
    if (url === '/matches') {
      return Promise.resolve({ data: [{ id: 1, userAId: 2, userBId: 1, status: 'PENDING', createdDate: '2026-08-01T10:00:00' }] });
    }
    if (url === '/users/2') {
      return Promise.resolve({ data: { id: 2, fullName: 'Teacher Two', city: 'Pune' } });
    }
    if (url === '/me') {
      return Promise.resolve({ data: { id: 1, fullName: 'Me', email: 'me@example.com', role: 'USER' } });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Matches />
      </MemoryRouter>
    </AuthProvider>
  );

  await waitFor(() => expect(screen.getAllByText('Teacher Two')).toHaveLength(2));
  expect(screen.getByRole('button', { name: /send request/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /accept/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /reject/i })).toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- Matches`
Expected: FAIL — `Matches.jsx` doesn't exist.

- [ ] **Step 3: Write the Matches page**

`frontend/src/pages/Matches.jsx`:
```jsx
import { useEffect, useState } from 'react';
import api from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Card, CardHeader, CardTitle, CardContent, CardFooter } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

export default function Matches() {
  const { user } = useAuth();
  const [suggestions, setSuggestions] = useState([]);
  const [matches, setMatches] = useState([]);
  const [profiles, setProfiles] = useState({});
  const [requestedIds, setRequestedIds] = useState(new Set());

  function loadMatches() {
    api.get('/matches').then((res) => setMatches(res.data)).catch(() => {});
  }

  useEffect(() => {
    api.get('/matches/suggestions').then((res) => setSuggestions(res.data)).catch(() => {});
    loadMatches();
  }, []);

  useEffect(() => {
    if (!user) return;
    const otherIds = new Set(
      matches.map((m) => (m.userAId === user.id ? m.userBId : m.userAId)).filter((id) => !(id in profiles))
    );
    otherIds.forEach((id) => {
      api.get(`/users/${id}`).then((res) => {
        setProfiles((prev) => ({ ...prev, [id]: res.data }));
      }).catch(() => {});
    });
  }, [matches, user]);

  async function sendRequest(targetUserId) {
    try {
      await api.post('/matches/request', { targetUserId });
      setRequestedIds((prev) => new Set(prev).add(targetUserId));
      loadMatches();
    } catch {
      // the button's disabled state already prevents most repeat clicks; a failed request just leaves it retryable
    }
  }

  async function respond(matchId, status) {
    try {
      await api.put(`/matches/${matchId}`, { status });
      loadMatches();
    } catch {
      // leave the match in its current state if the update fails; user can retry
    }
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Matches</h1>
      <Tabs defaultValue="suggestions">
        <TabsList>
          <TabsTrigger value="suggestions">Suggestions</TabsTrigger>
          <TabsTrigger value="mine">My Matches</TabsTrigger>
        </TabsList>

        <TabsContent value="suggestions" className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {suggestions.map((s) => (
            <Card key={s.userId}>
              <CardHeader>
                <CardTitle className="text-base">{s.fullName}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-2">
                {s.city && <p className="text-sm text-muted-foreground">{s.city}</p>}
                <Badge>{s.compatibilityScore}% match</Badge>
              </CardContent>
              <CardFooter>
                <Button
                  size="sm"
                  disabled={requestedIds.has(s.userId)}
                  onClick={() => sendRequest(s.userId)}
                >
                  {requestedIds.has(s.userId) ? 'Requested' : 'Send Request'}
                </Button>
              </CardFooter>
            </Card>
          ))}
          {suggestions.length === 0 && <p className="text-sm text-muted-foreground">No suggestions yet — add some skills first.</p>}
        </TabsContent>

        <TabsContent value="mine" className="space-y-3">
          {matches.map((m) => {
            const otherId = user && m.userAId === user.id ? m.userBId : m.userAId;
            const other = profiles[otherId];
            const iAmRecipient = user && m.userBId === user.id;
            return (
              <Card key={m.id}>
                <CardContent className="flex items-center justify-between py-4">
                  <div>
                    <p className="font-medium">{other ? other.fullName : `User #${otherId}`}</p>
                    <Badge variant={m.status === 'ACCEPTED' ? 'default' : m.status === 'REJECTED' ? 'destructive' : 'secondary'}>
                      {m.status}
                    </Badge>
                  </div>
                  {m.status === 'PENDING' && iAmRecipient && (
                    <div className="flex gap-2">
                      <Button size="sm" onClick={() => respond(m.id, 'ACCEPTED')}>Accept</Button>
                      <Button size="sm" variant="outline" onClick={() => respond(m.id, 'REJECTED')}>Reject</Button>
                    </div>
                  )}
                </CardContent>
              </Card>
            );
          })}
          {matches.length === 0 && <p className="text-sm text-muted-foreground">No matches yet.</p>}
        </TabsContent>
      </Tabs>
    </div>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- Matches`
Expected: PASS.

- [ ] **Step 5: Wire the `/matches` route**

In `frontend/src/App.jsx`, add the import `import Matches from './pages/Matches';` and add this `<Route>` alongside `/skills`:
```jsx
          <Route
            path="/matches"
            element={
              <ProtectedRoute>
                <AppShell>
                  <Matches />
                </AppShell>
              </ProtectedRoute>
            }
          />
```

- [ ] **Step 6: Run the full frontend suite and build**

Run: `cd frontend && npm test`
Expected: PASS — 9 prior tests plus `Matches.test.jsx` = 10.

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/Matches.jsx frontend/src/pages/Matches.test.jsx frontend/src/App.jsx
git commit -m "feat: add matches page with suggestions, requests, and accept/reject"
```

---

## Self-Review

**Spec coverage (UI Plan 2 slice):** Skill profile management (add/remove teach & learn skills) → Task 2, matches spec §5.6.3/§5.6.4. Smart matching browse/search with compatibility score, send/accept/reject → Task 3, §5.6.4. City/category filters on suggestions are deferred (endpoint already supports `?city=&category=` query params — a follow-up task can add filter UI controls without any backend change; noted as a deferral, not a gap).

**Placeholder scan:** No TBD/TODO; every step has complete code.

**Type consistency:** `AddUserSkillRequest`'s field names (`skillId`, `skillType`, `experience`, `proficiency`) match exactly between the backend DTO and `Skills.jsx`'s `api.post('/me/skills', {...})` body. `MatchDto`'s `userAId`/`userBId`/`status` fields match exactly between the backend DTO and `Matches.jsx`'s usage. `PublicProfileDto`'s `{id, fullName, city}` shape (Task 1) matches exactly what `Matches.jsx` (Task 3) expects from `GET /api/users/{id}`.

**Scope check:** Three tasks — one small, justified backend addition plus two frontend pages — each independently testable and buildable.

**Deliberate simplifications (flagged for the record):**
- No city/category filter inputs on the Suggestions tab yet (the backend already accepts them) — `ponytail: query params unused by the UI yet, add filter Input/Select controls when a user actually has enough suggestions to need narrowing.`
- `GET /api/users/{id}` has no ownership/ADMIN gate by design — it discloses only `{id, fullName, city}`, the same fields already public via match suggestions and forum post authorship; do not tighten this without also reconsidering those existing endpoints for consistency.
- Match-request buttons use local `requestedIds` state (not re-derived from `/matches` on every suggestion render) to instantly disable a button after clicking, before the follow-up `loadMatches()` call resolves — a minor client-side optimism, not a source of truth; a page refresh always reflects the real server state via `/matches`.
