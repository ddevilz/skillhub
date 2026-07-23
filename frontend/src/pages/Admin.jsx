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
      loadUsers();
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
                <p className="text-sm text-muted-foreground">{u.email} · {u.city ?? 'No city'} · {u.role} · Joined {u.createdDate}</p>
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
            {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
            <DialogFooter>
              <Button type="submit" disabled={!verifySkillId}>Grant</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  );
}

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
                  <span>{profiles[r.reviewerUserId]?.fullName ?? `User #${r.reviewerUserId}`}</span> rated{' '}
                  <span>{profiles[r.ratedUserId]?.fullName ?? `User #${r.ratedUserId}`}</span> {r.rating}/5
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
          <TabsTrigger value="catalog">Catalog</TabsTrigger>
          <TabsTrigger value="moderation">Moderation</TabsTrigger>
          <TabsTrigger value="reports">Reports</TabsTrigger>
        </TabsList>
        <TabsContent value="users">
          <UsersTab />
        </TabsContent>
        <TabsContent value="catalog">
          <CatalogTab />
        </TabsContent>
        <TabsContent value="moderation">
          <ModerationTab />
        </TabsContent>
        <TabsContent value="reports">
          <ReportsTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}
