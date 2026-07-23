import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import api from '../api/client';
import Forum from './Forum';

vi.mock('../api/client');

test('renders forum categories and posts for the active category', async () => {
  localStorage.setItem('token', 'test-token');

  api.get.mockImplementation((url) => {
    if (url === '/me') {
      return Promise.resolve({ data: { id: 1, fullName: 'Me', email: 'me@example.com', role: 'USER' } });
    }
    if (url === '/forum/categories') {
      return Promise.resolve({ data: [{ id: 1, categoryName: 'General', description: 'General chat' }] });
    }
    if (url === '/forum/categories/1/posts') {
      return Promise.resolve({
        data: [{ id: 5, categoryId: 1, userId: 2, authorName: 'Blake Mentor', title: 'Welcome thread', content: 'Say hi!', upvoteCount: 2, commentCount: 1, createdDate: '2026-07-01T09:00:00' }],
      });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Forum />
      </MemoryRouter>
    </AuthProvider>
  );

  expect(await screen.findByRole('tab', { name: 'General' })).toBeInTheDocument();
  expect(await screen.findByText('Welcome thread')).toBeInTheDocument();
  expect(screen.getByText(/blake mentor/i)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /new post/i })).toBeInTheDocument();
});
