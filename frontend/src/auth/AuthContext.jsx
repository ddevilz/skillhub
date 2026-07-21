import { createContext, useContext, useEffect, useState } from 'react';
import api from '../api/client';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [token, setToken] = useState(() => localStorage.getItem('token'));
  const [user, setUser] = useState(null);

  useEffect(() => {
    if (token && !user) {
      api.get('/me').then((res) => setUser(res.data)).catch(() => {});
    }
  }, [token, user]);

  function persist(res) {
    const { token: t, ...profile } = res.data;
    localStorage.setItem('token', t);
    setToken(t);
    setUser(profile);
    return profile;
  }

  async function login(email, password) {
    return persist(await api.post('/auth/login', { email, password }));
  }

  async function register(payload) {
    return persist(await api.post('/auth/register', payload));
  }

  function logout() {
    localStorage.removeItem('token');
    setToken(null);
    setUser(null);
  }

  return (
    <AuthContext.Provider value={{ user, token, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
