import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import api from '../api/client';
import Sessions from './Sessions';

vi.mock('../api/client');

test('renders upcoming sessions with resolved names, skill, and a confirm action', async () => {
  localStorage.setItem('token', 'test-token');

  api.get.mockImplementation((url) => {
    if (url === '/me') {
      return Promise.resolve({ data: { id: 1, fullName: 'Me', email: 'me@example.com', role: 'USER' } });
    }
    if (url === '/skills') {
      return Promise.resolve({ data: [{ id: 4, skillName: 'Python', category: 'Technology', description: null }] });
    }
    if (url === '/sessions') {
      return Promise.resolve({
        data: [
          {
            id: 10, matchId: 1, skillId: 4, teacherUserId: 2, learnerUserId: 1, scheduledByUserId: 2,
            sessionDate: '2026-08-01', startTime: '10:00:00', endTime: '11:00:00',
            mode: 'ONLINE', locationOrLink: 'https://meet.example/abc', status: 'PENDING',
            createdDate: '2026-07-20T09:00:00',
          },
        ],
      });
    }
    if (url === '/users/2') {
      return Promise.resolve({ data: { id: 2, fullName: 'Teacher Two', city: 'Pune' } });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Sessions />
      </MemoryRouter>
    </AuthProvider>
  );

  await waitFor(() => expect(screen.getByText('Teacher Two')).toBeInTheDocument());
  expect(screen.getByText('Python')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /confirm/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
});

test('opens the new session dialog and shows match/teacher/skill fields', async () => {
  localStorage.setItem('token', 'test-token');

  api.get.mockImplementation((url) => {
    if (url === '/me') {
      return Promise.resolve({ data: { id: 1, fullName: 'Me', email: 'me@example.com', role: 'USER' } });
    }
    if (url === '/skills') {
      return Promise.resolve({ data: [{ id: 4, skillName: 'Python', category: 'Technology', description: null }] });
    }
    if (url === '/sessions') {
      return Promise.resolve({ data: [] });
    }
    if (url === '/matches') {
      return Promise.resolve({ data: [{ id: 1, userAId: 1, userBId: 2, status: 'ACCEPTED', createdDate: '2026-07-20T09:00:00' }] });
    }
    if (url === '/users/2') {
      return Promise.resolve({ data: { id: 2, fullName: 'Teacher Two', city: 'Pune' } });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Sessions />
      </MemoryRouter>
    </AuthProvider>
  );

  const user = userEvent.setup();
  const newSessionButton = await screen.findByRole('button', { name: /new session/i });
  await user.click(newSessionButton);

  expect(await screen.findByText(/teacher two/i)).toBeInTheDocument();
  expect(screen.getByLabelText(/who teaches/i)).toBeInTheDocument();
  expect(screen.getByLabelText(/^skill$/i)).toBeInTheDocument();
});
