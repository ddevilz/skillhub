import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import api from '../api/client';
import Matches from './Matches';

vi.mock('../api/client');

test('renders suggestions and my matches, resolving the other participant name', async () => {
  localStorage.setItem('token', 'test-token');
  api.get.mockImplementation((url) => {
    if (url === '/matches/suggestions') {
      return Promise.resolve({ data: [{ userId: 2, fullName: 'Teacher Two', city: 'Pune', matchedSkills: 1, compatibilityScore: 100 }] });
    }
    if (url === '/matches') {
      return Promise.resolve({ data: [{ id: 1, userAId: 2, userBId: 1, status: 'PENDING', createdDate: '2026-08-01T10:00:00' }] });
    }
    if (url === '/users/2') {
      return Promise.resolve({ data: { id: 2, fullName: 'Teacher Two', city: 'Pune' } });
    }
    if (url === '/me') {
      return Promise.resolve({ data: { id: 1, fullName: 'Me', email: 'me@example.com', role: 'USER' } });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Matches />
      </MemoryRouter>
    </AuthProvider>
  );

  await waitFor(() => expect(screen.getAllByText('Teacher Two')).toHaveLength(2));
  expect(screen.getByRole('button', { name: /send request/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /accept/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /reject/i })).toBeInTheDocument();
});
