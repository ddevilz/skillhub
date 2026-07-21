import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AuthProvider } from '../auth/AuthContext';
import Register from './Register';

test('renders the registration form', () => {
  render(
    <AuthProvider>
      <MemoryRouter>
        <Register />
      </MemoryRouter>
    </AuthProvider>
  );
  expect(screen.getByRole('heading', { name: /create account/i })).toBeInTheDocument();
  expect(screen.getByLabelText(/full name/i)).toBeInTheDocument();
  expect(screen.getByLabelText(/email/i)).toBeInTheDocument();
  expect(screen.getByLabelText(/password/i)).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /register/i })).toBeInTheDocument();
});
