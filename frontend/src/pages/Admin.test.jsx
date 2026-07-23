import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import api from '../api/client';
import Admin from './Admin';

vi.mock('../api/client');

test('renders the users tab with search results and a status toggle', async () => {
  localStorage.setItem('token', 'test-token');

  api.get.mockImplementation((url) => {
    if (url === '/me') {
      return Promise.resolve({ data: { id: 1, fullName: 'Admin User', email: 'admin@example.com', role: 'ADMIN' } });
    }
    if (url === '/admin/users') {
      return Promise.resolve({
        data: [
          { id: 2, fullName: 'Alex Tester', email: 'alex.tester@example.com', city: 'Pune', role: 'USER', active: true, createdDate: '2026-07-01T09:00:00' },
        ],
      });
    }
    if (url === '/skills') {
      return Promise.resolve({ data: [{ id: 4, skillName: 'Python', category: 'Technology', description: null }] });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Admin />
      </MemoryRouter>
    </AuthProvider>
  );

  expect(await screen.findByText('Alex Tester')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /deactivate/i })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /grant verified badge/i })).toBeInTheDocument();
});

test('shows an access-denied message for a non-admin user', async () => {
  localStorage.setItem('token', 'test-token');

  api.get.mockImplementation((url) => {
    if (url === '/me') {
      return Promise.resolve({ data: { id: 1, fullName: 'Regular User', email: 'user@example.com', role: 'USER' } });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Admin />
      </MemoryRouter>
    </AuthProvider>
  );

  expect(await screen.findByText(/don't have access/i)).toBeInTheDocument();
});

test('renders the catalog tab with skills and categories, and opens an edit dialog', async () => {
  localStorage.setItem('token', 'test-token');

  api.get.mockImplementation((url) => {
    if (url === '/me') {
      return Promise.resolve({ data: { id: 1, fullName: 'Admin User', email: 'admin@example.com', role: 'ADMIN' } });
    }
    if (url === '/admin/users') return Promise.resolve({ data: [] });
    if (url === '/skills') {
      return Promise.resolve({ data: [{ id: 4, skillName: 'Python', category: 'Technology', description: null }] });
    }
    if (url === '/forum/categories') {
      return Promise.resolve({ data: [{ id: 1, categoryName: 'General Discussion', description: 'Say hi' }] });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Admin />
      </MemoryRouter>
    </AuthProvider>
  );

  const user = userEvent.setup();
  const catalogTab = await screen.findByRole('tab', { name: /catalog/i });
  await user.click(catalogTab);

  expect(await screen.findByText('Python')).toBeInTheDocument();
  expect(screen.getByText('General Discussion')).toBeInTheDocument();

  const editButtons = screen.getAllByRole('button', { name: /^edit$/i });
  await user.click(editButtons[0]);
  expect(await screen.findByRole('button', { name: /save/i })).toBeInTheDocument();
});

test('renders the moderation tab with flagged reviews and moderated forum content', async () => {
  localStorage.setItem('token', 'test-token');

  api.get.mockImplementation((url) => {
    if (url === '/me') {
      return Promise.resolve({ data: { id: 1, fullName: 'Admin User', email: 'admin@example.com', role: 'ADMIN' } });
    }
    if (url === '/admin/users') return Promise.resolve({ data: [] });
    if (url === '/skills') return Promise.resolve({ data: [] });
    if (url === '/forum/categories') return Promise.resolve({ data: [] });
    if (url === '/admin/reviews/flagged') {
      return Promise.resolve({
        data: [{ id: 9, sessionId: 10, reviewerUserId: 2, ratedUserId: 3, rating: 1, comments: 'Rude', flagged: true, createdDate: '2026-07-01T09:00:00' }],
      });
    }
    if (url === '/users/2') return Promise.resolve({ data: { id: 2, fullName: 'Reviewer Two', city: null } });
    if (url === '/users/3') return Promise.resolve({ data: { id: 3, fullName: 'Rated Three', city: null } });
    if (url === '/admin/forum/posts/moderated') {
      return Promise.resolve({ data: [{ id: 5, categoryId: 1, userId: 2, authorName: 'Reviewer Two', title: 'Bad post', content: 'x', upvoteCount: 0, commentCount: 0, createdDate: '2026-07-01T09:00:00' }] });
    }
    if (url === '/admin/forum/comments/moderated') {
      return Promise.resolve({ data: [] });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Admin />
      </MemoryRouter>
    </AuthProvider>
  );

  const user = userEvent.setup();
  const moderationTab = await screen.findByRole('tab', { name: /moderation/i });
  await user.click(moderationTab);

  expect(await screen.findByText('Reviewer Two')).toBeInTheDocument();
  expect(screen.getByText('Rated Three')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /unflag/i })).toBeInTheDocument();
  expect(screen.getByText('Bad post')).toBeInTheDocument();
});

test('renders the reports tab with all five reports', async () => {
  localStorage.setItem('token', 'test-token');

  api.get.mockImplementation((url) => {
    if (url === '/me') {
      return Promise.resolve({ data: { id: 1, fullName: 'Admin User', email: 'admin@example.com', role: 'ADMIN' } });
    }
    if (url === '/admin/users') return Promise.resolve({ data: [] });
    if (url === '/skills') return Promise.resolve({ data: [] });
    if (url === '/forum/categories') return Promise.resolve({ data: [] });
    if (url === '/admin/reviews/flagged') return Promise.resolve({ data: [] });
    if (url === '/admin/forum/posts/moderated') return Promise.resolve({ data: [] });
    if (url === '/admin/forum/comments/moderated') return Promise.resolve({ data: [] });
    if (url === '/admin/reports/users-over-time') {
      return Promise.resolve({ data: [{ date: '2026-07-01', count: 2 }] });
    }
    if (url === '/admin/reports/popular-skills') {
      return Promise.resolve({ data: [{ skillId: 4, skillName: 'Python', count: 5 }] });
    }
    if (url === '/admin/reports/session-stats') {
      return Promise.resolve({ data: { pending: 1, confirmed: 2, completed: 3, cancelled: 0 } });
    }
    if (url === '/admin/reports/top-mentors') {
      return Promise.resolve({ data: [{ userId: 2, fullName: 'Blake Mentor', avgRating: 5.0, reviewCount: 1 }] });
    }
    if (url === '/admin/reports/active-categories') {
      return Promise.resolve({ data: [{ categoryId: 1, categoryName: 'General Discussion', postCount: 2 }] });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Admin />
      </MemoryRouter>
    </AuthProvider>
  );

  const user = userEvent.setup();
  const reportsTab = await screen.findByRole('tab', { name: /reports/i });
  await user.click(reportsTab);

  expect(await screen.findByText('Blake Mentor')).toBeInTheDocument();
  expect(screen.getByText('General Discussion')).toBeInTheDocument();
  expect(screen.getAllByText('Python').length).toBeGreaterThan(0);
  expect(screen.getByText('3')).toBeInTheDocument();
});
