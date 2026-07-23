# SkillSwap Hub — UI Plan 4: Reviews + Badges + Forums

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Users can leave a rating+comment review after a completed session, see their own average rating and earned skill badges on the Dashboard, and browse/search/post/comment/upvote in the community forum.

**Architecture:** Four tasks, no backend changes — every endpoint is already live. Reviews extend the existing Sessions page (a "Leave a Review" dialog on COMPLETED session cards). Rating + Badges extend the existing Dashboard page. Forums is a brand-new `/forum` page (the nav link already exists from UI Plan 1 but has never been routed) split into two tasks: list/search/create (Task 3), then post detail/comments/upvote/delete (Task 4).

**Tech Stack:** React 18, Vite 5, Tailwind v4, shadcn/ui, Radix UI (reusing `Select`/`Dialog`/`Tabs`/`Card`/`Badge`/`Button`/`Input`/`Label` from prior plans — no new primitives).

## Global Constraints

- Frontend: path alias `@/*`, reuse existing primitives from `frontend/src/components/ui/` — no new shadcn primitive files. Multi-line free text (review comments, post content, comment text) uses a plain native `<textarea>` styled inline to match `Input`'s existing Tailwind classes (`flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50`) — this is a deliberate choice, not a gap: a single multi-line field per form doesn't justify a new shadcn `Textarea` primitive file (YAGNI), and copying `Input`'s exact classes keeps it visually consistent without one.
- Every mutating frontend action (leave review, create post, add comment, upvote, delete post, delete comment) gives the user feedback on success and failure via the established inline `role="alert"` pattern — dialog-scoped actions render their error inside that dialog's form (matching `Skills.jsx`/`Sessions.jsx` precedent), list/detail-scoped actions render a page-level or section-level alert.
- Several backend actions are designed to be attempted-and-rejected rather than pre-validated client-side, and the UI follows that exactly rather than inventing client-side pre-checks: reviewing an already-reviewed session returns 409 "You have already reviewed this session"; upvoting an already-upvoted post returns 409 "You have already upvoted this post". Both messages are already user-presentable as-is — display `err.response?.data?.message` directly, do not paraphrase it.
- Ownership-gated actions (delete post, delete comment) are only ever shown as buttons to the resource's own `userId === user.id` — matching this app's established 403-vs-404 convention (a non-owner attempting delete would get 404, not 403, so the correct UI behavior is to never expose the button at all rather than handle a 404 gracefully).
- Git author **Devashish Jadhav <jadhavom24@gmail.com>**. **Never** add a `Co-Authored-By` line or any AI/Claude attribution to commits. Conventional Commit messages. Commit at the end of every task.
- Verification: `npm test` (a render-level test per task's new behavior) + `npm run build`.

**Interfaces already available (all live, no backend changes in this plan):**
- `POST /api/sessions/{id}/review` (body `{rating: 1-5, comments?}`) → 201 + `ReviewDto`. Errors: 404 (not a participant), 409 (session not COMPLETED, or already reviewed by this user).
- `GET /api/users/{id}/rating` → `RatingSummaryDto(double averageRating, long reviewCount)`.
- `GET /api/users/{id}/badges` → `BadgeDto(Long id, Long skillId, String skillName, String badgeType, LocalDateTime awardedDate)[]`.
- `GET /api/forum/categories` → `ForumCategoryDto(Long id, String categoryName, String description)[]`.
- `GET /api/forum/categories/{id}/posts` → `ForumPostDto(Long id, Long categoryId, Long userId, String authorName, String title, String content, long upvoteCount, long commentCount, LocalDateTime createdDate)[]`.
- `POST /api/forum/categories/{id}/posts` (body `{title, content}`) → 201 + `ForumPostDto`.
- `GET /api/forum/posts/search?keyword=` → `ForumPostDto[]`.
- `GET /api/forum/posts/{id}` → `ForumPostDto`.
- `DELETE /api/forum/posts/{id}` → 204. Errors: 404 (not found, or not the owner — ownership is folded into 404 per this app's convention).
- `GET /api/forum/posts/{id}/comments` → `ForumCommentDto(Long id, Long postId, Long userId, String authorName, String commentText, LocalDateTime createdDate)[]`.
- `POST /api/forum/posts/{id}/comments` (body `{commentText}`) → 201 + `ForumCommentDto`.
- `DELETE /api/forum/comments/{id}` → 204. Errors: 404 (not found or not owner).
- `POST /api/forum/posts/{id}/upvote` → 201 + `ForumPostDto` (the updated post, with incremented `upvoteCount`). Errors: 409 (already upvoted by this user).
- Frontend: `useAuth()` → `{ user, ... }`, `user.id`/`user.fullName`. `api` axios client. `Select`/`Dialog`/`Tabs`/`Card`/`Badge`/`Button`/`Input`/`Label` primitives (all existing, from UI Plans 1-2). `frontend/src/pages/Sessions.jsx` and `frontend/src/pages/Dashboard.jsx` (existing files to extend). `frontend/src/App.jsx` (existing routes to extend — `/skills`, `/matches`, `/sessions` already wrapped in `ProtectedRoute` → `AppShell`, same pattern for `/forum`). `frontend/src/components/layout/Nav.jsx` already has a `/forum` nav link (added in UI Plan 1, never routed until this plan).

---

### Task 1: Leave a Review dialog on completed sessions

**Files:**
- Modify: `frontend/src/pages/Sessions.jsx`
- Test: `frontend/src/pages/Sessions.test.jsx` (extend)

**Interfaces:**
- Consumes: `POST /api/sessions/{id}/review`, `Dialog`/`DialogTrigger`/`DialogContent`/`DialogHeader`/`DialogTitle`/`DialogDescription`/`DialogFooter`, `Select`/`SelectTrigger`/`SelectValue`/`SelectContent`/`SelectItem`, `Button`, `Label` (all already imported in this file from UI Plan 3).
- Produces: a "Leave a Review" button on each `COMPLETED` session card, opening a review dialog.

**Business rule:** Only sessions with `status === 'COMPLETED'` show the button. Clicking it opens a dialog with a "Rating" `Select` (values `"1"`–`"5"`, labels `"1 - Poor"` … `"5 - Excellent"`) and a "Comments" `<textarea>` (optional, matches `CreateReviewRequest.comments`'s `@Size(max = 255)` — do not add client-side length validation beyond what the backend already enforces via its own 400 response, since duplicating a validation boundary is unnecessary here: the backend's error message on violation is already presentable as-is). Submit sends `POST /api/sessions/{id}/review` with `{rating: Number(rating), comments: comments || undefined}`. On success: close the dialog, add the session's id to a `reviewedSessionIds` state `Set` (component state, same optimistic-hide pattern as `Matches.jsx`'s `requestedIds`) so the button becomes disabled/relabeled `"Reviewed"` for the rest of this page session, matching the pattern already established for match requests — a page reload always reflects the true server state (no `reviewed` flag exists on `SessionDto` to persist this across reloads, which is a known, deliberate limitation, not a bug). On failure (e.g. the 409 "already reviewed" case): render `err.response?.data?.message` verbatim inside the dialog via the established `role="alert"` pattern — do not paraphrase or replace the backend's message.

- [ ] **Step 1: Write the failing test**

Add to `frontend/src/pages/Sessions.test.jsx`, as a new test below the existing ones (do not modify the existing tests' mocks — add a new `api.get`/`api.post` mock case set scoped to this test only, following the same self-contained `api.get.mockImplementation`/`api.post.mockImplementation` pattern already used per-test in this file):

```jsx
test('shows Leave a Review on a completed session and submits a rating', async () => {
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
            sessionDate: '2026-07-01', startTime: '10:00:00', endTime: '11:00:00',
            mode: 'ONLINE', locationOrLink: 'https://meet.example/abc', status: 'COMPLETED',
            createdDate: '2026-06-20T09:00:00',
          },
        ],
      });
    }
    if (url === '/users/2') {
      return Promise.resolve({ data: { id: 2, fullName: 'Teacher Two', city: 'Pune' } });
    }
    if (url === '/matches') {
      return Promise.resolve({ data: [] });
    }
    if (url === '/me/credits') {
      return Promise.resolve({ data: { totalCredits: 9, creditsEarned: 0, creditsSpent: 1 } });
    }
    if (url === '/me/credits/transactions') {
      return Promise.resolve({ data: [] });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  api.post.mockImplementation((url, body) => {
    if (url === '/sessions/10/review') {
      return Promise.resolve({ data: { id: 1, sessionId: 10, reviewerUserId: 1, ratedUserId: 2, rating: body.rating, comments: body.comments ?? null, flagged: false, createdDate: '2026-07-02T09:00:00' } });
    }
    return Promise.reject(new Error('unexpected post url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Sessions />
      </MemoryRouter>
    </AuthProvider>
  );

  const user = userEvent.setup();
  const reviewButton = await screen.findByRole('button', { name: /leave a review/i });
  await user.click(reviewButton);

  const ratingSelect = await screen.findByLabelText(/rating/i);
  await user.click(ratingSelect);
  const fiveOption = await screen.findByRole('option', { name: /5/ });
  await user.click(fiveOption);

  const submit = screen.getByRole('button', { name: /submit review/i });
  await user.click(submit);

  await waitFor(() => expect(screen.getByRole('button', { name: /reviewed/i })).toBeDisabled());
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- Sessions`
Expected: FAIL — no "Leave a Review" button exists yet.

- [ ] **Step 3: Add the Leave a Review dialog**

Modify `frontend/src/pages/Sessions.jsx`. Add new state alongside the existing state declarations:
```jsx
  const [reviewedSessionIds, setReviewedSessionIds] = useState(new Set());
  const [reviewTarget, setReviewTarget] = useState(null);
  const [reviewForm, setReviewForm] = useState({ rating: '', comments: '' });
  const [reviewError, setReviewError] = useState('');
```

Add this handler alongside the other action handlers (`runAction`, `createSession`, etc.):
```jsx
  function openReview(s) {
    setReviewTarget(s);
    setReviewForm({ rating: '', comments: '' });
    setReviewError('');
  }

  async function submitReview(e) {
    e.preventDefault();
    setReviewError('');
    try {
      await api.post(`/sessions/${reviewTarget.id}/review`, {
        rating: Number(reviewForm.rating),
        comments: reviewForm.comments || undefined,
      });
      setReviewedSessionIds((prev) => new Set(prev).add(reviewTarget.id));
      setReviewTarget(null);
    } catch (err) {
      setReviewError(err.response?.data?.message ?? 'Could not submit review');
    }
  }
```

Add the Review dialog JSX right after the existing Reschedule `<Dialog>` block (same top-level placement, still inside the outer `<div className="space-y-6">`):
```jsx
      <Dialog open={reviewTarget != null} onOpenChange={(open) => !open && setReviewTarget(null)}>
        <DialogContent>
          <form onSubmit={submitReview} className="space-y-4">
            <DialogHeader>
              <DialogTitle>Leave a review</DialogTitle>
              <DialogDescription>Rate this session and optionally leave a comment.</DialogDescription>
            </DialogHeader>
            <div className="space-y-2">
              <Label htmlFor="review-rating">Rating</Label>
              <Select value={reviewForm.rating} onValueChange={(v) => setReviewForm({ ...reviewForm, rating: v })}>
                <SelectTrigger id="review-rating"><SelectValue placeholder="Choose a rating" /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="1">1 - Poor</SelectItem>
                  <SelectItem value="2">2 - Fair</SelectItem>
                  <SelectItem value="3">3 - Good</SelectItem>
                  <SelectItem value="4">4 - Very good</SelectItem>
                  <SelectItem value="5">5 - Excellent</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label htmlFor="review-comments">Comments</Label>
              <textarea
                id="review-comments"
                value={reviewForm.comments}
                onChange={(e) => setReviewForm({ ...reviewForm, comments: e.target.value })}
                rows={3}
                className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                placeholder="Optional"
              />
            </div>
            {reviewError && <p role="alert" className="text-sm text-destructive">{reviewError}</p>}
            <DialogFooter>
              <Button type="submit" disabled={!reviewForm.rating}>Submit Review</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
```

Add a "Leave a Review" / "Reviewed" button to `SessionCard`'s action row, shown only for `COMPLETED` sessions:
```jsx
            {s.status === 'COMPLETED' && (
              <Button
                size="sm"
                variant="outline"
                disabled={reviewedSessionIds.has(s.id)}
                onClick={() => openReview(s)}
              >
                {reviewedSessionIds.has(s.id) ? 'Reviewed' : 'Leave a Review'}
              </Button>
            )}
```
(Place this alongside the existing `canConfirm`/`canComplete`/`canCancel` buttons in the same action `<div>` — `SessionCard` already closes over `openReview` and `reviewedSessionIds` since both live in the enclosing `Sessions` component, same pattern as `runAction`.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- Sessions`
Expected: PASS.

- [ ] **Step 5: Run the full frontend suite and build**

Run: `cd frontend && npm test`
Expected: PASS — 12 prior tests plus this new test = 13.

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/Sessions.jsx frontend/src/pages/Sessions.test.jsx
git commit -m "feat: add leave-a-review dialog to completed sessions"
```

---

### Task 2: My Rating + My Badges on Dashboard

**Files:**
- Modify: `frontend/src/pages/Dashboard.jsx`
- Test: `frontend/src/pages/Dashboard.test.jsx` (extend — read this file first to see its existing mock/assertions before adding to them)

**Interfaces:**
- Consumes: `GET /api/users/{id}/rating` → `RatingSummaryDto(averageRating, reviewCount)`, `GET /api/users/{id}/badges` → `BadgeDto[]`, both called with the current user's own `profile.id` once `profile` has loaded. `Card`/`CardHeader`/`CardTitle`/`CardContent`, `Badge` (existing).
- Produces: a 4th stat card ("Rating") in the existing stat grid, and a new "My Badges" section below it.

**Business rule:** the Rating card shows `averageRating.toFixed(1)` (e.g. `"4.5"`) with `reviewCount` sessions in smaller text underneath (e.g. `"3 reviews"`), or `"—"` before load / `"No ratings yet"` if `reviewCount === 0`. The Badges section renders one `Badge` component per earned badge reading `"{skillName} · {badgeType}"` (e.g. `"Python · INTERMEDIATE"`); if the badges list is empty, show `"No badges yet — complete more sessions to earn your first."`. Both new fetches depend on `profile.id`, so they must run in an effect that fires only after `profile` is set (not in the same unconditional mount-time effect that fetches `/me` itself) — do not attempt to call these endpoints with the wrong ID or before the id is known.

- [ ] **Step 1: Read the existing Dashboard test**

Read `frontend/src/pages/Dashboard.test.jsx` in full — you need to see its current mock shape and assertions before extending them, since this task adds new API calls the existing mock doesn't yet handle (its `api.get.mockImplementation` will need two new `if` branches for `/users/1/rating` and `/users/1/badges`, assuming the existing test's mocked `/me` response uses id `1` — confirm the actual id used and match it, don't assume).

- [ ] **Step 2: Extend the test**

Add two branches to the existing test's `api.get.mockImplementation` (using whatever user id the existing `/me` mock already returns):
```jsx
    if (url === '/users/1/rating') {
      return Promise.resolve({ data: { averageRating: 4.5, reviewCount: 3 } });
    }
    if (url === '/users/1/badges') {
      return Promise.resolve({ data: [{ id: 1, skillId: 4, skillName: 'Python', badgeType: 'INTERMEDIATE', awardedDate: '2026-07-01T09:00:00' }] });
    }
```
Add these assertions to the end of the existing test body:
```jsx
  expect(await screen.findByText('4.5')).toBeInTheDocument();
  expect(screen.getByText(/python/i)).toBeInTheDocument();
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd frontend && npm test -- Dashboard`
Expected: FAIL — no Rating card or Badges section rendered yet.

- [ ] **Step 4: Add the Rating card and Badges section**

Modify `frontend/src/pages/Dashboard.jsx`. Add `Badge` to the existing import line (change `import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';` — leave it as-is — and add a new line `import { Badge } from '@/components/ui/badge';`). Add new state:
```jsx
  const [rating, setRating] = useState(null);
  const [badges, setBadges] = useState([]);
```
Add a new effect that runs once `profile` is available (do not add these calls to the existing unconditional mount effect — `profile.id` doesn't exist until that fetch resolves):
```jsx
  useEffect(() => {
    if (!profile) return;
    api.get(`/users/${profile.id}/rating`).then((res) => setRating(res.data)).catch(() => {});
    api.get(`/users/${profile.id}/badges`).then((res) => setBadges(res.data)).catch(() => {});
  }, [profile]);
```
Change the stat grid's className from `grid gap-4 sm:grid-cols-3` to `grid gap-4 sm:grid-cols-2 lg:grid-cols-4`, and add a 4th `Card` right after the existing "Unread Notifications" one:
```jsx
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium text-muted-foreground">Rating</CardTitle>
          </CardHeader>
          <CardContent>
            {rating && rating.reviewCount > 0 ? (
              <>
                <p className="text-3xl font-bold">{rating.averageRating.toFixed(1)}</p>
                <p className="text-sm text-muted-foreground">{rating.reviewCount} reviews</p>
              </>
            ) : (
              <p className="text-3xl font-bold">{rating ? '—' : '—'}</p>
            )}
            {rating && rating.reviewCount === 0 && <p className="text-sm text-muted-foreground">No ratings yet</p>}
          </CardContent>
        </Card>
```
Add a Badges section after the stat grid `</div>` and before the existing logout `<button>`:
```jsx
      <div className="space-y-2">
        <h2 className="text-lg font-semibold">My Badges</h2>
        {badges.length === 0 ? (
          <p className="text-sm text-muted-foreground">No badges yet — complete more sessions to earn your first.</p>
        ) : (
          <div className="flex flex-wrap gap-2">
            {badges.map((b) => (
              <Badge key={b.id} variant="secondary">{b.skillName} · {b.badgeType}</Badge>
            ))}
          </div>
        )}
      </div>
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd frontend && npm test -- Dashboard`
Expected: PASS.

- [ ] **Step 6: Run the full frontend suite and build**

Run: `cd frontend && npm test`
Expected: PASS — 13 prior tests, `Dashboard.test.jsx` extended in place (no new file), so still 13.

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/Dashboard.jsx frontend/src/pages/Dashboard.test.jsx
git commit -m "feat: show rating and earned badges on the dashboard"
```

---

### Task 3: Forum page — categories, posts, search, new post

**Files:**
- Create: `frontend/src/pages/Forum.jsx`
- Modify: `frontend/src/App.jsx` (add the `/forum` route)
- Test: `frontend/src/pages/Forum.test.jsx`

**Interfaces:**
- Consumes: `GET /api/forum/categories`, `GET /api/forum/categories/{id}/posts`, `GET /api/forum/posts/search?keyword=`, `POST /api/forum/categories/{id}/posts`. `Tabs`/`TabsList`/`TabsTrigger`/`TabsContent`, `Card`/`CardContent`, `Dialog`/`DialogTrigger`/`DialogContent`/`DialogHeader`/`DialogTitle`/`DialogFooter`, `Select`/`SelectTrigger`/`SelectValue`/`SelectContent`/`SelectItem`, `Input`, `Label`, `Button`, `Badge`.
- Produces: `Forum` page component, routed at `/forum`. Post rows in this task are NOT clickable yet (no detail view) — Task 4 adds that. This task's post cards render title/author/upvote-count/comment-count/date as plain static content, nothing more; do not add an inert `onClick` handler with no effect.

**Business rules:**
- On mount, fetch `GET /api/forum/categories` once. Render one `TabsTrigger` per category; the active tab's `TabsContent` fetches (and caches per-category, so switching tabs back doesn't re-fetch) `GET /api/forum/categories/{id}/posts`.
- A search `Input` + "Search" `Button` above the tabs: submitting calls `GET /api/forum/posts/search?keyword=<value>` and, while a search is active (non-empty last-submitted keyword), replaces the Tabs entirely with a flat "Search results for '<keyword>'" list plus a "Clear search" button that restores the category tabs view.
- "New Post" dialog (a `DialogTrigger` button in the page header, matching `Skills.jsx`/`Sessions.jsx`'s established header-button-opens-dialog pattern): fields "Category" (`Select` built from the fetched categories), "Title" (`Input`), "Content" (`<textarea>`, same inline-styled pattern as Task 1). Submit disabled until category and title and content are all non-empty. On success: close dialog, reset form, if the new post's category is the currently active tab, refetch that category's posts (call the same per-category fetch function); reload always reflects truth regardless. On failure: error inside the dialog via `role="alert"`.

- [ ] **Step 1: Write the failing test**

`frontend/src/pages/Forum.test.jsx`:
```jsx
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import api from '../api/client';
import Forum from './Forum';

vi.mock('../api/client');

test('renders forum categories and posts for the active category', async () => {
  localStorage.setItem('token', 'test-token');

  api.get.mockImplementation((url) => {
    if (url === '/me') {
      return Promise.resolve({ data: { id: 1, fullName: 'Me', email: 'me@example.com', role: 'USER' } });
    }
    if (url === '/forum/categories') {
      return Promise.resolve({ data: [{ id: 1, categoryName: 'General', description: 'General chat' }] });
    }
    if (url === '/forum/categories/1/posts') {
      return Promise.resolve({
        data: [{ id: 5, categoryId: 1, userId: 2, authorName: 'Blake Mentor', title: 'Welcome thread', content: 'Say hi!', upvoteCount: 2, commentCount: 1, createdDate: '2026-07-01T09:00:00' }],
      });
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

  expect(await screen.findByRole('tab', { name: 'General' })).toBeInTheDocument();
  expect(await screen.findByText('Welcome thread')).toBeInTheDocument();
  expect(screen.getByText(/blake mentor/i)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /new post/i })).toBeInTheDocument();
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- Forum`
Expected: FAIL — `Forum.jsx` doesn't exist.

- [ ] **Step 3: Write the Forum page**

`frontend/src/pages/Forum.jsx`:
```jsx
import { useEffect, useState } from 'react';
import api from '../api/client';
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

function PostRow({ p }) {
  return (
    <Card>
      <CardContent className="space-y-1 py-4">
        <p className="font-medium">{p.title}</p>
        <p className="text-sm text-muted-foreground">
          by {p.authorName} · {p.createdDate}
        </p>
        <div className="flex gap-2">
          <Badge variant="secondary">{p.upvoteCount} upvotes</Badge>
          <Badge variant="secondary">{p.commentCount} comments</Badge>
        </div>
      </CardContent>
    </Card>
  );
}

export default function Forum() {
  const [categories, setCategories] = useState([]);
  const [postsByCategory, setPostsByCategory] = useState({});
  const [activeCategoryId, setActiveCategoryId] = useState(null);
  const [searchKeyword, setSearchKeyword] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [searchResults, setSearchResults] = useState([]);

  useEffect(() => {
    api.get('/forum/categories').then((res) => {
      setCategories(res.data);
      if (res.data.length > 0) setActiveCategoryId(String(res.data[0].id));
    }).catch(() => {});
  }, []);

  function loadCategoryPosts(categoryId) {
    api.get(`/forum/categories/${categoryId}/posts`).then((res) => {
      setPostsByCategory((prev) => ({ ...prev, [categoryId]: res.data }));
    }).catch(() => {});
  }

  useEffect(() => {
    if (activeCategoryId && !(activeCategoryId in postsByCategory)) {
      loadCategoryPosts(activeCategoryId);
    }
  }, [activeCategoryId]);

  async function runSearch(e) {
    e.preventDefault();
    const keyword = searchInput.trim();
    if (!keyword) return;
    try {
      const res = await api.get('/forum/posts/search', { params: { keyword } });
      setSearchResults(res.data);
      setSearchKeyword(keyword);
    } catch {
      setSearchResults([]);
      setSearchKeyword(keyword);
    }
  }

  function clearSearch() {
    setSearchKeyword('');
    setSearchInput('');
    setSearchResults([]);
  }

  const [newOpen, setNewOpen] = useState(false);
  const [newForm, setNewForm] = useState({ categoryId: '', title: '', content: '' });
  const [newError, setNewError] = useState('');

  async function createPost(e) {
    e.preventDefault();
    setNewError('');
    try {
      await api.post(`/forum/categories/${newForm.categoryId}/posts`, {
        title: newForm.title,
        content: newForm.content,
      });
      setNewOpen(false);
      setNewForm({ categoryId: '', title: '', content: '' });
      if (newForm.categoryId === activeCategoryId) loadCategoryPosts(activeCategoryId);
    } catch (err) {
      setNewError(err.response?.data?.message ?? 'Could not create post');
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-semibold">Forum</h1>
        <Dialog open={newOpen} onOpenChange={setNewOpen}>
          <DialogTrigger asChild>
            <Button>New Post</Button>
          </DialogTrigger>
          <DialogContent>
            <form onSubmit={createPost} className="space-y-4">
              <DialogHeader>
                <DialogTitle>Create a post</DialogTitle>
              </DialogHeader>
              <div className="space-y-2">
                <Label htmlFor="post-category">Category</Label>
                <Select value={newForm.categoryId} onValueChange={(v) => setNewForm({ ...newForm, categoryId: v })}>
                  <SelectTrigger id="post-category"><SelectValue placeholder="Choose a category" /></SelectTrigger>
                  <SelectContent>
                    {categories.map((c) => (
                      <SelectItem key={c.id} value={String(c.id)}>{c.categoryName}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="post-title">Title</Label>
                <Input
                  id="post-title"
                  value={newForm.title}
                  onChange={(e) => setNewForm({ ...newForm, title: e.target.value })}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="post-content">Content</Label>
                <textarea
                  id="post-content"
                  value={newForm.content}
                  onChange={(e) => setNewForm({ ...newForm, content: e.target.value })}
                  rows={4}
                  className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
                />
              </div>
              {newError && <p role="alert" className="text-sm text-destructive">{newError}</p>}
              <DialogFooter>
                <Button
                  type="submit"
                  disabled={!newForm.categoryId || !newForm.title || !newForm.content}
                >
                  Post
                </Button>
              </DialogFooter>
            </form>
          </DialogContent>
        </Dialog>
      </div>

      <form onSubmit={runSearch} className="flex gap-2">
        <Input
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          placeholder="Search posts..."
        />
        <Button type="submit" variant="outline">Search</Button>
      </form>

      {searchKeyword ? (
        <div className="space-y-3">
          <div className="flex items-center justify-between">
            <p className="text-sm text-muted-foreground">Search results for "{searchKeyword}"</p>
            <Button size="sm" variant="ghost" onClick={clearSearch}>Clear search</Button>
          </div>
          {searchResults.map((p) => <PostRow key={p.id} p={p} />)}
          {searchResults.length === 0 && <p className="text-sm text-muted-foreground">No posts found.</p>}
        </div>
      ) : (
        <Tabs value={activeCategoryId ?? undefined} onValueChange={setActiveCategoryId}>
          <TabsList>
            {categories.map((c) => (
              <TabsTrigger key={c.id} value={String(c.id)}>{c.categoryName}</TabsTrigger>
            ))}
          </TabsList>
          {categories.map((c) => (
            <TabsContent key={c.id} value={String(c.id)} className="space-y-3">
              {(postsByCategory[c.id] ?? []).map((p) => <PostRow key={p.id} p={p} />)}
              {(postsByCategory[c.id] ?? []).length === 0 && (
                <p className="text-sm text-muted-foreground">No posts yet in this category.</p>
              )}
            </TabsContent>
          ))}
        </Tabs>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- Forum`
Expected: PASS.

- [ ] **Step 5: Wire the `/forum` route**

In `frontend/src/App.jsx`, add the import `import Forum from './pages/Forum';` and add this `<Route>` alongside `/sessions`:
```jsx
          <Route
            path="/forum"
            element={
              <ProtectedRoute>
                <AppShell>
                  <Forum />
                </AppShell>
              </ProtectedRoute>
            }
          />
```

- [ ] **Step 6: Run the full frontend suite and build**

Run: `cd frontend && npm test`
Expected: PASS — 13 prior tests plus `Forum.test.jsx` = 14.

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/pages/Forum.jsx frontend/src/pages/Forum.test.jsx frontend/src/App.jsx
git commit -m "feat: add forum page with categories, posts, search, and new post"
```

---

### Task 4: Forum post detail — comments, upvote, delete

**Files:**
- Modify: `frontend/src/pages/Forum.jsx`
- Test: `frontend/src/pages/Forum.test.jsx` (extend)

**Interfaces:**
- Consumes: `GET /api/forum/posts/{id}`, `GET /api/forum/posts/{id}/comments`, `POST /api/forum/posts/{id}/comments`, `DELETE /api/forum/posts/{id}`, `DELETE /api/forum/comments/{id}`, `POST /api/forum/posts/{id}/upvote`, `useAuth()` (for ownership checks).
- Produces: clicking a post's title opens a detail view (replacing the list view via a `selectedPostId` state, no new route) showing the full post, an Upvote button, a Delete button (only if `post.userId === user.id`), a comment list (each with a Delete button if `comment.userId === user.id`), an "Add comment" form, and a "Back to Forum" button.

**Business rules:**
- `PostRow` (from Task 3) becomes clickable: wrap its title in a `<button>` (styled as a link, e.g. `className="font-medium underline-offset-4 hover:underline text-left"`) that calls `onSelect={() => setSelectedPostId(p.id)}` — pass `onSelect` down from `Forum` as a prop to `PostRow` (it's called from both the category-tabs list and the search-results list, so both call sites pass the same handler).
- When `selectedPostId` is set, `Forum` renders `PostDetail` instead of the search/tabs view entirely (a top-level `if (selectedPostId) return <PostDetail .../>` early return inside the component, or an `{selectedPostId ? <PostDetail/> : <>...</>}` conditional — either is fine, pick whichever keeps the component readable).
- `PostDetail` fetches the post (`GET /api/forum/posts/{id}`) and comments (`GET /api/forum/posts/{id}/comments`) on mount / whenever `selectedPostId` changes.
- Upvote button: disabled if the post is already in a local `upvotedPostIds` `Set` (component state in `Forum`, passed down, same optimistic pattern as `Matches.jsx`'s `requestedIds` and Task 1's `reviewedSessionIds`) — on click, `POST /api/forum/posts/{id}/upvote`, on success add to the set and update the displayed `upvoteCount` from the response body's `upvoteCount` (the endpoint returns the updated post), on failure (409 already-upvoted) show `err.response?.data?.message` verbatim via a `role="alert"` paragraph in the detail view.
- Delete post button (owner only): on click, `DELETE /api/forum/posts/{id}`; on success, clear `selectedPostId` (returns to the list) and refresh that category's post list (or search results, whichever was active) so the deleted post disappears; on failure show the error via the same `role="alert"` paragraph.
- Comments: each rendered with `authorName`, `commentText`, `createdDate`, and (owner only) a "Delete" button calling `DELETE /api/forum/comments/{id}`, which on success removes it from local state (refetch the comments list, simplest and consistent with this app's reload-after-mutate convention elsewhere).
- Add-comment form: a `<textarea>` (same inline-styled pattern) + "Comment" button; on submit, `POST /api/forum/posts/{id}/comments` with `{commentText}`, on success clear the textarea and refetch comments, on failure show the error inline (same `role="alert"` paragraph, this view's shared error state covers upvote/delete-post/add-comment/delete-comment — one shared error state for the whole detail view is acceptable here, matching `Matches.jsx`'s single-error-state-per-page precedent).

- [ ] **Step 1: Extend the test**

Add to `frontend/src/pages/Forum.test.jsx`, extending the existing test's `api.get.mockImplementation` with two more cases (`/forum/posts/5` and `/forum/posts/5/comments`) and adding assertions for the detail view at the end of the existing test:

```jsx
    if (url === '/forum/posts/5') {
      return Promise.resolve({ data: { id: 5, categoryId: 1, userId: 2, authorName: 'Blake Mentor', title: 'Welcome thread', content: 'Say hi!', upvoteCount: 2, commentCount: 1, createdDate: '2026-07-01T09:00:00' } });
    }
    if (url === '/forum/posts/5/comments') {
      return Promise.resolve({ data: [{ id: 20, postId: 5, userId: 1, authorName: 'Me', commentText: 'Hi there!', createdDate: '2026-07-01T10:00:00' }] });
    }
```

Add `import userEvent from '@testing-library/user-event';` to the top of the file if not already present. Add this to the end of the existing test body:
```jsx
  const postLink = screen.getByRole('button', { name: /welcome thread/i });
  await user.click(postLink);

  expect(await screen.findByText('Say hi!')).toBeInTheDocument();
  expect(screen.getByText('Hi there!')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /upvote/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /back to forum/i })).toBeInTheDocument();
```
(Add `const user = userEvent.setup();` near the top of the test body, right after `render(...)`, if the test doesn't already have one from a prior step.)

- [ ] **Step 2: Run test to verify it fails**

Run: `cd frontend && npm test -- Forum`
Expected: FAIL — post titles aren't clickable yet, no detail view exists.

- [ ] **Step 3: Add the post detail view**

Modify `frontend/src/pages/Forum.jsx`. Change `PostRow` to accept and use an `onSelect` prop:
```jsx
function PostRow({ p, onSelect }) {
  return (
    <Card>
      <CardContent className="space-y-1 py-4">
        <button
          type="button"
          onClick={() => onSelect(p.id)}
          className="font-medium underline-offset-4 hover:underline text-left"
        >
          {p.title}
        </button>
        <p className="text-sm text-muted-foreground">
          by {p.authorName} · {p.createdDate}
        </p>
        <div className="flex gap-2">
          <Badge variant="secondary">{p.upvoteCount} upvotes</Badge>
          <Badge variant="secondary">{p.commentCount} comments</Badge>
        </div>
      </CardContent>
    </Card>
  );
}
```

Add a new `PostDetail` component in the same file, above `export default function Forum()`:
```jsx
function PostDetail({ postId, onBack, onDeleted }) {
  const { user } = useAuth();
  const [post, setPost] = useState(null);
  const [comments, setComments] = useState([]);
  const [upvotedPostIds, setUpvotedPostIds] = useState(new Set());
  const [commentText, setCommentText] = useState('');
  const [error, setError] = useState('');

  function loadComments() {
    api.get(`/forum/posts/${postId}/comments`).then((res) => setComments(res.data)).catch(() => {});
  }

  useEffect(() => {
    api.get(`/forum/posts/${postId}`).then((res) => setPost(res.data)).catch(() => {});
    loadComments();
  }, [postId]);

  async function upvote() {
    setError('');
    try {
      const res = await api.post(`/forum/posts/${postId}/upvote`);
      setPost(res.data);
      setUpvotedPostIds((prev) => new Set(prev).add(postId));
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not upvote');
    }
  }

  async function deletePost() {
    setError('');
    try {
      await api.delete(`/forum/posts/${postId}`);
      onDeleted();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not delete post');
    }
  }

  async function deleteComment(commentId) {
    setError('');
    try {
      await api.delete(`/forum/comments/${commentId}`);
      loadComments();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not delete comment');
    }
  }

  async function addComment(e) {
    e.preventDefault();
    setError('');
    try {
      await api.post(`/forum/posts/${postId}/comments`, { commentText });
      setCommentText('');
      loadComments();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not add comment');
    }
  }

  if (!post) return null;

  return (
    <div className="space-y-4">
      <Button variant="ghost" size="sm" onClick={onBack}>Back to Forum</Button>
      <Card>
        <CardContent className="space-y-2 py-4">
          <h2 className="text-xl font-semibold">{post.title}</h2>
          <p className="text-sm text-muted-foreground">by {post.authorName} · {post.createdDate}</p>
          <p>{post.content}</p>
          <div className="flex gap-2 pt-2">
            <Button size="sm" disabled={upvotedPostIds.has(postId)} onClick={upvote}>
              {upvotedPostIds.has(postId) ? 'Upvoted' : `Upvote (${post.upvoteCount})`}
            </Button>
            {user && post.userId === user.id && (
              <Button size="sm" variant="outline" onClick={deletePost}>Delete</Button>
            )}
          </div>
        </CardContent>
      </Card>

      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}

      <div className="space-y-3">
        <h3 className="font-semibold">Comments</h3>
        {comments.map((c) => (
          <Card key={c.id}>
            <CardContent className="flex items-center justify-between py-3">
              <div>
                <p className="text-sm">{c.commentText}</p>
                <p className="text-xs text-muted-foreground">{c.authorName} · {c.createdDate}</p>
              </div>
              {user && c.userId === user.id && (
                <Button size="sm" variant="ghost" onClick={() => deleteComment(c.id)}>Delete</Button>
              )}
            </CardContent>
          </Card>
        ))}
        {comments.length === 0 && <p className="text-sm text-muted-foreground">No comments yet.</p>}

        <form onSubmit={addComment} className="space-y-2">
          <textarea
            value={commentText}
            onChange={(e) => setCommentText(e.target.value)}
            rows={2}
            className="flex w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50"
            placeholder="Add a comment..."
          />
          <Button type="submit" size="sm" disabled={!commentText.trim()}>Comment</Button>
        </form>
      </div>
    </div>
  );
}
```

Add the import `import { useAuth } from '../auth/AuthContext';` to the top of `Forum.jsx`.

In the `Forum` component, add `const [selectedPostId, setSelectedPostId] = useState(null);` alongside the existing state. Pass `onSelect={setSelectedPostId}` to every `<PostRow ... />` usage (both the search-results `.map()` and the category-tabs `.map()`). Wrap the component's return value:
```jsx
  if (selectedPostId) {
    return (
      <PostDetail
        postId={selectedPostId}
        onBack={() => setSelectedPostId(null)}
        onDeleted={() => {
          setSelectedPostId(null);
          if (activeCategoryId) loadCategoryPosts(activeCategoryId);
        }}
      />
    );
  }

  return (
    <div className="space-y-6">
      {/* ...existing JSX unchanged... */}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd frontend && npm test -- Forum`
Expected: PASS.

- [ ] **Step 5: Run the full frontend suite and build**

Run: `cd frontend && npm test`
Expected: PASS — 14 prior tests, `Forum.test.jsx` extended in place = 14.

Run: `cd frontend && npm run build`
Expected: succeeds.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/Forum.jsx frontend/src/pages/Forum.test.jsx
git commit -m "feat: add forum post detail with comments, upvote, and delete"
```

---

## Self-Review

**Spec coverage (UI Plan 4 slice):** Post-session review with rating+comment → Task 1, spec §5.6.6 reviews. Average rating + earned badges visible to the user → Task 2, spec §5.6.6/§5.6.7 reviews+badges. Community forums (categories, posts, search, comments, upvote) → Tasks 3-4, spec §5.6.8 forums. Admin moderation of forum content already exists server-side and has no UI in this plan by design — that's Admin UI's scope, not this one's.

**Placeholder scan:** No TBD/TODO; every step has complete code. Task 3 deliberately ships non-clickable post rows (Task 4 makes them clickable) — a real, working, testable slice (browse/search/create) on its own, not a placeholder; call-site wiring for the click handler doesn't exist until Task 4 actually builds the thing it would call.

**Type consistency:** `CreateReviewRequest`'s fields (`rating`, `comments`) match Task 1's POST body exactly. `RatingSummaryDto`'s fields (`averageRating`, `reviewCount`) and `BadgeDto`'s fields (`skillId`, `skillName`, `badgeType`, `awardedDate`) match Task 2's usage exactly. `ForumPostDto`'s fields (`id`, `categoryId`, `userId`, `authorName`, `title`, `content`, `upvoteCount`, `commentCount`, `createdDate`) and `ForumCommentDto`'s fields (`id`, `postId`, `userId`, `authorName`, `commentText`, `createdDate`) match exactly across Tasks 3-4's `PostRow`/`PostDetail`. `CreateForumPostRequest`'s fields (`title`, `content`) and `CreateForumCommentRequest`'s field (`commentText`) match the POST bodies exactly.

**Scope check:** Four tasks, no backend changes, reuses every existing UI primitive from Plans 1-3. The `/forum` route and its Nav link (present since UI Plan 1 but unrouted until now) are finally connected.

**Deliberate simplifications (flagged for the record):**
- No `reviewed`/`upvoted` flag persists across a page reload (no such field exists on `SessionDto`/`ForumPostDto`) — the optimistic `Set`-based hide/disable is session-local only, exactly mirroring `Matches.jsx`'s `requestedIds` precedent from UI Plan 2. A user who reloads the page can attempt the same action again and will see the backend's 409 message rather than a pre-emptively disabled button — this is a known, accepted UX rough edge, not a bug.
- Forum search has no debounce and requires an explicit "Search" submit — acceptable at this project's scale (YAGNI), consistent with Sessions/Matches not needing any either.
- No pagination anywhere in this plan (post lists, comment lists, badge lists) — acceptable at this project's scale, matching the precedent set by UI Plan 3's transaction history.
- The multi-line `<textarea>` fields (review comments, post content, comment text) are hand-styled inline rather than a new shadcn `Textarea` primitive — a deliberate YAGNI call given how few occurrences exist, not an oversight.
