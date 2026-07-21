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
