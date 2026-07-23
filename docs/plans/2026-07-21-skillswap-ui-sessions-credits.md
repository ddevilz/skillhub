# SkillSwap Hub — UI Plan 3: Sessions + Credits

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Users can view and manage their sessions (upcoming/past/cancelled/all), schedule a new session from an accepted match, confirm/cancel/reschedule/complete sessions, and see their credit balance breakdown and transaction history — all on a `/sessions` page.

**Architecture:** Extends the existing shadcn/Tailwind foundation and Select/Dialog/Tabs primitives (from UI Plans 1–2) with a `/sessions` page. Three tasks: (1) the session list with tabs and lifecycle actions against sessions that already exist, (2) a "New Session" dialog (scheduling from an ACCEPTED match) plus a "Reschedule" dialog for existing sessions, (3) a credits summary + transaction history section on the same page. No backend changes — every endpoint this plan needs already exists and is live.

**Tech Stack:** React 18, Vite 5, Tailwind v4, shadcn/ui, Radix UI (reusing `Select`/`Dialog`/`Tabs` from UI Plan 2 — no new primitives needed).

## Global Constraints

- Frontend: path alias `@/*`, shadcn components in `frontend/src/components/ui/`. Reuse the existing `Select`/`Dialog`/`Tabs`/`Card`/`Button`/`Badge`/`Label`/`Input` primitives from prior plans — do not create new ones.
- Every mutating frontend action (create session, confirm, cancel, reschedule, complete) gives the user feedback on success and failure via the established inline `role="alert"` error-paragraph pattern (see `frontend/src/pages/Skills.jsx`/`Matches.jsx` for the exact precedent). A dialog-based action's error renders inside that dialog's form (matching `Skills.jsx`'s "Add a skill" dialog); a list-level action's error (confirm/cancel/complete, no dialog involved) renders as a page-level alert (matching `Matches.jsx`'s pattern).
- Native HTML date/time inputs (`<input type="date">`, `<input type="time">`) for scheduling — no date-picker library. This is a deliberate `ladder` choice: the platform already provides this.
- Session creation, confirm, cancel, reschedule, and complete map 1:1 to already-live backend endpoints (see Interfaces below) — do not invent new backend behavior or new fields.
- Git author **Devashish Jadhav <jadhavom24@gmail.com>**. **Never** add a `Co-Authored-By` line or any AI/Claude attribution to commits. Conventional Commit messages. Commit at the end of every task.
- Verification: `npm test` (a render-level test per task's new behavior) + `npm run build`.

**Interfaces already available (all live, no backend changes in this plan):**
- `SessionDto(Long id, Long matchId, Long skillId, Long teacherUserId, Long learnerUserId, Long scheduledByUserId, LocalDate sessionDate, LocalTime startTime, LocalTime endTime, String mode, String locationOrLink, String status, LocalDateTime createdDate)` — `mode` is `"ONLINE"|"OFFLINE"`, `status` is `"PENDING"|"CONFIRMED"|"COMPLETED"|"CANCELLED"`. Dates/times serialize as ISO strings (`"2026-08-01"`, `"10:00:00"`).
- `GET /api/sessions?filter=` (filter omitted or one of `upcoming|past|cancelled`; omitted/unknown returns all) → `SessionDto[]`.
- `POST /api/sessions` (body `{matchId, teacherUserId, skillId, sessionDate, startTime, endTime, mode, locationOrLink}`) → 201 + `SessionDto`. Errors: 404 (match not found / not a participant), 400 (match not ACCEPTED, or `teacherUserId` isn't a match participant, or bad `mode`), 402 Payment Required (learner has insufficient credits), 404 (skill not found).
- `PUT /api/sessions/{id}/confirm` → `SessionDto`. Errors: 404 (not a participant), 400 (the scheduler tried to confirm their own proposal — only the other participant may confirm), 409 (session not PENDING).
- `PUT /api/sessions/{id}/cancel` → `SessionDto`. Errors: 404, 409 (already COMPLETED/CANCELLED).
- `PUT /api/sessions/{id}/reschedule` (body `{sessionDate, startTime, endTime}`) → `SessionDto` (resets status to PENDING, the rescheduler becomes the new `scheduledByUserId`). Errors: 404, 409 (not PENDING/CONFIRMED).
- `PUT /api/sessions/{id}/complete` → `SessionDto` (settles credits, awards badges). Errors: 404, 409 (not CONFIRMED).
- `GET /api/me/credits` → `SkillCreditDto(int totalCredits, int creditsEarned, int creditsSpent)`.
- `GET /api/me/credits/transactions` → `CreditTransactionDto(Long id, Long sessionId, String transactionType, int amount, LocalDateTime transactionDate)[]`.
- `GET /api/matches` → `MatchDto(Long id, Long userAId, Long userBId, String status, LocalDateTime createdDate)[]` — filter client-side for `status === "ACCEPTED"`.
- `GET /api/skills` → `SkillDto(Long id, String skillName, String category, String description)[]`.
- `GET /api/users/{id}` → `PublicProfileDto(Long id, String fullName, String city)` (added in UI Plan 2, used here identically to resolve names).
- Frontend: `useAuth()` → `{ user, token, login, logout }`, `user.id`/`user.fullName`. `api` axios client. `AppShell`/`ProtectedRoute` wrapping pattern already used by `/skills` and `/matches` routes in `frontend/src/App.jsx`.

---

### Task 1: Sessions list — tabs, name/skill resolution, lifecycle actions

**Files:**
- Create: `frontend/src/pages/Sessions.jsx`
- Modify: `frontend/src/App.jsx` (add the `/sessions` route)
- Test: `frontend/src/pages/Sessions.test.jsx`

**Interfaces:**
- Consumes: `GET /api/sessions`, `GET /api/skills`, `GET /api/users/{id}`, `PUT /api/sessions/{id}/confirm`, `PUT /api/sessions/{id}/cancel`, `PUT /api/sessions/{id}/complete`, `useAuth()`. `Tabs`/`TabsList`/`TabsTrigger`/`TabsContent`, `Card`/`CardHeader`/`CardTitle`/`CardContent`, `Button`, `Badge` (all existing).
- Produces: `Sessions` page component, routed at `/sessions`. This task does NOT include a "New Session" button or a Reschedule dialog — those are Task 2. This task does NOT include the credits section — that is Task 3.

**Business rules:**
- Fetch sessions once (no `filter` query param — fetch all, filter client-side into four tabs: Upcoming = `status === 'PENDING' || status === 'CONFIRMED'`, Past = `status === 'COMPLETED'`, Cancelled = `status === 'CANCELLED'`, All = everything), sorted by `sessionDate` then `startTime` ascending.
- For each session, resolve the *other* participant's name: `otherId = session.teacherUserId === user.id ? session.learnerUserId : session.teacherUserId`, fetched once per distinct ID via `GET /api/users/{id}` and cached in a `profiles` state object (same pattern as `frontend/src/pages/Matches.jsx`).
- Resolve each session's skill name via a `skillsById` map built once from `GET /api/skills` (fetched once on mount).
- Each session card shows: date + time range (e.g. `2026-08-01 · 10:00–11:00`), skill name, the other participant's name, a "Teaching" or "Learning" label (`session.teacherUserId === user.id ? 'Teaching' : 'Learning'`), the mode (`ONLINE`/`OFFLINE`) and, if present, `locationOrLink` rendered as a clickable link when `mode === 'ONLINE'` (plain text otherwise), and a status `Badge`.
- Actions per session, based on `status` and whether the current user is the scheduler:
  - `status === 'PENDING' && session.scheduledByUserId !== user.id` → show a "Confirm" button.
  - `status === 'PENDING' || status === 'CONFIRMED'` → show a "Cancel" button.
  - `status === 'CONFIRMED'` → show a "Complete" button.
  - `status === 'COMPLETED' || status === 'CANCELLED'` → no actions, only the badge.
- Every action (confirm/cancel/complete) reports failure via a single page-level `error` state rendered as `{error && <p role="alert" className="text-sm text-destructive">{error}</p>}` directly under the `<h1>`; clear it at the start of each action and on success reload the sessions list.

- [ ] **Step 1: Write the failing test**

`frontend/src/pages/Sessions.test.jsx`:
```jsx
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import api from '../api/client';
import Sessions from './Sessions';

vi.mock('../api/client');

test('renders upcoming sessions with resolved names, skill, and a confirm action', async () => {
  localStorage.setItem('token', 'test-token');

  api.get.mockImplementation((url) => {
    if (url === '/me') {
      return Promise.resolve({ data: { id: 1, fullName: 'Me', email: 'me@example.com', role: 'USER' } });
    }
    if (url === '/skills') {
      return Promise.resolve({ data: [{ id: 4, skillName: 'Python', category: 'Technology', description: null }] });
    }
    if (url === '/sessions') {
      return Promise.resolve({
        data: [
          {
            id: 10, matchId: 1, skillId: 4, teacherUserId: 2, learnerUserId: 1, scheduledByUserId: 2,
            sessionDate: '2026-08-01', startTime: '10:00:00', endTime: '11:00:00',
            mode: 'ONLINE', locationOrLink: 'https://meet.example/abc', status: 'PENDING',
            createdDate: '2026-07-20T09:00:00',
          },
        ],
      });
    }
    if (url === '/users/2') {
      return Promise.resolve({ data: { id: 2, fullName: 'Teacher Two', city: 'Pune' } });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Sessions />
      </MemoryRouter>
    </AuthProvider>
  );

  await waitFor(() => expect(screen.getByText('Teacher Two')).toBeInTheDocument());
  expect(screen.getByText('Python')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /confirm/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- Sessions`
Expected: FAIL — `Sessions.jsx` doesn't exist.

- [ ] **Step 3: Write the Sessions page**

`frontend/src/pages/Sessions.jsx`:
```jsx
import { useEffect, useState } from 'react';
import api from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

function sortSessions(list) {
  return [...list].sort((a, b) => {
    const d = a.sessionDate.localeCompare(b.sessionDate);
    return d !== 0 ? d : a.startTime.localeCompare(b.startTime);
  });
}

export default function Sessions() {
  const { user } = useAuth();
  const [sessions, setSessions] = useState([]);
  const [skillsById, setSkillsById] = useState({});
  const [profiles, setProfiles] = useState({});
  const [error, setError] = useState('');

  function loadSessions() {
    api.get('/sessions').then((res) => setSessions(sortSessions(res.data))).catch(() => {});
  }

  useEffect(() => {
    loadSessions();
    api.get('/skills').then((res) => {
      setSkillsById(Object.fromEntries(res.data.map((s) => [s.id, s.skillName])));
    }).catch(() => {});
  }, []);

  useEffect(() => {
    if (!user) return;
    const otherIds = new Set(
      sessions
        .map((s) => (s.teacherUserId === user.id ? s.learnerUserId : s.teacherUserId))
        .filter((id) => !(id in profiles))
    );
    otherIds.forEach((id) => {
      api.get(`/users/${id}`).then((res) => {
        setProfiles((prev) => ({ ...prev, [id]: res.data }));
      }).catch(() => {});
    });
  }, [sessions, user]);

  async function runAction(sessionId, action) {
    setError('');
    try {
      await api.put(`/sessions/${sessionId}/${action}`);
      loadSessions();
    } catch (err) {
      setError(err.response?.data?.message ?? `Could not ${action} session`);
    }
  }

  const upcoming = sessions.filter((s) => s.status === 'PENDING' || s.status === 'CONFIRMED');
  const past = sessions.filter((s) => s.status === 'COMPLETED');
  const cancelled = sessions.filter((s) => s.status === 'CANCELLED');

  function SessionCard({ s }) {
    const otherId = user && s.teacherUserId === user.id ? s.learnerUserId : s.teacherUserId;
    const other = profiles[otherId];
    const teaching = user && s.teacherUserId === user.id;
    const canConfirm = s.status === 'PENDING' && user && s.scheduledByUserId !== user.id;
    const canCancel = s.status === 'PENDING' || s.status === 'CONFIRMED';
    const canComplete = s.status === 'CONFIRMED';

    return (
      <Card>
        <CardContent className="flex items-center justify-between py-4">
          <div className="space-y-1">
            <p className="font-medium">
              {s.sessionDate} · {s.startTime.slice(0, 5)}–{s.endTime.slice(0, 5)}
            </p>
            <p className="text-sm text-muted-foreground">
              {skillsById[s.skillId] ?? `Skill #${s.skillId}`} with {other ? other.fullName : `User #${otherId}`}
              {' · '}
              {teaching ? 'Teaching' : 'Learning'}
            </p>
            <p className="text-sm text-muted-foreground">
              {s.mode === 'ONLINE' && s.locationOrLink ? (
                <a href={s.locationOrLink} target="_blank" rel="noreferrer" className="underline">
                  {s.locationOrLink}
                </a>
              ) : (
                s.mode
              )}
            </p>
            <Badge variant={s.status === 'CANCELLED' ? 'destructive' : s.status === 'COMPLETED' ? 'default' : 'secondary'}>
              {s.status}
            </Badge>
          </div>
          <div className="flex gap-2">
            {canConfirm && <Button size="sm" onClick={() => runAction(s.id, 'confirm')}>Confirm</Button>}
            {canComplete && <Button size="sm" onClick={() => runAction(s.id, 'complete')}>Complete</Button>}
            {canCancel && <Button size="sm" variant="outline" onClick={() => runAction(s.id, 'cancel')}>Cancel</Button>}
          </div>
        </CardContent>
      </Card>
    );
  }

  function SessionList({ list, emptyText }) {
    return (
      <div className="space-y-3">
        {list.map((s) => <SessionCard key={s.id} s={s} />)}
        {list.length === 0 && <p className="text-sm text-muted-foreground">{emptyText}</p>}
      </div>
    );
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Sessions</h1>
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}

      <Tabs defaultValue="upcoming">
        <TabsList>
          <TabsTrigger value="upcoming">Upcoming</TabsTrigger>
          <TabsTrigger value="past">Past</TabsTrigger>
          <TabsTrigger value="cancelled">Cancelled</TabsTrigger>
          <TabsTrigger value="all">All</TabsTrigger>
        </TabsList>
        <TabsContent value="upcoming">
          <SessionList list={upcoming} emptyText="No upcoming sessions." />
        </TabsContent>
        <TabsContent value="past">
          <SessionList list={past} emptyText="No past sessions yet." />
        </TabsContent>
        <TabsContent value="cancelled">
          <SessionList list={cancelled} emptyText="No cancelled sessions." />
        </TabsContent>
        <TabsContent value="all">
          <SessionList list={sessions} emptyText="No sessions yet." />
        </TabsContent>
      </Tabs>
    </div>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- Sessions`
Expected: PASS.

- [ ] **Step 5: Wire the `/sessions` route**

In `frontend/src/App.jsx`, add the import `import Sessions from './pages/Sessions';` and add this `<Route>` alongside `/skills`/`/matches`:
```jsx
          <Route
            path="/sessions"
            element={
              <ProtectedRoute>
                <AppShell>
                  <Sessions />
                </AppShell>
              </ProtectedRoute>
            }
          />
```

- [ ] **Step 6: Run the full frontend suite and build**

Run: `cd frontend && npm test`
Expected: PASS — 10 prior tests plus `Sessions.test.jsx` = 11.

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/Sessions.jsx frontend/src/pages/Sessions.test.jsx frontend/src/App.jsx
git commit -m "feat: add sessions list with tabs, name/skill resolution, and lifecycle actions"
```

---

### Task 2: New Session dialog + Reschedule dialog

**Files:**
- Modify: `frontend/src/pages/Sessions.jsx`
- Test: `frontend/src/pages/Sessions.test.jsx` (extend)

**Interfaces:**
- Consumes: `GET /api/matches` (filter client-side for `status === 'ACCEPTED'`), `POST /api/sessions`, `PUT /api/sessions/{id}/reschedule`, `GET /api/users/{id}` (already used by Task 1 for participant names — reuse the same `profiles` cache for match-participant names too), `Dialog`/`DialogTrigger`/`DialogContent`/`DialogHeader`/`DialogTitle`/`DialogFooter`, `Select`/`SelectTrigger`/`SelectValue`/`SelectContent`/`SelectItem`, `Input`, `Label` (all existing).
- Produces: a "New Session" button in the page header opening a creation dialog; a "Reschedule" button on `PENDING`/`CONFIRMED` session cards opening a reschedule dialog.

**Business rules:**
- New Session dialog fields, in order: "With" (`Select` of accepted matches, option label = the other participant's resolved name, value = `matchId`), "Who teaches?" (`Select`, options `"You"` (value = `user.id`) and the other participant's name (value = their id) — rebuilt whenever the selected match changes, since the "other" person depends on which match is picked), "Skill" (`Select` built from the full `GET /api/skills` catalog, matching `Skills.jsx`'s established catalog-select pattern — no attempt to filter to only overlapping skills, since no backend endpoint exposes that; this is a deliberate, documented MVP simplification), "Date" (`<input type="date">`), "Start time" / "End time" (`<input type="time">` × 2), "Mode" (`Select`, options `Online`/`Offline` mapping to `"ONLINE"`/`"OFFLINE"`), "Location / link" (`Input`, placeholder `"Zoom/Meet link, or leave blank for in-person"`). Submit button disabled until match, teacher, skill, date, start time, and end time are all set. On success: close the dialog, reset the form, reload the sessions list (call the same `loadSessions()` used by Task 1). On failure: render the error inside the dialog's form via the established `role="alert"` pattern (matching `Skills.jsx`'s "Add a skill" dialog), not the page-level alert.
- Reschedule dialog: pre-filled with the session's current `sessionDate`/`startTime`/`endTime`, only those three fields, submits via `PUT /api/sessions/{id}/reschedule`. On success: close, reload sessions. On failure: error inside this dialog's form (same pattern).
- The "New Session" button and dialog trigger and the "Reschedule" per-card trigger both live inside `Sessions.jsx` from this point on — you are editing the file Task 1 created, not replacing it.

- [ ] **Step 1: Extend the test for New Session + Reschedule**

Add to `frontend/src/pages/Sessions.test.jsx` (keep the existing test from Task 1 unchanged; add this new one below it, updating the shared `api.get.mockImplementation` if needed so both tests' expectations hold — the existing mock already covers `/me`, `/skills`, `/sessions`, `/users/2`; add a `/matches` case for this test):

```jsx
test('opens the new session dialog and shows match/teacher/skill fields', async () => {
  localStorage.setItem('token', 'test-token');

  api.get.mockImplementation((url) => {
    if (url === '/me') {
      return Promise.resolve({ data: { id: 1, fullName: 'Me', email: 'me@example.com', role: 'USER' } });
    }
    if (url === '/skills') {
      return Promise.resolve({ data: [{ id: 4, skillName: 'Python', category: 'Technology', description: null }] });
    }
    if (url === '/sessions') {
      return Promise.resolve({ data: [] });
    }
    if (url === '/matches') {
      return Promise.resolve({ data: [{ id: 1, userAId: 1, userBId: 2, status: 'ACCEPTED', createdDate: '2026-07-20T09:00:00' }] });
    }
    if (url === '/users/2') {
      return Promise.resolve({ data: { id: 2, fullName: 'Teacher Two', city: 'Pune' } });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Sessions />
      </MemoryRouter>
    </AuthProvider>
  );

  const user = userEvent.setup();
  const newSessionButton = await screen.findByRole('button', { name: /new session/i });
  await user.click(newSessionButton);

  expect(await screen.findByText(/teacher two/i)).toBeInTheDocument();
  expect(screen.getByLabelText(/who teaches/i)).toBeInTheDocument();
  expect(screen.getByLabelText(/^skill$/i)).toBeInTheDocument();
});
```

Add `import userEvent from '@testing-library/user-event';` to the top of `Sessions.test.jsx` alongside the existing imports (check `frontend/src/pages/Matches.test.jsx` for the exact same import already used there).

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- Sessions`
Expected: FAIL — no "New Session" button exists yet.

- [ ] **Step 3: Add the New Session and Reschedule dialogs**

Modify `frontend/src/pages/Sessions.jsx`. Add these imports at the top, alongside the existing ones:
```jsx
import {
  Dialog,
  DialogTrigger,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from '@/components/ui/dialog';
import { Select, SelectTrigger, SelectValue, SelectContent, SelectItem } from '@/components/ui/select';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
```

Inside the `Sessions` component, add new state and data-loading alongside the existing state (after the `profiles` state and its effect):
```jsx
  const [matches, setMatches] = useState([]);
  const [newOpen, setNewOpen] = useState(false);
  const [newForm, setNewForm] = useState({
    matchId: '', teacherUserId: '', skillId: '', sessionDate: '', startTime: '', endTime: '',
    mode: 'ONLINE', locationOrLink: '',
  });
  const [newError, setNewError] = useState('');

  const [rescheduleTarget, setRescheduleTarget] = useState(null);
  const [rescheduleForm, setRescheduleForm] = useState({ sessionDate: '', startTime: '', endTime: '' });
  const [rescheduleError, setRescheduleError] = useState('');

  useEffect(() => {
    api.get('/matches').then((res) => setMatches(res.data.filter((m) => m.status === 'ACCEPTED'))).catch(() => {});
  }, []);

  useEffect(() => {
    if (!user) return;
    const matchOtherIds = matches.map((m) => (m.userAId === user.id ? m.userBId : m.userAId));
    const otherIds = new Set(matchOtherIds.filter((id) => !(id in profiles)));
    otherIds.forEach((id) => {
      api.get(`/users/${id}`).then((res) => {
        setProfiles((prev) => ({ ...prev, [id]: res.data }));
      }).catch(() => {});
    });
  }, [matches, user]);

  const selectedMatch = matches.find((m) => String(m.id) === newForm.matchId);
  const selectedMatchOtherId = selectedMatch && user
    ? (selectedMatch.userAId === user.id ? selectedMatch.userBId : selectedMatch.userAId)
    : null;
  const selectedMatchOtherName = selectedMatchOtherId != null
    ? (profiles[selectedMatchOtherId]?.fullName ?? `User #${selectedMatchOtherId}`)
    : null;

  async function createSession(e) {
    e.preventDefault();
    setNewError('');
    try {
      await api.post('/sessions', {
        matchId: Number(newForm.matchId),
        teacherUserId: Number(newForm.teacherUserId),
        skillId: Number(newForm.skillId),
        sessionDate: newForm.sessionDate,
        startTime: newForm.startTime,
        endTime: newForm.endTime,
        mode: newForm.mode,
        locationOrLink: newForm.locationOrLink || undefined,
      });
      setNewOpen(false);
      setNewForm({ matchId: '', teacherUserId: '', skillId: '', sessionDate: '', startTime: '', endTime: '', mode: 'ONLINE', locationOrLink: '' });
      loadSessions();
    } catch (err) {
      setNewError(err.response?.data?.message ?? 'Could not create session');
    }
  }

  function openReschedule(s) {
    setRescheduleTarget(s);
    setRescheduleForm({ sessionDate: s.sessionDate, startTime: s.startTime.slice(0, 5), endTime: s.endTime.slice(0, 5) });
    setRescheduleError('');
  }

  async function submitReschedule(e) {
    e.preventDefault();
    setRescheduleError('');
    try {
      await api.put(`/sessions/${rescheduleTarget.id}/reschedule`, rescheduleForm);
      setRescheduleTarget(null);
      loadSessions();
    } catch (err) {
      setRescheduleError(err.response?.data?.message ?? 'Could not reschedule session');
    }
  }
```

Replace the page header (the `<h1>` line) so it includes the New Session dialog trigger:
```jsx
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Sessions</h1>
        <Dialog open={newOpen} onOpenChange={setNewOpen}>
          <DialogTrigger asChild>
            <Button>New Session</Button>
          </DialogTrigger>
          <DialogContent>
            <form onSubmit={createSession} className="space-y-4">
              <DialogHeader>
                <DialogTitle>Schedule a session</DialogTitle>
                <DialogDescription>Pick an accepted match, who's teaching, and a time.</DialogDescription>
              </DialogHeader>

              <div className="space-y-2">
                <Label htmlFor="match-select">With</Label>
                <Select
                  value={newForm.matchId}
                  onValueChange={(v) => setNewForm({ ...newForm, matchId: v, teacherUserId: '' })}
                >
                  <SelectTrigger id="match-select"><SelectValue placeholder="Choose a match" /></SelectTrigger>
                  <SelectContent>
                    {matches.map((m) => {
                      const otherId = user && m.userAId === user.id ? m.userBId : m.userAId;
                      const name = profiles[otherId]?.fullName ?? `User #${otherId}`;
                      return <SelectItem key={m.id} value={String(m.id)}>{name}</SelectItem>;
                    })}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="teacher-select">Who teaches?</Label>
                <Select
                  value={newForm.teacherUserId}
                  onValueChange={(v) => setNewForm({ ...newForm, teacherUserId: v })}
                  disabled={!selectedMatch}
                >
                  <SelectTrigger id="teacher-select"><SelectValue placeholder="Choose who teaches" /></SelectTrigger>
                  <SelectContent>
                    {user && <SelectItem value={String(user.id)}>You</SelectItem>}
                    {selectedMatchOtherId != null && (
                      <SelectItem value={String(selectedMatchOtherId)}>{selectedMatchOtherName}</SelectItem>
                    )}
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="skill-select">Skill</Label>
                <Select value={newForm.skillId} onValueChange={(v) => setNewForm({ ...newForm, skillId: v })}>
                  <SelectTrigger id="skill-select"><SelectValue placeholder="Choose a skill" /></SelectTrigger>
                  <SelectContent>
                    {Object.entries(skillsById).map(([id, name]) => (
                      <SelectItem key={id} value={id}>{name}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div className="space-y-2">
                  <Label htmlFor="session-date">Date</Label>
                  <Input
                    id="session-date"
                    type="date"
                    value={newForm.sessionDate}
                    onChange={(e) => setNewForm({ ...newForm, sessionDate: e.target.value })}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="start-time">Start time</Label>
                  <Input
                    id="start-time"
                    type="time"
                    value={newForm.startTime}
                    onChange={(e) => setNewForm({ ...newForm, startTime: e.target.value })}
                  />
                </div>
                <div className="space-y-2">
                  <Label htmlFor="end-time">End time</Label>
                  <Input
                    id="end-time"
                    type="time"
                    value={newForm.endTime}
                    onChange={(e) => setNewForm({ ...newForm, endTime: e.target.value })}
                  />
                </div>
              </div>

              <div className="space-y-2">
                <Label htmlFor="mode-select">Mode</Label>
                <Select value={newForm.mode} onValueChange={(v) => setNewForm({ ...newForm, mode: v })}>
                  <SelectTrigger id="mode-select"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    <SelectItem value="ONLINE">Online</SelectItem>
                    <SelectItem value="OFFLINE">Offline</SelectItem>
                  </SelectContent>
                </Select>
              </div>

              <div className="space-y-2">
                <Label htmlFor="location-link">Location / link</Label>
                <Input
                  id="location-link"
                  value={newForm.locationOrLink}
                  onChange={(e) => setNewForm({ ...newForm, locationOrLink: e.target.value })}
                  placeholder="Zoom/Meet link, or leave blank for in-person"
                />
              </div>

              {newError && <p role="alert" className="text-sm text-destructive">{newError}</p>}

              <DialogFooter>
                <Button
                  type="submit"
                  disabled={!newForm.matchId || !newForm.teacherUserId || !newForm.skillId || !newForm.sessionDate || !newForm.startTime || !newForm.endTime}
                >
                  Create
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      </div>
```

Add the Reschedule dialog just after the `Tabs` block (still inside the outer `<div className="space-y-6">`):
```jsx
      <Dialog open={rescheduleTarget != null} onOpenChange={(open) => !open && setRescheduleTarget(null)}>
        <DialogContent>
          <form onSubmit={submitReschedule} className="space-y-4">
            <DialogHeader>
              <DialogTitle>Reschedule session</DialogTitle>
            </DialogHeader>
            <div className="grid grid-cols-3 gap-3">
              <div className="space-y-2">
                <Label htmlFor="resched-date">Date</Label>
                <Input
                  id="resched-date"
                  type="date"
                  value={rescheduleForm.sessionDate}
                  onChange={(e) => setRescheduleForm({ ...rescheduleForm, sessionDate: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="resched-start">Start time</Label>
                <Input
                  id="resched-start"
                  type="time"
                  value={rescheduleForm.startTime}
                  onChange={(e) => setRescheduleForm({ ...rescheduleForm, startTime: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="resched-end">End time</Label>
                <Input
                  id="resched-end"
                  type="time"
                  value={rescheduleForm.endTime}
                  onChange={(e) => setRescheduleForm({ ...rescheduleForm, endTime: e.target.value })}
                />
              </div>
            </div>
            {rescheduleError && <p role="alert" className="text-sm text-destructive">{rescheduleError}</p>}
            <DialogFooter>
              <Button type="submit">Save</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
```

Finally, add a "Reschedule" button to `SessionCard` alongside the existing Confirm/Complete/Cancel buttons, shown under the same condition as Cancel (`canCancel`):
```jsx
            {canCancel && <Button size="sm" variant="outline" onClick={() => openReschedule(s)}>Reschedule</Button>}
```
(`openReschedule` is defined on the outer `Sessions` component — `SessionCard` is defined inside `Sessions`, so it already closes over that function; no prop threading needed, matching how `runAction` is already used the same way.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- Sessions`
Expected: PASS — both tests green.

- [ ] **Step 5: Run the full frontend suite and build**

Run: `cd frontend && npm test`
Expected: PASS — 11 prior tests plus 1 new Task-2 test = 12.

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/Sessions.jsx frontend/src/pages/Sessions.test.jsx
git commit -m "feat: add new session and reschedule dialogs to the sessions page"
```

---

### Task 3: Credits summary + transaction history

**Files:**
- Modify: `frontend/src/pages/Sessions.jsx`
- Test: `frontend/src/pages/Sessions.test.jsx` (extend)

**Interfaces:**
- Consumes: `GET /api/me/credits` → `SkillCreditDto(int totalCredits, int creditsEarned, int creditsSpent)`, `GET /api/me/credits/transactions` → `CreditTransactionDto(Long id, Long sessionId, String transactionType, int amount, LocalDateTime transactionDate)[]`.
- Produces: a "Credits" section on the same `/sessions` page, above the Tabs, showing three stat cards (Total / Earned / Spent, matching `Dashboard.jsx`'s existing stat-card markup exactly) and a simple transaction history list below them.

**Business rule:** the transaction list renders each entry as a single row: signed amount (`+N` for `EARNED`-type transactions, `-N` for `SPENT`-type — check the actual `transactionType` string values by reading `backend/src/main/java/com/skillswap/entity/CreditTransactionType.java` before writing the sign logic, do not guess), the transaction date, and `Session #<sessionId>`. No pagination — this project's scale doesn't need it (YAGNI).

- [ ] **Step 1: Confirm the transaction type enum values**

Run: `cat /Users/devashish/Desktop/personal/final-project/backend/src/main/java/com/skillswap/entity/TransactionType.java`

Expected output: `public enum TransactionType { EARNED, SPENT }` — so the sign logic in Step 4 below uses `'EARNED'`/`'SPENT'` exactly as written. (Confirmed directly while writing this plan; this step exists so the implementer verifies it independently rather than trusting the plan's prose.)

- [ ] **Step 2: Extend the test for the credits section**

Add to `frontend/src/pages/Sessions.test.jsx`, extending the Task 1 test's mock (`api.get.mockImplementation`) with two more cases — `/me/credits` and `/me/credits/transactions` — so the existing test continues to pass with the new section present, and add a new assertion inside that same test (do not write a third test; the credits section renders on every page load, so it belongs in the existing render test):

```jsx
    if (url === '/me/credits') {
      return Promise.resolve({ data: { totalCredits: 12, creditsEarned: 15, creditsSpent: 3 } });
    }
    if (url === '/me/credits/transactions') {
      return Promise.resolve({
        data: [{ id: 1, sessionId: 10, transactionType: 'EARNED', amount: 1, transactionDate: '2026-07-20T09:00:00' }],
      });
    }
```

Add this assertion to the end of the first test (`renders upcoming sessions with resolved names, skill, and a confirm action`):
```jsx
  expect(await screen.findByText('12')).toBeInTheDocument();
  expect(screen.getByText(/session #10/i)).toBeInTheDocument();
```

(Replace `'EARNED'` above with whatever the actual enum constant is if Step 1 found a different name — e.g. if it's `CREDIT`/`DEBIT` instead, use that literal string in the mock.)

- [ ] **Step 3: Run test to verify it fails**

Run: `cd frontend && npm test -- Sessions`
Expected: FAIL — no credits section rendered yet.

- [ ] **Step 4: Add the credits section**

Modify `frontend/src/pages/Sessions.jsx`. Add state and a data-loading effect alongside the existing ones:
```jsx
  const [credits, setCredits] = useState(null);
  const [transactions, setTransactions] = useState([]);

  useEffect(() => {
    api.get('/me/credits').then((res) => setCredits(res.data)).catch(() => {});
    api.get('/me/credits/transactions').then((res) => setTransactions(res.data)).catch(() => {});
  }, []);
```

Add this section in the JSX, directly after the header `<div>` (New Session dialog) and before the page-level `error` paragraph:
```jsx
      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium text-muted-foreground">Total Credits</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-bold">{credits ? credits.totalCredits : '—'}</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium text-muted-foreground">Earned</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-bold">{credits ? credits.creditsEarned : '—'}</p>
          </CardContent>
        </Card>
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium text-muted-foreground">Spent</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-bold">{credits ? credits.creditsSpent : '—'}</p>
          </CardContent>
        </Card>
      </div>

      <div className="space-y-2">
        <h2 className="text-lg font-semibold">Transaction History</h2>
        {transactions.length === 0 && <p className="text-sm text-muted-foreground">No transactions yet.</p>}
        {transactions.map((t) => (
          <div key={t.id} className="flex items-center justify-between rounded-md border p-3 text-sm">
            <span>Session #{t.sessionId}</span>
            <span className="text-muted-foreground">{t.transactionDate}</span>
            <span className={t.transactionType === 'EARNED' ? 'text-emerald-600' : 'text-destructive'}>
              {t.transactionType === 'EARNED' ? '+' : '-'}{t.amount}
            </span>
          </div>
        ))}
      </div>
```

Add `CardHeader`, `CardTitle` to the existing `import { Card, CardContent } from '@/components/ui/card';` line (change it to `import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';`) — check `frontend/src/components/ui/card.jsx` exports these names (it does, per `Dashboard.jsx`'s existing usage).

If Step 1 found `transactionType` values other than `EARNED`/`SPENT`, adjust the `'EARNED'` comparisons above (both the `+`/`-` sign and the color class) to match the real enum constants — do not leave a comparison against a string that can never match.

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend && npm test -- Sessions`
Expected: PASS.

- [ ] **Step 6: Run the full frontend suite and build**

Run: `cd frontend && npm test`
Expected: PASS — 12 tests total (no new test file added, Task 1's test was extended in place).

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/Sessions.jsx frontend/src/pages/Sessions.test.jsx
git commit -m "feat: add credits summary and transaction history to the sessions page"
```

---

## Self-Review

**Spec coverage (UI Plan 3 slice):** Session scheduling from an accepted match, confirm/cancel/reschedule/complete lifecycle, upcoming/past/cancelled/all views → Task 1 + Task 2, spec §5.6.3 session scheduler. Credit balance + transaction history → Task 3, spec §5.6.5 credits. Video call = pasted meeting link (no SDK) → `locationOrLink` plain text `Input`, exactly per `CLAUDE.md`'s scope guardrail.

**Placeholder scan:** No TBD/TODO; every step has complete code. Task 1 intentionally ships without a New Session button (Task 2 adds it) — this is a real, working, testable slice on its own (viewing/managing sessions that already exist via seed/API), not a placeholder.

**Type consistency:** `CreateSessionRequest`'s exact field names (`matchId`, `teacherUserId`, `skillId`, `sessionDate`, `startTime`, `endTime`, `mode`, `locationOrLink`) match between the backend DTO and Task 2's `createSession` POST body. `RescheduleSessionRequest`'s fields (`sessionDate`, `startTime`, `endTime`) match Task 2's `submitReschedule` PUT body exactly (the reschedule form's field names are identical to the request body's field names, so the object can be sent directly with no remapping). `SessionDto`'s field names (`teacherUserId`, `learnerUserId`, `scheduledByUserId`, `skillId`, `sessionDate`, `startTime`, `endTime`, `mode`, `locationOrLink`, `status`) match exactly across Task 1's `SessionCard` and Task 2's dialogs.

**Scope check:** Three tasks, no backend changes, reuses every existing UI primitive from Plans 1–2. No new nav item added (Credits folds into the existing `/sessions` page, matching the existing `NAV_LINKS` in `Nav.jsx` which already has a "Sessions" entry and nothing named "Credits").

**Deliberate simplifications (flagged for the record):**
- New Session's skill `Select` lists the full catalog, not just skills the two matched users actually share — no backend endpoint exposes the overlap, and building one is out of scope for a frontend-only plan. If the user picks a skill neither party has recorded, `POST /api/sessions` will still succeed (the backend only validates that the skill exists in the catalog, not that either participant has it listed) — this mirrors the backend's actual validation, not a UI gap.
- Suggestions-vs-existing-sessions cross-referencing is not attempted (e.g. warning about scheduling conflicts/overlaps) — no endpoint supports it and the spec doesn't require it.
- Transaction history has no pagination — acceptable at this project's scale (YAGNI).
