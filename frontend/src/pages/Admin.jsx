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
