import { useEffect, useState } from 'react';
import api from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
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
    api.get('/me/credits').then((res) => setCredits(res.data)).catch(() => {});
    api.get('/me/credits/transactions').then((res) => setTransactions(res.data)).catch(() => {});
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

  const [credits, setCredits] = useState(null);
  const [transactions, setTransactions] = useState([]);

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
              <span>{skillsById[s.skillId] ?? `Skill #${s.skillId}`}</span> with{' '}
              <span>{other ? other.fullName : `User #${otherId}`}</span>
              {' · '}
              {teaching ? 'Teaching' : 'Learning'}
            </p>
            <p className="text-sm text-muted-foreground">
              {s.mode === 'ONLINE' && s.locationOrLink ? (
                <a href={s.locationOrLink} target="_blank" rel="noreferrer" className="underline">
                  {s.locationOrLink}
                </a>
              ) : (
                s.locationOrLink || s.mode
              )}
            </p>
            <Badge variant={s.status === 'CANCELLED' ? 'destructive' : s.status === 'COMPLETED' ? 'default' : 'secondary'}>
              {s.status}
            </Badge>
          </div>
          <div className="flex gap-2">
            {canConfirm && <Button size="sm" onClick={() => runAction(s.id, 'confirm')}>Confirm</Button>}
            {canComplete && <Button size="sm" onClick={() => runAction(s.id, 'complete')}>Complete</Button>}
            {canCancel && <Button size="sm" variant="outline" onClick={() => openReschedule(s)}>Reschedule</Button>}
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

      <Dialog open={rescheduleTarget != null} onOpenChange={(open) => !open && setRescheduleTarget(null)}>
        <DialogContent>
          <form onSubmit={submitReschedule} className="space-y-4">
            <DialogHeader>
              <DialogTitle>Reschedule session</DialogTitle>
              <DialogDescription>Pick a new date and time; the other participant will need to re-confirm.</DialogDescription>
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
    </div>
  );
}
