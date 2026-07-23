import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
    if (url === '/forum/posts/5') {
      return Promise.resolve({ data: { id: 5, categoryId: 1, userId: 2, authorName: 'Blake Mentor', title: 'Welcome thread', content: 'Say hi!', upvoteCount: 2, commentCount: 1, createdDate: '2026-07-01T09:00:00' } });
    }
    if (url === '/forum/posts/5/comments') {
      return Promise.resolve({ data: [{ id: 20, postId: 5, userId: 1, authorName: 'Me', commentText: 'Hi there!', createdDate: '2026-07-01T10:00:00' }] });
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

  const user = userEvent.setup();

  expect(await screen.findByRole('tab', { name: 'General' })).toBeInTheDocument();
  expect(await screen.findByText('Welcome thread')).toBeInTheDocument();
  expect(screen.getByText(/blake mentor/i)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /new post/i })).toBeInTheDocument();

  const postLink = screen.getByRole('button', { name: /welcome thread/i });
  await user.click(postLink);

  expect(await screen.findByText('Say hi!')).toBeInTheDocument();
  expect(screen.getByText('Hi there!')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /upvote/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /back to forum/i })).toBeInTheDocument();
});

test('shows a Moderate Post button to an admin viewing someone else\'s post', async () => {
  localStorage.setItem('token', 'test-token');

  api.get.mockImplementation((url) => {
    if (url === '/me') {
      return Promise.resolve({ data: { id: 99, fullName: 'Admin User', email: 'admin@example.com', role: 'ADMIN' } });
    }
    if (url === '/forum/categories') {
      return Promise.resolve({ data: [{ id: 1, categoryName: 'General', description: 'General chat' }] });
    }
    if (url === '/forum/categories/1/posts') {
      return Promise.resolve({
        data: [{ id: 5, categoryId: 1, userId: 2, authorName: 'Blake Mentor', title: 'Welcome thread', content: 'Say hi!', upvoteCount: 2, commentCount: 0, createdDate: '2026-07-01T09:00:00' }],
      });
    }
    if (url === '/forum/posts/5') {
      return Promise.resolve({ data: { id: 5, categoryId: 1, userId: 2, authorName: 'Blake Mentor', title: 'Welcome thread', content: 'Say hi!', upvoteCount: 2, commentCount: 0, createdDate: '2026-07-01T09:00:00' } });
    }
    if (url === '/forum/posts/5/comments') {
      return Promise.resolve({ data: [] });
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

  const user = userEvent.setup();
  const postLink = await screen.findByRole('button', { name: /welcome thread/i });
  await user.click(postLink);

  expect(await screen.findByRole('button', { name: /moderate post/i })).toBeInTheDocument();
  expect(screen.queryByRole('button', { name: /^delete$/i })).not.toBeInTheDocument();
});
