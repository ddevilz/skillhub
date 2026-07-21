import { useEffect, useState } from 'react';
import api from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Card, CardHeader, CardTitle, CardContent } from '@/components/ui/card';

export default function Dashboard() {
  const { logout } = useAuth();
  const [profile, setProfile] = useState(null);
  const [credits, setCredits] = useState(null);
  const [upcomingCount, setUpcomingCount] = useState(null);
  const [unreadCount, setUnreadCount] = useState(null);

  useEffect(() => {
    api.get('/me').then((res) => setProfile(res.data)).catch(() => {});
    api.get('/me/credits').then((res) => setCredits(res.data)).catch(() => {});
    api.get('/sessions', { params: { filter: 'upcoming' } })
      .then((res) => setUpcomingCount(res.data.length))
      .catch(() => {});
    api.get('/notifications/unread-count').then((res) => setUnreadCount(res.data.count)).catch(() => {});
  }, []);

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold">Dashboard</h1>
        {profile && <p className="text-muted-foreground">Welcome, <span>{profile.fullName}</span></p>}
      </div>

      <div className="grid gap-4 sm:grid-cols-3">
        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium text-muted-foreground">Credits</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-bold">{credits ? credits.totalCredits : '—'}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium text-muted-foreground">Upcoming Sessions</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-bold">{upcomingCount !== null ? upcomingCount : '—'}</p>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="text-sm font-medium text-muted-foreground">Unread Notifications</CardTitle>
          </CardHeader>
          <CardContent>
            <p className="text-3xl font-bold">{unreadCount !== null ? unreadCount : '—'}</p>
          </CardContent>
        </Card>
      </div>

      <button onClick={logout} className="text-sm text-muted-foreground underline-offset-4 hover:underline">
        Log out
      </button>
    </div>
  );
}
