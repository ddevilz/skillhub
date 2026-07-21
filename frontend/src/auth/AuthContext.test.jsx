import { render, screen, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import { AuthProvider, useAuth } from './AuthContext';
import api from '../api/client';

vi.mock('../api/client');

function Probe() {
  const { user } = useAuth();
  return <div>{user ? user.fullName : 'no user yet'}</div>;
}

test('hydrates user from /me when a token exists in localStorage but user is not yet loaded', async () => {
  localStorage.setItem('token', 'fake-token');
  api.get.mockResolvedValue({ data: { id: 1, fullName: 'Hydrated User', email: 'h@example.com', role: 'USER' } });

  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>
  );

  await waitFor(() => expect(screen.getByText('Hydrated User')).toBeInTheDocument());
  expect(api.get).toHaveBeenCalledWith('/me');

  localStorage.removeItem('token');
});

test('does not attempt to hydrate when no token is present', () => {
  localStorage.removeItem('token');
  api.get.mockClear();

  render(
    <AuthProvider>
      <Probe />
    </AuthProvider>
  );

  expect(screen.getByText('no user yet')).toBeInTheDocument();
  expect(api.get).not.toHaveBeenCalled();
});
