import { useEffect, useState } from 'react';
import api from '../api/client';
import { useAuth } from '../auth/AuthContext';

export default function Dashboard() {
  const { logout } = useAuth();
  const [profile, setProfile] = useState(null);

  useEffect(() => {
    api.get('/me').then((res) => setProfile(res.data)).catch(() => {});
  }, []);

  return (
    <div>
      <h1>Dashboard</h1>
      {profile && <p>Welcome, {profile.fullName}</p>}
      <button onClick={logout}>Log out</button>
    </div>
  );
}
