# SkillSwap Hub — UI Plan 5: Admin

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An admin user can manage users (search/filter, activate/deactivate, grant a verified badge), manage the skill catalog and forum categories, review moderation queues (flagged reviews, moderated forum content), and view five read-only platform reports — all on a single `/admin` page.

**Architecture:** One new page (`Admin.jsx`) gated on `user.role === 'ADMIN'`, organized internally with a top-level `Tabs` (Users / Catalog / Moderation / Reports), reusing every existing primitive from prior plans. Four tasks, one per tab, plus a small, explicitly-justified addition to the already-merged `Forum.jsx` (Task 3): the backend's `moderatePost`/`moderateComment` admin endpoints currently have no caller anywhere in the frontend — without wiring them in somewhere, the admin moderation queues this plan builds would have no real way to ever become non-empty through the app itself. Task 3 adds two small admin-only "Moderate" buttons to the existing Forum post detail view to close that gap. No other backend changes — every endpoint this plan needs already exists and is live.

**Tech Stack:** React 18, Vite 5, Tailwind v4, shadcn/ui, Radix UI (reusing `Select`/`Dialog`/`Tabs`/`Card`/`Badge`/`Button`/`Input`/`Label` from prior plans — no new primitives).

## Global Constraints

- Frontend: path alias `@/*`, reuse existing primitives from `frontend/src/components/ui/` — no new shadcn primitive files.
- `Admin.jsx` performs its own role check as the first thing it does: if `user?.role !== 'ADMIN'`, render a plain "You don't have access to this page." message and make no API calls at all — do not rely solely on the Nav link being hidden (a non-admin can still type the URL directly), and do not build a separate reusable `AdminRoute` wrapper component for a single page (YAGNI).
- Every mutating frontend action (activate/deactivate user, grant verified badge, create/edit/delete skill, create/edit/delete category, unflag/delete review, delete moderated post/comment, moderate a post/comment) gives the user feedback on success and failure via the established inline `role="alert"` pattern — dialog-scoped actions render inside their dialog's form, list-scoped actions render a section-level alert.
- Several admin actions can legitimately conflict (deleting an in-use skill or a category with posts returns 409 with a specific, presentable message) — display `err.response?.data?.message` verbatim, do not paraphrase, matching the established pattern from every prior plan.
- Git author **Devashish Jadhav <jadhavom24@gmail.com>**. **Never** add a `Co-Authored-By` line or any AI/Claude attribution to commits. Conventional Commit messages. Commit at the end of every task.
- Verification: `npm test` (a render-level test per task's new behavior) + `npm run build`.

**Interfaces already available (all live, no backend changes in this plan):**
- `GET /api/admin/users?search=&active=` (both optional) → `AdminUserDto(Long id, String fullName, String email, String city, String role, boolean active, LocalDateTime createdDate)[]`. Requires ADMIN (403 otherwise).
- `PUT /api/admin/users/{id}/status` (body `{active: boolean}`) → `AdminUserDto`.
- `POST /api/admin/users/{id}/skills/{skillId}/verify` → 200, no body. Grants a `VERIFIED` badge for that skill to that user.
- `GET /api/skills` → `SkillDto(Long id, String skillName, String category, String description)[]` (public catalog read, already used by Skills.jsx/Sessions.jsx/Forum.jsx).
- `POST /api/admin/skills` / `PUT /api/admin/skills/{id}` (body `AdminSkillRequest(String skillName, String category, String description)`) → 201/200 + `SkillDto`.
- `DELETE /api/admin/skills/{id}` → 204. Errors: 409 "Skill is in use and cannot be deleted".
- `GET /api/forum/categories` → `ForumCategoryDto(Long id, String categoryName, String description)[]` (public read, already used by Forum.jsx).
- `POST /api/admin/forum/categories` / `PUT /api/admin/forum/categories/{id}` (body `CreateForumCategoryRequest(String categoryName, String description)`) → 201/200 + `ForumCategoryDto`.
- `DELETE /api/admin/forum/categories/{id}` → 204. Errors: 409 "Category has posts and cannot be deleted".
- `GET /api/admin/reviews/flagged` → `ReviewDto(Long id, Long sessionId, Long reviewerUserId, Long ratedUserId, int rating, String comments, boolean flagged, LocalDateTime createdDate)[]`.
- `PUT /api/admin/reviews/{id}/unflag` → 200, no body.
- `DELETE /api/admin/reviews/{id}` → 204.
- `GET /api/admin/forum/posts/moderated` → `ForumPostDto[]` (already includes `authorName`).
- `GET /api/admin/forum/comments/moderated` → `ForumCommentDto[]` (already includes `authorName`).
- `DELETE /api/admin/forum/posts/{id}` / `DELETE /api/admin/forum/comments/{id}` → 204. (No "unmoderate"/restore endpoint exists — once moderated, the only admin action is delete.)
- `PUT /api/admin/forum/posts/{id}/moderate` / `PUT /api/admin/forum/comments/{id}/moderate` → 200, no body. (Currently unused by any frontend code — Task 3 wires these in.)
- `GET /api/admin/reports/users-over-time` → `DailyCountDto(LocalDate date, long count)[]`.
- `GET /api/admin/reports/popular-skills` → `SkillPopularityDto(Long skillId, String skillName, long count)[]`.
- `GET /api/admin/reports/session-stats` → `SessionStatsDto(long pending, long confirmed, long completed, long cancelled)`.
- `GET /api/admin/reports/top-mentors` → `TopMentorDto(Long userId, String fullName, double avgRating, long reviewCount)[]`.
- `GET /api/admin/reports/active-categories` → `CategoryActivityDto(Long categoryId, String categoryName, long postCount)[]`.
- `GET /api/users/{id}` → `PublicProfileDto(Long id, String fullName, String city)` (added in UI Plan 2, used here to resolve reviewer/rated-user names on the flagged-reviews queue).
- Frontend: `useAuth()` → `{ user, ... }`, `user.role`. `api` axios client. `Select`/`Dialog`/`Tabs`/`Card`/`Badge`/`Button`/`Input`/`Label` primitives (all existing). `frontend/src/pages/Forum.jsx` (existing file, Task 3 makes a small addition to it). `frontend/src/App.jsx` (existing routes to extend). `frontend/src/components/layout/Nav.jsx` already has a `/admin` nav link, conditionally rendered for `user?.role === 'ADMIN'` (added in UI Plan 1, never routed until this plan).

---

### Task 1: Admin page shell + Users tab

**Files:**
- Create: `frontend/src/pages/Admin.jsx`
- Modify: `frontend/src/App.jsx` (add the `/admin` route)
- Test: `frontend/src/pages/Admin.test.jsx`

**Interfaces:**
- Consumes: `GET /api/admin/users`, `PUT /api/admin/users/{id}/status`, `POST /api/admin/users/{id}/skills/{skillId}/verify`, `GET /api/skills` (for the verified-badge skill picker), `useAuth()`. `Tabs`/`TabsList`/`TabsTrigger`/`TabsContent`, `Card`/`CardContent`, `Input`, `Button`, `Badge`, `Dialog`/`DialogTrigger`/`DialogContent`/`DialogHeader`/`DialogTitle`/`DialogFooter`, `Select`/`SelectTrigger`/`SelectValue`/`SelectContent`/`SelectItem`, `Label`.
- Produces: `Admin` page component routed at `/admin`, with a top-level `Tabs` (`users`/`catalog`/`moderation`/`reports`, default `users`). This task builds only the `Users` tab's content; the other three tabs render an empty placeholder-free `TabsContent` for now — wait, do not leave empty placeholder tabs. Instead: this task's `Tabs` only has a `TabsTrigger`/`TabsContent` pair for `"users"` — the other three `TabsTrigger`s are added by their own tasks (Tasks 2-4 each add one `TabsTrigger`/`TabsContent` pair to this same file, alongside the tab list already present in the file — read the current file first, do not recreate the whole `Tabs` block).

**Business rules:**
- On mount (only if `user.role === 'ADMIN'`): fetch `GET /api/admin/users` with no params (full list) and `GET /api/skills` (for the verified-badge dialog's skill picker).
- A search `Input` (by name/email — the backend's `search` param does substring matching server-side) + a status filter `Select` (`All` / `Active` / `Inactive`, mapping to `active=undefined` / `true` / `false`) — submitting either (Enter on the input, or changing the select) re-fetches `GET /api/admin/users?search=&active=`.
- Each user row: `fullName`, `email`, `city`, `role`, an active/inactive `Badge`, `createdDate`, an "Activate"/"Deactivate" `Button` (label and target boolean flip based on current `active`), and a "Grant Verified Badge" `Button` opening a small dialog (per-row) with a `Select` of the full skill catalog — submitting calls `POST /api/admin/users/{id}/skills/{skillId}/verify`, closes the dialog, and shows a temporary inline success/failure indication via the same section-level `role="alert"` paragraph used for the activate/deactivate action (one shared `error` state for the whole Users tab, matching the single-error-state-per-section precedent from `Matches.jsx`/`Forum.jsx`'s `PostDetail`).
- After any successful mutation (status toggle, verify), reload the user list with the currently-active search/filter values (not the unfiltered list) so the view stays consistent with what the admin was looking at.

- [ ] **Step 1: Write the failing test**

`frontend/src/pages/Admin.test.jsx`:
```jsx
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import api from '../api/client';
import Admin from './Admin';

vi.mock('../api/client');

test('renders the users tab with search results and a status toggle', async () => {
  localStorage.setItem('token', 'test-token');

  api.get.mockImplementation((url) => {
    if (url === '/me') {
      return Promise.resolve({ data: { id: 1, fullName: 'Admin User', email: 'admin@example.com', role: 'ADMIN' } });
    }
    if (url === '/admin/users') {
      return Promise.resolve({
        data: [
          { id: 2, fullName: 'Alex Tester', email: 'alex.tester@example.com', city: 'Pune', role: 'USER', active: true, createdDate: '2026-07-01T09:00:00' },
        ],
      });
    }
    if (url === '/skills') {
      return Promise.resolve({ data: [{ id: 4, skillName: 'Python', category: 'Technology', description: null }] });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Admin />
      </MemoryRouter>
    </AuthProvider>
  );

  expect(await screen.findByText('Alex Tester')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /deactivate/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /grant verified badge/i })).toBeInTheDocument();
});

test('shows an access-denied message for a non-admin user', async () => {
  localStorage.setItem('token', 'test-token');

  api.get.mockImplementation((url) => {
    if (url === '/me') {
      return Promise.resolve({ data: { id: 1, fullName: 'Regular User', email: 'user@example.com', role: 'USER' } });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Admin />
      </MemoryRouter>
    </AuthProvider>
  );

  expect(await screen.findByText(/don't have access/i)).toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- Admin`
Expected: FAIL — `Admin.jsx` doesn't exist.

- [ ] **Step 3: Write the Admin page (shell + Users tab)**

`frontend/src/pages/Admin.jsx`:
```jsx
import { useEffect, useState } from 'react';
import api from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Card, CardContent } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Select, SelectTrigger, SelectValue, SelectContent, SelectItem } from '@/components/ui/select';
import {
  Dialog,
  DialogTrigger,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';

function UsersTab() {
  const [users, setUsers] = useState([]);
  const [skills, setSkills] = useState([]);
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [error, setError] = useState('');
  const [verifyTarget, setVerifyTarget] = useState(null);
  const [verifySkillId, setVerifySkillId] = useState('');

  function loadUsers(searchValue = search, statusValue = statusFilter) {
    const params = {};
    if (searchValue) params.search = searchValue;
    if (statusValue === 'active') params.active = true;
    if (statusValue === 'inactive') params.active = false;
    api.get('/admin/users', { params }).then((res) => setUsers(res.data)).catch(() => {});
  }

  useEffect(() => {
    loadUsers();
    api.get('/skills').then((res) => setSkills(res.data)).catch(() => {});
  }, []);

  function runFilter(e) {
    e.preventDefault();
    loadUsers();
  }

  async function toggleStatus(u) {
    setError('');
    try {
      await api.put(`/admin/users/${u.id}/status`, { active: !u.active });
      loadUsers();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not update user status');
    }
  }

  function openVerify(u) {
    setVerifyTarget(u);
    setVerifySkillId('');
    setError('');
  }

  async function submitVerify(e) {
    e.preventDefault();
    setError('');
    try {
      await api.post(`/admin/users/${verifyTarget.id}/skills/${verifySkillId}/verify`);
      setVerifyTarget(null);
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not grant verified badge');
    }
  }

  return (
    <div className="space-y-4">
      <form onSubmit={runFilter} className="flex gap-2">
        <Input
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search by name or email..."
        />
        <Select value={statusFilter} onValueChange={(v) => { setStatusFilter(v); loadUsers(search, v); }}>
          <SelectTrigger className="w-40"><SelectValue /></SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All</SelectItem>
            <SelectItem value="active">Active</SelectItem>
            <SelectItem value="inactive">Inactive</SelectItem>
          </SelectContent>
        </Select>
        <Button type="submit" variant="outline">Search</Button>
      </form>

      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}

      <div className="space-y-3">
        {users.map((u) => (
          <Card key={u.id}>
            <CardContent className="flex items-center justify-between py-4">
              <div>
                <p className="font-medium">{u.fullName}</p>
                <p className="text-sm text-muted-foreground">{u.email} · {u.city ?? 'No city'} · {u.role}</p>
                <Badge variant={u.active ? 'default' : 'destructive'}>{u.active ? 'Active' : 'Inactive'}</Badge>
              </div>
              <div className="flex gap-2">
                <Button size="sm" variant="outline" onClick={() => toggleStatus(u)}>
                  {u.active ? 'Deactivate' : 'Activate'}
                </Button>
                <Button size="sm" onClick={() => openVerify(u)}>Grant Verified Badge</Button>
              </div>
            </CardContent>
          </Card>
        ))}
        {users.length === 0 && <p className="text-sm text-muted-foreground">No users found.</p>}
      </div>

      <Dialog open={verifyTarget != null} onOpenChange={(open) => !open && setVerifyTarget(null)}>
        <DialogContent>
          <form onSubmit={submitVerify} className="space-y-4">
            <DialogHeader>
              <DialogTitle>Grant verified badge</DialogTitle>
            </DialogHeader>
            <div className="space-y-2">
              <Label htmlFor="verify-skill">Skill</Label>
              <Select value={verifySkillId} onValueChange={setVerifySkillId}>
                <SelectTrigger id="verify-skill"><SelectValue placeholder="Choose a skill" /></SelectTrigger>
                <SelectContent>
                  {skills.map((s) => (
                    <SelectItem key={s.id} value={String(s.id)}>{s.skillName}</SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <DialogFooter>
              <Button type="submit" disabled={!verifySkillId}>Grant</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}

export default function Admin() {
  const { user } = useAuth();

  if (!user || user.role !== 'ADMIN') {
    return <p className="text-sm text-muted-foreground">You don't have access to this page.</p>;
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Admin</h1>
      <Tabs defaultValue="users">
        <TabsList>
          <TabsTrigger value="users">Users</TabsTrigger>
        </TabsList>
        <TabsContent value="users">
          <UsersTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- Admin`
Expected: PASS — both tests.

- [ ] **Step 5: Wire the `/admin` route**

In `frontend/src/App.jsx`, add the import `import Admin from './pages/Admin';` and add this `<Route>` alongside `/forum`:
```jsx
          <Route
            path="/admin"
            element={
              <ProtectedRoute>
                <AppShell>
                  <Admin />
                </AppShell>
              </ProtectedRoute>
            }
          />
```

- [ ] **Step 6: Run the full frontend suite and build**

Run: `cd frontend && npm test`
Expected: PASS — 14 prior tests plus 2 new `Admin.test.jsx` tests = 16.

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/Admin.jsx frontend/src/pages/Admin.test.jsx frontend/src/App.jsx
git commit -m "feat: add admin page with role gate and users tab"
```

---

### Task 2: Catalog tab — skill and category CRUD

**Files:**
- Modify: `frontend/src/pages/Admin.jsx`
- Test: `frontend/src/pages/Admin.test.jsx` (extend)

**Interfaces:**
- Consumes: `GET /api/skills`, `POST /api/admin/skills`, `PUT /api/admin/skills/{id}`, `DELETE /api/admin/skills/{id}`, `GET /api/forum/categories`, `POST /api/admin/forum/categories`, `PUT /api/admin/forum/categories/{id}`, `DELETE /api/admin/forum/categories/{id}`. Same primitives as Task 1, already imported.
- Produces: a `"catalog"` `TabsTrigger`/`TabsContent` pair added to the existing `Tabs` in `Admin.jsx`, containing two sections: "Skills" and "Forum Categories", each with its own list + Add/Edit dialog + Delete action.

**Business rule:** one shared dialog per section handles both create and edit — an `editingSkill`/`editingCategory` state (`null` = create mode, an object = edit mode) controls the dialog's title, initial field values, and whether submit calls `POST` or `PUT`. Delete shows its error inline (a section-level `role="alert"`, one per section) rather than paraphrasing the backend's 409 "in use"/"has posts" messages. After any successful create/edit/delete, reload that section's list.

- [ ] **Step 1: Extend the test**

Add to `frontend/src/pages/Admin.test.jsx`, as a new test (add the two new URL cases to a fresh `api.get.mockImplementation` scoped to this test, following this file's established per-test self-contained mock pattern):

```jsx
test('renders the catalog tab with skills and categories, and opens an edit dialog', async () => {
  localStorage.setItem('token', 'test-token');

  api.get.mockImplementation((url) => {
    if (url === '/me') {
      return Promise.resolve({ data: { id: 1, fullName: 'Admin User', email: 'admin@example.com', role: 'ADMIN' } });
    }
    if (url === '/admin/users') return Promise.resolve({ data: [] });
    if (url === '/skills') {
      return Promise.resolve({ data: [{ id: 4, skillName: 'Python', category: 'Technology', description: null }] });
    }
    if (url === '/forum/categories') {
      return Promise.resolve({ data: [{ id: 1, categoryName: 'General Discussion', description: 'Say hi' }] });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Admin />
      </MemoryRouter>
    </AuthProvider>
  );

  const user = userEvent.setup();
  const catalogTab = await screen.findByRole('tab', { name: /catalog/i });
  await user.click(catalogTab);

  expect(await screen.findByText('Python')).toBeInTheDocument();
  expect(screen.getByText('General Discussion')).toBeInTheDocument();

  const editButtons = screen.getAllByRole('button', { name: /^edit$/i });
  await user.click(editButtons[0]);
  expect(await screen.findByRole('button', { name: /save/i })).toBeInTheDocument();
});
```

Add `import userEvent from '@testing-library/user-event';` to the top of the file if not already present (check first).

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- Admin`
Expected: FAIL — no "Catalog" tab exists yet.

- [ ] **Step 3: Add the Catalog tab**

Modify `frontend/src/pages/Admin.jsx`. Add two new components above `export default function Admin()`:

```jsx
function SkillsSection() {
  const [skills, setSkills] = useState([]);
  const [editing, setEditing] = useState(null);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ skillName: '', category: '', description: '' });
  const [error, setError] = useState('');

  function loadSkills() {
    api.get('/skills').then((res) => setSkills(res.data)).catch(() => {});
  }

  useEffect(loadSkills, []);

  function openCreate() {
    setEditing(null);
    setForm({ skillName: '', category: '', description: '' });
    setError('');
    setOpen(true);
  }

  function openEdit(s) {
    setEditing(s);
    setForm({ skillName: s.skillName, category: s.category, description: s.description ?? '' });
    setError('');
    setOpen(true);
  }

  async function submit(e) {
    e.preventDefault();
    setError('');
    const body = { skillName: form.skillName, category: form.category, description: form.description || undefined };
    try {
      if (editing) {
        await api.put(`/admin/skills/${editing.id}`, body);
      } else {
        await api.post('/admin/skills', body);
      }
      setOpen(false);
      loadSkills();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not save skill');
    }
  }

  async function remove(s) {
    setError('');
    try {
      await api.delete(`/admin/skills/${s.id}`);
      loadSkills();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not delete skill');
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">Skills</h2>
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild>
            <Button size="sm" onClick={openCreate}>Add Skill</Button>
          </DialogTrigger>
          <DialogContent>
            <form onSubmit={submit} className="space-y-4">
              <DialogHeader>
                <DialogTitle>{editing ? 'Edit skill' : 'Add a skill'}</DialogTitle>
              </DialogHeader>
              <div className="space-y-2">
                <Label htmlFor="skill-name">Name</Label>
                <Input id="skill-name" value={form.skillName} onChange={(e) => setForm({ ...form, skillName: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="skill-category">Category</Label>
                <Input id="skill-category" value={form.category} onChange={(e) => setForm({ ...form, category: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="skill-description">Description</Label>
                <Input id="skill-description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
              </div>
              <DialogFooter>
                <Button type="submit" disabled={!form.skillName || !form.category}>{editing ? 'Save' : 'Add'}</Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      </div>
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      <div className="space-y-2">
        {skills.map((s) => (
          <Card key={s.id}>
            <CardContent className="flex items-center justify-between py-3">
              <div>
                <p className="font-medium">{s.skillName}</p>
                <p className="text-sm text-muted-foreground">{s.category}</p>
              </div>
              <div className="flex gap-2">
                <Button size="sm" variant="outline" onClick={() => openEdit(s)}>Edit</Button>
                <Button size="sm" variant="outline" onClick={() => remove(s)}>Delete</Button>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}

function CategoriesSection() {
  const [categories, setCategories] = useState([]);
  const [editing, setEditing] = useState(null);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ categoryName: '', description: '' });
  const [error, setError] = useState('');

  function loadCategories() {
    api.get('/forum/categories').then((res) => setCategories(res.data)).catch(() => {});
  }

  useEffect(loadCategories, []);

  function openCreate() {
    setEditing(null);
    setForm({ categoryName: '', description: '' });
    setError('');
    setOpen(true);
  }

  function openEdit(c) {
    setEditing(c);
    setForm({ categoryName: c.categoryName, description: c.description ?? '' });
    setError('');
    setOpen(true);
  }

  async function submit(e) {
    e.preventDefault();
    setError('');
    const body = { categoryName: form.categoryName, description: form.description || undefined };
    try {
      if (editing) {
        await api.put(`/admin/forum/categories/${editing.id}`, body);
      } else {
        await api.post('/admin/forum/categories', body);
      }
      setOpen(false);
      loadCategories();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not save category');
    }
  }

  async function remove(c) {
    setError('');
    try {
      await api.delete(`/admin/forum/categories/${c.id}`);
      loadCategories();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not delete category');
    }
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">Forum Categories</h2>
        <Dialog open={open} onOpenChange={setOpen}>
          <DialogTrigger asChild>
            <Button size="sm" onClick={openCreate}>Add Category</Button>
          </DialogTrigger>
          <DialogContent>
            <form onSubmit={submit} className="space-y-4">
              <DialogHeader>
                <DialogTitle>{editing ? 'Edit category' : 'Add a category'}</DialogTitle>
              </DialogHeader>
              <div className="space-y-2">
                <Label htmlFor="category-name">Name</Label>
                <Input id="category-name" value={form.categoryName} onChange={(e) => setForm({ ...form, categoryName: e.target.value })} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="category-description">Description</Label>
                <Input id="category-description" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
              </div>
              <DialogFooter>
                <Button type="submit" disabled={!form.categoryName}>{editing ? 'Save' : 'Add'}</Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      </div>
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
      <div className="space-y-2">
        {categories.map((c) => (
          <Card key={c.id}>
            <CardContent className="flex items-center justify-between py-3">
              <div>
                <p className="font-medium">{c.categoryName}</p>
                {c.description && <p className="text-sm text-muted-foreground">{c.description}</p>}
              </div>
              <div className="flex gap-2">
                <Button size="sm" variant="outline" onClick={() => openEdit(c)}>Edit</Button>
                <Button size="sm" variant="outline" onClick={() => remove(c)}>Delete</Button>
              </div>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}

function CatalogTab() {
  return (
    <div className="space-y-6">
      <SkillsSection />
      <CategoriesSection />
    </div>
  );
}
```

Add the `"catalog"` tab trigger/content to the existing `Tabs` block in `Admin()`:
```jsx
          <TabsTrigger value="catalog">Catalog</TabsTrigger>
```
(add right after the existing `<TabsTrigger value="users">Users</TabsTrigger>`), and:
```jsx
        <TabsContent value="catalog">
          <CatalogTab />
        </TabsContent>
```
(add right after the existing `<TabsContent value="users">...</TabsContent>`).

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- Admin`
Expected: PASS — all 3 Admin tests.

- [ ] **Step 5: Run the full frontend suite and build**

Run: `cd frontend && npm test`
Expected: PASS — 16 prior tests, `Admin.test.jsx` extended in place = 17.

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/Admin.jsx frontend/src/pages/Admin.test.jsx
git commit -m "feat: add admin catalog tab for skill and category CRUD"
```

---

### Task 3: Moderation tab + admin Moderate buttons on the Forum page

**Files:**
- Modify: `frontend/src/pages/Admin.jsx`
- Modify: `frontend/src/pages/Forum.jsx`
- Test: `frontend/src/pages/Admin.test.jsx` (extend), `frontend/src/pages/Forum.test.jsx` (extend)

**Interfaces:**
- Consumes (Admin.jsx): `GET /api/admin/reviews/flagged`, `PUT /api/admin/reviews/{id}/unflag`, `DELETE /api/admin/reviews/{id}`, `GET /api/admin/forum/posts/moderated`, `GET /api/admin/forum/comments/moderated`, `DELETE /api/admin/forum/posts/{id}`, `DELETE /api/admin/forum/comments/{id}`, `GET /api/users/{id}` (to resolve reviewer/rated-user names, same cache pattern as `Matches.jsx`/`Sessions.jsx`).
- Consumes (Forum.jsx): `PUT /api/admin/forum/posts/{id}/moderate`, `PUT /api/admin/forum/comments/{id}/moderate`, `useAuth()` (already imported in this file from UI Plan 4 Task 4).
- Produces: a `"moderation"` `TabsTrigger`/`TabsContent` pair added to `Admin.jsx`'s `Tabs`, with three sections: "Flagged Reviews", "Moderated Posts", "Moderated Comments". Two new admin-only buttons in `Forum.jsx`'s `PostDetail`: "Moderate Post" (next to the existing owner-only Delete) and, per comment, "Moderate" (next to the existing owner-only Delete) — both gated on `user?.role === 'ADMIN'`, both distinct from the existing owner-gated Delete buttons (a post/comment's author and an admin are not mutually exclusive, so both buttons can appear together for an admin viewing their own post — that's correct, not a bug).

**Business rules:**
- Flagged Reviews section: fetch `GET /api/admin/reviews/flagged` on mount; resolve `reviewerUserId`/`ratedUserId` names via the same `profiles`-cache-by-distinct-id pattern used in `Matches.jsx`/`Sessions.jsx` (a `profiles` state object, populated by `GET /api/users/{id}` for every not-yet-seen id across both fields). Each row shows rating, comments, reviewer name, rated-user name, createdDate, an "Unflag" button and a "Delete" button. Both reload the list on success; failures render via a section-level `role="alert"`.
- Moderated Posts / Moderated Comments sections: fetch their respective `GET .../moderated` endpoints on mount. Each row shows the post/comment's existing `authorName`/content/title fields (already present on the DTO, no name resolution needed) and a single "Delete" button (no restore action exists in the backend). Reload on success; failures via a section-level `role="alert"` (one shared per section, or one shared across all three sections — either is acceptable; pick one and be consistent, matching this app's established single-error-state-per-view precedent).
- `Forum.jsx`'s "Moderate Post" button: on click, `PUT /api/admin/forum/posts/{id}/moderate`; on success, call `onBack()` (the post is now filtered out of every public GET, so staying on its detail view would show stale content); on failure, use the existing shared `error` state already present in `PostDetail`. The "Moderate" button per comment: on click, `PUT /api/admin/forum/comments/{id}/moderate`; on success, refetch the comments list (`loadComments()`, the same function `deleteComment` already calls) so the moderated comment disappears from the visible thread; on failure, same shared `error` state.

- [ ] **Step 1: Extend Admin.test.jsx**

Add a new test to `frontend/src/pages/Admin.test.jsx`:

```jsx
test('renders the moderation tab with flagged reviews and moderated forum content', async () => {
  localStorage.setItem('token', 'test-token');

  api.get.mockImplementation((url) => {
    if (url === '/me') {
      return Promise.resolve({ data: { id: 1, fullName: 'Admin User', email: 'admin@example.com', role: 'ADMIN' } });
    }
    if (url === '/admin/users') return Promise.resolve({ data: [] });
    if (url === '/skills') return Promise.resolve({ data: [] });
    if (url === '/forum/categories') return Promise.resolve({ data: [] });
    if (url === '/admin/reviews/flagged') {
      return Promise.resolve({
        data: [{ id: 9, sessionId: 10, reviewerUserId: 2, ratedUserId: 3, rating: 1, comments: 'Rude', flagged: true, createdDate: '2026-07-01T09:00:00' }],
      });
    }
    if (url === '/users/2') return Promise.resolve({ data: { id: 2, fullName: 'Reviewer Two', city: null } });
    if (url === '/users/3') return Promise.resolve({ data: { id: 3, fullName: 'Rated Three', city: null } });
    if (url === '/admin/forum/posts/moderated') {
      return Promise.resolve({ data: [{ id: 5, categoryId: 1, userId: 2, authorName: 'Reviewer Two', title: 'Bad post', content: 'x', upvoteCount: 0, commentCount: 0, createdDate: '2026-07-01T09:00:00' }] });
    }
    if (url === '/admin/forum/comments/moderated') {
      return Promise.resolve({ data: [] });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Admin />
      </MemoryRouter>
    </AuthProvider>
  );

  const user = userEvent.setup();
  const moderationTab = await screen.findByRole('tab', { name: /moderation/i });
  await user.click(moderationTab);

  expect(await screen.findByText('Reviewer Two')).toBeInTheDocument();
  expect(screen.getByText('Rated Three')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /unflag/i })).toBeInTheDocument();
  expect(screen.getByText('Bad post')).toBeInTheDocument();
});
```

- [ ] **Step 2: Extend Forum.test.jsx**

Add a new test to `frontend/src/pages/Forum.test.jsx` asserting that an ADMIN-role user sees a "Moderate Post" button on a post's detail view (reuse this file's existing category/post/comment mock shape from its current test, changing only the mocked `/me` response's `role` to `'ADMIN'` and adding a distinct `user.id` that does NOT own the post, so this test also proves the Moderate button is independent of ownership):

```jsx
test('shows a Moderate Post button to an admin viewing someone else\'s post', async () => {
  localStorage.setItem('token', 'test-token');

  api.get.mockImplementation((url) => {
    if (url === '/me') {
      return Promise.resolve({ data: { id: 99, fullName: 'Admin User', email: 'admin@example.com', role: 'ADMIN' } });
    }
    if (url === '/forum/categories') {
      return Promise.resolve({ data: [{ id: 1, categoryName: 'General', description: 'General chat' }] });
    }
    if (url === '/forum/categories/1/posts') {
      return Promise.resolve({
        data: [{ id: 5, categoryId: 1, userId: 2, authorName: 'Blake Mentor', title: 'Welcome thread', content: 'Say hi!', upvoteCount: 2, commentCount: 0, createdDate: '2026-07-01T09:00:00' }],
      });
    }
    if (url === '/forum/posts/5') {
      return Promise.resolve({ data: { id: 5, categoryId: 1, userId: 2, authorName: 'Blake Mentor', title: 'Welcome thread', content: 'Say hi!', upvoteCount: 2, commentCount: 0, createdDate: '2026-07-01T09:00:00' } });
    }
    if (url === '/forum/posts/5/comments') {
      return Promise.resolve({ data: [] });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Forum />
      </MemoryRouter>
    </AuthProvider>
  );

  const user = userEvent.setup();
  const postLink = await screen.findByRole('button', { name: /welcome thread/i });
  await user.click(postLink);

  expect(await screen.findByRole('button', { name: /moderate post/i })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /^delete$/i })).not.toBeInTheDocument();
});
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `cd frontend && npm test -- Admin Forum`
Expected: FAIL — no "Moderation" tab in Admin, no "Moderate Post" button in Forum.

- [ ] **Step 4: Add the Moderation tab to Admin.jsx**

Add a new component above `export default function Admin()`:
```jsx
function ModerationTab() {
  const { user } = useAuth();
  const [reviews, setReviews] = useState([]);
  const [profiles, setProfiles] = useState({});
  const [posts, setPosts] = useState([]);
  const [comments, setComments] = useState([]);
  const [error, setError] = useState('');

  function loadFlaggedReviews() {
    api.get('/admin/reviews/flagged').then((res) => setReviews(res.data)).catch(() => {});
  }
  function loadModeratedPosts() {
    api.get('/admin/forum/posts/moderated').then((res) => setPosts(res.data)).catch(() => {});
  }
  function loadModeratedComments() {
    api.get('/admin/forum/comments/moderated').then((res) => setComments(res.data)).catch(() => {});
  }

  useEffect(() => {
    loadFlaggedReviews();
    loadModeratedPosts();
    loadModeratedComments();
  }, []);

  useEffect(() => {
    const ids = new Set();
    reviews.forEach((r) => {
      if (!(r.reviewerUserId in profiles)) ids.add(r.reviewerUserId);
      if (!(r.ratedUserId in profiles)) ids.add(r.ratedUserId);
    });
    ids.forEach((id) => {
      api.get(`/users/${id}`).then((res) => {
        setProfiles((prev) => ({ ...prev, [id]: res.data }));
      }).catch(() => {});
    });
  }, [reviews]);

  async function unflag(r) {
    setError('');
    try {
      await api.put(`/admin/reviews/${r.id}/unflag`);
      loadFlaggedReviews();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not unflag review');
    }
  }

  async function deleteReview(r) {
    setError('');
    try {
      await api.delete(`/admin/reviews/${r.id}`);
      loadFlaggedReviews();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not delete review');
    }
  }

  async function deletePost(p) {
    setError('');
    try {
      await api.delete(`/admin/forum/posts/${p.id}`);
      loadModeratedPosts();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not delete post');
    }
  }

  async function deleteComment(c) {
    setError('');
    try {
      await api.delete(`/admin/forum/comments/${c.id}`);
      loadModeratedComments();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not delete comment');
    }
  }

  return (
    <div className="space-y-6">
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}

      <div className="space-y-3">
        <h2 className="text-lg font-semibold">Flagged Reviews</h2>
        {reviews.map((r) => (
          <Card key={r.id}>
            <CardContent className="flex items-center justify-between py-3">
              <div>
                <p className="text-sm">
                  {profiles[r.reviewerUserId]?.fullName ?? `User #${r.reviewerUserId}`} rated{' '}
                  {profiles[r.ratedUserId]?.fullName ?? `User #${r.ratedUserId}`} {r.rating}/5
                </p>
                {r.comments && <p className="text-sm text-muted-foreground">{r.comments}</p>}
              </div>
              <div className="flex gap-2">
                <Button size="sm" variant="outline" onClick={() => unflag(r)}>Unflag</Button>
                <Button size="sm" variant="outline" onClick={() => deleteReview(r)}>Delete</Button>
              </div>
            </CardContent>
          </Card>
        ))}
        {reviews.length === 0 && <p className="text-sm text-muted-foreground">No flagged reviews.</p>}
      </div>

      <div className="space-y-3">
        <h2 className="text-lg font-semibold">Moderated Posts</h2>
        {posts.map((p) => (
          <Card key={p.id}>
            <CardContent className="flex items-center justify-between py-3">
              <div>
                <p className="font-medium">{p.title}</p>
                <p className="text-sm text-muted-foreground">by {p.authorName}</p>
              </div>
              <Button size="sm" variant="outline" onClick={() => deletePost(p)}>Delete</Button>
            </CardContent>
          </Card>
        ))}
        {posts.length === 0 && <p className="text-sm text-muted-foreground">No moderated posts.</p>}
      </div>

      <div className="space-y-3">
        <h2 className="text-lg font-semibold">Moderated Comments</h2>
        {comments.map((c) => (
          <Card key={c.id}>
            <CardContent className="flex items-center justify-between py-3">
              <div>
                <p className="text-sm">{c.commentText}</p>
                <p className="text-sm text-muted-foreground">by {c.authorName}</p>
              </div>
              <Button size="sm" variant="outline" onClick={() => deleteComment(c)}>Delete</Button>
            </CardContent>
          </Card>
        ))}
        {comments.length === 0 && <p className="text-sm text-muted-foreground">No moderated comments.</p>}
      </div>
    </div>
  );
}
```

Add `import { useAuth } from '../auth/AuthContext';` to `Admin.jsx`'s imports (not present yet — Tasks 1-2 didn't need it).

Add the `"moderation"` tab trigger/content to the existing `Tabs`:
```jsx
          <TabsTrigger value="moderation">Moderation</TabsTrigger>
```
and:
```jsx
        <TabsContent value="moderation">
          <ModerationTab />
        </TabsContent>
```

- [ ] **Step 5: Add the admin Moderate buttons to Forum.jsx**

Modify `frontend/src/pages/Forum.jsx`'s `PostDetail` component. Add two handlers alongside the existing `upvote`/`deletePost`/`deleteComment`/`addComment`:
```jsx
  async function moderatePost() {
    setError('');
    try {
      await api.put(`/admin/forum/posts/${postId}/moderate`);
      onBack();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not moderate post');
    }
  }

  async function moderateComment(commentId) {
    setError('');
    try {
      await api.put(`/admin/forum/comments/${commentId}/moderate`);
      loadComments();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not moderate comment');
    }
  }
```

Add a "Moderate Post" button next to the existing owner-only Delete button, gated on `user?.role === 'ADMIN'` (independent of the existing `user.userId === user.id` ownership check — both can render together):
```jsx
            {user && user.role === 'ADMIN' && (
              <Button size="sm" variant="outline" onClick={moderatePost}>Moderate Post</Button>
            )}
```
(Place this as a sibling of the existing owner-gated Delete button, inside the same `<div className="flex gap-2 pt-2">`.)

Add a "Moderate" button next to each comment's existing owner-only Delete button, same admin gate:
```jsx
              {user && user.role === 'ADMIN' && (
                <Button size="sm" variant="ghost" onClick={() => moderateComment(c.id)}>Moderate</Button>
              )}
```
(Place this as a sibling of the existing per-comment owner-gated Delete button.)

- [ ] **Step 6: Run tests to verify they pass**

Run: `cd frontend && npm test -- Admin Forum`
Expected: PASS — all Admin tests, all Forum tests.

- [ ] **Step 7: Run the full frontend suite and build**

Run: `cd frontend && npm test`
Expected: PASS — 17 prior tests plus 1 new Admin test and 1 new Forum test = 19.

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/pages/Admin.jsx frontend/src/pages/Admin.test.jsx frontend/src/pages/Forum.jsx frontend/src/pages/Forum.test.jsx
git commit -m "feat: add admin moderation tab and wire moderate actions into the forum"
```

---

### Task 4: Reports tab

**Files:**
- Modify: `frontend/src/pages/Admin.jsx`
- Test: `frontend/src/pages/Admin.test.jsx` (extend)

**Interfaces:**
- Consumes: `GET /api/admin/reports/users-over-time`, `GET /api/admin/reports/popular-skills`, `GET /api/admin/reports/session-stats`, `GET /api/admin/reports/top-mentors`, `GET /api/admin/reports/active-categories`.
- Produces: a `"reports"` `TabsTrigger`/`TabsContent` pair added to `Admin.jsx`'s `Tabs`, containing five read-only sections. No charting library — every report renders as a simple list or, for `session-stats` (a single object, not a list), four stat cards matching the established `Dashboard.jsx`/`Sessions.jsx` stat-card pattern. This is a deliberate simplification: the project has no charting dependency and adding one for five small admin-only reports is out of scope (YAGNI).

**Business rule:** all five fetches run once on mount (no filters, no interactivity — these are read-only summaries). Empty lists show a simple "No data yet." message per section, consistent with every other list in this app.

- [ ] **Step 1: Extend the test**

Add a new test to `frontend/src/pages/Admin.test.jsx`:

```jsx
test('renders the reports tab with all five reports', async () => {
  localStorage.setItem('token', 'test-token');

  api.get.mockImplementation((url) => {
    if (url === '/me') {
      return Promise.resolve({ data: { id: 1, fullName: 'Admin User', email: 'admin@example.com', role: 'ADMIN' } });
    }
    if (url === '/admin/users') return Promise.resolve({ data: [] });
    if (url === '/skills') return Promise.resolve({ data: [] });
    if (url === '/forum/categories') return Promise.resolve({ data: [] });
    if (url === '/admin/reviews/flagged') return Promise.resolve({ data: [] });
    if (url === '/admin/forum/posts/moderated') return Promise.resolve({ data: [] });
    if (url === '/admin/forum/comments/moderated') return Promise.resolve({ data: [] });
    if (url === '/admin/reports/users-over-time') {
      return Promise.resolve({ data: [{ date: '2026-07-01', count: 3 }] });
    }
    if (url === '/admin/reports/popular-skills') {
      return Promise.resolve({ data: [{ skillId: 4, skillName: 'Python', count: 5 }] });
    }
    if (url === '/admin/reports/session-stats') {
      return Promise.resolve({ data: { pending: 1, confirmed: 2, completed: 3, cancelled: 0 } });
    }
    if (url === '/admin/reports/top-mentors') {
      return Promise.resolve({ data: [{ userId: 2, fullName: 'Blake Mentor', avgRating: 5.0, reviewCount: 1 }] });
    }
    if (url === '/admin/reports/active-categories') {
      return Promise.resolve({ data: [{ categoryId: 1, categoryName: 'General Discussion', postCount: 2 }] });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Admin />
      </MemoryRouter>
    </AuthProvider>
  );

  const user = userEvent.setup();
  const reportsTab = await screen.findByRole('tab', { name: /reports/i });
  await user.click(reportsTab);

  expect(await screen.findByText('Blake Mentor')).toBeInTheDocument();
  expect(screen.getByText('General Discussion')).toBeInTheDocument();
  expect(screen.getAllByText('Python').length).toBeGreaterThan(0);
  expect(screen.getByText('3')).toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- Admin`
Expected: FAIL — no "Reports" tab exists yet.

- [ ] **Step 3: Add the Reports tab**

Add a new component above `export default function Admin()`:
```jsx
function ReportsTab() {
  const [usersOverTime, setUsersOverTime] = useState([]);
  const [popularSkills, setPopularSkills] = useState([]);
  const [sessionStats, setSessionStats] = useState(null);
  const [topMentors, setTopMentors] = useState([]);
  const [activeCategories, setActiveCategories] = useState([]);

  useEffect(() => {
    api.get('/admin/reports/users-over-time').then((res) => setUsersOverTime(res.data)).catch(() => {});
    api.get('/admin/reports/popular-skills').then((res) => setPopularSkills(res.data)).catch(() => {});
    api.get('/admin/reports/session-stats').then((res) => setSessionStats(res.data)).catch(() => {});
    api.get('/admin/reports/top-mentors').then((res) => setTopMentors(res.data)).catch(() => {});
    api.get('/admin/reports/active-categories').then((res) => setActiveCategories(res.data)).catch(() => {});
  }, []);

  return (
    <div className="space-y-6">
      <div className="space-y-2">
        <h2 className="text-lg font-semibold">Session Stats</h2>
        <div className="grid gap-4 sm:grid-cols-4">
          {[
            ['Pending', sessionStats?.pending],
            ['Confirmed', sessionStats?.confirmed],
            ['Completed', sessionStats?.completed],
            ['Cancelled', sessionStats?.cancelled],
          ].map(([label, value]) => (
            <Card key={label}>
              <CardContent className="py-4">
                <p className="text-sm text-muted-foreground">{label}</p>
                <p className="text-3xl font-bold">{value ?? '—'}</p>
              </CardContent>
            </Card>
          ))}
        </div>
      </div>

      <div className="space-y-2">
        <h2 className="text-lg font-semibold">Users Over Time</h2>
        {usersOverTime.map((d) => (
          <div key={d.date} className="flex justify-between border-b py-1 text-sm">
            <span>{d.date}</span>
            <span>{d.count}</span>
          </div>
        ))}
        {usersOverTime.length === 0 && <p className="text-sm text-muted-foreground">No data yet.</p>}
      </div>

      <div className="space-y-2">
        <h2 className="text-lg font-semibold">Popular Skills</h2>
        {popularSkills.map((s) => (
          <div key={s.skillId} className="flex justify-between border-b py-1 text-sm">
            <span>{s.skillName}</span>
            <span>{s.count}</span>
          </div>
        ))}
        {popularSkills.length === 0 && <p className="text-sm text-muted-foreground">No data yet.</p>}
      </div>

      <div className="space-y-2">
        <h2 className="text-lg font-semibold">Top Mentors</h2>
        {topMentors.map((m) => (
          <div key={m.userId} className="flex justify-between border-b py-1 text-sm">
            <span>{m.fullName}</span>
            <span>{m.avgRating.toFixed(1)} ({m.reviewCount})</span>
          </div>
        ))}
        {topMentors.length === 0 && <p className="text-sm text-muted-foreground">No data yet.</p>}
      </div>

      <div className="space-y-2">
        <h2 className="text-lg font-semibold">Active Categories</h2>
        {activeCategories.map((c) => (
          <div key={c.categoryId} className="flex justify-between border-b py-1 text-sm">
            <span>{c.categoryName}</span>
            <span>{c.postCount} posts</span>
          </div>
        ))}
        {activeCategories.length === 0 && <p className="text-sm text-muted-foreground">No data yet.</p>}
      </div>
    </div>
  );
}
```

Add the `"reports"` tab trigger/content to the existing `Tabs`:
```jsx
          <TabsTrigger value="reports">Reports</TabsTrigger>
```
and:
```jsx
        <TabsContent value="reports">
          <ReportsTab />
        </TabsContent>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- Admin`
Expected: PASS — all 5 Admin tests.

- [ ] **Step 5: Run the full frontend suite and build**

Run: `cd frontend && npm test`
Expected: PASS — 19 prior tests, `Admin.test.jsx` extended in place = 20.

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/Admin.jsx frontend/src/pages/Admin.test.jsx
git commit -m "feat: add admin reports tab"
```

---

## Self-Review

**Spec coverage (UI Plan 5 slice):** Admin user management (search/filter/activate-deactivate) → Task 1, spec §5.6.9 admin. Skill catalog + forum category CRUD → Task 2. Content moderation (flagged reviews, moderated forum content) → Task 3, plus the Forum.jsx bridge that makes the moderation queues actually reachable through the app (a genuine gap found while designing this plan: the backend's moderate endpoints had no caller anywhere in the frontend before this task). Platform reports (5 reports) → Task 4, spec §5.6.10 reports. Verified-badge granting → Task 1 (grouped with Users since it's a per-user admin action, not a catalog action).

**Placeholder scan:** No TBD/TODO; every step has complete code. Tasks 1-4 each add exactly one `TabsTrigger`/`TabsContent` pair to a shared `Tabs` — no task ships a dead/empty tab waiting for a future task to fill it, since each task's own tab is fully functional the moment it lands.

**Type consistency:** `AdminUserDto`'s fields (`fullName`, `email`, `city`, `role`, `active`, `createdDate`) match Task 1's rendering exactly. `AdminSkillRequest`'s fields (`skillName`, `category`, `description`) and `CreateForumCategoryRequest`'s fields (`categoryName`, `description`) match Task 2's POST/PUT bodies exactly. `ReviewDto`'s fields (`reviewerUserId`, `ratedUserId`, `rating`, `comments`) and the moderated `ForumPostDto`/`ForumCommentDto` shapes match Task 3's rendering exactly. `DailyCountDto`/`SkillPopularityDto`/`SessionStatsDto`/`TopMentorDto`/`CategoryActivityDto` field names match Task 4's rendering exactly.

**Scope check:** Four tasks, one small and explicitly-justified addition to an already-merged file (`Forum.jsx`), no backend changes, reuses every existing UI primitive from Plans 1-4. The `/admin` route and its Nav link (present since UI Plan 1 but unrouted until now) are finally connected.

**Deliberate simplifications (flagged for the record):**
- No charting library for the Reports tab — five small admin-only reports rendered as plain lists/stat-cards, not worth a new dependency (YAGNI).
- `loadUsers` takes explicit `(searchValue, statusValue)` parameters (defaulting to current state) rather than reading `search`/`statusFilter` purely from closure — this avoids a stale-closure bug where the status `Select`'s `onValueChange` would otherwise fire `loadUsers` before its own `setStatusFilter` update had been re-rendered into the closure it captures. The `Select` (no separate submit step) calls `loadUsers(search, v)` directly with the freshly-selected value; the search `Input` (submitted via the form) calls plain `loadUsers()`, which reads current state at that point — both paths are correct without a `useEffect` dependency on every keystroke.
- The Moderation tab's flagged-reviews name resolution reuses the established `profiles`-cache pattern for consistency with the rest of the app, at the cost of N extra `GET /api/users/{id}` calls for a queue that is expected to be small and admin-only — acceptable at this project's scale.
- No pagination anywhere in the Admin page (user list, skill list, category list, moderation queues, reports) — consistent with every prior plan's stated scale assumption.
