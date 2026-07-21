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
  const [error, setError] = useState('');

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
    setError('');
    try {
      await api.post('/matches/request', { targetUserId });
      setRequestedIds((prev) => new Set(prev).add(targetUserId));
      loadMatches();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not send request');
    }
  }

  async function respond(matchId, status) {
    setError('');
    try {
      await api.put(`/matches/${matchId}`, { status });
      loadMatches();
    } catch (err) {
      setError(err.response?.data?.message ?? 'Could not update match');
    }
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold">Matches</h1>
      {error && <p role="alert" className="text-sm text-destructive">{error}</p>}
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
