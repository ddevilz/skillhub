import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import api from '../api/client';
import Skills from './Skills';

vi.mock('../api/client');

test('renders my teach and learn skills, split by type', async () => {
  api.get.mockImplementation((url) => {
    if (url === '/skills') {
      return Promise.resolve({ data: [{ id: 4, skillName: 'Python', category: 'Technology', description: null }] });
    }
    if (url === '/me/skills') {
      return Promise.resolve({
        data: [
          { id: 1, skillId: 4, skillName: 'Python', category: 'Technology', skillType: 'CAN_TEACH', experience: '2 years', proficiency: 'Advanced' },
          { id: 2, skillId: 4, skillName: 'Python', category: 'Technology', skillType: 'WANT_TO_LEARN', experience: null, proficiency: null },
        ],
      });
    }
    return Promise.reject(new Error('unexpected url ' + url));
  });

  render(
    <AuthProvider>
      <MemoryRouter>
        <Skills />
      </MemoryRouter>
    </AuthProvider>
  );

  await waitFor(() => expect(screen.getAllByText('Python')).toHaveLength(2));
  expect(screen.getByRole('button', { name: /add skill/i })).toBeInTheDocument();
});
