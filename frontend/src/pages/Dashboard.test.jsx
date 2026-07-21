import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import api from '../api/client';
import Dashboard from './Dashboard';

vi.mock('../api/client');

test('renders credits, upcoming sessions, and unread notification counts', async () => {
  api.get.mockImplementation((url) => {
    if (url === '/me') return Promise.resolve({ data: { id: 1, fullName: 'Deva', email: 'd@example.com', role: 'USER' } });
    if (url === '/me/credits') return Promise.resolve({ data: { totalCredits: 12, creditsEarned: 3, creditsSpent: 1 } });
    if (url === '/sessions') return Promise.resolve({ data: [{ id: 1 }, { id: 2 }] });
    if (url === '/notifications/unread-count') return Promise.resolve({ data: { count: 4 } });
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Dashboard />
      </MemoryRouter>
    </AuthProvider>
  );

  await waitFor(() => expect(screen.getByText('Deva')).toBeInTheDocument());
  expect(screen.getByText('12')).toBeInTheDocument();
  expect(screen.getByText('2')).toBeInTheDocument();
  expect(screen.getByText('4')).toBeInTheDocument();
});
