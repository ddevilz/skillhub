import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '@/auth/AuthContext';
import Nav from './Nav';

test('renders the brand and primary navigation links', () => {
  render(
    <AuthProvider>
      <MemoryRouter>
        <Nav />
      </MemoryRouter>
    </AuthProvider>
  );
  expect(screen.getByText('SkillSwap Hub')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Dashboard' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Skills' })).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Sessions' })).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /account menu/i })).toBeInTheDocument();
});
