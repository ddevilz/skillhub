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
